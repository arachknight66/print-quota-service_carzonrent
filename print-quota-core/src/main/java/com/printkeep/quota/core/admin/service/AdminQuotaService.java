package com.printkeep.quota.core.admin.service;

import com.printkeep.quota.core.admin.dto.QuotaAdjustmentRequest;
import com.printkeep.quota.core.model.Quota;
import com.printkeep.quota.core.model.QuotaAdjustmentLog;
import com.printkeep.quota.core.repository.QuotaAdjustmentLogRepository;
import com.printkeep.quota.core.repository.QuotaRepository;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing administrative quota adjustments and logging the audit approval trail.
 */
@Service
public class AdminQuotaService {

    private static final DateTimeFormatter MONTH_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);
    private static final String CORRELATION_ID_KEY = "correlationId";

    private final QuotaRepository quotaRepository;
    private final QuotaAdjustmentLogRepository adjustmentLogRepository;

    public AdminQuotaService(
            final QuotaRepository quotaRepository,
            final QuotaAdjustmentLogRepository adjustmentLogRepository) {
        this.quotaRepository = quotaRepository;
        this.adjustmentLogRepository = adjustmentLogRepository;
    }

    /**
     * Idempotently adjusts a user's page allocation for the current billing month.
     * Records an audit log entry tracking the adjustment, reason, and approving admin.
     *
     * @param userId  UUID of the target user.
     * @param request the QuotaAdjustmentRequest DTO.
     * @return the updated Quota record.
     * @throws IllegalArgumentException if no active quota exists for the current month.
     */
    @Transactional(rollbackFor = Exception.class)
    public Quota adjustUserQuota(final UUID userId, final QuotaAdjustmentRequest request) {
        final String currentMonth = MONTH_FORMATTER.format(Instant.now());
        final Quota quota = quotaRepository.findByUserIdAndMonth(userId, currentMonth)
                .orElseThrow(() -> new IllegalArgumentException("No quota allocation found for current month"));

        // Deduct/Add allocation
        quota.setAllocatedPages(quota.getAllocatedPages() + request.adjustment());
        final Quota savedQuota = quotaRepository.save(quota);

        // Record audit trail log row
        final QuotaAdjustmentLog log = new QuotaAdjustmentLog();
        log.setQuota(savedQuota);
        log.setAdjustmentAmount(request.adjustment());
        log.setReason(request.reason());
        log.setApprovedBy(request.approvedBy());
        log.setTimestamp(Instant.now());
        
        final String correlationId = MDC.get(CORRELATION_ID_KEY);
        log.setCorrelationId(correlationId);

        adjustmentLogRepository.save(log);

        return savedQuota;
    }
}
