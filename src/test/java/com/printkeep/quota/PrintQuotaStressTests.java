package com.printkeep.quota;

import com.printkeep.quota.model.PrintLog;
import com.printkeep.quota.model.Quota;
import com.printkeep.quota.model.User;
import com.printkeep.quota.repository.PrintLogRepository;
import com.printkeep.quota.repository.QuotaRepository;
import com.printkeep.quota.repository.UserRepository;
import com.printkeep.quota.service.LdapService;
import com.printkeep.quota.service.PrintQuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "server.ssl.enabled=false",
    "print-quota.default-quota=100",
    "print-quota.printer-forward-type=RAW",
    "print-quota.physical-printer-raw-host=localhost",
    "print-quota.physical-printer-raw-port=9100",
    "print-quota.admin-emails=admin@test.local",
    "spring.mail.host=localhost"
})
@ActiveProfiles("test")
public class PrintQuotaStressTests {

    @Autowired
    private PrintQuotaService printQuotaService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private QuotaRepository quotaRepository;

    @Autowired
    private PrintLogRepository printLogRepository;

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public LdapService ldapService() {
            return new LdapService() {
                @Override
                public String getUserDepartment(String username) {
                    return "Default";
                }
            };
        }
    }

    @Autowired
    private LdapService ldapService;

    @BeforeEach
    public void setup() {
        printLogRepository.deleteAll();
        quotaRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    public void testConcurrentPrintQuotaDeduction() throws InterruptedException {
        // 1. Setup user with a quota of 100 pages
        String username = "concur.user";
        User user = User.builder()
                .domainUsername(username)
                .department("Engineering")
                .isActive(true)
                .build();
        user = userRepository.save(user);

        String currentMonthYear = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        Quota quota = Quota.builder()
                .user(user)
                .monthYear(currentMonthYear)
                .allocatedPages(100)
                .usedPages(0)
                .build();
        quotaRepository.save(quota);

        // We will submit 50 print requests concurrently.
        // Each print request calculates to 3 pages (simplex 3 impressions).
        // Max possible successful jobs: 100 / 3 = 33 jobs (consuming 99 pages).
        // 17 jobs must be rejected due to insufficient quota.
        int numThreads = 50;
        int jobImpressions = 3;
        String sides = "one-sided";
        String docName = "ConcurrentDoc";

        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    // Wait for all threads to align and start at the exact same moment
                    startLatch.await();
                    PrintQuotaService.QuotaCheckResult result = printQuotaService.processPrintJobQuota(
                            username, jobImpressions, sides, docName
                    );
                    if (result.isAllowed()) {
                        successCount.incrementAndGet();
                    } else if (result.getMessage().contains("Insufficient print quota")) {
                        rejectCount.incrementAndGet();
                    } else {
                        errorCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // Release the gates to start stress test
        long startTime = System.currentTimeMillis();
        startLatch.countDown();
        
        // Wait for all threads to finish (timeout after 15 seconds)
        boolean finished = endLatch.await(15, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();
        executor.shutdown();

        assertTrue(finished, "Stress test threads did not finish in time");

        System.out.println("====== STRESS TEST RESULTS ======");
        System.out.println("Total Time: " + (endTime - startTime) + " ms");
        System.out.println("Successful Jobs: " + successCount.get());
        System.out.println("Rejected Jobs (Quota): " + rejectCount.get());
        System.out.println("Errors: " + errorCount.get());
        System.out.println("=================================");

        // Assertions
        assertEquals(33, successCount.get(), "Successful jobs should be exactly 33");
        assertEquals(17, rejectCount.get(), "Rejected jobs should be exactly 17");
        assertEquals(0, errorCount.get(), "There should be no errors during execution");

        // Verify the database final states
        Quota finalQuota = quotaRepository.findByUserAndMonthYear(user, currentMonthYear).orElseThrow();
        assertEquals(99, finalQuota.getUsedPages(), "Quota used pages should be exactly 99");

        // Verify log count
        List<PrintLog> logs = printLogRepository.findAll();
        assertEquals(50, logs.size(), "Total print log entries should be 50");

        long dbSuccessCount = logs.stream().filter(l -> l.getStatus() == PrintLog.Status.SUCCESS).count();
        long dbRejectCount = logs.stream().filter(l -> l.getStatus() == PrintLog.Status.REJECTED_QUOTA).count();

        assertEquals(33, dbSuccessCount, "DB successful logs count should be 33");
        assertEquals(17, dbRejectCount, "DB rejected logs count should be 17");
    }
}
