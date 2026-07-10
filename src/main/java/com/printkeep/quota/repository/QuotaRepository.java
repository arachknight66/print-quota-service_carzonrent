package com.printkeep.quota.repository;

import com.printkeep.quota.model.Quota;
import com.printkeep.quota.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface QuotaRepository extends JpaRepository<Quota, Long> {
    Optional<Quota> findByUserAndMonthYear(User user, String monthYear);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM Quota q WHERE q.user = :user AND q.monthYear = :monthYear")
    Optional<Quota> findByUserAndMonthYearForUpdate(@Param("user") User user, @Param("monthYear") String monthYear);
}
