package com.printkeep.quota.core.proxy.service;

import com.printkeep.quota.codec.model.IppPacket;
import com.printkeep.quota.codec.parser.IppParser;
import com.printkeep.quota.core.processing.decision.IppDecision;
import com.printkeep.quota.core.processing.pipeline.PipelineResult;
import com.printkeep.quota.core.processing.pipeline.PrintProcessingPipeline;
import com.printkeep.quota.core.processing.service.PrintTransactionService;
import com.printkeep.quota.core.proxy.client.IppHttpClient;
import com.printkeep.quota.core.proxy.response.IppResponseGenerator;
import com.printkeep.quota.core.proxy.routing.PrinterRoutingService;
import com.printkeep.quota.core.proxy.stream.IppStreamPartitioner;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Core orchestrating proxy service intercepting print streams, evaluating decisions,
 * and forwarding allowed streams to target printers.
 */
@Service
public class PrinterProxyService {

    private static final Logger log = LoggerFactory.getLogger(PrinterProxyService.class);

    private final PrintProcessingPipeline pipeline;
    private final PrinterRoutingService routingService;
    private final IppHttpClient ippHttpClient;
    private final IppResponseGenerator responseGenerator;
    private final PrintTransactionService transactionService;
    private final MeterRegistry registry;

    private final Counter requestsProxied;
    private final Counter printerFailures;
    private final Counter rejectedJobs;
    private final Counter quotaRefunds;

    public PrinterProxyService(
            final PrintProcessingPipeline pipeline,
            final PrinterRoutingService routingService,
            final IppHttpClient ippHttpClient,
            final IppResponseGenerator responseGenerator,
            final PrintTransactionService transactionService,
            final MeterRegistry registry) {
        this.pipeline = pipeline;
        this.routingService = routingService;
        this.ippHttpClient = ippHttpClient;
        this.responseGenerator = responseGenerator;
        this.transactionService = transactionService;
        this.registry = registry;

        this.requestsProxied = Counter.builder("proxy.requests.proxied")
                .description("Total print requests proxied")
                .register(registry);

        this.printerFailures = Counter.builder("proxy.printer.failures")
                .description("Total connection or server failures from target printers")
                .register(registry);

        this.rejectedJobs = Counter.builder("proxy.jobs.rejected")
                .description("Total print jobs rejected by quota middleware")
                .register(registry);

        this.quotaRefunds = Counter.builder("quota.refunds.total")
                .description("Total automatic quota refunds issued after printer forwarding failure")
                .register(registry);
    }

