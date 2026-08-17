package com.printkeep.quota.core.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.printkeep.quota.core.AbstractIntegrationTest;
import com.printkeep.quota.core.model.PrintLog;
import com.printkeep.quota.core.model.PrintStatus;
import com.printkeep.quota.core.model.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Repository integration tests for the PrintLog entity.
 */
class PrintLogRepositoryTests extends AbstractIntegrationTest {

    @Autowired
    private PrintLogRepository printLogRepository;

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
            user.setDomainUsername("company\\log-user-" + UUID.randomUUID().toString().substring(0, 8));
            user.setDepartment("Operations");
            return userRepository.saveAndFlush(user);
        });
    }

    /**
     * Verifies that a print log entry can be successfully saved, audit fields populated, and relationships lazy-loaded.
     */
    @Test
    void testSaveAndRetrievePrintLog() {
        transactionTemplate.executeWithoutResult(status -> {
            final PrintLog log = new PrintLog();
            log.setUser(testUser);
            log.setDocumentName("QuarterlyReport.xlsx");
            log.setPrinterName("MainHQPrinter");
            log.setPageCount(12);
            log.setStatus(PrintStatus.SUCCESS);
            log.setCorrelationId(UUID.randomUUID().toString());

            final PrintLog saved = printLogRepository.saveAndFlush(log);
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getCreatedAt()).isNotNull();

            final List<PrintLog> logs = printLogRepository.findByUserId(testUser.getId());
            assertThat(logs).hasSize(1);
            assertThat(logs.get(0).getDocumentName()).isEqualTo("QuarterlyReport.xlsx");
            
            // Verify lazy relationship target isn't fully loaded but accessible
            assertThat(logs.get(0).getUser()).isEqualTo(testUser);
        });
    }

    /**
     * Verifies range query performance filters correctly.
     */
    @Test
    void testRangeQueriesByTimestamp() {
        transactionTemplate.executeWithoutResult(status -> {
            final Instant now = Instant.now();
            
            final PrintLog log1 = new PrintLog();
            log1.setUser(testUser);
            log1.setDocumentName("doc1.pdf");
            log1.setPrinterName("P1");
            log1.setPageCount(5);
            log1.setStatus(PrintStatus.SUCCESS);
            log1.setCorrelationId(UUID.randomUUID().toString());
            log1.setTimestamp(now.minus(2, ChronoUnit.HOURS));
            printLogRepository.save(log1);

            final PrintLog log2 = new PrintLog();
            log2.setUser(testUser);
            log2.setDocumentName("doc2.pdf");
            log2.setPrinterName("P2");
            log2.setPageCount(2);
            log2.setStatus(PrintStatus.SUCCESS);
            log2.setCorrelationId(UUID.randomUUID().toString());
            log2.setTimestamp(now.plus(2, ChronoUnit.HOURS));
            printLogRepository.save(log2);

            printLogRepository.flush();

            // Retrieve logs between now-3h and now-1h (should return log1)
            final List<PrintLog> range1 = printLogRepository.findByTimestampBetween(
                    now.minus(3, ChronoUnit.HOURS),
                    now.minus(1, ChronoUnit.HOURS)
            );
            assertThat(range1).hasSize(1);
            assertThat(range1.get(0).getDocumentName()).isEqualTo("doc1.pdf");

            // Retrieve logs between now-3h and now+3h (should return both)
            final List<PrintLog> range2 = printLogRepository.findByUserIdAndTimestampBetween(
                    testUser.getId(),
                    now.minus(3, ChronoUnit.HOURS),
                    now.plus(3, ChronoUnit.HOURS)
            );
            assertThat(range2).hasSize(2);
        });
    }

    /**
     * Verifies database CHECK constraint on page count (page_count > 0).
     */
    @Test
    void testPageCountCheckConstraint() {
        final PrintLog log = new PrintLog();
        log.setUser(testUser);
        log.setDocumentName("bad_doc.pdf");
        log.setPrinterName("P1");
        log.setPageCount(0); // Violation: page count must be > 0
        log.setStatus(PrintStatus.ERROR);
        log.setCorrelationId(UUID.randomUUID().toString());

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            printLogRepository.saveAndFlush(log);
        })).satisfies(e -> assertThat(e).isInstanceOfAny(
                DataIntegrityViolationException.class,
                jakarta.validation.ConstraintViolationException.class
        ));
    }
}
