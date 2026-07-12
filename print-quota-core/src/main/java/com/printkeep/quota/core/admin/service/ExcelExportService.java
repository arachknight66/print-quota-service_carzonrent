package com.printkeep.quota.core.admin.service;

import com.printkeep.quota.core.model.PrintLog;
import com.printkeep.quota.core.model.Quota;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

/**
 * Service handling data exports in Excel (.xlsx) and CSV formats using streaming strategies
 * to ensure memory footprint remains bounded even under large volumes.
 */
@Service
public class ExcelExportService {

    /**
     * Streams PrintLog records into Excel format.
     */
    public void exportPrintLogsToExcel(final List<PrintLog> logs, final OutputStream out) throws IOException {
        exportPrintLogsToExcel(logs.stream(), out);
    }

    /**
     * Streams PrintLog records into Excel format.
     */
    public void exportPrintLogsToExcel(final Stream<PrintLog> logs, final OutputStream out) throws IOException {
        try (final SXSSFWorkbook workbook = new SXSSFWorkbook(100)) { // Flush rows to disk after 100 rows
            final Sheet sheet = workbook.createSheet("Print Logs");

            // Create header row
            final Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("ID");
            header.createCell(1).setCellValue("Timestamp");
            header.createCell(2).setCellValue("User");
            header.createCell(3).setCellValue("Document");
            header.createCell(4).setCellValue("Printer");
            header.createCell(5).setCellValue("Pages");
            header.createCell(6).setCellValue("Status");
            header.createCell(7).setCellValue("Correlation ID");

            final int[] rowIdx = {1};
            logs.forEach(log -> {
                final Row row = sheet.createRow(rowIdx[0]++);
                row.createCell(0).setCellValue(log.getId().toString());
                row.createCell(1).setCellValue(log.getTimestamp().toString());
                row.createCell(2).setCellValue(log.getUser() != null ? log.getUser().getDomainUsername() : "N/A");
                row.createCell(3).setCellValue(log.getDocumentName());
                row.createCell(4).setCellValue(log.getPrinterName());
                row.createCell(5).setCellValue(log.getPageCount());
                row.createCell(6).setCellValue(log.getStatus().name());
                row.createCell(7).setCellValue(log.getCorrelationId());
            });

            workbook.write(out);
            workbook.dispose(); // Delete temp files
        }
    }

    /**
     * Streams PrintLog records into CSV format.
     */
    public void exportPrintLogsToCsv(final List<PrintLog> logs, final OutputStream out) {
        exportPrintLogsToCsv(logs.stream(), out);
    }

    /**
     * Streams PrintLog records into CSV format.
     */
    public void exportPrintLogsToCsv(final Stream<PrintLog> logs, final OutputStream out) {
        final PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        writer.println("ID,Timestamp,User,Document,Printer,Pages,Status,CorrelationID");

        logs.forEach(log -> {
            writer.printf("%s,%s,%s,%s,%s,%d,%s,%s%n",
                    log.getId(),
                    log.getTimestamp(),
                    log.getUser() != null ? escapeCsv(log.getUser().getDomainUsername()) : "N/A",
                    escapeCsv(log.getDocumentName()),
                    escapeCsv(log.getPrinterName()),
                    log.getPageCount(),
                    log.getStatus().name(),
                    escapeCsv(log.getCorrelationId())
            );
        });
        writer.flush();
    }

    /**
     * Streams Quota records into Excel format.
     */
    public void exportQuotasToExcel(final List<Quota> quotas, final OutputStream out) throws IOException {
        exportQuotasToExcel(quotas.stream(), out);
    }

    /**
     * Streams Quota records into Excel format.
     */
    public void exportQuotasToExcel(final Stream<Quota> quotas, final OutputStream out) throws IOException {
        try (final SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            final Sheet sheet = workbook.createSheet("Quotas");

            final Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("ID");
            header.createCell(1).setCellValue("User");
            header.createCell(2).setCellValue("Month");
            header.createCell(3).setCellValue("Allocated");
            header.createCell(4).setCellValue("Used");
            header.createCell(5).setCellValue("Remaining");

            final int[] rowIdx = {1};
            quotas.forEach(quota -> {
                final Row row = sheet.createRow(rowIdx[0]++);
                row.createCell(0).setCellValue(quota.getId().toString());
                row.createCell(1).setCellValue(quota.getUser().getDomainUsername());
                row.createCell(2).setCellValue(quota.getMonth());
                row.createCell(3).setCellValue(quota.getAllocatedPages());
                row.createCell(4).setCellValue(quota.getUsedPages());
                row.createCell(5).setCellValue(quota.getAllocatedPages() - quota.getUsedPages());
            });

            workbook.write(out);
            workbook.dispose();
        }
    }

    /**
     * Streams Quota records into CSV format.
     */
    public void exportQuotasToCsv(final List<Quota> quotas, final OutputStream out) {
        exportQuotasToCsv(quotas.stream(), out);
    }

    /**
     * Streams Quota records into CSV format.
     */
    public void exportQuotasToCsv(final Stream<Quota> quotas, final OutputStream out) {
        final PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        writer.println("ID,User,Month,Allocated,Used,Remaining");

        quotas.forEach(quota -> {
            writer.printf("%s,%s,%s,%d,%d,%d%n",
                    quota.getId(),
                    escapeCsv(quota.getUser().getDomainUsername()),
                    quota.getMonth(),
                    quota.getAllocatedPages(),
                    quota.getUsedPages(),
                    quota.getAllocatedPages() - quota.getUsedPages()
            );
        });
        writer.flush();
    }

    private String escapeCsv(final String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
