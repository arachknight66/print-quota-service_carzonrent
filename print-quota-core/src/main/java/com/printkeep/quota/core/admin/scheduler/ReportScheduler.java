package com.printkeep.quota.core.admin.scheduler;

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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

/**
 * Scheduled tasks manager running monthly quota resets, daily summaries,
 * and database logs cleanups with customizable cron patterns.
 */
@Component
public class ReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReportScheduler.class);
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);

    private final UserRepository userRepository;
    private final QuotaRepository quotaRepository;
    private final PrintLogRepository printLogRepository;
    private final DashboardService dashboardService;
    private final ExcelExportService exportService;
    private final EmailService emailService;

    private final Counter schedulerExecutions;
    private final Counter quotaResets;

    @Value("${app.quota.default-limit:100}")
    private int defaultQuotaLimit;

    @Value("${app.admin.emails:admin@company.local}")
    private String[] adminEmails;

    @Value("${app.retention.days:30}")
    private int retentionDays;

    public ReportScheduler(
            final UserRepository userRepository,
            final QuotaRepository quotaRepository,
            final PrintLogRepository printLogRepository,
            final DashboardService dashboardService,
            final ExcelExportService exportService,
            final EmailService emailService,
            final MeterRegistry registry) {
        this.userRepository = userRepository;
        this.quotaRepository = quotaRepository;
        this.printLogRepository = printLogRepository;
        this.dashboardService = dashboardService;
        this.exportService = exportService;
        this.emailService = emailService;

        this.schedulerExecutions = Counter.builder("scheduler.executions")
                .description("Total cron executions triggered")
                .register(registry);
        this.quotaResets = Counter.builder("quota.resets")
                .description("Total user quota allocation resets")
                .register(registry);
    }

    /**
     * Resets monthly quotas. Executes at midnight on the first day of every month: "0 0 0 1 * *".
     */
    @Scheduled(cron = "${app.cron.quota-reset:0 0 0 1 * *}")
    @Transactional
    public void executeMonthlyQuotaReset() {
        schedulerExecutions.increment();
        final String nextMonth = MONTH_FORMATTER.format(Instant.now());
        log.info("Starting scheduled monthly user quota allocation for month: {}", nextMonth);

        final int[] allocations = {0};
        try (Stream<User> activeUsers = userRepository.streamAll()) {
            activeUsers.forEach(user -> {
            if (quotaRepository.findByUserIdAndMonth(user.getId(), nextMonth).isEmpty()) {
                final Quota quota = new Quota();
                quota.setUser(user);
                quota.setMonth(nextMonth);
                quota.setAllocatedPages(defaultQuotaLimit);
                quota.setUsedPages(0);
                quotaRepository.save(quota);
                    allocations[0]++;
            }
            });
        }

        quotaResets.increment(allocations[0]);
        log.info("Finished scheduled monthly quota reset. Created {} user allocations.", allocations[0]);
    }

    /**
     * Generates and emails the daily print summary. Runs daily at 11 PM: "0 0 23 * * *".
     */
    @Scheduled(cron = "${app.cron.daily-summary:0 0 23 * * *}")
    @Transactional(readOnly = true)
    public void executeDailySummary() {
        schedulerExecutions.increment();
        log.info("Generating daily print quota summary report...");

        final DashboardSummary summary = dashboardService.getDashboardSummary();

        final String subject = "PrintKeep Daily Summary Report - " + Instant.now().toString().substring(0, 10);
        final String htmlBody = String.format(
                "<h3>PrintKeep Operational Daily Report</h3>"
                + "<p><strong>Total Users:</strong> %d</p>"
                + "<p><strong>Monthly Pages Printed:</strong> %d</p>"
                + "<p><strong>Allowed Jobs:</strong> %d</p>"
                + "<p><strong>Rejected Jobs:</strong> %d</p>"
                + "<p><strong>Average Job Size:</strong> %.2f pages</p>",
                summary.getTotalUsers(),
                summary.getMonthlyPagesPrinted(),
                summary.getAllowedJobs(),
                summary.getRejectedJobs(),
                summary.getAverageJobSize()
        );

        // Export logs to attachment
        byte[] attachmentBytes = null;
        try (final ByteArrayOutputStream out = new ByteArrayOutputStream();
                Stream<PrintLog> logs = printLogRepository.streamAllForExport()) {
            exportService.exportPrintLogsToExcel(logs, out);
            attachmentBytes = out.toByteArray();
        } catch (final Exception e) {
            log.error("Failed to generate Excel attachment for daily summary email", e);
        }

        emailService.sendEmailWithAttachment(
                adminEmails,
                subject,
                htmlBody,
                "daily_print_logs.xlsx",
                attachmentBytes
        );
    }

    /**
     * Cleans up aged logs based on retention rules. Runs weekly on Sunday at midnight: "0 0 0 * * 0".
     */
    @Scheduled(cron = "${app.cron.cleanup:0 0 0 * * 0}")
    public void executeAuditCleanup() {
        schedulerExecutions.increment();
        final Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        log.info("Running weekly audit log cleanup. Retention days: {}. Cutoff date: {}", retentionDays, cutoff);

        final List<PrintLog> oldLogs = printLogRepository.findByTimestampBetween(Instant.EPOCH, cutoff);
        if (!oldLogs.isEmpty()) {
            printLogRepository.deleteAll(oldLogs);
            log.info("Cleared {} old audit log entries older than cutoff date.", oldLogs.size());
        } else {
            log.info("No audit logs found older than cutoff date.");
        }
    }
}
