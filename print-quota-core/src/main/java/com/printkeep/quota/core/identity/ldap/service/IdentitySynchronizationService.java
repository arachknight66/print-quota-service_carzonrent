package com.printkeep.quota.core.identity.ldap.service;

import com.printkeep.quota.core.identity.ldap.dto.LdapUserDto;
import com.printkeep.quota.core.identity.ldap.exception.LdapConnectionException;
import com.printkeep.quota.core.identity.ldap.exception.LdapSynchronizationException;
import com.printkeep.quota.core.identity.ldap.mapper.LdapUserMapper;
import com.printkeep.quota.core.identity.ldap.repository.LdapUserRepository;
import com.printkeep.quota.core.model.User;
import com.printkeep.quota.core.repository.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Service orchestrating identity synchronization from Active Directory to the PostgreSQL database.
 */
@Service
public class IdentitySynchronizationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdentitySynchronizationService.class);

    private final LdapUserRepository ldapUserRepository;
    private final UserRepository userRepository;
    private final LdapUserMapper ldapUserMapper;
    private final MeterRegistry meterRegistry;

    private final Counter successCounter;
    private final Counter failureCounter;
    private final Counter errorCounter;
    private final Timer syncTimer;

    @Value("${app.ldap.domain-prefix:company\\\\}")
    private String domainPrefix;

    public IdentitySynchronizationService(
            final LdapUserRepository ldapUserRepository,
            final UserRepository userRepository,
            final LdapUserMapper ldapUserMapper,
            final MeterRegistry meterRegistry) {
        this.ldapUserRepository = ldapUserRepository;
        this.userRepository = userRepository;
        this.ldapUserMapper = ldapUserMapper;
        this.meterRegistry = meterRegistry;

        this.successCounter = Counter.builder("identity.sync.success")
                .description("Number of successfully synchronized user records")
                .register(meterRegistry);
        this.failureCounter = Counter.builder("identity.sync.failed")
                .description("Number of failed user record synchronizations")
                .register(meterRegistry);
        this.errorCounter = Counter.builder("identity.sync.errors")
                .description("Number of general LDAP/database connection errors")
                .register(meterRegistry);
        this.syncTimer = Timer.builder("identity.sync.duration")
                .description("Total time taken to complete synchronization")
                .register(meterRegistry);
    }

    /**
     * Triggers batch identity synchronization from LDAP/Active Directory into local storage.
     */
    @Transactional
    public void synchronizeIdentities() {
        final String correlationId = UUID.randomUUID().toString();
        final Instant startTime = Instant.now();
        LOGGER.info("[SyncID: {}] Starting Active Directory identity synchronization...", correlationId);

        List<LdapUserDto> ldapUsers;
        try {
            ldapUsers = ldapUserRepository.findAllUsers();
        } catch (final LdapConnectionException e) {
            errorCounter.increment();
            LOGGER.error("[SyncID: {}] LDAP connection failed during synchronization: {}", correlationId, e.getMessage());
            throw new LdapSynchronizationException("LDAP read failure during synchronization", e);
        } catch (final Exception e) {
            errorCounter.increment();
            LOGGER.error("[SyncID: {}] Unexpected exception reading LDAP directories: {}", correlationId, e.getMessage());
            throw new LdapSynchronizationException("Unexpected synchronization read failure", e);
        }

        int processed = 0;
        int createdCount = 0;
        int updatedCount = 0;

        for (final LdapUserDto ldapUser : ldapUsers) {
            if (ldapUser.username() == null) {
                continue;
            }

            try {
                final String domainUsername = resolveDomainUsername(ldapUser.username());
                final Optional<User> existingUserOpt = userRepository.findByDomainUsername(domainUsername);

                if (existingUserOpt.isPresent()) {
                    final User existingUser = existingUserOpt.get();
                    ldapUserMapper.updateEntity(ldapUser, existingUser);
                    userRepository.save(existingUser);
                    updatedCount++;
                } else {
                    final User newUser = ldapUserMapper.toEntity(ldapUser);
                    userRepository.save(newUser);
                    createdCount++;
                }
                successCounter.increment();
                processed++;
            } catch (final Exception e) {
                failureCounter.increment();
                LOGGER.error("[SyncID: {}] Failed to synchronize user '{}': {}", correlationId, ldapUser.username(), e.getMessage());
            }
        }

        userRepository.flush();

        final Duration duration = Duration.between(startTime, Instant.now());
        syncTimer.record(duration.toMillis(), TimeUnit.MILLISECONDS);

        LOGGER.info("[SyncID: {}] Identity synchronization complete. Duration: {}ms, Users Processed: {}, Created: {}, Updated: {}",
                correlationId, duration.toMillis(), processed, createdCount, updatedCount);
    }

    private String resolveDomainUsername(final String username) {
        if (username.contains("\\")) {
            return username;
        }
        return domainPrefix + username;
    }
}
