package com.printkeep.quota.codec.exception;

/**
 * Exception thrown when binary parsing of an IPP packet fails.
 */
@SuppressWarnings("serial")
public class IppParserException extends IppException {
    public IppParserException(final String message) {
        super(message);
    }

    public IppParserException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
