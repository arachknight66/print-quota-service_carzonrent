package com.printkeep.quota.core.identity.ldap.scheduler;

import com.printkeep.quota.core.identity.ldap.config.LdapProperties;
import com.printkeep.quota.core.identity.ldap.service.IdentitySynchronizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Connection;

/**
 * Scheduler for driving periodic batch user synchronizations from Active Directory.
 */
@Component
public class IdentitySyncScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdentitySyncScheduler.class);

    private final IdentitySynchronizationService syncService;
    private final LdapProperties ldapProperties;
    private final SyncLeaderLock syncLeaderLock;

    public IdentitySyncScheduler(
            final IdentitySynchronizationService syncService,
            final LdapProperties ldapProperties,
            final SyncLeaderLock syncLeaderLock) {
        this.syncService = syncService;
        this.ldapProperties = ldapProperties;
        this.syncLeaderLock = syncLeaderLock;
    }

    /**
     * Periodically triggers batch synchronization based on the configured cron schedule.
     * Only runs if scheduled sync is enabled in configuration properties.
     * Uses database-level advisory locking to guarantee only one node executes the sync.
     */
    @Scheduled(cron = "${app.ldap.sync.cron:0 0 * * * *}")
    public void scheduledSync() {
        if (!ldapProperties.getSync().isEnabled()) {
            LOGGER.debug("Scheduled Active Directory synchronization is disabled by configuration.");
            return;
        }

        LOGGER.info("Scheduled synchronization trigger fired. Attempting to acquire leader lock.");
        final Connection conn = syncLeaderLock.acquireLock();
        if (conn == null) {
            LOGGER.debug("Skipping sync — another instance holds the leader lock");
            return;
        }

        try {
            triggerSync();
        } finally {
            syncLeaderLock.releaseLock(conn);
        }
    }

    /**
     * Manually triggers immediate full synchronization.
     * Can be invoked programmatically or via administrative controllers.
     */
    public void triggerSync() {
        try {
            syncService.synchronizeIdentities();
        } catch (final Exception e) {
            LOGGER.error("Batch identity synchronization execution failed: {}", e.getMessage(), e);
        }
    }
}
