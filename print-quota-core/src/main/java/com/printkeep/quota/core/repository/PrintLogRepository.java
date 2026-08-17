package com.printkeep.quota.core.repository;

import com.printkeep.quota.core.model.PrintLog;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for managing PrintLog entity persistence.
 */
@Repository
public interface PrintLogRepository extends JpaRepository<PrintLog, UUID> {

    /**
     * Streams all print logs for export jobs without materializing the entire table.
     *
     * @return stream of print logs.
     */
    @Query("SELECT pl FROM PrintLog pl LEFT JOIN FETCH pl.user")
    Stream<PrintLog> streamAllForExport();

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

    /**
     * Advanced audit search supporting dynamic parameters with pagination.
     */
    @org.springframework.data.jpa.repository.Query("SELECT pl FROM PrintLog pl WHERE "
            + "(CAST(:username AS string) IS NULL OR LOWER(pl.user.domainUsername) LIKE LOWER(CONCAT('%', CAST(:username AS string), '%'))) AND "
            + "(CAST(:department AS string) IS NULL OR LOWER(pl.user.department) LIKE LOWER(CONCAT('%', CAST(:department AS string), '%'))) AND "
            + "(CAST(:printerName AS string) IS NULL OR LOWER(pl.printerName) LIKE LOWER(CONCAT('%', CAST(:printerName AS string), '%'))) AND "
            + "(:status IS NULL OR pl.status = :status) AND "
            + "(:startDate IS NULL OR pl.timestamp >= :startDate) AND "
            + "(:endDate IS NULL OR pl.timestamp <= :endDate) AND "
            + "(CAST(:documentName AS string) IS NULL OR LOWER(pl.documentName) LIKE LOWER(CONCAT('%', CAST(:documentName AS string), '%'))) AND "
            + "(CAST(:correlationId AS string) IS NULL OR pl.correlationId = CAST(:correlationId AS string))")
    org.springframework.data.domain.Page<PrintLog> searchPrintLogs(
            @org.springframework.data.repository.query.Param("username") String username,
            @org.springframework.data.repository.query.Param("department") String department,
            @org.springframework.data.repository.query.Param("printerName") String printerName,
            @org.springframework.data.repository.query.Param("status") com.printkeep.quota.core.model.PrintStatus status,
            @org.springframework.data.repository.query.Param("startDate") Instant startDate,
            @org.springframework.data.repository.query.Param("endDate") Instant endDate,
            @org.springframework.data.repository.query.Param("documentName") String documentName,
            @org.springframework.data.repository.query.Param("correlationId") String correlationId,
            org.springframework.data.domain.Pageable pageable);

    long countByStatus(com.printkeep.quota.core.model.PrintStatus status);

    @org.springframework.data.jpa.repository.Query("SELECT AVG(pl.pageCount) FROM PrintLog pl WHERE pl.status = com.printkeep.quota.core.model.PrintStatus.SUCCESS")
    Double findAveragePageCount();

    @org.springframework.data.jpa.repository.Query("SELECT MAX(pl.pageCount) FROM PrintLog pl WHERE pl.status = com.printkeep.quota.core.model.PrintStatus.SUCCESS")
    Integer findMaxPageCount();

    @org.springframework.data.jpa.repository.Query("SELECT pl.printerName, COUNT(pl) FROM PrintLog pl GROUP BY pl.printerName ORDER BY COUNT(pl) DESC")
    List<Object[]> findMostActivePrinters(org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT pl.user.department, COUNT(pl) FROM PrintLog pl GROUP BY pl.user.department ORDER BY COUNT(pl) DESC")
    List<Object[]> findMostActiveDepartments(org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT pl.user.domainUsername, COUNT(pl) FROM PrintLog pl GROUP BY pl.user.domainUsername ORDER BY COUNT(pl) DESC")
    List<Object[]> findMostActiveUsers(org.springframework.data.domain.Pageable pageable);
}
