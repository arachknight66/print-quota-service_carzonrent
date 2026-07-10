package com.printkeep.quota.core.processing.exception;

/**
 * Base exception class for all print job processing errors.
 */
@SuppressWarnings("serial")
public class PrintProcessingException extends RuntimeException {
    public PrintProcessingException(final String message) {
        super(message);
    }

    public PrintProcessingException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
