package com.printkeep.quota.core.identity.ldap.scheduler;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.printkeep.quota.core.identity.ldap.config.LdapProperties;
import com.printkeep.quota.core.identity.ldap.service.IdentitySynchronizationService;
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

    private IdentitySyncScheduler scheduler;
    private LdapProperties.Sync syncConfig;

    @BeforeEach
    void setUp() {
        syncConfig = new LdapProperties.Sync();
        lenient().when(ldapProperties.getSync()).thenReturn(syncConfig);
        scheduler = new IdentitySyncScheduler(syncService, ldapProperties);
    }

    @Test
    void testScheduledSyncWhenEnabled() {
        syncConfig.setEnabled(true);
        scheduler.scheduledSync();
        verify(syncService, times(1)).synchronizeIdentities();
    }

    @Test
    void testScheduledSyncWhenDisabled() {
        syncConfig.setEnabled(false);
        scheduler.scheduledSync();
        verify(syncService, never()).synchronizeIdentities();
    }

    @Test
    void testTriggerSyncAlwaysRuns() {
        scheduler.triggerSync();
        verify(syncService, times(1)).synchronizeIdentities();
    }
}
