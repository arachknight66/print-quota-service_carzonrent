package com.printkeep.quota.core.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.printkeep.quota.core.model.Quota;
import com.printkeep.quota.core.model.User;
import com.printkeep.quota.core.repository.QuotaRepository;
import com.printkeep.quota.core.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@ActiveProfiles("qa")
class DatabaseH2IntegrationTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private QuotaRepository quotaRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private User testUser;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);

        // Use native queries to physically delete records and bypass Hibernate soft-delete logic
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("DELETE FROM quota_adjustment_logs").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM quotas").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM print_logs").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM users").executeUpdate();
        });

        // Setup test user
        testUser = new User();
        testUser.setDomainUsername("company\\jsmith");
        testUser.setDepartment("Finance");
        testUser.setActive(true);
        testUser = userRepository.save(testUser);
    }

    @Test
    void testUserAndQuotaPersistence() {
        final Quota quota = new Quota();
        quota.setUser(testUser);
        quota.setMonth("2026-08");
        quota.setAllocatedPages(100);
        quota.setUsedPages(20);

        final Quota savedQuota = quotaRepository.save(quota);
        assertThat(savedQuota.getId()).isNotNull();

        final Quota fetched = quotaRepository.findByUserIdAndMonth(testUser.getId(), "2026-08").orElseThrow();
        assertThat(fetched.getAllocatedPages()).isEqualTo(100);
        assertThat(fetched.getUsedPages()).isEqualTo(20);
    }

    @Test
    void testPessimisticWriteLockConcurrency() throws Exception {
        // Pre-create quota record
        final Quota quota = new Quota();
        quota.setUser(testUser);
        quota.setMonth("2026-08");
        quota.setAllocatedPages(100);
        quota.setUsedPages(0);
        final Quota savedQuota = quotaRepository.save(quota);

        final ExecutorService executor = Executors.newFixedThreadPool(2);

        // Thread 1 acquires pessimistic lock and holds it for 300ms
        final Future<Boolean> thread1Future = executor.submit(() -> 
            transactionTemplate.execute(status -> {
                try {
                    final Quota lockedQuota = quotaRepository.findByUserIdAndMonthForUpdate(testUser.getId(), "2026-08").orElseThrow();
                    lockedQuota.setUsedPages(lockedQuota.getUsedPages() + 10);
                    quotaRepository.save(lockedQuota);

                    // Keep lock active for a bit to force thread 2 to wait
                    Thread.sleep(300);
                    return true;
                } catch (final Exception e) {
                    return false;
                }
            })
        );

        // Wait a moment before starting thread 2 to ensure thread 1 locks it first
        Thread.sleep(50);

        // Thread 2 tries to acquire lock and updates used pages by 20
        final Future<Boolean> thread2Future = executor.submit(() -> 
            transactionTemplate.execute(status -> {
                try {
                    final Quota lockedQuota = quotaRepository.findByUserIdAndMonthForUpdate(testUser.getId(), "2026-08").orElseThrow();
                    lockedQuota.setUsedPages(lockedQuota.getUsedPages() + 20);
                    quotaRepository.save(lockedQuota);
                    return true;
                } catch (final Exception e) {
                    return false;
                }
            })
        );

        // Wait for both to complete
        final boolean t1Result = thread1Future.get(2, TimeUnit.SECONDS);
        final boolean t2Result = thread2Future.get(2, TimeUnit.SECONDS);

        executor.shutdown();

        assertThat(t1Result).isTrue();
        assertThat(t2Result).isTrue();

        // Final used pages must be exactly 30 (10 + 20) with no updates lost or race conditions
        final Quota finalQuota = quotaRepository.findById(savedQuota.getId()).orElseThrow();
        assertThat(finalQuota.getUsedPages()).isEqualTo(30);
    }
}
