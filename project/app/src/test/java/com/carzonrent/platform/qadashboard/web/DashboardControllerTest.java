package com.carzonrent.platform.qadashboard.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rendersDashboard() throws Exception {
        mockMvc.perform(get("/").header("X-Forwarded-For", "127.0.0.1"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(content().string(containsString("Carzonrent Platform")))
                .andExpect(content().string(containsString("/style.css")));
    }

    @Test
    void exposesHealthAndInfo() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/info").header("X-Forwarded-For", "127.0.0.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environment").value("QA"))
                .andExpect(jsonPath("$.reverseProxyStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.backendAddress").value("127.0.0.1:8085"))
                .andExpect(jsonPath("$.gitCommitId").exists())
                .andExpect(jsonPath("$.dockerImageTag").exists());

        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app").exists())
                .andExpect(jsonPath("$.git").exists());
    }

    @Test
    void unknownRouteReturns404() throws Exception {
        mockMvc.perform(get("/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.correlationId").exists());
    }
}
