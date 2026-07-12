package com.printkeep.quota.core.admin.controller;

import com.printkeep.quota.core.admin.service.ExcelExportService;
import com.printkeep.quota.core.model.PrintLog;
import com.printkeep.quota.core.model.Quota;
import com.printkeep.quota.core.repository.PrintLogRepository;
import com.printkeep.quota.core.repository.QuotaRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Controller exposing REST download endpoints for CSV/Excel data streams.
 */
@Controller
@RequestMapping("/api/v1/admin/reports")
public class ReportController {

    private final ExcelExportService exportService;
    private final PrintLogRepository printLogRepository;
    private final QuotaRepository quotaRepository;

    public ReportController(
            final ExcelExportService exportService,
            final PrintLogRepository printLogRepository,
            final QuotaRepository quotaRepository) {
        this.exportService = exportService;
        this.printLogRepository = printLogRepository;
        this.quotaRepository = quotaRepository;
    }

    @GetMapping("/export/logs")
    public void exportLogs(
            @RequestParam(defaultValue = "excel") final String format,
            final HttpServletResponse response) throws IOException {

        final List<PrintLog> logs = printLogRepository.findAll();

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

    @GetMapping("/export/quotas")
    public void exportQuotas(
            @RequestParam(defaultValue = "excel") final String format,
            @RequestParam(required = false) final String month,
            final HttpServletResponse response) throws IOException {

        final List<Quota> quotas = month != null 
                ? quotaRepository.findByMonth(month) 
                : quotaRepository.findAll();

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
