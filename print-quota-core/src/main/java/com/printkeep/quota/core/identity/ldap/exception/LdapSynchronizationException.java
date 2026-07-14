package com.printkeep.quota.core.identity.ldap.exception;

/**
 * Exception thrown when user identity synchronization processes fail.
 */
public class LdapSynchronizationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new LdapSynchronizationException with the specified message.
     *
     * @param message descriptive error message.
     */
    public LdapSynchronizationException(final String message) {
        super(message);
    }

    /**
     * Constructs a new LdapSynchronizationException with the specified message and cause.
     *
     * @param message descriptive error message.
     * @param cause   underlying cause of the exception.
     */
    public LdapSynchronizationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
