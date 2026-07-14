package com.printkeep.quota.core.identity.ldap.exception;

/**
 * Exception thrown when a requested user is not found in the Active Directory/LDAP directory.
 */
public class LdapUserNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new LdapUserNotFoundException with the specified message.
     *
     * @param message descriptive error message.
     */
    public LdapUserNotFoundException(final String message) {
        super(message);
    }
}
