package com.printkeep.quota.core.repository;

import com.printkeep.quota.core.model.PrintLog;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for managing PrintLog entity persistence.
 */
@Repository
public interface PrintLogRepository extends JpaRepository<PrintLog, UUID> {

    /**
     * Retrieves all print logs associated with a specific user.
     *
     * @param userId UUID of the user.
     * @return list of print logs.
     */
    List<PrintLog> findByUserId(UUID userId);

    /**
     * Retrieves all print logs recorded within a specified UTC timestamp range.
     * Used principally for generating monthly reporting summaries.
     *
     * @param start start timestamp (inclusive).
     * @param end   end timestamp (inclusive).
     * @return list of print logs.
     */
    List<PrintLog> findByTimestampBetween(Instant start, Instant end);

    /**
     * Retrieves all print logs recorded for a user within a specified UTC timestamp range.
     *
     * @param userId UUID of the user.
     * @param start  start timestamp (inclusive).
     * @param end    end timestamp (inclusive).
     * @return list of print logs.
     */
    List<PrintLog> findByUserIdAndTimestampBetween(UUID userId, Instant start, Instant end);
}