    /**
     * Intercepts, validates, and proxies an incoming binary IPP print job stream.
     *
     * @param in            incoming binary print stream.
     * @param out           client response output stream.
     * @param printerName   logical target printer.
     * @param correlationId request trace correlation token.
     * @param clientHost    client host address.
     * @param verifiedClientCN verified client certificate common name.
     * @throws IOException if network or write failures occur.
     */
    public void proxyPrintJob(
            final InputStream in,
            final OutputStream out,
            final String printerName,
            final String correlationId,
            final String clientHost,
            final String verifiedClientCN) throws IOException {

        requestsProxied.increment();
        // 1. Capture attributes header block without loading print document in memory
        byte[] capturedHeader;
        try {
            capturedHeader = IppStreamPartitioner.captureAttributesBlock(in);
        } catch (final Exception e) {
            log.error("[CorrID: {}] Failed to parse IPP request attributes block", correlationId, e);
            final byte[] errBytes = responseGenerator.generateErrorResponse(
                    (byte) 2, (byte) 0, (short) 0x0400, 1, "Malformed IPP attributes: " + e.getMessage()
            );
            out.write(errBytes);
            rejectedJobs.increment();
            return;
        }

        // 2. Decode the header attributes to read target properties
        IppPacket packet;
        try (final ByteArrayInputStream bin = new ByteArrayInputStream(capturedHeader)) {
            packet = IppParser.parse(bin);
        } catch (final Exception e) {
            log.error("[CorrID: {}] Failed to parse captured IPP attributes", correlationId, e);
            final byte[] errBytes = responseGenerator.generateErrorResponse(
                    (byte) 2, (byte) 0, (short) 0x0400, 1, "Malformed IPP parameters: " + e.getMessage()
            );
            out.write(errBytes);
            rejectedJobs.increment();
            return;
        }

        // 3. Process the print job decision pipeline
        final PipelineResult result = pipeline.process(packet, correlationId, clientHost, verifiedClientCN);

        if (!result.isAllowed()) {
            log.warn("[CorrID: {}] Print job rejected. Decision: {}, Reason: {}",
                    correlationId, result.decision(), result.reason());

            final short ippStatus = mapDecisionToIppStatus(result.decision());
            final byte[] errBytes = responseGenerator.generateErrorResponse(
                    packet.majorVersion(),
                    packet.minorVersion(),
                    ippStatus,
                    packet.transactionId(),
                    result.reason()
            );
            out.write(errBytes);
            rejectedJobs.increment();
            return;
        }

        // 4. Resolve physical printer destination
        final Optional<String> printerUriOpt = routingService.resolvePrinterUri(printerName);
        if (printerUriOpt.isEmpty()) {
            log.error("[CorrID: {}] Target printer logical name '{}' cannot be resolved", correlationId, printerName);
            final byte[] errBytes = responseGenerator.generateErrorResponse(
                    packet.majorVersion(),
                    packet.minorVersion(),
                    (short) 0x0406, // client-error-not-found
                    packet.transactionId(),
                    "Destination printer not found: " + printerName
            );
            out.write(errBytes);
            rejectedJobs.increment();
            return;
        }

        final String printerUri = printerUriOpt.get();

        // 5. Combine attributes header and remaining raw document payload into a single streaming object
        final InputStream compositeRequest = new SequenceInputStream(
                new ByteArrayInputStream(capturedHeader),
                in
        );

        // 6. Forward print stream to the destination printer and stream the response back
        log.info("[CorrID: {}] Print job ALLOWED. Forwarding stream to logical printer: {} (URI: {})",
                correlationId, printerName, printerUri);

        try {
            final Instant forwardStart = Instant.now();
            try (final InputStream printerResponse = ippHttpClient.sendStream(printerUri, compositeRequest)) {
                // Relay response directly back to the client spooler
                printerResponse.transferTo(out);
            }

            final Duration duration = Duration.between(forwardStart, Instant.now());
            Timer.builder("proxy.forwarding.latency")
                    .description("IPP proxy forwarding latency in milliseconds")
                    .tag("printer", printerName)
                    .register(registry)
                    .record(duration);

            log.info("[CorrID: {}] Forwarding complete. Relayed response to client spooler.", correlationId);
        } catch (final Exception e) {
            log.error("[CorrID: {}] Error forwarding print stream to printer {}", correlationId, printerUri, e);
            printerFailures.increment();

            // Refund the pages that were deducted during quota reservation, since the job did not reach the printer
            if (result.context() != null) {
                transactionService.refundQuota(result.context());
                quotaRefunds.increment();
                log.info("[CorrID: {}] Quota refund issued for {} page(s) due to printer forwarding failure",
                        correlationId, result.estimatedPages());
            }

            final byte[] errBytes = responseGenerator.generateErrorResponse(
                    packet.majorVersion(),
                    packet.minorVersion(),
                    (short) 0x0500, // server-error-internal-error
                    packet.transactionId(),
                    "Failed to communicate with physical printer: " + e.getMessage()
            );
            out.write(errBytes);
        }
    }

    private short mapDecisionToIppStatus(final IppDecision decision) {
        return switch (decision) {
            case REJECT_INSUFFICIENT_QUOTA -> (short) 0x0401; // client-error-not-authorized
            case REJECT_UNKNOWN_USER -> (short) 0x0401;       // client-error-not-authorized
            case REJECT_DISABLED_USER -> (short) 0x0401;      // client-error-not-authorized
            case REJECT_INVALID_REQUEST -> (short) 0x0400;    // client-error-bad-request
            default -> (short) 0x0500;                        // server-error-internal-error
        };
    }
}
