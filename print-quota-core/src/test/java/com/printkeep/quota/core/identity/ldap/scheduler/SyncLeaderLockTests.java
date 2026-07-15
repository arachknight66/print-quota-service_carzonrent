package com.printkeep.quota.core.identity.ldap.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.printkeep.quota.core.AbstractIntegrationTest;
import java.sql.Connection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration tests verifying the SyncLeaderLock advisory locking mechanism against PostgreSQL database.
 */
class SyncLeaderLockTests extends AbstractIntegrationTest {

    @Autowired
    private SyncLeaderLock syncLeaderLock;

    @Test
    void testAdvisoryLockAcquireAndReleaseRoundTrip() throws Exception {
        // First acquire should succeed and return a non-null Connection
        final Connection conn = syncLeaderLock.acquireLock();
        assertThat(conn).isNotNull();
        assertThat(conn.isClosed()).isFalse();

        // While held, another acquire attempt on a different connection should fail (return null)
        final Connection conn2 = syncLeaderLock.acquireLock();
        assertThat(conn2).isNull();

        // Release the first lock
        syncLeaderLock.releaseLock(conn);
        assertThat(conn.isClosed()).isTrue();

        // After release, a new attempt should succeed
        final Connection conn3 = syncLeaderLock.acquireLock();
        assertThat(conn3).isNotNull();

        // Cleanup
        syncLeaderLock.releaseLock(conn3);
    }
}
