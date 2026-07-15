package com.printkeep.quota.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.printkeep.quota.core.model.ErrorResponse;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Servlet filter restricting access to the admin API endpoints to pre-approved network CIDR ranges.
 * Scoped to /api/v1/admin/** via FilterRegistrationBean — runs regardless of active Spring profile.
 * Bypass is available per-environment via the app.admin.ip-filter-enabled property.
 */
public class AdminIpFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(AdminIpFilter.class);

    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private final boolean filterEnabled;
    private final List<CidrBlock> allowedNetworks;
    private final ObjectMapper objectMapper;

    public AdminIpFilter(final boolean filterEnabled, final String allowedNetworksConfig) {
        this.filterEnabled = filterEnabled;
        this.allowedNetworks = parseCidrList(allowedNetworksConfig);
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public void doFilter(
            final ServletRequest request,
            final ServletResponse response,
            final FilterChain chain) throws IOException, ServletException {

        if (!filterEnabled) {
            chain.doFilter(request, response);
            return;
        }

        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }

        final String clientIp = resolveClientIp(httpRequest);
        if (isAllowed(clientIp)) {
            chain.doFilter(request, response);
            return;
        }

        final String correlationId = MDC.get(CORRELATION_ID_KEY);
        log.warn(
                "[CorrID: {}] Admin API access denied. sourceIp={}, path={}",
                correlationId,
                clientIp,
                httpRequest.getRequestURI()
        );

        final ErrorResponse errorResponse = new ErrorResponse(
                Instant.now(),
                HttpServletResponse.SC_FORBIDDEN,
                "Forbidden",
                "Admin API not accessible from this network",
                httpRequest.getRequestURI(),
                correlationId
        );

        httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
        httpResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(httpResponse.getWriter(), errorResponse);
    }

    /**
     * Resolves the originating client IP, preferring the first entry of X-Forwarded-For if present
     * (Apache/reverse-proxy may sit in front in some deployments).
     */
    private static String resolveClientIp(final HttpServletRequest request) {
        final String xForwardedFor = request.getHeader(X_FORWARDED_FOR);
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // X-Forwarded-For may be a comma-separated list; take the first (original client) entry
            final String firstEntry = xForwardedFor.split(",")[0].strip();
            if (!firstEntry.isEmpty()) {
                return firstEntry;
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * Checks whether the given IPv4 address falls within any of the allowed CIDR blocks.
     */
    private boolean isAllowed(final String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return false;
        }
        final long ipLong;
        try {
            ipLong = ipToLong(ipAddress);
        } catch (final IllegalArgumentException e) {
            log.warn("Admin IP filter: failed to parse client IP address '{}', denying access", ipAddress);
            return false;
        }
        for (final CidrBlock cidr : allowedNetworks) {
            if (cidr.contains(ipLong)) {
                return true;
            }
        }
        return false;
    }

    private static List<CidrBlock> parseCidrList(final String config) {
        if (config == null || config.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(config.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .map(CidrBlock::parse)
                .toList();
    }

    /**
     * Converts a dotted-decimal IPv4 address to a 32-bit long.
     */
    static long ipToLong(final String ipAddress) {
        final String[] parts = ipAddress.split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Not a valid IPv4 address: " + ipAddress);
        }
        long result = 0L;
        for (final String part : parts) {
            final int octet = Integer.parseInt(part);
            if (octet < 0 || octet > 255) {
                throw new IllegalArgumentException("Octet out of range in IP address: " + ipAddress);
            }
            result = (result << 8) | octet;
        }
        return result;
    }

    /**
     * Immutable parsed representation of an IPv4 CIDR block (e.g. 10.0.0.0/8).
     */
    static final class CidrBlock {

        private final long networkAddress;
        private final long networkMask;

        private CidrBlock(final long networkAddress, final long networkMask) {
            this.networkAddress = networkAddress;
            this.networkMask = networkMask;
        }

        static CidrBlock parse(final String cidr) {
            final String[] parts = cidr.split("/");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid CIDR notation: " + cidr);
            }
            final long networkAddress = ipToLong(parts[0].strip());
            final int prefixLength = Integer.parseInt(parts[1].strip());
            if (prefixLength < 0 || prefixLength > 32) {
                throw new IllegalArgumentException("Invalid CIDR prefix length: " + cidr);
            }
            final long networkMask = prefixLength == 0 ? 0L : (0xFFFFFFFFL << (32 - prefixLength)) & 0xFFFFFFFFL;
            return new CidrBlock(networkAddress & networkMask, networkMask);
        }

        boolean contains(final long ipLong) {
            return (ipLong & networkMask) == networkAddress;
        }
    }
}
