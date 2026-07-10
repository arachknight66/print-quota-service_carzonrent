package com.printkeep.quota.scheduler;

import com.printkeep.quota.service.PrintQuotaService;
import com.printkeep.quota.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReportScheduler {

    private final ReportService reportService;
    private final PrintQuotaService printQuotaService;

    /**
     * Executes at midnight on the 1st of every month.
     * Generates/emails the usage report for the previous month and rolls over quotas for the current month.
     */
    @Scheduled(cron = "${print-quota.report-cron:0 0 0 1 * ?}")
    public void executeMonthlyRolloverAndReport() {
        log.info("Monthly cron job started: Executing report generation and quota rollover.");

        // 1. Generate and email monthly report
        try {
            reportService.generateAndSendMonthlyReport();
            log.info("Monthly report generation process completed successfully.");
        } catch (Exception e) {
            log.error("Failed to generate and dispatch monthly report", e);
        }

        // 2. Roll over quotas for the active users to the new month
        try {
            printQuotaService.rollOverMonthlyQuotas();
            log.info("Monthly quota rollover completed successfully.");
        } catch (Exception e) {
            log.error("Failed to perform monthly quota rollover", e);
        }

        log.info("Monthly cron job completed.");
    }
}
