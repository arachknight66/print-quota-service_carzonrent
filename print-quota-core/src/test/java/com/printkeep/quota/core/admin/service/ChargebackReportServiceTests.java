package com.printkeep.quota.core.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.printkeep.quota.core.admin.dto.DepartmentChargebackDto;
import com.printkeep.quota.core.repository.QuotaRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Unit tests verifying ChargebackReportService aggregation and Excel streaming.
 */
class ChargebackReportServiceTests {

    private QuotaRepository quotaRepository;
    private ChargebackReportService reportService;

    @BeforeEach
    void setUp() {
        quotaRepository = mock(QuotaRepository.class);
        // Using cost per page of 0.10 for simple verification
        reportService = new ChargebackReportService(quotaRepository, 0.10);
    }

    @Test
    void testExportChargebackToExcelCalculatesCorrectCosts() throws Exception {
        final String month = "2026-07";
        final List<DepartmentChargebackDto> rollups = List.of(
                new DepartmentChargebackDto("Engineering", 10L, 500L, 120L),
                new DepartmentChargebackDto("Marketing", 5L, 200L, 45L)
        );

        when(quotaRepository.getDepartmentChargeback(month)).thenReturn(rollups);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        reportService.exportChargebackToExcel(month, out);
        final byte[] bytes = out.toByteArray();

        assertThat(bytes).isNotEmpty();

        // Parse Excel from output stream bytes
        try (final Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            final Sheet sheet = workbook.getSheet("Chargeback Report");
            assertThat(sheet).isNotNull();

            // Assert headers
            final Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Department");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("Employee Count");
            assertThat(header.getCell(2).getStringCellValue()).isEqualTo("Total Allocated Pages");
            assertThat(header.getCell(3).getStringCellValue()).isEqualTo("Total Used Pages");
            assertThat(header.getCell(4).getStringCellValue()).isEqualTo("Estimated Cost ($)");

            // Assert row 1 (Engineering)
            final Row row1 = sheet.getRow(1);
            assertThat(row1.getCell(0).getStringCellValue()).isEqualTo("Engineering");
            assertThat(row1.getCell(1).getNumericCellValue()).isEqualTo(10.0);
            assertThat(row1.getCell(2).getNumericCellValue()).isEqualTo(500.0);
            assertThat(row1.getCell(3).getNumericCellValue()).isEqualTo(120.0);
            // 120 pages * $0.10 = $12.00
            assertThat(row1.getCell(4).getNumericCellValue()).isEqualTo(12.00);

            // Assert row 2 (Marketing)
            final Row row2 = sheet.getRow(2);
            assertThat(row2.getCell(0).getStringCellValue()).isEqualTo("Marketing");
            assertThat(row2.getCell(1).getNumericCellValue()).isEqualTo(5.0);
            assertThat(row2.getCell(2).getNumericCellValue()).isEqualTo(200.0);
            assertThat(row2.getCell(3).getNumericCellValue()).isEqualTo(45.0);
            // 45 pages * $0.10 = $4.50
            assertThat(row2.getCell(4).getNumericCellValue()).isEqualTo(4.50);
        }
    }
}
