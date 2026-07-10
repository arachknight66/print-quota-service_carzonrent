package com.printkeep.quota.core.identity.ldap;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.sdk.LDAPException;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;

import javax.naming.directory.DirContext;

/**
 * Test configuration that provides an in-memory UnboundID LDAP server
 * pre-populated with test user entries for integration testing.
 */
@TestConfiguration
public class LdapTestConfiguration {

    private static final String BASE_DN = "dc=company,dc=local";
    private static final String ADMIN_DN = "cn=admin,dc=company,dc=local";
    private static final String ADMIN_PASSWORD = "admin";
    private static final String USERS_OU = "ou=users,dc=company,dc=local";

    private InMemoryDirectoryServer directoryServer;

    /**
     * Creates and starts an {@link InMemoryDirectoryServer} on a random available port.
     * The server is populated with a base DN, an organizational unit for users,
     * and three test user entries.
     *
     * @return the running in-memory directory server
     * @throws LDAPException if the server cannot be created or started
     */
    @Bean
    public InMemoryDirectoryServer inMemoryDirectoryServer() throws LDAPException {
        InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(BASE_DN);
        config.addAdditionalBindCredentials(ADMIN_DN, ADMIN_PASSWORD);
        config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("default", 0));
        config.setSchema(null); // Disable schema validation for flexible attribute support

        directoryServer = new InMemoryDirectoryServer(config);
        directoryServer.startListening();

        populateDirectory();

        return directoryServer;
    }

    /**
     * Provides LDAP connection properties pointing at the in-memory server.
     *
     * @param server the running in-memory directory server
     * @return configured {@link LdapProperties}
     */
    @Bean
    @Primary
    public LdapProperties ldapProperties(InMemoryDirectoryServer server) {
        int port = server.getListenPort();
        LdapProperties properties = new LdapProperties();
        properties.setUrls(java.util.List.of("ldap://localhost:" + port));
        properties.setBaseDn(BASE_DN);
        properties.setUsername(ADMIN_DN);
        properties.setPassword(ADMIN_PASSWORD);
        properties.setSearchBase("ou=users");
        return properties;
    }

    /**
     * Provides a configured {@link LdapContextSource} bound to the in-memory server.
     *
     * @param ldapProperties the LDAP connection properties
     * @return an initialized {@link LdapContextSource}
     */
    @Bean
    @Primary
    public LdapContextSource ldapContextSource(LdapProperties ldapProperties) {
        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrls(ldapProperties.getUrls());
        contextSource.setBase(ldapProperties.getBaseDn());
        contextSource.setUserDn(ldapProperties.getUsername());
        contextSource.setPassword(ldapProperties.getPassword());
        contextSource.afterPropertiesSet();
        return contextSource;
    }

    /**
     * Exposes the {@link LdapContextSource} as a {@link javax.naming.directory.DirContext}-producing
     * {@link org.springframework.ldap.core.ContextSource} bean.
     *
     * @param ldapContextSource the configured context source
     * @return the same context source, typed as {@link org.springframework.ldap.core.ContextSource}
     */
    @Bean
    @Primary
    public org.springframework.ldap.core.ContextSource contextSource(LdapContextSource ldapContextSource) {
        return ldapContextSource;
    }

    /**
     * Provides an {@link LdapTemplate} wired to the in-memory server.
     *
     * @param contextSource the LDAP context source
     * @return a ready-to-use {@link LdapTemplate}
     */
    @Bean
    @Primary
    public LdapTemplate ldapTemplate(org.springframework.ldap.core.ContextSource contextSource) {
        return new LdapTemplate(contextSource);
    }

    /**
     * Shuts down the in-memory directory server when the application context is destroyed.
     */
    @PreDestroy
    public void shutdown() {
        if (directoryServer != null) {
            directoryServer.shutDown(true);
        }
    }

    // -------------------------------------------------------------------------
    // Directory population helpers
    // -------------------------------------------------------------------------

    private void populateDirectory() throws LDAPException {
        // Create base DN entry
        directoryServer.add(
                "dn: " + BASE_DN,
                "objectClass: top",
                "objectClass: domain",
                "dc: company"
        );

        // Create organizational unit for users
        directoryServer.add(
                "dn: " + USERS_OU,
                "objectClass: top",
                "objectClass: organizationalUnit",
                "ou: users"
        );

        // User 1 — active Engineering employee
        directoryServer.add(
                "dn: cn=jdoe," + USERS_OU,
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
                "dn: cn=asmith," + USERS_OU,
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
                "dn: cn=disabled_user," + USERS_OU,
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
    }
}
