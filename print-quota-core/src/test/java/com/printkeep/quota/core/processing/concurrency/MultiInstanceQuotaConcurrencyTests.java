package com.printkeep.quota.core.processing.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.printkeep.quota.codec.model.IppPacket;
import com.printkeep.quota.core.AbstractIntegrationTest;
import com.printkeep.quota.core.model.PrintLog;
import com.printkeep.quota.core.model.PrintStatus;
import com.printkeep.quota.core.model.Quota;
import com.printkeep.quota.core.model.User;
import com.printkeep.quota.core.processing.dto.PrintJobMetadata;
import com.printkeep.quota.core.processing.exception.QuotaExceededException;
import com.printkeep.quota.core.processing.pipeline.PipelineContext;
import com.printkeep.quota.core.processing.service.AuditService;
import com.printkeep.quota.core.processing.service.impl.PrintTransactionServiceImpl;
import com.printkeep.quota.core.repository.PrintLogRepository;
import com.printkeep.quota.core.repository.QuotaRepository;
import com.printkeep.quota.core.repository.UserRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Concurrency integration tests validating database-level pessimistic write locking 
 * across two simulated separate service instances sharing the same PostgreSQL database.
 */
class MultiInstanceQuotaConcurrencyTests extends AbstractIntegrationTest {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private QuotaRepository quotaRepository;

    @Autowired
    private PrintLogRepository printLogRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private PrintTransactionServiceImpl instanceOne;
    private PrintTransactionServiceImpl instanceTwo;

    private TransactionTemplate transactionTemplate;
    private User testUser;

    @BeforeEach
    void setUp() {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        
        // Instantiate two separate transaction services simulating separate app nodes
        this.instanceOne = new PrintTransactionServiceImpl(quotaRepository, printLogRepository, auditService);
        this.instanceTwo = new PrintTransactionServiceImpl(quotaRepository, printLogRepository, auditService);

        this.testUser = transactionTemplate.execute(status -> {
            printLogRepository.deleteAll();
            quotaRepository.deleteAll();
            userRepository.deleteAll();

            final User user = new User();
            user.setDomainUsername("company\\multiinstanceuser");
            user.setDepartment("Engineering");
            final User savedUser = userRepository.saveAndFlush(user);

            final Quota quota = new Quota();
            quota.setUser(savedUser);
            quota.setMonth(MONTH_FORMATTER.format(Instant.now()));
            quota.setAllocatedPages(50); // Total quota limit is 50 pages
            quota.setUsedPages(0);
            quotaRepository.saveAndFlush(quota);

            return savedUser;
        });
    }

    @Test
    void testMultiInstanceConcurrentPrintRequestsPessimisticLocking() throws Exception {
        final int numThreads = 10;
        final int pagesRequestedPerJob = 10; // 10 threads requesting 10 pages each = 100 pages. Max limit 50.
        final ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        final List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            final String corrId = "corr-multi-thread-" + i;
            // Alternating requests between instanceOne and instanceTwo
            final PrintTransactionServiceImpl serviceInstance = (i % 2 == 0) ? instanceOne : instanceTwo;
            tasks.add(() -> {
                final PrintJobMetadata metadata = new PrintJobMetadata(
                        "multiinstanceuser", "Doc.pdf", "Printer1", 1, false, false,
                        pagesRequestedPerJob, "ConcurrentJob", "localhost", Instant.now()
                );
                final IppPacket packet = new IppPacket((byte) 2, (byte) 0, (short) 0x0002, 1, List.of(), new byte[0]);
                final PipelineContext context = new PipelineContext(packet, corrId, "localhost");
                context.setUser(testUser);
                context.setMetadata(metadata);

                try {
                    serviceInstance.reserveQuota(context);
                    return true;
                } catch (final QuotaExceededException e) {
                    return false;
                }
            });
        }

        final List<Future<Boolean>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        int successCount = 0;
        int failureCount = 0;
        for (final Future<Boolean> future : futures) {
            if (future.get()) {
                successCount++;
            } else {
                failureCount++;
            }
        }

        // Verify that exactly 5 requests succeeded and 5 requests failed
        assertThat(successCount).isEqualTo(5);
        assertThat(failureCount).isEqualTo(5);

        // Verify that used pages are exactly 50
        final Quota finalQuota = quotaRepository.findByUserIdAndMonth(
                testUser.getId(), MONTH_FORMATTER.format(Instant.now())).orElseThrow();
        assertThat(finalQuota.getUsedPages()).isEqualTo(50);

        // Verify that exactly 5 PrintLog audit entries are SUCCESS and 5 are REJECTED_QUOTA
        final List<PrintLog> logs = printLogRepository.findAll();
        assertThat(logs).hasSize(10);

        final long successLogs = logs.stream().filter(l -> l.getStatus() == PrintStatus.SUCCESS).count();
        final long rejectedLogs = logs.stream().filter(l -> l.getStatus() == PrintStatus.REJECTED_QUOTA).count();

        assertThat(successLogs).isEqualTo(5);
        assertThat(rejectedLogs).isEqualTo(5);
    }
}
