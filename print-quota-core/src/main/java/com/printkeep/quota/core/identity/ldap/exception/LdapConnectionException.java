package com.printkeep.quota.core.identity.ldap.exception;

/**
 * Exception thrown when a connection to the Active Directory/LDAP server cannot be established or is lost.
 */
public class LdapConnectionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new LdapConnectionException with the specified message.
     *
     * @param message descriptive error message.
     */
    public LdapConnectionException(final String message) {
        super(message);
    }

    /**
     * Constructs a new LdapConnectionException with the specified message and cause.
     *
     * @param message descriptive error message.
     * @param cause   underlying cause of the exception.
     */
    public LdapConnectionException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
