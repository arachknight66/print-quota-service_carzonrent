package com.printkeep.quota.core.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.printkeep.quota.core.AbstractIntegrationTest;
import com.printkeep.quota.core.model.User;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository integration tests for the User entity.
 */
@Transactional
class UserRepositoryTests extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    /**
     * Verifies that a user can be saved, audit fields are populated, and the user can be retrieved.
     */
    @Test
    void testSaveAndRetrieveUser() {
        final User user = new User();
        user.setDomainUsername("company\\jdoe");
        user.setDepartment("Engineering");
        user.setActive(true);

        final User saved = userRepository.saveAndFlush(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getCreatedBy()).isEqualTo("system");

        final Optional<User> retrieved = userRepository.findById(saved.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getDomainUsername()).isEqualTo("company\\jdoe");
    }

    /**
     * Verifies that domain username unique constraint is enforced at database level.
     */
    @Test
    void testUniqueDomainUsernameConstraint() {
        final User user1 = new User();
        user1.setDomainUsername("company\\unique");
        user1.setDepartment("HR");
        userRepository.saveAndFlush(user1);

        final User user2 = new User();
        user2.setDomainUsername("company\\unique");
        user2.setDepartment("Finance");

        assertThatThrownBy(() -> userRepository.saveAndFlush(user2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Verifies soft-delete functionality where deleted users are automatically filtered.
     */
    @Test
    void testSoftDeleteUser() {
        final User user = new User();
        user.setDomainUsername("company\\delete-me");
        user.setDepartment("Sales");
        final User saved = userRepository.saveAndFlush(user);

        userRepository.delete(saved);
        userRepository.flush();

        // Retrieve by ID should now be empty due to @SQLRestriction
        final Optional<User> retrieved = userRepository.findById(saved.getId());
        assertThat(retrieved).isEmpty();
    }
}
