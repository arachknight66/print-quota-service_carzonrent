package com.printkeep.quota.core.repository;

import com.printkeep.quota.core.model.Quota;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
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
     * Streams all quota records for export jobs without materializing the entire table.
     *
     * @return stream of quota records.
     */
    @Query("SELECT q FROM Quota q LEFT JOIN FETCH q.user")
    Stream<Quota> streamAllForExport();

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

    /**
     * Search quotas by dynamic filtering with pagination.
     */
    @Query("SELECT q FROM Quota q WHERE "
            + "(:username IS NULL OR LOWER(q.user.domainUsername) LIKE LOWER(CONCAT('%', :username, '%'))) AND "
            + "(:month IS NULL OR q.month = :month)")
    org.springframework.data.domain.Page<Quota> searchQuotas(
            @Param("username") String username,
            @Param("month") String month,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Finds all quotas matching a specific month.
     */
    java.util.List<Quota> findByMonth(String month);

    /**
     * Streams quota records matching a specific month for export jobs.
     *
     * @param month target month.
     * @return stream of quota records.
     */
    @Query("SELECT q FROM Quota q LEFT JOIN FETCH q.user WHERE q.month = :month")
    Stream<Quota> streamByMonthForExport(@Param("month") String month);

    @Query("SELECT SUM(q.allocatedPages) FROM Quota q WHERE q.month = :month")
    Long sumAllocatedPages(@Param("month") String month);

    @Query("SELECT SUM(q.usedPages) FROM Quota q WHERE q.month = :month")
    Long sumUsedPages(@Param("month") String month);
}
