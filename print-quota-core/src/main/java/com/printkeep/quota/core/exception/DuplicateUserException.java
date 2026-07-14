package com.printkeep.quota.core.exception;

/**
 * Exception thrown when attempting to persist a user with a domain username that already exists.
 */
public class DuplicateUserException extends PersistenceException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new DuplicateUserException with the specified message.
     *
     * @param message descriptive error message.
     */
    public DuplicateUserException(final String message) {
        super(message);
    }
}
