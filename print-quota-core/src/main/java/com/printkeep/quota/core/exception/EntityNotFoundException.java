package com.printkeep.quota.core.exception;

/**
 * Exception thrown when an expected entity (User, Quota, or PrintLog) cannot be found by its identifier.
 */
public class EntityNotFoundException extends PersistenceException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new EntityNotFoundException with the specified message.
     *
     * @param message descriptive error message.
     */
    public EntityNotFoundException(final String message) {
        super(message);
    }
}
