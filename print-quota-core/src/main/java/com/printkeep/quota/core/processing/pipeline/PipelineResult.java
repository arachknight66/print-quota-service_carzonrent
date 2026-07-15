package com.printkeep.quota.core.processing.pipeline;

import com.printkeep.quota.core.processing.decision.IppDecision;

/**
 * Immutable outcome returned by the processing pipeline.
 * Carries the originating PipelineContext so callers (e.g. PrinterProxyService)
 * can access user/metadata details after the pipeline has completed.
 */
public record PipelineResult(
        IppDecision decision,
        String reason,
        String correlationId,
        int estimatedPages,
        int copies,
        String username,
        String printerName,
        long latencyMs,
        PipelineContext context
) {
    public boolean isAllowed() {
        return decision == IppDecision.ALLOW;
    }
}
