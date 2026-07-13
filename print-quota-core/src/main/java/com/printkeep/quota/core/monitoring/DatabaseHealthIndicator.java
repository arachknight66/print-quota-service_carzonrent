package com.printkeep.quota.core.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;

/**
 * Custom HealthIndicator for monitoring database status, connectivity, and response times.
 */
@Component
public class DatabaseHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(DatabaseHealthIndicator.class);
    private final DataSource dataSource;

    public DatabaseHealthIndicator(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        final Instant start = Instant.now();
        try (final Connection conn = dataSource.getConnection();
             final Statement stmt = conn.createStatement()) {

            stmt.execute("SELECT 1");
            final long latencyMs = Duration.between(start, Instant.now()).toMillis();
            log.info("Database health check completed successfully in {} ms", latencyMs);

            return Health.up()
                    .withDetail("validationQuery", "SELECT 1")
                    .withDetail("latencyMs", latencyMs)
                    .build();
        } catch (final Exception e) {
            final long latencyMs = Duration.between(start, Instant.now()).toMillis();
            log.error("Database health check failed after {} ms", latencyMs, e);
            return Health.down(e)
                    .withDetail("latencyMs", latencyMs)
                    .build();
        }
    }
}
