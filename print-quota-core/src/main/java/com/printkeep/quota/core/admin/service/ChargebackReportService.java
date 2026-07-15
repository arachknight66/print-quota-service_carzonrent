package com.printkeep.quota.core.admin.service;

import com.printkeep.quota.core.admin.dto.DepartmentChargebackDto;
import com.printkeep.quota.core.repository.QuotaRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Service driving the monthly department-level financial chargeback report generation.
 * Generates streaming memory-bounded Excel spreadsheets matching the SXSSF conventions.
 */
@Service
public class ChargebackReportService {

    private final QuotaRepository quotaRepository;
    private final double costPerPage;

    public ChargebackReportService(
            final QuotaRepository quotaRepository,
            @Value("${app.quota.cost-per-page:0.05}") final double costPerPage) {
        this.quotaRepository = quotaRepository;
        this.costPerPage = costPerPage;
    }

    /**
     * Queries aggregated monthly department metrics and streams them into an Excel spreadsheet format.
     * Calculates costs as: (totalUsedPages * costPerPage).
     *
     * @param month target billing month in YYYY-MM format.
     * @param out   output stream where the generated spreadsheet is serialized.
     * @throws IOException if streaming or serialization errors occur.
     */
    public void exportChargebackToExcel(final String month, final OutputStream out) throws IOException {
        final List<DepartmentChargebackDto> rollupList = quotaRepository.getDepartmentChargeback(month);

        try (final SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            final Sheet sheet = workbook.createSheet("Chargeback Report");

            // Write sheet headers
            final Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Department");
            header.createCell(1).setCellValue("Employee Count");
            header.createCell(2).setCellValue("Total Allocated Pages");
            header.createCell(3).setCellValue("Total Used Pages");
            header.createCell(4).setCellValue("Estimated Cost ($)");

            int rowIdx = 1;
            for (final DepartmentChargebackDto rollup : rollupList) {
                final Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(rollup.department());
                row.createCell(1).setCellValue(rollup.employeeCount());
                row.createCell(2).setCellValue(rollup.totalAllocatedPages());
                row.createCell(3).setCellValue(rollup.totalUsedPages());

                // Perform decimal scaling to prevent floating-point representation anomalies
                final double totalCost = BigDecimal.valueOf(rollup.totalUsedPages() * costPerPage)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();
                row.createCell(4).setCellValue(totalCost);
            }

            workbook.write(out);
            workbook.dispose();
        }
    }
}
