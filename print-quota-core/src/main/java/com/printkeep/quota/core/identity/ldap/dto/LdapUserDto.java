package com.printkeep.quota.core.identity.ldap.dto;

/**
 * Data Transfer Object representing user attributes retrieved from Active Directory / LDAP.
 */
public record LdapUserDto(
        String username,
        String displayName,
        String email,
        String department,
        String employeeId,
        boolean enabled,
        String distinguishedName
) {}
