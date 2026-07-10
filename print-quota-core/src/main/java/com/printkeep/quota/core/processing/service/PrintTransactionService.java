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
}
