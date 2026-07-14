package com.printkeep.quota.core.interceptor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class MdcInterceptorTests {

    private MdcInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new MdcInterceptor();
        MDC.clear();
    }

    @Test
    void testPreHandleGeneratesCorrelationIdIfMissing() {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final MockHttpServletResponse response = new MockHttpServletResponse();

        final boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        assertThat(MDC.get("correlationId")).isNotBlank();
        assertThat(response.getHeader("X-Correlation-ID")).isNotBlank();
    }

    @Test
    void testPreHandlePropagatesExistingCorrelationIdInHeader() {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-ID", "custom-id-123");
        final MockHttpServletResponse response = new MockHttpServletResponse();

        final boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        assertThat(MDC.get("correlationId")).isEqualTo("custom-id-123");
        assertThat(response.getHeader("X-Correlation-ID")).isEqualTo("custom-id-123");
    }

    @Test
    void testAfterCompletionClearsMdc() {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put("correlationId", "test-id");

        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(MDC.get("correlationId")).isNull();
    }
}
