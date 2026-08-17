package com.printkeep.quota.core;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.DockerClientFactory;

/**
 * Base abstract class for integration tests running against a real PostgreSQL Testcontainers instance.
 * Starts a single database container statically, sharing it across all inheriting tests to minimize startup overhead.
 * Uses {@link DockerCondition} to skip test class execution and context loading if Docker is unavailable.
 */
@SpringBootTest
@ActiveProfiles("dev")
@ExtendWith(DockerCondition.class)
public abstract class AbstractIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("printquota")
            .withUsername("printuser")
            .withPassword("printpassword");

    static {
        try {
            if (DockerClientFactory.instance().isDockerAvailable()) {
                POSTGRES.start();
            }
        } catch (final Exception e) {
            // Ignore, condition will prevent class usage
        }
    }

    /**
     * Injects the dynamic JDBC connection details of the Testcontainer into the Spring environment.
     *
     * @param registry Spring property registry.
     */
    @DynamicPropertySource
    static void configureProperties(final DynamicPropertyRegistry registry) {
        if (POSTGRES.isRunning()) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        }
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "30");
    }
}
