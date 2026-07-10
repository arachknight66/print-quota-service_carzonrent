package com.printkeep.quota.core.identity.service.impl;

import com.printkeep.quota.core.identity.service.IdentityService;
import com.printkeep.quota.core.model.User;
import com.printkeep.quota.core.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Implementation of IdentityService resolving users from PostgreSQL repository.
 */
@Service
public class IdentityServiceImpl implements IdentityService {

    private final UserRepository userRepository;
    private final String domainPrefix;

    public IdentityServiceImpl(
            final UserRepository userRepository,
            @Value("${app.ldap.domain-prefix:company\\\\}") final String domainPrefix) {
        this.userRepository = userRepository;
        this.domainPrefix = domainPrefix;
    }

    @Override
    public Optional<User> findUser(final String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }

        // Try direct lookup
        Optional<User> userOpt = userRepository.findByDomainUsername(username);
        if (userOpt.isPresent()) {
            return userOpt;
        }

        // Try lookup with prepended domain prefix if not already present
        if (!username.contains("\\")) {
            final String prefixed = domainPrefix + username;
            userOpt = userRepository.findByDomainUsername(prefixed);
            if (userOpt.isPresent()) {
                return userOpt;
            }
        }

        return Optional.empty();
    }
}
