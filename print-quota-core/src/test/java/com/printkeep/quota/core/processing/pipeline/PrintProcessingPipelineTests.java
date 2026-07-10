package com.printkeep.quota.core.processing.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.printkeep.quota.codec.model.IppPacket;
import com.printkeep.quota.core.processing.decision.IppDecision;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests verifying orchestrator execution and metric updates of PrintProcessingPipeline.
 */
class PrintProcessingPipelineTests {

    private PrintProcessingPipeline pipeline;
    private PipelineStage stage1;
    private PipelineStage stage2;
    private MeterRegistry registry;

    @BeforeEach
    void setUp() {
        stage1 = mock(PipelineStage.class);
        stage2 = mock(PipelineStage.class);
        registry = new SimpleMeterRegistry();

        pipeline = new PrintProcessingPipeline(List.of(stage1, stage2), registry);
    }

    @Test
    void testPipelineExecutesAllStagesAndReturnsAllow() {
        final IppPacket packet = new IppPacket((byte) 2, (byte) 0, (short) 0x0002, 1, List.of(), new byte[0]);

        // Mock stages doing nothing (succeeding)
        final PipelineResult result = pipeline.process(packet, "corr-123", "localhost");

        assertThat(result.decision()).isEqualTo(IppDecision.ALLOW);
        assertThat(result.correlationId()).isEqualTo("corr-123");
        assertThat(registry.counter("print.requests.received").count()).isEqualTo(1.0);
    }

    @Test
    void testPipelineShortCircuitsOnRejection() {
        final IppPacket packet = new IppPacket((byte) 2, (byte) 0, (short) 0x0002, 1, List.of(), new byte[0]);

        // Mock stage 1 returning rejection
        doAnswer(invocation -> {
            final PipelineContext ctx = invocation.getArgument(0);
            ctx.setDecision(IppDecision.REJECT_INVALID_REQUEST);
            ctx.setReason("Malformed parameters");
            return null;
        }).when(stage1).process(any(PipelineContext.class));

        final PipelineResult result = pipeline.process(packet, "corr-456", "localhost");

        assertThat(result.decision()).isEqualTo(IppDecision.REJECT_INVALID_REQUEST);
        assertThat(result.reason()).isEqualTo("Malformed parameters");
        assertThat(registry.counter("print.requests.invalid").count()).isEqualTo(1.0);
    }
}
