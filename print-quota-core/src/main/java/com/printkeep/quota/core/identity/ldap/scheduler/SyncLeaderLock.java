package com.printkeep.quota.core.identity.ldap.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Component managing PostgreSQL advisory locks to ensure single-writer synchronization
 * across multiple print-quota-core instances.
 */
@Component
public class SyncLeaderLock {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncLeaderLock.class);

    // Documented lock key constant to avoid collisions with any other future advisory lock usage.
    // Represents a fixed long value distinct for the identity sync task.
    public static final long SYNC_LOCK_KEY = 4829304918290382901L;

    private final DataSource dataSource;

    public SyncLeaderLock(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Obtains a dedicated database connection to check/acquire a session-level advisory lock.
     * Callers must retain this connection and pass it to {@link #releaseLock(Connection)} in a finally block
     * to guarantee that lock acquisition and release occur within the exact same database session.
     *
     * @return the active Connection if the lock was successfully acquired, otherwise null.
     */
    public Connection acquireLock() {
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
                stmt.setLong(1, SYNC_LOCK_KEY);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next() && rs.getBoolean(1)) {
                        LOGGER.debug("Successfully acquired pg_try_advisory_lock for key {}", SYNC_LOCK_KEY);
                        return conn;
                    }
                }
            }
            // If lock was not acquired, close connection immediately
            conn.close();
            return null;
        } catch (final SQLException e) {
            LOGGER.error("Failed to acquire pg_try_advisory_lock for key {}", SYNC_LOCK_KEY, e);
            if (conn != null) {
                try {
                    conn.close();
                } catch (final SQLException ex) {
                    LOGGER.error("Failed to close connection after failed lock attempt", ex);
                }
            }
            return null;
        }
    }

    /**
     * Releases the session-level advisory lock on the provided connection and returns the connection to the pool.
     *
     * @param conn the active connection that holds the lock.
     */
    public void releaseLock(final Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            try (PreparedStatement stmt = conn.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                stmt.setLong(1, SYNC_LOCK_KEY);
                stmt.execute();
                LOGGER.debug("Successfully released pg_advisory_unlock for key {}", SYNC_LOCK_KEY);
            }
        } catch (final SQLException e) {
            LOGGER.error("Failed to release pg_advisory_unlock for key {}", SYNC_LOCK_KEY, e);
        } finally {
            try {
                conn.close();
            } catch (final SQLException e) {
                LOGGER.error("Failed to close connection during lock release", e);
            }
        }
    }
}
