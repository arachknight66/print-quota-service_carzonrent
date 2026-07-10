package com.printkeep.quota.core.processing.pipeline;

import com.printkeep.quota.core.processing.decision.IppDecision;

/**
 * Immutable outcome returned by the processing pipeline.
 */
public record PipelineResult(
        IppDecision decision,
        String reason,
        String correlationId,
        int estimatedPages,
        int copies,
        String username,
        String printerName,
        long latencyMs
) {
    public boolean isAllowed() {
        return decision == IppDecision.ALLOW;
    }
}
