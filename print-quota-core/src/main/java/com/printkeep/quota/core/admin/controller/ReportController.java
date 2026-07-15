package com.printkeep.quota.core.admin.controller;

import com.printkeep.quota.core.admin.service.ExcelExportService;
import com.printkeep.quota.core.admin.service.ChargebackReportService;
import com.printkeep.quota.core.model.PrintLog;
import com.printkeep.quota.core.model.Quota;
import com.printkeep.quota.core.repository.PrintLogRepository;
import com.printkeep.quota.core.repository.QuotaRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.time.Instant;
import java.util.stream.Stream;

/**
 * Controller exposing REST download endpoints for CSV/Excel data streams.
 */
@Controller
@RequestMapping("/api/v1/admin/reports")
public class ReportController {

    private final ExcelExportService exportService;
    private final ChargebackReportService chargebackReportService;
    private final PrintLogRepository printLogRepository;
    private final QuotaRepository quotaRepository;

    public ReportController(
            final ExcelExportService exportService,
            final ChargebackReportService chargebackReportService,
            final PrintLogRepository printLogRepository,
            final QuotaRepository quotaRepository) {
        this.exportService = exportService;
        this.chargebackReportService = chargebackReportService;
        this.printLogRepository = printLogRepository;
        this.quotaRepository = quotaRepository;
    }

    @GetMapping("/export/logs")
    @Transactional(readOnly = true)
    public void exportLogs(
            @RequestParam(defaultValue = "excel") final String format,
            final HttpServletResponse response) throws IOException {

        try (Stream<PrintLog> logs = printLogRepository.streamAllForExport()) {
            if ("csv".equalsIgnoreCase(format)) {
                response.setContentType("text/csv");
                response.setHeader("Content-Disposition", "attachment; filename=\"print_logs_" + Instant.now().getEpochSecond() + ".csv\"");
                exportService.exportPrintLogsToCsv(logs, response.getOutputStream());
            } else {
                response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                response.setHeader("Content-Disposition", "attachment; filename=\"print_logs_" + Instant.now().getEpochSecond() + ".xlsx\"");
                exportService.exportPrintLogsToExcel(logs, response.getOutputStream());
            }
        }
    }

    @GetMapping("/export/quotas")
    @Transactional(readOnly = true)
    public void exportQuotas(
            @RequestParam(defaultValue = "excel") final String format,
            @RequestParam(required = false) final String month,
            final HttpServletResponse response) throws IOException {

        try (Stream<Quota> quotas = month != null
                ? quotaRepository.streamByMonthForExport(month)
                : quotaRepository.streamAllForExport()) {
            if ("csv".equalsIgnoreCase(format)) {
                response.setContentType("text/csv");
                response.setHeader("Content-Disposition", "attachment; filename=\"quotas_" + Instant.now().getEpochSecond() + ".csv\"");
                exportService.exportQuotasToCsv(quotas, response.getOutputStream());
            } else {
                response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                response.setHeader("Content-Disposition", "attachment; filename=\"quotas_" + Instant.now().getEpochSecond() + ".xlsx\"");
                exportService.exportQuotasToExcel(quotas, response.getOutputStream());
            }
        }
    }

    @GetMapping("/chargeback")
    public void exportChargeback(
            @RequestParam final String month,
            final HttpServletResponse response) throws IOException {

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"chargeback_report_"
                + month + "_" + Instant.now().getEpochSecond() + ".xlsx\"");

        chargebackReportService.exportChargebackToExcel(month, response.getOutputStream());
    }
}
