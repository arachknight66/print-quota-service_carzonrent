package com.printkeep.quota.core.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.printkeep.quota.core.admin.service.ChargebackReportService;
import com.printkeep.quota.core.admin.service.ExcelExportService;
import com.printkeep.quota.core.model.PrintLog;
import com.printkeep.quota.core.model.Quota;
import com.printkeep.quota.core.repository.PrintLogRepository;
import com.printkeep.quota.core.repository.QuotaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.io.OutputStream;
import java.util.stream.Stream;

@WebMvcTest(controllers = ReportController.class, properties = "app.admin.ip-filter-enabled=false")
@EnableSpringDataWebSupport
class ReportControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExcelExportService exportService;

    @MockBean
    private ChargebackReportService chargebackReportService;

    @MockBean
    private PrintLogRepository printLogRepository;

    @MockBean
    private QuotaRepository quotaRepository;

    @Test
    @WithMockUser(roles = "PRINTKEEP_ADMIN")
    void testExportLogsExcel() throws Exception {
        final Stream<PrintLog> logsStream = Stream.of(new PrintLog());
        when(printLogRepository.streamAllForExport()).thenReturn(logsStream);

        mockMvc.perform(get("/api/v1/admin/reports/export/logs").param("format", "excel"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().exists("Content-Disposition"));

        verify(exportService, times(1)).exportPrintLogsToExcel(eq(logsStream), any(OutputStream.class));
    }

    @Test
    @WithMockUser(roles = "PRINTKEEP_ADMIN")
    void testExportLogsCsv() throws Exception {
        final Stream<PrintLog> logsStream = Stream.of(new PrintLog());
        when(printLogRepository.streamAllForExport()).thenReturn(logsStream);

        mockMvc.perform(get("/api/v1/admin/reports/export/logs").param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv"))
                .andExpect(header().exists("Content-Disposition"));

        verify(exportService, times(1)).exportPrintLogsToCsv(eq(logsStream), any(OutputStream.class));
    }

    @Test
    @WithMockUser(roles = "PRINTKEEP_ADMIN")
    void testExportQuotasExcel() throws Exception {
        final Stream<Quota> quotaStream = Stream.of(new Quota());
        when(quotaRepository.streamAllForExport()).thenReturn(quotaStream);

        mockMvc.perform(get("/api/v1/admin/reports/export/quotas").param("format", "excel"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().exists("Content-Disposition"));

        verify(exportService, times(1)).exportQuotasToExcel(eq(quotaStream), any(OutputStream.class));
    }

    @Test
    @WithMockUser(roles = "PRINTKEEP_ADMIN")
    void testExportQuotasCsvWithMonthFilter() throws Exception {
        final Stream<Quota> quotaStream = Stream.of(new Quota());
        when(quotaRepository.streamByMonthForExport("2026-08")).thenReturn(quotaStream);

        mockMvc.perform(get("/api/v1/admin/reports/export/quotas")
                        .param("format", "csv")
                        .param("month", "2026-08"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv"))
                .andExpect(header().exists("Content-Disposition"));

        verify(exportService, times(1)).exportQuotasToCsv(eq(quotaStream), any(OutputStream.class));
    }

    @Test
    @WithMockUser(roles = "PRINTKEEP_ADMIN")
    void testExportChargeback() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports/chargeback").param("month", "2026-08"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().exists("Content-Disposition"));

        verify(chargebackReportService, times(1)).exportChargebackToExcel(eq("2026-08"), any(OutputStream.class));
    }
}
