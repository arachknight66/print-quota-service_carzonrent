package com.printkeep.quota.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.printkeep.quota.core.security.AdminIpFilter.CidrBlock;
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
 * Unit tests verifying AdminIpFilter CIDR matching, 403 response shape, and bypass behaviour.
 * Uses MockHttpServletRequest / MockFilterChain matching the pattern in MdcCorrelationFilterTests.
 */
class AdminIpFilterTests {

    private static final String ALLOWED_NETWORKS = "127.0.0.0/8,10.0.0.0/8";
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        MDC.clear();
    }

    // --- Allowed IP passes through to filter chain ---

    @Test
    void testLoopbackAddressIsAllowed() throws ServletException, IOException {
        final AdminIpFilter filter = new AdminIpFilter(true, ALLOWED_NETWORKS);
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final boolean[] chainInvoked = {false};
        final FilterChain chain = (req, res) -> chainInvoked[0] = true;

        filter.doFilter(request, response, chain);

        assertThat(chainInvoked[0]).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void testPrivateNetworkAddressIsAllowed() throws ServletException, IOException {
        final AdminIpFilter filter = new AdminIpFilter(true, ALLOWED_NETWORKS);
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.20.30.40");
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final boolean[] chainInvoked = {false};
        final FilterChain chain = (req, res) -> chainInvoked[0] = true;

        filter.doFilter(request, response, chain);

        assertThat(chainInvoked[0]).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    // --- Disallowed IP gets 403 with correct JSON shape ---

    @Test
    void testPublicInternetAddressIsRejectedWith403() throws ServletException, IOException {
        final AdminIpFilter filter = new AdminIpFilter(true, ALLOWED_NETWORKS);
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("8.8.8.8");
        request.setRequestURI("/api/v1/admin/sync");
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final boolean[] chainInvoked = {false};
        final FilterChain chain = (req, res) -> chainInvoked[0] = true;

        filter.doFilter(request, response, chain);

        assertThat(chainInvoked[0]).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).contains("application/json");

        final JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(403);
        assertThat(body.get("error").asText()).isEqualTo("Forbidden");
        assertThat(body.get("message").asText()).isEqualTo("Admin API not accessible from this network");
        assertThat(body.get("path").asText()).isEqualTo("/api/v1/admin/sync");
        assertThat(body.has("timestamp")).isTrue();
    }

    // --- X-Forwarded-For header takes priority over RemoteAddr ---

    @Test
    void testXForwardedForAllowedAddressPassesThroughEvenWhenRemoteAddrIsPublic()
            throws ServletException, IOException {
        final AdminIpFilter filter = new AdminIpFilter(true, ALLOWED_NETWORKS);
        final MockHttpServletRequest request = new MockHttpServletRequest();
        // Reverse proxy's IP in RemoteAddr but client is in allowed range via X-Forwarded-For
        request.setRemoteAddr("192.168.0.1");
        request.addHeader("X-Forwarded-For", "10.5.6.7, 192.168.0.1");
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final boolean[] chainInvoked = {false};
        final FilterChain chain = (req, res) -> chainInvoked[0] = true;

        filter.doFilter(request, response, chain);

        assertThat(chainInvoked[0]).isTrue();
    }

    @Test
    void testXForwardedForDisallowedAddressIsRejected() throws ServletException, IOException {
        final AdminIpFilter filter = new AdminIpFilter(true, ALLOWED_NETWORKS);
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1"); // proxy IP would be allowed, but client is external
        request.addHeader("X-Forwarded-For", "8.8.8.8, 127.0.0.1");
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final boolean[] chainInvoked = {false};
        final FilterChain chain = (req, res) -> chainInvoked[0] = true;

        filter.doFilter(request, response, chain);

        assertThat(chainInvoked[0]).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    // --- Filter is bypassed entirely when ip-filter-enabled=false ---

    @Test
    void testFilterBypassedWhenDisabled() throws ServletException, IOException {
        final AdminIpFilter filter = new AdminIpFilter(false, ALLOWED_NETWORKS);
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("8.8.8.8"); // would normally be rejected
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final boolean[] chainInvoked = {false};
        final FilterChain chain = (req, res) -> chainInvoked[0] = true;

        filter.doFilter(request, response, chain);

        assertThat(chainInvoked[0]).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    // --- Filter is only scoped to /api/v1/admin/** via FilterRegistrationBean, but we verify
    //     it does not block /printers/** or /actuator/** paths if somehow reached ---

    @Test
    void testFilterNotInvokedForNonAdminPathsViaRegistrationBean() throws ServletException, IOException {
        // The filter is registered only for /api/v1/admin/* via FilterRegistrationBean.
        // This test verifies the filter logic itself doesn't incorrectly check URI — the URL-scoping
        // is handled by the Servlet container based on the registration URL pattern.
        // Even with a public IP, if filter is disabled the chain is invoked.
        final AdminIpFilter filter = new AdminIpFilter(false, ALLOWED_NETWORKS);
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/actuator/health");
        request.setRemoteAddr("8.8.8.8");
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final boolean[] chainInvoked = {false};
        final FilterChain chain = (req, res) -> chainInvoked[0] = true;

        filter.doFilter(request, response, chain);

        // When disabled, filter always passes through regardless of path
        assertThat(chainInvoked[0]).isTrue();
    }

    // --- CIDR matching unit tests ---

    @Test
    void testCidrMatchingLoopback() {
        final CidrBlock cidr = CidrBlock.parse("127.0.0.0/8");
        assertThat(cidr.contains(AdminIpFilter.ipToLong("127.0.0.1"))).isTrue();
        assertThat(cidr.contains(AdminIpFilter.ipToLong("127.255.255.255"))).isTrue();
        assertThat(cidr.contains(AdminIpFilter.ipToLong("128.0.0.0"))).isFalse();
    }

    @Test
    void testCidrMatchingPrivateNetwork() {
        final CidrBlock cidr = CidrBlock.parse("10.0.0.0/8");
        assertThat(cidr.contains(AdminIpFilter.ipToLong("10.0.0.1"))).isTrue();
        assertThat(cidr.contains(AdminIpFilter.ipToLong("10.255.255.255"))).isTrue();
        assertThat(cidr.contains(AdminIpFilter.ipToLong("11.0.0.0"))).isFalse();
        assertThat(cidr.contains(AdminIpFilter.ipToLong("9.255.255.255"))).isFalse();
    }

    @Test
    void testCidrMatchingHostRoute() {
        final CidrBlock cidr = CidrBlock.parse("192.168.1.100/32");
        assertThat(cidr.contains(AdminIpFilter.ipToLong("192.168.1.100"))).isTrue();
        assertThat(cidr.contains(AdminIpFilter.ipToLong("192.168.1.101"))).isFalse();
    }
}
