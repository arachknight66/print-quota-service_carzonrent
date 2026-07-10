package com.printkeep.quota.core.filter;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit tests verifying correlation ID extraction, generation, and MDC binding.
 */
class MdcCorrelationFilterTests {

    private MdcCorrelationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new MdcCorrelationFilter();
        MDC.clear();
    }

    @Test
    void testFilterGeneratesCorrelationIdIfMissing() throws ServletException, IOException {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final FilterChain filterChain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                // Within the filter chain execution, MDC must contain the generated correlation ID
                assertThat(MDC.get("correlationId")).isNotBlank();
            }
        };

        filter.doFilter(request, response, filterChain);

        // Verify response header is populated
        final String correlationHeader = response.getHeader("X-Correlation-ID");
        assertThat(correlationHeader).isNotBlank();

        // Verify MDC has been cleared after filter completion
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void testFilterPropagatesExistingCorrelationId() throws ServletException, IOException {
        final String existingCorrelationId = "custom-correlation-12345";
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-ID", existingCorrelationId);

        final MockHttpServletResponse response = new MockHttpServletResponse();
        final FilterChain filterChain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                // Verify the existing ID is bound to the MDC
                assertThat(MDC.get("correlationId")).isEqualTo(existingCorrelationId);
            }
        };

        filter.doFilter(request, response, filterChain);

        // Verify response header matches the existing one
        assertThat(response.getHeader("X-Correlation-ID")).isEqualTo(existingCorrelationId);

        // Verify MDC has been cleared
        assertThat(MDC.get("correlationId")).isNull();
    }
}
