package com.printkeep.quota.core.processing.exception;

/**
 * Exception thrown when a user's print quota balance is insufficient for a print job.
 */
@SuppressWarnings("serial")
public class QuotaExceededException extends PrintProcessingException {
    public QuotaExceededException(final String message) {
        super(message);
    }
}
