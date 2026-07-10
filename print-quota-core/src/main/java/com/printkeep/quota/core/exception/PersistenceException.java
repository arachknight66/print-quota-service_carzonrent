package com.printkeep.quota.core.exception;

/**
 * Base exception for database and persistence layer errors in the Print Quota Management System.
 */
public class PersistenceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new PersistenceException with the specified message.
     *
     * @param message descriptive error message.
     */
    public PersistenceException(final String message) {
        super(message);
    }

    /**
     * Constructs a new PersistenceException with the specified message and cause.
     *
     * @param message descriptive error message.
     * @param cause   underlying cause of the exception.
     */
    public PersistenceException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
