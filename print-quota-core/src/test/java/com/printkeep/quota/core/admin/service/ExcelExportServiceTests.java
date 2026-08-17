package com.printkeep.quota.core.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.printkeep.quota.core.model.PrintLog;
import com.printkeep.quota.core.model.PrintStatus;
import com.printkeep.quota.core.model.Quota;
import com.printkeep.quota.core.model.User;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

class ExcelExportServiceTests {

    private ExcelExportService exportService;

    @BeforeEach
    void setUp() {
        exportService = new ExcelExportService();
    }

    @Test
    void testExportPrintLogsToExcelEmptyAndPopulated() throws Exception {
        // Test empty logs list
        final ByteArrayOutputStream outEmpty = new ByteArrayOutputStream();
        exportService.exportPrintLogsToExcel(Collections.emptyList(), outEmpty);
        assertThat(outEmpty.toByteArray()).isNotEmpty();

        // Try reading empty Excel sheets
        try (final Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(outEmpty.toByteArray()))) {
            final Sheet sheet = workbook.getSheet("Print Logs");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getLastRowNum()).isEqualTo(0); // Only header
            final Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("ID");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("Timestamp");
        }

        // Test populated logs list
        final User user = new User();
        user.setDomainUsername("admin");

        final PrintLog log = new PrintLog();
        log.setId(UUID.randomUUID());
        log.setTimestamp(Instant.parse("2026-08-17T10:00:00Z"));
        log.setUser(user);
        log.setDocumentName("TestDocument.pdf");
        log.setPrinterName("LaserJet_HQ");
        log.setPageCount(12);
        log.setStatus(PrintStatus.SUCCESS);
        log.setCorrelationId("corr-111");

        final ByteArrayOutputStream outPopulated = new ByteArrayOutputStream();
        exportService.exportPrintLogsToExcel(List.of(log), outPopulated);

        try (final Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(outPopulated.toByteArray()))) {
            final Sheet sheet = workbook.getSheet("Print Logs");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getLastRowNum()).isEqualTo(1); // Header + 1 record

            final Row row = sheet.getRow(1);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo(log.getId().toString());
            assertThat(row.getCell(2).getStringCellValue()).isEqualTo("admin");
            assertThat(row.getCell(3).getStringCellValue()).isEqualTo("TestDocument.pdf");
            assertThat(row.getCell(5).getNumericCellValue()).isEqualTo(12.0);
            assertThat(row.getCell(6).getStringCellValue()).isEqualTo("SUCCESS");
        }
    }

    @Test
    void testExportPrintLogsToCsvWithEscaping() {
        final User user = new User();
        user.setDomainUsername("doe, john");

        final PrintLog log = new PrintLog();
        log.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        log.setTimestamp(Instant.parse("2026-08-17T10:00:00Z"));
        log.setUser(user);
        // Test embedded quotes, commas, and newlines in text fields
        log.setDocumentName("Invoice \"Q3\", final.pdf");
        log.setPrinterName("Dept-A, Printer");
        log.setPageCount(5);
        log.setStatus(PrintStatus.SUCCESS);
        log.setCorrelationId("corr,id\nwith_newline");

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportService.exportPrintLogsToCsv(List.of(log), out);
        final String csvContent = out.toString();

        assertThat(csvContent).contains("ID,Timestamp,User,Document,Printer,Pages,Status,CorrelationID");
        // Verify CSV escaping with double quotes
        assertThat(csvContent).contains("\"doe, john\"");
        assertThat(csvContent).contains("\"Invoice \"\"Q3\"\", final.pdf\"");
        assertThat(csvContent).contains("\"Dept-A, Printer\"");
        assertThat(csvContent).contains("\"corr,id\nwith_newline\"");
    }

    @Test
    void testExportQuotasToExcelAndCsv() throws Exception {
        final User user = new User();
        user.setDomainUsername("jdoe");

        final Quota quota = new Quota();
        quota.setId(UUID.randomUUID());
        quota.setUser(user);
        quota.setMonth("2026-08");
        quota.setAllocatedPages(150);
        quota.setUsedPages(30);

        // Test Excel
        final ByteArrayOutputStream outExcel = new ByteArrayOutputStream();
        exportService.exportQuotasToExcel(List.of(quota), outExcel);
        try (final Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(outExcel.toByteArray()))) {
            final Sheet sheet = workbook.getSheet("Quotas");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            final Row row = sheet.getRow(1);
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo("jdoe");
            assertThat(row.getCell(2).getStringCellValue()).isEqualTo("2026-08");
            assertThat(row.getCell(3).getNumericCellValue()).isEqualTo(150.0);
            assertThat(row.getCell(4).getNumericCellValue()).isEqualTo(30.0);
            assertThat(row.getCell(5).getNumericCellValue()).isEqualTo(120.0); // remaining
        }

        // Test CSV
        final ByteArrayOutputStream outCsv = new ByteArrayOutputStream();
        exportService.exportQuotasToCsv(List.of(quota), outCsv);
        final String csvContent = outCsv.toString();
        assertThat(csvContent).contains("ID,User,Month,Allocated,Used,Remaining");
        assertThat(csvContent).contains("jdoe,2026-08,150,30,120");
    }
}
