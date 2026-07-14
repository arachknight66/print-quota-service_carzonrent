package com.printkeep.quota.core.processing.stages;

import com.printkeep.quota.core.processing.decision.IppDecision;
import com.printkeep.quota.core.processing.pipeline.PipelineContext;
import com.printkeep.quota.core.processing.pipeline.PipelineStage;
import com.printkeep.quota.core.processing.service.PrintTransactionService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Pipeline stage executing the locking database transaction to reserve quota balances.
 */
@Component
@Order(5)
public class PrintTransactionStage implements PipelineStage {

    private final PrintTransactionService transactionService;

    public PrintTransactionStage(final PrintTransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Override
    public void process(final PipelineContext context) {
        // Only run transaction reservation if pre-flight checks allowed it
        if (context.getDecision() != null && context.getDecision() != IppDecision.ALLOW) {
            return;
        }

        try {
            transactionService.reserveQuota(context);
        } catch (final Exception e) {
            // Exceptions are already registered to context decision/reason inside reserveQuota
            // We absorb here so the pipeline can return a structured result instead of crash
        }
    }
}
