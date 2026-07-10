package com.printkeep.quota.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.embedded.jetty.JettyServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.test.context.ActiveProfiles;

/**
 * Unit tests to verify that the custom WebServerConfig loads properly.
 */
@SpringBootTest
@ActiveProfiles("dev")
class WebServerConfigTests {

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("jettyWebServerFactoryCustomizer")
    private WebServerFactoryCustomizer<JettyServletWebServerFactory> customizer;

    /**
     * Verifies that the Jetty customizer bean is loaded into the context.
     */
    @Test
    void testCustomizerBeanExists() {
        assertThat(customizer).isNotNull();
    }
}
