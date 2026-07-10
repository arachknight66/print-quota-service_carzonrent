package com.printkeep.quota.repository;

import com.printkeep.quota.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByDomainUsername(String domainUsername);
    List<User> findByIsActiveTrue();
}
