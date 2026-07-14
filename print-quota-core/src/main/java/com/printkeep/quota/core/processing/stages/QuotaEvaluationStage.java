package com.printkeep.quota.core.processing.stages;

import com.printkeep.quota.core.model.Quota;
import com.printkeep.quota.core.model.User;
import com.printkeep.quota.core.processing.decision.IppDecision;
import com.printkeep.quota.core.processing.pipeline.PipelineContext;
import com.printkeep.quota.core.processing.pipeline.PipelineStage;
import com.printkeep.quota.core.repository.QuotaRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Pre-flight pipeline stage checking if a user has sufficient quota balance before initiating locking transactions.
 */
@Component
@Order(4)
public class QuotaEvaluationStage implements PipelineStage {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);

    private final QuotaRepository quotaRepository;

    public QuotaEvaluationStage(final QuotaRepository quotaRepository) {
        this.quotaRepository = quotaRepository;
    }

    @Override
    public void process(final PipelineContext context) {
        final User user = context.getUser();
        if (user == null) {
            // User was not resolved in IdentityResolutionStage
            return;
        }

        final String currentMonth = MONTH_FORMATTER.format(context.getStartTime());
        final Optional<Quota> quotaOpt = quotaRepository.findByUserIdAndMonth(user.getId(), currentMonth);

        if (quotaOpt.isEmpty()) {
            context.setDecision(IppDecision.REJECT_INSUFFICIENT_QUOTA);
            context.setReason("No print quota allocated for the month: " + currentMonth);
            return;
        }

        final Quota quota = quotaOpt.get();
        final int neededPages = context.getMetadata().estimatedPages();

        if (quota.getRemainingPages() < neededPages) {
            context.setDecision(IppDecision.REJECT_INSUFFICIENT_QUOTA);
            context.setReason(String.format("Insufficient quota. Needed: %d, Available: %d",
                    neededPages, quota.getRemainingPages()));
            return;
        }

        // Cache the pre-flight resolved quota
        context.setQuota(quota);
    }
}
