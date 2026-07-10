package com.printkeep.quota.core.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.printkeep.quota.core.AbstractIntegrationTest;
import com.printkeep.quota.core.model.Quota;
import com.printkeep.quota.core.model.User;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Repository integration tests for the Quota entity.
 */
class QuotaRepositoryTests extends AbstractIntegrationTest {

    @Autowired
    private QuotaRepository quotaRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private User testUser;

    @BeforeEach
    void setUp() {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.testUser = transactionTemplate.execute(status -> {
            final User user = new User();
            user.setDomainUsername("company\\quota-user-" + java.util.UUID.randomUUID().toString().substring(0, 8));
            user.setDepartment("Marketing");
            return userRepository.saveAndFlush(user);
        });
    }

    /**
     * Verifies basic save, dynamic calculation, and find capabilities.
     */
    @Test
    void testSaveAndCalculateRemainingPages() {
        transactionTemplate.executeWithoutResult(status -> {
            final Quota quota = new Quota();
            quota.setUser(testUser);
            quota.setMonth("2026-07");
            quota.setAllocatedPages(100);
            quota.setUsedPages(30);

            final Quota saved = quotaRepository.saveAndFlush(quota);
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getRemainingPages()).isEqualTo(70);

            final Optional<Quota> retrieved = quotaRepository.findByUserIdAndMonth(testUser.getId(), "2026-07");
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().getRemainingPages()).isEqualTo(70);
            assertThat(retrieved.get().getVersion()).isEqualTo(0L);
        });
    }

    /**
     * Verifies that the unique constraint on User and Month prevents duplicate quota allocation.
     */
    @Test
    void testUniqueUserAndMonthConstraint() {
        transactionTemplate.executeWithoutResult(status -> {
            final Quota quota1 = new Quota();
            quota1.setUser(testUser);
            quota1.setMonth("2026-07");
            quota1.setAllocatedPages(100);
            quotaRepository.saveAndFlush(quota1);

            final Quota quota2 = new Quota();
            quota2.setUser(testUser);
            quota2.setMonth("2026-07");
            quota2.setAllocatedPages(150);

            assertThatThrownBy(() -> quotaRepository.saveAndFlush(quota2))
                    .isInstanceOf(DataIntegrityViolationException.class);
        });
    }

    /**
     * Verifies that database check constraints reject invalid balances (e.g. used pages exceeding allocated pages).
     */
    @Test
    void testDatabaseCheckConstraints() {
        transactionTemplate.executeWithoutResult(status -> {
            final Quota quota = new Quota();
            quota.setUser(testUser);
            quota.setMonth("2026-07");
            quota.setAllocatedPages(50);
            quota.setUsedPages(60); // Exceeds allocated

            assertThatThrownBy(() -> quotaRepository.saveAndFlush(quota))
                    .isInstanceOf(DataIntegrityViolationException.class);
        });
    }

    /**
     * Verifies that optimistic locking increments version and prevents concurrent updates.
     */
    @Test
    void testOptimisticLocking() {
        // Step 1: Create a quota row
        final Quota quota = transactionTemplate.execute(status -> {
            final Quota q = new Quota();
            q.setUser(testUser);
            q.setMonth("2026-07");
            q.setAllocatedPages(100);
            return quotaRepository.saveAndFlush(q);
        });

        // Step 2: Simulate Concurrent Transactions
        transactionTemplate.executeWithoutResult(status -> {
            // Load record in Transaction 1
            final Quota q1 = quotaRepository.findById(quota.getId()).orElseThrow();
            q1.setUsedPages(20);
            quotaRepository.saveAndFlush(q1);
        });

        // Try to update the stale record in Transaction 2
        assertThatThrownBy(() -> {
            transactionTemplate.executeWithoutResult(status -> {
                quota.setUsedPages(50);
                quotaRepository.saveAndFlush(quota);
            });
        }).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
