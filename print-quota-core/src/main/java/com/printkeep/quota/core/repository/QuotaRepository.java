package com.printkeep.quota.core.repository;

import com.printkeep.quota.core.model.Quota;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for managing Quota entity persistence.
 */
@Repository
public interface QuotaRepository extends JpaRepository<Quota, UUID> {

    /**
     * Finds a quota allocation for a user during a specific month.
     *
     * @param userId UUID of the user.
     * @param month  target month (format: YYYY-MM).
     * @return an Optional containing the quota, or empty.
     */
    Optional<Quota> findByUserIdAndMonth(UUID userId, String month);

    /**
     * Finds a quota allocation for a user during a specific month, applying a pessimistic write lock.
     * Used to prevent race conditions during concurrent deduction transactions.
     *
     * @param userId UUID of the user.
     * @param month  target month (format: YYYY-MM).
     * @return an Optional containing the locked quota, or empty.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM Quota q WHERE q.user.id = :userId AND q.month = :month")
    Optional<Quota> findByUserIdAndMonthForUpdate(@Param("userId") UUID userId, @Param("month") String month);
}
