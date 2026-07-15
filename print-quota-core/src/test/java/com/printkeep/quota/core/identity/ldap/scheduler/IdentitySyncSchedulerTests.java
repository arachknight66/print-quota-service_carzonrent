package com.printkeep.quota.core.identity.ldap.scheduler;

import static org.mockito.Mockito.*;

import com.printkeep.quota.core.identity.ldap.config.LdapProperties;
import com.printkeep.quota.core.identity.ldap.service.IdentitySynchronizationService;
import java.sql.Connection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for IdentitySyncScheduler.
 */
@ExtendWith(MockitoExtension.class)
class IdentitySyncSchedulerTests {

    @Mock
    private IdentitySynchronizationService syncService;

    @Mock
    private LdapProperties ldapProperties;

    @Mock
    private SyncLeaderLock syncLeaderLock;

    @Mock
    private Connection mockConnection;

    private IdentitySyncScheduler scheduler;
    private LdapProperties.Sync syncConfig;

    @BeforeEach
    void setUp() {
        syncConfig = new LdapProperties.Sync();
        lenient().when(ldapProperties.getSync()).thenReturn(syncConfig);
        scheduler = new IdentitySyncScheduler(syncService, ldapProperties, syncLeaderLock);
    }

    @Test
    void testScheduledSyncWhenEnabledAndLockAcquired() {
        syncConfig.setEnabled(true);
        when(syncLeaderLock.acquireLock()).thenReturn(mockConnection);

        scheduler.scheduledSync();

        verify(syncService, times(1)).synchronizeIdentities();
        verify(syncLeaderLock, times(1)).acquireLock();
        verify(syncLeaderLock, times(1)).releaseLock(mockConnection);
    }

    @Test
    void testScheduledSyncWhenEnabledAndLockNotAcquired() {
        syncConfig.setEnabled(true);
        when(syncLeaderLock.acquireLock()).thenReturn(null);

        scheduler.scheduledSync();

        verify(syncService, never()).synchronizeIdentities();
        verify(syncLeaderLock, times(1)).acquireLock();
        verify(syncLeaderLock, never()).releaseLock(any());
    }

    @Test
    void testScheduledSyncWhenDisabled() {
        syncConfig.setEnabled(false);
        scheduler.scheduledSync();
        verify(syncService, never()).synchronizeIdentities();
        verify(syncLeaderLock, never()).acquireLock();
    }

    @Test
    void testTriggerSyncAlwaysRuns() {
        scheduler.triggerSync();
        verify(syncService, times(1)).synchronizeIdentities();
    }
}
