package com.printkeep.quota.core.identity.service;

import com.printkeep.quota.core.model.User;
import java.util.Optional;

/**
 * Service defining interface for looking up resolved user identities.
 */
public interface IdentityService {

    /**
     * Resolves a user by their domain username.
     *
     * @param username the username (with or without domain prefix).
     * @return an Optional containing the User if found, otherwise empty.
     */
    Optional<User> findUser(String username);
}
