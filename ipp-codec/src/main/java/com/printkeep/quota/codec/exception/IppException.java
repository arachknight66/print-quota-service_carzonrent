package com.printkeep.quota.codec.exception;

/**
 * Base exception for all IPP protocol errors.
 */
@SuppressWarnings("serial")
public class IppException extends RuntimeException {
    public IppException(final String message) {
        super(message);
    }

    public IppException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
