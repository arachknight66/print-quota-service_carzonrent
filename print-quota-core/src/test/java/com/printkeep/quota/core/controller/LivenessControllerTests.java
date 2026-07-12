package com.printkeep.quota.core.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Unit tests for LivenessController verifying custom health status endpoints.
 */
@WebMvcTest(LivenessController.class)
@AutoConfigureMockMvc(addFilters = false)
class LivenessControllerTests {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Verifies that the liveness endpoint returns 200 OK and status UP.
     *
     * @throws Exception if mock request execution fails
     */
    @Test
    void testLivenessEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/status/liveness")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    /**
     * Verifies that the readiness endpoint returns 200 OK and status UP.
     *
     * @throws Exception if mock request execution fails
     */
    @Test
    void testReadinessEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/status/readiness")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    /**
     * Verifies that the version endpoint returns 200 OK and correct version value.
     *
     * @throws Exception if mock request execution fails
     */
    @Test
    void testVersionEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/status/version")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("1.0.0"));
    }

    /**
     * Verifies that the build info endpoint returns 200 OK and contains expected metadata.
     *
     * @throws Exception if mock request execution fails
     */
    @Test
    void testBuildInfoEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/status/build")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").exists())
                .andExpect(jsonPath("$.version").value("1.0.0"))
                .andExpect(jsonPath("$.javaVersion").exists())
                .andExpect(jsonPath("$.osName").exists());
    }
}
