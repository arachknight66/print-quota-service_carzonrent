package com.printkeep.quota.core.config;

import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.springframework.boot.web.embedded.jetty.JettyServerCustomizer;
import org.springframework.boot.web.embedded.jetty.JettyServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for the embedded Jetty web server.
 * Customizes Jetty servlet container thread pool properties.
 */
@Configuration
public class WebServerConfig {

    private static final int MIN_THREADS = 10;
    private static final int MAX_THREADS = 200;
    private static final int IDLE_TIMEOUT_MS = 60000;
    private static final String THREAD_POOL_NAME = "jetty-http";

    /**
     * Customizes the embedded Jetty servlet web server factory with custom thread pools.
     *
     * @return a customizer for the JettyServletWebServerFactory
     */
    @Bean
    public WebServerFactoryCustomizer<JettyServletWebServerFactory> customJettyWebServerFactoryCustomizer() {
        return factory -> factory.addServerCustomizers((JettyServerCustomizer) server -> {
            if (server.getThreadPool() instanceof QueuedThreadPool threadPool) {
                threadPool.setMinThreads(MIN_THREADS);
                threadPool.setMaxThreads(MAX_THREADS);
                threadPool.setIdleTimeout(IDLE_TIMEOUT_MS);
                threadPool.setName(THREAD_POOL_NAME);
            }
        });
    }
}
