package com.printkeep.quota.core.processing.pipeline;

import com.printkeep.quota.codec.model.IppPacket;
import com.printkeep.quota.core.processing.decision.IppDecision;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Orchestrator implementing Chain of Responsibility over pipeline processing stages.
 */
@Component
public class PrintProcessingPipeline {

    private static final Logger log = LoggerFactory.getLogger(PrintProcessingPipeline.class);

    private final List<PipelineStage> stages;
    private final MeterRegistry registry;

    private final Counter requestsReceived;
    private final Counter invalidPackets;
    private final Counter identityFailures;
    private final Counter quotaFailures;

    public PrintProcessingPipeline(final List<PipelineStage> stages, final MeterRegistry registry) {
        this.stages = stages;
        this.registry = registry;

        this.requestsReceived = Counter.builder("print.requests.received")
                .description("Total print requests received")
                .register(registry);

        this.invalidPackets = Counter.builder("print.requests.invalid")
                .description("Total requests rejected due to invalid packets")
                .register(registry);

        this.identityFailures = Counter.builder("print.requests.identity.failures")
                .description("Total requests rejected due to identity failures")
                .register(registry);

        this.quotaFailures = Counter.builder("print.requests.quota.failures")
                .description("Total requests rejected due to insufficient quota")
                .register(registry);
    }

    /**
     * Executes the print request through the pipeline stages.
     *
     * @param packet        the raw decoded IPP packet.
     * @param correlationId unique request trace ID.
     * @param host          client hostname.
     * @return the pipeline evaluation result.
     */
    public PipelineResult process(final IppPacket packet, final String correlationId, final String host) {
        requestsReceived.increment();
        final Instant start = Instant.now();
        final PipelineContext context = new PipelineContext(packet, correlationId, host);

        try {
            for (final PipelineStage stage : stages) {
                stage.process(context);
                if (context.getDecision() != null && context.getDecision() != IppDecision.ALLOW) {
                    // Early exit if rejected
                    break;
                }
            }

            if (context.getDecision() == null) {
                context.setDecision(IppDecision.ALLOW);
            }
        } catch (final Exception e) {
            log.error("[CorrID: {}] Unexpected pipeline stage failure", correlationId, e);
            context.setException(e);
            if (context.getDecision() == null) {
                context.setDecision(IppDecision.SYSTEM_ERROR);
                context.setReason("Internal pipeline processing error: " + e.getMessage());
            }
        }

        final Duration duration = Duration.between(start, Instant.now());
        final long latencyMs = duration.toMillis();

        // Log latency metric
        Timer.builder("print.evaluation.latency")
                .description("Print evaluation latency in milliseconds")
                .register(registry)
                .record(duration);

        // Increment decision metrics
        Counter.builder("print.requests.processed")
                .tag("decision", context.getDecision().name())
                .register(registry)
                .increment();

        // Increment specific failure counters
        switch (context.getDecision()) {
            case REJECT_INVALID_REQUEST -> invalidPackets.increment();
            case REJECT_UNKNOWN_USER, REJECT_DISABLED_USER -> identityFailures.increment();
            case REJECT_INSUFFICIENT_QUOTA -> quotaFailures.increment();
            default -> { }
        }

        final int estimatedPages = context.getMetadata() != null ? context.getMetadata().estimatedPages() : 0;
        final int copies = context.getMetadata() != null ? context.getMetadata().requestedCopies() : 0;
        final String username = context.getMetadata() != null ? context.getMetadata().username() : "unknown";
        final String printerName = context.getMetadata() != null ? context.getMetadata().printerName() : "unknown";

        return new PipelineResult(
                context.getDecision(),
                context.getReason(),
                correlationId,
                estimatedPages,
                copies,
                username,
                printerName,
                latencyMs
        );
    }
}
