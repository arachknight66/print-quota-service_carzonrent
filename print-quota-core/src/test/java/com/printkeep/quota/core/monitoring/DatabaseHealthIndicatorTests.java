package com.printkeep.quota.core.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

class DatabaseHealthIndicatorTests {

    @SuppressWarnings("unchecked")
    private <T> T createMockProxy(final Class<T> interfaceClass, final java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                handler
        );
    }

    @Test
    void testHealthUpWhenQuerySucceeds() {
        final Statement mockStatement = createMockProxy(Statement.class, (proxy, method, args) -> {
            if ("execute".equals(method.getName())) {
                return true;
            }
            if ("close".equals(method.getName())) {
                return null;
            }
            return null;
        });

        final Connection mockConnection = createMockProxy(Connection.class, (proxy, method, args) -> {
            if ("createStatement".equals(method.getName())) {
                return mockStatement;
            }
            if ("close".equals(method.getName())) {
                return null;
            }
            return null;
        });

        final DataSource mockDataSource = createMockProxy(DataSource.class, (proxy, method, args) -> {
            if ("getConnection".equals(method.getName())) {
                return mockConnection;
            }
            return null;
        });

        final DatabaseHealthIndicator indicator = new DatabaseHealthIndicator(mockDataSource);
        final Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("validationQuery", "SELECT 1");
        assertThat(health.getDetails()).containsKey("latencyMs");
    }

    @Test
    void testHealthDownWhenQueryFails() {
        final Statement mockStatement = createMockProxy(Statement.class, (proxy, method, args) -> {
            if ("execute".equals(method.getName())) {
                throw new SQLException("Database unavailable");
            }
            if ("close".equals(method.getName())) {
                return null;
            }
            return null;
        });

        final Connection mockConnection = createMockProxy(Connection.class, (proxy, method, args) -> {
            if ("createStatement".equals(method.getName())) {
                return mockStatement;
            }
            if ("close".equals(method.getName())) {
                return null;
            }
            return null;
        });

        final DataSource mockDataSource = createMockProxy(DataSource.class, (proxy, method, args) -> {
            if ("getConnection".equals(method.getName())) {
                return mockConnection;
            }
            return null;
        });

        final DatabaseHealthIndicator indicator = new DatabaseHealthIndicator(mockDataSource);
        final Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("latencyMs");
    }
}
