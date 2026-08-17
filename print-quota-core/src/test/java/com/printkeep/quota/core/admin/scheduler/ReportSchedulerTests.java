package com.printkeep.quota.core.admin.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.printkeep.quota.core.admin.dto.DashboardSummary;
import com.printkeep.quota.core.admin.service.DashboardService;
import com.printkeep.quota.core.admin.service.EmailService;
import com.printkeep.quota.core.admin.service.ExcelExportService;
import com.printkeep.quota.core.model.PrintLog;
import com.printkeep.quota.core.model.Quota;
import com.printkeep.quota.core.model.User;
import com.printkeep.quota.core.repository.PrintLogRepository;
import com.printkeep.quota.core.repository.QuotaRepository;
import com.printkeep.quota.core.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.OutputStream;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@ExtendWith(MockitoExtension.class)
class ReportSchedulerTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private QuotaRepository quotaRepository;

    @Mock
    private PrintLogRepository printLogRepository;

    @Mock
    private DashboardService dashboardService;

    @Mock
    private ExcelExportService exportService;

    @Mock
    private EmailService emailService;

    private ReportScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ReportScheduler(
                userRepository, quotaRepository, printLogRepository,
                dashboardService, exportService, emailService,
                new SimpleMeterRegistry()
        );
        ReflectionTestUtils.setField(scheduler, "defaultQuotaLimit", 100);
        ReflectionTestUtils.setField(scheduler, "adminEmails", new String[]{"admin@company.local"});
        ReflectionTestUtils.setField(scheduler, "retentionDays", 30);
    }

    @Test
    void testExecuteMonthlyQuotaResetCreatesAllocationsForNewUsers() {
        final User user1 = new User();
        user1.setId(UUID.randomUUID());
        final User user2 = new User();
        user2.setId(UUID.randomUUID());

        when(userRepository.streamAll()).thenReturn(Stream.of(user1, user2));

        // User 1 already has quota for this month, User 2 does not
        when(quotaRepository.findByUserIdAndMonth(eq(user1.getId()), anyString()))
                .thenReturn(Optional.of(new Quota()));
        when(quotaRepository.findByUserIdAndMonth(eq(user2.getId()), anyString()))
                .thenReturn(Optional.empty());

        scheduler.executeMonthlyQuotaReset();

        // Should save quota only for User 2
        verify(quotaRepository, times(1)).save(any(Quota.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testExecuteDailySummaryGeneratesAndEmailsReport() throws Exception {
        final DashboardSummary summary = DashboardSummary.builder()
                .totalUsers(50L)
                .monthlyPagesPrinted(1200L)
                .allowedJobs(350L)
                .rejectedJobs(15L)
                .averageJobSize(4.5)
                .build();

        when(dashboardService.getDashboardSummary()).thenReturn(summary);

        final PrintLog logEntry = new PrintLog();
        when(printLogRepository.streamAllForExport()).thenReturn(Stream.of(logEntry));

        scheduler.executeDailySummary();

        // Suppressed warnings on unchecked class verification for generic Stream
        verify(exportService, times(1)).exportPrintLogsToExcel(any(Stream.class), any(OutputStream.class));
        verify(emailService, times(1)).sendEmailWithAttachment(
                eq(new String[]{"admin@company.local"}),
                contains("PrintKeep Daily Summary Report"),
                contains("Total Users:"),
                eq("daily_print_logs.xlsx"),
                any()
        );
    }

    @Test
    void testExecuteAuditCleanupDeletesExpiredLogs() {
        final PrintLog oldLog = new PrintLog();
        when(printLogRepository.findByTimestampBetween(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(oldLog));

        scheduler.executeAuditCleanup();

        verify(printLogRepository, times(1)).deleteAll(List.of(oldLog));
    }

    @Test
    void testExecuteAuditCleanupDoesNothingWhenNoOldLogs() {
        when(printLogRepository.findByTimestampBetween(any(Instant.class), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        scheduler.executeAuditCleanup();

        verify(printLogRepository, never()).deleteAll(anyList());
    }
}
