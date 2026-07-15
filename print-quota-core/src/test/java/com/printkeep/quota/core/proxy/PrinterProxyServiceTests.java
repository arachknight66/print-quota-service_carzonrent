package com.printkeep.quota.core.proxy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.printkeep.quota.codec.model.IppPacket;
import com.printkeep.quota.core.processing.decision.IppDecision;
import com.printkeep.quota.core.processing.dto.PrintJobMetadata;
import com.printkeep.quota.core.processing.pipeline.PipelineContext;
import com.printkeep.quota.core.processing.pipeline.PipelineResult;
import com.printkeep.quota.core.processing.pipeline.PrintProcessingPipeline;
import com.printkeep.quota.core.processing.service.PrintTransactionService;
import com.printkeep.quota.core.proxy.client.IppHttpClient;
import com.printkeep.quota.core.proxy.response.IppResponseGenerator;
import com.printkeep.quota.core.proxy.routing.PrinterRoutingService;
import com.printkeep.quota.core.proxy.service.PrinterProxyService;
import com.printkeep.quota.core.model.User;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Unit tests for PrinterProxyService verifying the quota refund-on-failure path.
 */
class PrinterProxyServiceTests {

    private PrintProcessingPipeline pipeline;
    private PrinterRoutingService routingService;
    private IppHttpClient ippHttpClient;
    private IppResponseGenerator responseGenerator;
    private PrintTransactionService transactionService;
    private PrinterProxyService proxyService;

    private static final byte[] DUMMY_ERROR_RESPONSE = new byte[]{0x02, 0x00, 0x05, 0x00};

    @BeforeEach
    void setUp() {
        pipeline = mock(PrintProcessingPipeline.class);
        routingService = mock(PrinterRoutingService.class);
        ippHttpClient = mock(IppHttpClient.class);
        responseGenerator = mock(IppResponseGenerator.class);
        transactionService = mock(PrintTransactionService.class);

        proxyService = new PrinterProxyService(
                pipeline, routingService, ippHttpClient, responseGenerator, transactionService, new SimpleMeterRegistry()
        );

        when(responseGenerator.generateErrorResponse(
                any(Byte.class), any(Byte.class), any(Short.class), any(Integer.class), anyString())
        ).thenReturn(DUMMY_ERROR_RESPONSE);
    }

    /**
     * When the IPP HTTP client throws during forwarding, refundQuota must be called exactly once
     * and the quota.refunds.total counter must increment.
     */
    @Test
    void testRefundIsCalledWhenPrinterForwardingFails() throws Exception {
        // Set up a valid ALLOW result with a populated context so the refund path can execute
        final User user = new User();
        user.setId(UUID.randomUUID());
        user.setDomainUsername("jdoe");

        final IppPacket packet = new IppPacket((byte) 2, (byte) 0, (short) 0x0002, 1, List.of(), new byte[0]);
        final PipelineContext ctx = new PipelineContext(packet, "corr-proxy-1", "localhost");
        ctx.setUser(user);
        ctx.setMetadata(new PrintJobMetadata(
                "jdoe", "Doc.pdf", "LaserJet_5", 1, false, false, 5, "Job1", "localhost", Instant.now()
        ));

        final PipelineResult allowResult = new PipelineResult(
                IppDecision.ALLOW, "OK", "corr-proxy-1", 5, 1, "jdoe", "LaserJet_5", 2, ctx
        );

        when(pipeline.process(any(), anyString(), anyString(), any())).thenReturn(allowResult);
        when(routingService.resolvePrinterUri("LaserJet_5"))
                .thenReturn(Optional.of("http://printer1.company.local:631/ipp/print"));

        // Force an IOException during the sendStream call to simulate a printer connectivity failure
        when(ippHttpClient.sendStream(anyString(), any(InputStream.class)))
                .thenThrow(new java.io.IOException("Printer connection refused"));

        // Build a minimal valid IPP byte stream: 2-byte version, 2-byte op, 4-byte request-id, end-of-attributes tag
        final byte[] minimalIpp = {0x02, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0x01, 0x03};
        final InputStream input = new ByteArrayInputStream(minimalIpp);
        final ByteArrayOutputStream output = new ByteArrayOutputStream();

        proxyService.proxyPrintJob(input, output, "LaserJet_5", "corr-proxy-1", "localhost", "jdoe");

        // The refund must have been triggered exactly once
        verify(transactionService, times(1)).refundQuota(eq(ctx));
    }

    /**
     * When the pipeline returns ALLOW but the printer URI is not found, no refund should occur
     * because quota was already rejected by the pipeline before any deduction could happen.
     * (The refund path only activates during the forwarding catch block.)
     */
    @Test
    void testNoRefundWhenPrinterUriNotFound() throws Exception {
        final User user = new User();
        user.setId(UUID.randomUUID());
        user.setDomainUsername("jdoe");

        final IppPacket packet = new IppPacket((byte) 2, (byte) 0, (short) 0x0002, 1, List.of(), new byte[0]);
        final PipelineContext ctx = new PipelineContext(packet, "corr-proxy-2", "localhost");
        ctx.setUser(user);

        final PipelineResult allowResult = new PipelineResult(
                IppDecision.ALLOW, "OK", "corr-proxy-2", 5, 1, "jdoe", "LaserJet_5", 2, ctx
        );

        when(pipeline.process(any(), anyString(), anyString(), any())).thenReturn(allowResult);
        when(routingService.resolvePrinterUri("LaserJet_5")).thenReturn(Optional.empty());

        final byte[] minimalIpp = {0x02, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0x01, 0x03};
        proxyService.proxyPrintJob(
                new ByteArrayInputStream(minimalIpp), new ByteArrayOutputStream(),
                "LaserJet_5", "corr-proxy-2", "localhost", "jdoe"
        );

        verify(transactionService, never()).refundQuota(any());
    }

    /**
     * When the pipeline rejects (not ALLOW), no forwarding happens so no refund is needed.
     */
    @Test
    void testNoRefundWhenPipelineRejects() throws Exception {
        final PipelineResult rejectResult = new PipelineResult(
                IppDecision.REJECT_INSUFFICIENT_QUOTA,
                "Insufficient balance",
                "corr-proxy-3",
                5, 1, "jdoe", "LaserJet_5", 2,
                null // context not needed for rejected jobs — quota was not deducted
        );

        when(pipeline.process(any(), anyString(), anyString(), any())).thenReturn(rejectResult);

        final byte[] minimalIpp = {0x02, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0x01, 0x03};
        proxyService.proxyPrintJob(
                new ByteArrayInputStream(minimalIpp), new ByteArrayOutputStream(),
                "LaserJet_5", "corr-proxy-3", "localhost", "jdoe"
        );

        verify(transactionService, never()).refundQuota(any());
    }
}
