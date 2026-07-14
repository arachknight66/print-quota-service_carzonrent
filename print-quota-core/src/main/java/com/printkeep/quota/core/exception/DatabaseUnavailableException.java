package com.printkeep.quota.core.exception;

/**
 * Exception thrown when the database service is unavailable or connection timeouts occur.
 */
public class DatabaseUnavailableException extends PersistenceException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new DatabaseUnavailableException with the specified message.
     *
     * @param message descriptive error message.
     */
    public DatabaseUnavailableException(final String message) {
        super(message);
    }

    /**
     * Constructs a new DatabaseUnavailableException with the specified message and cause.
     *
     * @param message descriptive error message.
     * @param cause   underlying cause of the exception.
     */
    public DatabaseUnavailableException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
