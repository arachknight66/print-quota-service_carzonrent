package com.printkeep.quota.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.printkeep.quota.core.security.ClientCertAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

/**
 * Unit tests verifying security-related servlet filter registration.
 */
class SecurityConfigTests {

    @Test
    void testClientCertificateFilterRegistrationIsScopedAndOrderedFirst() {
        final SecurityConfig securityConfig = new SecurityConfig();

        final FilterRegistrationBean<ClientCertAuthFilter> registration =
                securityConfig.clientCertAuthFilterRegistration();

        assertThat(registration.getFilter()).isInstanceOf(ClientCertAuthFilter.class);
        assertThat(registration.getUrlPatterns()).containsExactlyInAnyOrder("/printers/*", "/api/v1/me/*");
        assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        assertThat(registration.getFilterName()).isEqualTo("clientCertAuthFilter");
    }
}
