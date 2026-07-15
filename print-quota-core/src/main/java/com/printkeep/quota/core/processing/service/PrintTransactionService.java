package com.printkeep.quota.core.processing.service;

import com.printkeep.quota.core.processing.pipeline.PipelineContext;

/**
 * Service orchestrating page reservations and audit trails under transactional boundaries.
 */
public interface PrintTransactionService {

    /**
     * Reserves print quota pages for the request using pessimistic database locks,
     * committing the page deduction and saving the audit log.
     *
     * @param context the current request context.
     * @throws Exception if quota balance is insufficient or database transaction fails.
     */
    void reserveQuota(PipelineContext context) throws Exception;

    /**
     * Refunds quota pages for a print job that failed during physical forwarding.
     * Runs in its own independent transaction (REQUIRES_NEW) so the refund always commits
     * regardless of the state of the surrounding call stack.
     * Writes an immutable ERROR PrintLog row recording the automatic refund (BR-16 compliant —
     * does not mutate the original SUCCESS log row).
     *
     * @param context the pipeline context carrying user and metadata from the original request.
     */
    void refundQuota(PipelineContext context);
}
