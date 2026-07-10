package com.printkeep.quota.core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Configuration class for relational database structures, enabling transaction management,
 * JPA repository integration, and transaction boundaries.
 *
 * <p>Isolation Level Justification:
 * The default isolation level for this application is {@code READ_COMMITTED}.
 * READ_COMMITTED prevents reading uncommitted "dirty" data, while maximizing concurrent throughput.
 * This is crucial for managing print quota balances under load, where lock durations must be minimal
 * (under 10ms), and row-level pessimistic locks (SELECT FOR UPDATE) are used to handle write-safety.
 * </p>
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.printkeep.quota.core.repository")
public class DatabaseConfig {
    
    // Spring Boot auto-configures the TransactionManager and DataSource based on Hikari properties.
    // Custom configurations can be added here if non-default overrides are required.
}
