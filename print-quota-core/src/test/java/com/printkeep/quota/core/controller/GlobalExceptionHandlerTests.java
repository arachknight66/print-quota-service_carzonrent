package com.printkeep.quota.core.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Unit tests for GlobalExceptionHandler.
 * Verifies that standard exceptions are caught and correctly mapped to ErrorResponse models.
 */
@WebMvcTest(controllers = {TestController.class})
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerTests {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Verifies handling of general uncaught exceptions (HTTP 500).
     *
     * @throws Exception if mock request execution fails
     */
    @Test
    void testHandleGeneralException() throws Exception {
        mockMvc.perform(get("/test/runtime-exception")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("An unexpected internal error occurred. Reference correlation ID: ")))
                .andExpect(jsonPath("$.path").value("/test/runtime-exception"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    /**
     * Verifies handling of method not supported exception (HTTP 405).
     *
     * @throws Exception if mock request execution fails
     */
    @Test
    void testHandleMethodNotSupported() throws Exception {
        mockMvc.perform(post("/test/runtime-exception")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"))
                .andExpect(jsonPath("$.path").value("/test/runtime-exception"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    /**
     * Verifies handling of resource not found exception (HTTP 404).
     *
     * @throws Exception if mock request execution fails
     */
    @Test
    void testHandleResourceNotFound() throws Exception {
        mockMvc.perform(get("/test/non-existent-path")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/test/non-existent-path"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    /**
     * Verifies handling of validation exceptions (HTTP 400).
     *
     * @throws Exception if mock request execution fails
     */
    @Test
    void testHandleValidationException() throws Exception {
        mockMvc.perform(post("/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed: name must not be blank"))
                .andExpect(jsonPath("$.path").value("/test/validation"))
                .andExpect(jsonPath("$.correlationId").exists());
    }
}
