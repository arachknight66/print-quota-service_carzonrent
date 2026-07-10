package com.printkeep.quota.core.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that extracts or generates a unique correlation ID for every incoming request
 * and binds it to Logback MDC context for request tracing.
 */
@Component
public class MdcCorrelationFilter implements Filter {

    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final String CORRELATION_HEADER = "X-Correlation-ID";

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {
        
        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            final HttpServletRequest httpRequest = (HttpServletRequest) request;
            final HttpServletResponse httpResponse = (HttpServletResponse) response;

            String correlationId = httpRequest.getHeader(CORRELATION_HEADER);
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }

            MDC.put(CORRELATION_ID_KEY, correlationId);
            httpResponse.setHeader(CORRELATION_HEADER, correlationId);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CORRELATION_ID_KEY);
        }
    }
}
