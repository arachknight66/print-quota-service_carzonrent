package com.printkeep.quota.core.identity.ldap.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.printkeep.quota.core.AbstractIntegrationTest;
import com.printkeep.quota.core.identity.ldap.LdapTestConfiguration;
import com.printkeep.quota.core.identity.ldap.dto.LdapUserDto;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for LdapUserRepository using an embedded UnboundID LDAP server.
 */
@Import(LdapTestConfiguration.class)
@TestPropertySource(properties = {"app.ldap.sync.enabled=false"})
class LdapUserRepositoryTests extends AbstractIntegrationTest {

    @Autowired
    private LdapUserRepository ldapUserRepository;

    @Test
    void testFindByUsername_existingUser() {
        final Optional<LdapUserDto> result = ldapUserRepository.findByUsername("jdoe");
        assertThat(result).isPresent();
        assertThat(result.get().username()).isEqualTo("jdoe");
        assertThat(result.get().department()).isEqualTo("Engineering");
        assertThat(result.get().enabled()).isTrue();
    }

    @Test
    void testFindByUsername_nonExistent() {
        final Optional<LdapUserDto> result = ldapUserRepository.findByUsername("nobody");
        assertThat(result).isEmpty();
    }

    @Test
    void testFindAllUsers() {
        final List<LdapUserDto> users = ldapUserRepository.findAllUsers();
        assertThat(users).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void testFindByUsername_disabledUser() {
        final Optional<LdapUserDto> result = ldapUserRepository.findByUsername("disabled_user");
        assertThat(result).isPresent();
        assertThat(result.get().enabled()).isFalse();
    }

    @Test
    void testEscapeLdapSearchValueEscapesFilterMetacharacters() {
        final String escaped = LdapUserRepository.escapeLdapSearchValue("a*b(c)d\\e\u0000");

        assertThat(escaped).isEqualTo("a\\2ab\\28c\\29d\\5ce\\00");
    }
}
