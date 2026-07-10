package com.printkeep.quota.service;

import com.printkeep.quota.model.PrintLog;
import com.printkeep.quota.model.Quota;
import com.printkeep.quota.repository.PrintLogRepository;
import com.printkeep.quota.repository.QuotaRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final QuotaRepository quotaRepository;
    private final PrintLogRepository printLogRepository;
    private final JavaMailSender mailSender;

    @Value("${print-quota.admin-emails}")
    private List<String> adminEmails;

    @Value("${spring.mail.username}")
    private String mailFrom;

    /**
     * Generates the Excel report for the previous month and emails it to IT Admins.
     */
    public void generateAndSendMonthlyReport() {
        // Calculate previous month details
        LocalDate today = LocalDate.now();
        LocalDate firstOfCurrentMonth = today.withDayOfMonth(1);
        LocalDate firstOfPreviousMonth = firstOfCurrentMonth.minusMonths(1);
        YearMonth previousMonth = YearMonth.from(firstOfPreviousMonth);
        String previousMonthStr = previousMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));

        log.info("Generating monthly report for period: {}", previousMonthStr);

        LocalDateTime startDateTime = firstOfPreviousMonth.atStartOfDay();
        LocalDateTime endDateTime = previousMonth.atEndOfMonth().atTime(LocalTime.MAX);

        // Fetch Data
        List<Quota> quotas = quotaRepository.findAll().stream()
                .filter(q -> q.getMonthYear().equals(previousMonthStr))
                .toList();

        List<PrintLog> printLogs = printLogRepository.findByTimestampBetween(startDateTime, endDateTime);

        // Build Excel Workbook
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            createSummarySheet(workbook, quotas, previousMonthStr);
            createDetailSheet(workbook, printLogs, previousMonthStr);

            workbook.write(bos);
            byte[] excelBytes = bos.toByteArray();

            // Send Email
            sendReportEmail(excelBytes, previousMonthStr);

        } catch (Exception e) {
            log.error("Failed to generate and email monthly print quota report", e);
        }
    }

    private void createSummarySheet(Workbook workbook, List<Quota> quotas, String monthStr) {
        Sheet sheet = workbook.createSheet("Employee Usage Summary");

        // Typography
        Font titleFont = workbook.createFont();
        titleFont.setFontHeightInPoints((short) 16);
        titleFont.setBold(true);
        titleFont.setColor(IndexedColors.DARK_BLUE.getIndex());

        Font headerFont = workbook.createFont();
        headerFont.setFontHeightInPoints((short) 11);
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());

        Font boldFont = workbook.createFont();
        boldFont.setBold(true);

        // Style header cells
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headerStyle.setFont(headerFont);
        setBorder(headerStyle);

        // Style data cells
        CellStyle dataStyle = workbook.createCellStyle();
        dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(dataStyle);

        CellStyle zebraStyle = workbook.createCellStyle();
        zebraStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        zebraStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        zebraStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(zebraStyle);

        CellStyle numberStyle = workbook.createCellStyle();
        numberStyle.setAlignment(HorizontalAlignment.RIGHT);
        numberStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(numberStyle);

        CellStyle boldStyle = workbook.createCellStyle();
        boldStyle.setFont(boldFont);
        setBorder(boldStyle);

        CellStyle boldNumberStyle = workbook.createCellStyle();
        boldNumberStyle.setFont(boldFont);
        boldNumberStyle.setAlignment(HorizontalAlignment.RIGHT);
        setBorder(boldNumberStyle);

        // Row 0: Title
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Print Quota Monthly Report Summary: " + monthStr);
        CellStyle titleStyle = workbook.createCellStyle();
        titleStyle.setFont(titleFont);
        titleCell.setCellStyle(titleStyle);

        // Row 2: Headers
        String[] headers = {"Employee ID", "Domain Username", "Department", "Allocated Pages", "Used Pages", "Usage %", "Active Status"};
        Row headerRow = sheet.createRow(2);
        headerRow.setHeightInPoints(24);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Add Data Rows
        int rowIdx = 3;
        int totalAllocated = 0;
        int totalUsed = 0;

        for (Quota q : quotas) {
            Row row = sheet.createRow(rowIdx++);
            row.setHeightInPoints(18);

            CellStyle currentStyle = (rowIdx % 2 == 0) ? zebraStyle : dataStyle;

            Cell cell0 = row.createCell(0);
            cell0.setCellValue(q.getUser().getEmployeeId().toString());
            cell0.setCellStyle(currentStyle);

            Cell cell1 = row.createCell(1);
            cell1.setCellValue(q.getUser().getDomainUsername());
            cell1.setCellStyle(currentStyle);

            Cell cell2 = row.createCell(2);
            cell2.setCellValue(q.getUser().getDepartment());
            cell2.setCellStyle(currentStyle);

            Cell cell3 = row.createCell(3);
            cell3.setCellValue(q.getAllocatedPages());
            cell3.setCellStyle(numberStyle);
            totalAllocated += q.getAllocatedPages();

            Cell cell4 = row.createCell(4);
            cell4.setCellValue(q.getUsedPages());
            cell4.setCellStyle(numberStyle);
            totalUsed += q.getUsedPages();

            Cell cell5 = row.createCell(5);
            double usagePercent = q.getAllocatedPages() > 0 ? (double) q.getUsedPages() / q.getAllocatedPages() * 100 : 0.0;
            cell5.setCellValue(String.format("%.1f%%", usagePercent));
            cell5.setCellStyle(numberStyle);

            Cell cell6 = row.createCell(6);
            cell6.setCellValue(q.getUser().getIsActive() ? "Active" : "Inactive");
            cell6.setCellStyle(currentStyle);
        }

        // Totals Row
        Row totalRow = sheet.createRow(rowIdx);
        totalRow.setHeightInPoints(20);
        Cell labelCell = totalRow.createCell(0);
        labelCell.setCellValue("TOTALS");
        labelCell.setCellStyle(boldStyle);

        for (int i = 1; i <= 2; i++) {
            totalRow.createCell(i).setCellStyle(boldStyle); // empty styled cells
        }

        Cell allocTotalCell = totalRow.createCell(3);
        allocTotalCell.setCellValue(totalAllocated);
        allocTotalCell.setCellStyle(boldNumberStyle);

        Cell usedTotalCell = totalRow.createCell(4);
        usedTotalCell.setCellValue(totalUsed);
        usedTotalCell.setCellStyle(boldNumberStyle);

        Cell usageTotalPercentCell = totalRow.createCell(5);
        double totalUsagePercent = totalAllocated > 0 ? (double) totalUsed / totalAllocated * 100 : 0.0;
        usageTotalPercentCell.setCellValue(String.format("%.1f%%", totalUsagePercent));
        usageTotalPercentCell.setCellStyle(boldNumberStyle);

        totalRow.createCell(6).setCellStyle(boldStyle);

        // Auto-fit Columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createDetailSheet(Workbook workbook, List<PrintLog> logs, String monthStr) {
        Sheet sheet = workbook.createSheet("Detailed Print History");

        Font titleFont = workbook.createFont();
        titleFont.setFontHeightInPoints((short) 16);
        titleFont.setBold(true);
        titleFont.setColor(IndexedColors.DARK_BLUE.getIndex());

        Font headerFont = workbook.createFont();
        headerFont.setFontHeightInPoints((short) 11);
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());

        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headerStyle.setFont(headerFont);
        setBorder(headerStyle);

        CellStyle dataStyle = workbook.createCellStyle();
        dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(dataStyle);

        CellStyle zebraStyle = workbook.createCellStyle();
        zebraStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        zebraStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        zebraStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(zebraStyle);

        CellStyle numberStyle = workbook.createCellStyle();
        numberStyle.setAlignment(HorizontalAlignment.RIGHT);
        numberStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(numberStyle);

        // Title Row
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Detailed Transaction Print Logs: " + monthStr);
        CellStyle titleStyle = workbook.createCellStyle();
        titleStyle.setFont(titleFont);
        titleCell.setCellStyle(titleStyle);

        // Headers
        String[] headers = {"Log ID", "Employee ID", "Username", "Timestamp", "Document Name", "Calculated Pages", "Status"};
        Row headerRow = sheet.createRow(2);
        headerRow.setHeightInPoints(24);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Data Rows
        int rowIdx = 3;
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (PrintLog logEntry : logs) {
            Row row = sheet.createRow(rowIdx++);
            row.setHeightInPoints(18);

            CellStyle currentStyle = (rowIdx % 2 == 0) ? zebraStyle : dataStyle;

            Cell cell0 = row.createCell(0);
            cell0.setCellValue(logEntry.getLogId());
            cell0.setCellStyle(currentStyle);

            Cell cell1 = row.createCell(1);
            cell1.setCellValue(logEntry.getUser().getEmployeeId().toString());
            cell1.setCellStyle(currentStyle);

            Cell cell2 = row.createCell(2);
            cell2.setCellValue(logEntry.getUser().getDomainUsername());
            cell2.setCellStyle(currentStyle);

            Cell cell3 = row.createCell(3);
            cell3.setCellValue(logEntry.getTimestamp().format(timeFormatter));
            cell3.setCellStyle(currentStyle);

            Cell cell4 = row.createCell(4);
            cell4.setCellValue(logEntry.getDocumentName());
            cell4.setCellStyle(currentStyle);

            Cell cell5 = row.createCell(5);
            cell5.setCellValue(logEntry.getPageCount());
            cell5.setCellStyle(numberStyle);

            Cell cell6 = row.createCell(6);
            cell6.setCellValue(logEntry.getStatus().name());
            cell6.setCellStyle(currentStyle);
        }

        // Auto-fit Columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void setBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private void sendReportEmail(byte[] excelBytes, String monthStr) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(mailFrom);
        helper.setSubject("Print Quota Monthly Report - " + monthStr);
        helper.setText("Hello IT Team,\n\nPlease find attached the Print Quota Management System usage report for the month " 
                + monthStr + ".\n\nBest regards,\nPrint Quota Middleware System");

        for (String email : adminEmails) {
            helper.addTo(email);
        }

        // Attach workbook
        helper.addAttachment("Print_Quota_Report_" + monthStr + ".xlsx", new ByteArrayResource(excelBytes));

        log.info("Sending Print Quota report email to: {}", adminEmails);
        mailSender.send(message);
        log.info("Print Quota report email sent successfully.");
    }
}
