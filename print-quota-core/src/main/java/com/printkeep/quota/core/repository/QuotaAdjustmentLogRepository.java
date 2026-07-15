package com.printkeep.quota.core.repository;

import com.printkeep.quota.core.model.QuotaAdjustmentLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository interface for persistence operations on QuotaAdjustmentLog.
 */
@Repository
public interface QuotaAdjustmentLogRepository extends JpaRepository<QuotaAdjustmentLog, UUID> {
}
