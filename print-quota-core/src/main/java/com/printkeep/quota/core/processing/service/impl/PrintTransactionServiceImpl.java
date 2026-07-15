package com.printkeep.quota.core.processing.service.impl;

import com.printkeep.quota.core.model.PrintLog;
import com.printkeep.quota.core.model.PrintStatus;
import com.printkeep.quota.core.model.Quota;
import com.printkeep.quota.core.model.User;
import com.printkeep.quota.core.processing.decision.IppDecision;
import com.printkeep.quota.core.processing.exception.QuotaExceededException;
import com.printkeep.quota.core.processing.pipeline.PipelineContext;
import com.printkeep.quota.core.processing.service.AuditService;
import com.printkeep.quota.core.processing.service.PrintTransactionService;
import com.printkeep.quota.core.repository.PrintLogRepository;
import com.printkeep.quota.core.repository.QuotaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Implementation of PrintTransactionService managing pessimistic locks and transactions.
 */
@Service
public class PrintTransactionServiceImpl implements PrintTransactionService {

    private static final Logger log = LoggerFactory.getLogger(PrintTransactionServiceImpl.class);
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);

    private final QuotaRepository quotaRepository;
    private final PrintLogRepository printLogRepository;
    private final AuditService auditService;

    public PrintTransactionServiceImpl(
            final QuotaRepository quotaRepository,
            final PrintLogRepository printLogRepository,
            final AuditService auditService) {
        this.quotaRepository = quotaRepository;
        this.printLogRepository = printLogRepository;
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

    /**
     * Refunds quota pages for a failed print job forwarding.
     * Runs in REQUIRES_NEW so this transaction commits independently of the caller's exception state.
     * Writes a separate immutable PrintLog ERROR row (never mutates the original audit entry, per BR-16).
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void refundQuota(final PipelineContext context) {
        if (context == null || context.getUser() == null || context.getMetadata() == null) {
            return;
        }

        final User user = context.getUser();
        final int pagesToRefund = context.getMetadata().estimatedPages();
        final String month = MONTH_FORMATTER.format(context.getStartTime());

        try {
            final java.util.Optional<Quota> quotaOpt =
                    quotaRepository.findByUserIdAndMonthForUpdate(user.getId(), month);

            if (quotaOpt.isEmpty()) {
                return;
            }

            final Quota quota = quotaOpt.get();
            // Clamp at 0 — do not allow usedPages to go negative
            quota.setUsedPages(Math.max(0, quota.getUsedPages() - pagesToRefund));
            quotaRepository.save(quota);

            // Write a separate, immutable refund log row (BR-16: audit trail shows both charge and refund)
            final PrintLog refundLog = new PrintLog();
            refundLog.setUser(user);
            refundLog.setTimestamp(Instant.now());
            refundLog.setCorrelationId(context.getCorrelationId());
            refundLog.setDocumentName(context.getMetadata().documentName());
            refundLog.setPrinterName(context.getMetadata().printerName());
            refundLog.setPageCount(pagesToRefund);
            refundLog.setStatus(PrintStatus.ERROR);
            refundLog.setErrorMessage(
                    "Automatic quota refund: printer forwarding failed for job originally charged "
                    + pagesToRefund + " page(s). Correlation: " + context.getCorrelationId());
            printLogRepository.save(refundLog);

        } catch (final Exception e) {
            // Log but do not rethrow — refund failure must not cascade into additional exceptions
            // for the caller that is already handling a printer failure
            log.error("[CorrID: {}] Failed to process automatic quota refund for user {}",
                    context.getCorrelationId(),
                    user.getDomainUsername(), e);
        }
    }
}
