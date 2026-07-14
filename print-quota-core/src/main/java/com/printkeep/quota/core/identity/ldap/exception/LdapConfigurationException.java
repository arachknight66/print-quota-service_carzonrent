package com.printkeep.quota.core.identity.ldap.exception;

/**
 * Exception thrown when there is a configuration error with the LDAP settings.
 */
public class LdapConfigurationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new LdapConfigurationException with the specified message.
     *
     * @param message descriptive error message.
     */
    public LdapConfigurationException(final String message) {
        super(message);
    }

    /**
     * Constructs a new LdapConfigurationException with the specified message and cause.
     *
     * @param message descriptive error message.
     * @param cause   underlying cause of the exception.
     */
    public LdapConfigurationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
