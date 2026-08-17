package com.printkeep.quota.core.admin.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.printkeep.quota.core.admin.dto.DashboardSummary;
import com.printkeep.quota.core.admin.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DashboardController.class)
class DashboardControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    @Test
    @WithMockUser
    void testGetDashboardSummary() throws Exception {
        final DashboardSummary summary = DashboardSummary.builder()
                .totalUsers(10L)
                .monthlyPagesPrinted(500L)
                .allowedJobs(100L)
                .rejectedJobs(5L)
                .averageJobSize(5.0)
                .build();

        when(dashboardService.getDashboardSummary()).thenReturn(summary);

        mockMvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.totalUsers").value(10))
                .andExpect(jsonPath("$.monthlyPagesPrinted").value(500))
                .andExpect(jsonPath("$.allowedJobs").value(100))
                .andExpect(jsonPath("$.rejectedJobs").value(5))
                .andExpect(jsonPath("$.averageJobSize").value(5.0));

        verify(dashboardService, times(1)).getDashboardSummary();
    }
}
