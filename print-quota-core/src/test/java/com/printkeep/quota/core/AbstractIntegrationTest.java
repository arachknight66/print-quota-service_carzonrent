package com.printkeep.quota.core;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base abstract class for integration tests running against a real PostgreSQL Testcontainers instance.
 * Starts a single database container statically, sharing it across all inheriting tests to minimize startup overhead.
 */
@SpringBootTest
@ActiveProfiles("dev")
public abstract class AbstractIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("printquota")
            .withUsername("printuser")
            .withPassword("printpassword");

    static {
        POSTGRES.start();
    }

    /**
     * Injects the dynamic JDBC connection details of the Testcontainer into the Spring environment.
     *
     * @param registry Spring property registry.
     */
    @DynamicPropertySource
    static void configureProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
