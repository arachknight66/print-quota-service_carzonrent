package com.printkeep.quota.core.processing.service.impl;

import com.printkeep.quota.core.model.Quota;
import com.printkeep.quota.core.model.User;
import com.printkeep.quota.core.processing.decision.IppDecision;
import com.printkeep.quota.core.processing.exception.QuotaExceededException;
import com.printkeep.quota.core.processing.pipeline.PipelineContext;
import com.printkeep.quota.core.processing.service.AuditService;
import com.printkeep.quota.core.processing.service.PrintTransactionService;
import com.printkeep.quota.core.repository.QuotaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Implementation of PrintTransactionService managing pessimistic locks and transactions.
 */
@Service
public class PrintTransactionServiceImpl implements PrintTransactionService {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);

    private final QuotaRepository quotaRepository;
    private final AuditService auditService;

    public PrintTransactionServiceImpl(final QuotaRepository quotaRepository, final AuditService auditService) {
        this.quotaRepository = quotaRepository;
        this.auditService = auditService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reserveQuota(final PipelineContext context) throws Exception {
        final User user = context.getUser();
        if (user == null || context.getMetadata() == null) {
            context.setDecision(IppDecision.REJECT_INVALID_REQUEST);
            context.setReason("Cannot reserve quota: user or metadata unresolved");
            auditService.logAudit(context);
            return;
        }

        final int neededPages = context.getMetadata().estimatedPages();
        final String month = MONTH_FORMATTER.format(context.getStartTime());

        try {
            // Acquire pessimistic write lock
            final Quota quota = quotaRepository.findByUserIdAndMonthForUpdate(user.getId(), month)
                    .orElseThrow(() -> new QuotaExceededException("No print quota allocated for the month: " + month));

            if (quota.getRemainingPages() < neededPages) {
                throw new QuotaExceededException(String.format("Insufficient quota. Available: %d, Required: %d",
                        quota.getRemainingPages(), neededPages));
            }

            // Deduct pages and update
            quota.setUsedPages(quota.getUsedPages() + neededPages);
            quotaRepository.save(quota);

            context.setDecision(IppDecision.ALLOW);
            auditService.logAudit(context);
        } catch (final QuotaExceededException e) {
            context.setDecision(IppDecision.REJECT_INSUFFICIENT_QUOTA);
            context.setReason(e.getMessage());
            context.setException(e);
            auditService.logAudit(context);
            throw e;
        } catch (final Exception e) {
            context.setDecision(IppDecision.SYSTEM_ERROR);
            context.setReason("Database transaction error: " + e.getMessage());
            context.setException(e);
            auditService.logAudit(context);
            throw e;
        }
    }
}
