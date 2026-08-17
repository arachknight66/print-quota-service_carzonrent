package com.printkeep.quota.core.repository;

import com.printkeep.quota.core.model.User;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for managing User entity persistence.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Streams all users for scheduled allocation processing.
     *
     * @return stream of users.
     */
    @Query("SELECT u FROM User u")
    Stream<User> streamAll();

    /**
     * Finds an active user by their domain username.
     *
     * @param domainUsername unique domain username to search.
     * @return an Optional containing the found user, or empty.
     */
    Optional<User> findByDomainUsername(String domainUsername);

    long countByIsActive(boolean isActive);

    /**
     * Search users by dynamic filtering with pagination.
     */
    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE "
            + "(CAST(:username AS string) IS NULL OR LOWER(u.domainUsername) LIKE LOWER(CONCAT('%', CAST(:username AS string), '%'))) AND "
            + "(CAST(:department AS string) IS NULL OR LOWER(u.department) LIKE LOWER(CONCAT('%', CAST(:department AS string), '%')))")
    org.springframework.data.domain.Page<User> searchUsers(
            @org.springframework.data.repository.query.Param("username") String username,
            @org.springframework.data.repository.query.Param("department") String department,
            org.springframework.data.domain.Pageable pageable);
}
