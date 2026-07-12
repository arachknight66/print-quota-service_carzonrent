package com.printkeep.quota.core.identity.ldap.repository;

import com.printkeep.quota.core.identity.ldap.config.LdapProperties;
import com.printkeep.quota.core.identity.ldap.dto.LdapUserDto;
import com.printkeep.quota.core.identity.ldap.exception.LdapConnectionException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.ldap.control.PagedResultsDirContextProcessor;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.ContextSource;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.SingleContextSource;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.ldap.support.LdapUtils;
import org.springframework.stereotype.Repository;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import java.util.List;
import java.util.Optional;

/**
 * Repository for performing read-only queries against Active Directory / LDAP.
 * Encapsulates search control, pagination, and caching strategies.
 */
@Repository
public class LdapUserRepository {

    private final LdapTemplate ldapTemplate;
    private final ContextSource contextSource;
    private final LdapProperties properties;

    public LdapUserRepository(final LdapTemplate ldapTemplate, final ContextSource contextSource, final LdapProperties properties) {
        this.ldapTemplate = ldapTemplate;
        this.contextSource = contextSource;
        this.properties = properties;
    }

    /**
     * Resolves a user by their Active Directory sAMAccountName.
     * Caches lookups with a standard cache key to prevent repeated Active Directory round-trips.
     *
     * @param username Active Directory unique login name.
     * @return an Optional containing the LdapUserDto if found, otherwise empty.
     */
    @Cacheable(value = "ldapUsers", key = "#username")
    public Optional<LdapUserDto> findByUsername(final String username) {
        try {
            final List<LdapUserDto> users = ldapTemplate.search(
                    LdapQueryBuilder.query()
                            .base(properties.getSearchBase())
                            .where("objectClass").is("user")
                            .and("sAMAccountName").is(username),
                    getAttributesMapper()
            );
            return users.stream().findFirst();
        } catch (final Exception e) {
            throw new LdapConnectionException("Failed to lookup user in LDAP: " + username, e);
        }
    }

    /**
     * Performs a paged search of the search base to load all AD users.
     * Uses SingleContextSource to share a single LDAP session connection context, avoiding cookie expiration.
     *
     * @return List of all synced LDAP user records.
     */
    public List<LdapUserDto> findAllUsers() {
        try {
            return SingleContextSource.doWithSingleContext(contextSource, operations -> {
                final List<LdapUserDto> allUsers = new java.util.ArrayList<>();
                final PagedResultsDirContextProcessor processor = new PagedResultsDirContextProcessor(1000);

                final SearchControls controls = new SearchControls();
                controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
                controls.setReturningAttributes(new String[]{
                        "sAMAccountName", "displayName", "mail", "department", "employeeID", "userAccountControl", "distinguishedName"
                });

                do {
                    final List<LdapUserDto> page = operations.search(
                            LdapUtils.newLdapName(properties.getSearchBase()),
                            "(objectClass=user)",
                            controls,
                            getAttributesMapper(),
                            processor
                    );
                    allUsers.addAll(page);
                } while (processor.getCookie() != null && processor.getCookie().getCookie() != null);

                return allUsers;
            });
        } catch (final Exception e) {
            throw new LdapConnectionException("Failed to list all users from LDAP", e);
        }
    }

    /**
     * Searches Active Directory using a text query against sAMAccountName, displayName, or mail.
     *
     * @param queryText search parameter text.
     * @return list of matching users.
     */
    public List<LdapUserDto> search(final String queryText) {
        try {
            return ldapTemplate.search(
                    LdapQueryBuilder.query()
                            .base(properties.getSearchBase())
                            .where("objectClass").is("user")
                            .and(LdapQueryBuilder.query()
                                    .where("sAMAccountName").like("*" + queryText + "*")
                                    .or("displayName").like("*" + queryText + "*")
                                    .or("mail").like("*" + queryText + "*")),
                    getAttributesMapper()
            );
        } catch (final Exception e) {
            throw new LdapConnectionException("Failed to search users in LDAP with query: " + queryText, e);
        }
    }

    private AttributesMapper<LdapUserDto> getAttributesMapper() {
        return attrs -> {
            final String username = getAttributeValue(attrs, "sAMAccountName");
            final String displayName = getAttributeValue(attrs, "displayName");
            final String email = getAttributeValue(attrs, "mail");
            final String department = getAttributeValue(attrs, "department");
            final String employeeId = getAttributeValue(attrs, "employeeID");

            final String uacVal = getAttributeValue(attrs, "userAccountControl");
            boolean enabled = true;
            if (uacVal != null) {
                try {
                    final int uac = Integer.parseInt(uacVal);
                    // ACCOUNTDISABLE bit is 0x02
                    enabled = (uac & 2) == 0;
                } catch (final NumberFormatException e) {
                    // Fallback to active
                }
            }

            final String dn = getAttributeValue(attrs, "distinguishedName");
            return new LdapUserDto(username, displayName, email, department, employeeId, enabled, dn);
        };
    }

    private String getAttributeValue(final Attributes attrs, final String attrName) throws NamingException {
        final Attribute attr = attrs.get(attrName);
        if (attr == null) {
            return null;
        }
        return (String) attr.get();
    }
}
