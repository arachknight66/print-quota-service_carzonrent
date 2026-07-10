package com.printkeep.quota.core.repository;

import com.printkeep.quota.core.model.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for managing User entity persistence.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Finds an active user by their domain username.
     *
     * @param domainUsername unique domain username to search.
     * @return an Optional containing the found user, or empty.
     */
    Optional<User> findByDomainUsername(String domainUsername);
}
