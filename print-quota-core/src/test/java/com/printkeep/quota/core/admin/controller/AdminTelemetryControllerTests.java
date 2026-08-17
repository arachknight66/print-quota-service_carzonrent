package com.printkeep.quota.core.admin.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsInAnyOrder;

import com.printkeep.quota.core.proxy.routing.PrinterConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.util.Collections;

@WebMvcTest(AdminTelemetryController.class)
class AdminTelemetryControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PrinterConfig printerConfig;

    @MockBean
    private DataSource dataSource;

    @Test
    @WithMockUser
    void testGetPrintersDefaultFallback() throws Exception {
        when(printerConfig.getPrinters()).thenReturn(Collections.emptyMap());

        mockMvc.perform(get("/api/v1/admin/telemetry/printers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                // Assert names in any order since Map.of iteration order is JVM-dependent
                .andExpect(jsonPath("$[*].name").value(containsInAnyOrder("printer-hr", "printer-it", "printer-finance")));
    }

    @Test
    @WithMockUser
    void testGetJobs() throws Exception {
        mockMvc.perform(get("/api/v1/admin/telemetry/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("Monthly Quota Reset"));
    }

    @Test
    @WithMockUser
    void testGetSystemTelemetry() throws Exception {
        mockMvc.perform(get("/api/v1/admin/telemetry/system"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jvmMemory").exists())
                .andExpect(jsonPath("$.cpu").exists())
                .andExpect(jsonPath("$.database").exists());
    }
}
