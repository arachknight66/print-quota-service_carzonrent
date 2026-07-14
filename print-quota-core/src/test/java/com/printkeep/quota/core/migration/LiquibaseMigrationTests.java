package com.printkeep.quota.core.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.printkeep.quota.core.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration tests to verify that Liquibase migrations execute successfully
 * and schema tables are created in the PostgreSQL database.
 */
class LiquibaseMigrationTests extends AbstractIntegrationTest {

    @Autowired
    private DataSource dataSource;

    /**
     * Verifies that the required schema tables (users, quotas, print_logs)
     * are created and present in the database.
     *
     * @throws Exception if database access fails.
     */
    @Test
    void testSchemaTablesExist() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            final DatabaseMetaData metaData = connection.getMetaData();
            
            // Check users table
            try (ResultSet rs = metaData.getTables(null, null, "users", new String[]{"TABLE"})) {
                assertThat(rs.next()).isTrue();
            }

            // Check quotas table
            try (ResultSet rs = metaData.getTables(null, null, "quotas", new String[]{"TABLE"})) {
                assertThat(rs.next()).isTrue();
            }

            // Check print_logs table
            try (ResultSet rs = metaData.getTables(null, null, "print_logs", new String[]{"TABLE"})) {
                assertThat(rs.next()).isTrue();
            }
        }
    }
}
