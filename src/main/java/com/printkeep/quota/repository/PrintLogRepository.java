package com.printkeep.quota.repository;

import com.printkeep.quota.model.PrintLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface PrintLogRepository extends JpaRepository<PrintLog, Long> {
    List<PrintLog> findByTimestampBetween(LocalDateTime start, LocalDateTime end);
}
