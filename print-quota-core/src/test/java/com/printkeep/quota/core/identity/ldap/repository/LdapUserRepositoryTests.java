package com.printkeep.quota.core.identity.ldap.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.printkeep.quota.core.AbstractIntegrationTest;
import com.printkeep.quota.core.identity.ldap.dto.LdapUserDto;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for LdapUserRepository using an embedded UnboundID LDAP server.
 */
@TestPropertySource(properties = {"app.ldap.sync.enabled=false"})
class LdapUserRepositoryTests extends AbstractIntegrationTest {

    private static final InMemoryDirectoryServer directoryServer;
    private static final int ldapPort;

    static {
        try {
            InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig("dc=company,dc=local");
            config.addAdditionalBindCredentials("cn=admin,dc=company,dc=local", "admin");
            config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("default", 0));
            config.setSchema(null);

            directoryServer = new InMemoryDirectoryServer(config);
            directoryServer.startListening();
            ldapPort = directoryServer.getListenPort();

            // Populate entries
            directoryServer.add(
                    "dn: dc=company,dc=local",
                    "objectClass: top",
                    "objectClass: domain",
                    "dc: company"
            );
            directoryServer.add(
                    "dn: ou=users,dc=company,dc=local",
                    "objectClass: top",
                    "objectClass: organizationalUnit",
                    "ou: users"
            );
            // User 1 — active Engineering employee
            directoryServer.add(
                    "dn: cn=jdoe,ou=users,dc=company,dc=local",
                    "objectClass: top",
                    "objectClass: person",
                    "objectClass: organizationalPerson",
                    "objectClass: inetOrgPerson",
                    "objectClass: user",
                    "cn: jdoe",
                    "sn: Doe",
                    "displayName: John Doe",
                    "mail: jdoe@company.local",
                    "sAMAccountName: jdoe",
                    "department: Engineering",
                    "employeeID: E001",
                    "userAccountControl: 512",
                    "distinguishedName: cn=jdoe,ou=users,dc=company,dc=local"
            );
            // User 2 — active HR employee
            directoryServer.add(
                    "dn: cn=asmith,ou=users,dc=company,dc=local",
                    "objectClass: top",
                    "objectClass: person",
                    "objectClass: organizationalPerson",
                    "objectClass: inetOrgPerson",
                    "objectClass: user",
                    "cn: asmith",
                    "sn: Smith",
                    "displayName: Alice Smith",
                    "mail: asmith@company.local",
                    "sAMAccountName: asmith",
                    "department: HR",
                    "employeeID: E002",
                    "userAccountControl: 512",
                    "distinguishedName: cn=asmith,ou=users,dc=company,dc=local"
            );
            // User 3 — disabled IT employee (userAccountControl=514)
            directoryServer.add(
                    "dn: cn=disabled_user,ou=users,dc=company,dc=local",
                    "objectClass: top",
                    "objectClass: person",
                    "objectClass: organizationalPerson",
                    "objectClass: inetOrgPerson",
                    "objectClass: user",
                    "cn: disabled_user",
                    "sn: Disabled",
                    "displayName: Disabled User",
                    "mail: disabled@company.local",
                    "sAMAccountName: disabled_user",
                    "department: IT",
                    "employeeID: E003",
                    "userAccountControl: 514",
                    "distinguishedName: cn=disabled_user,ou=users,dc=company,dc=local"
            );

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    if (directoryServer != null) {
                        directoryServer.shutDown(true);
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @DynamicPropertySource
    static void configureLdapProperties(DynamicPropertyRegistry registry) {
        registry.add("app.ldap.urls", () -> List.of("ldap://localhost:" + ldapPort));
        registry.add("app.ldap.base-dn", () -> "dc=company,dc=local");
        registry.add("app.ldap.username", () -> "cn=admin,dc=company,dc=local");
        registry.add("app.ldap.password", () -> "admin");
        registry.add("app.ldap.search-base", () -> "ou=users");
    }

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
