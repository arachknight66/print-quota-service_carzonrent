package com.printkeep.quota.core.model;

import java.time.Instant;

/**
 * Standard error response model returned to clients when REST API exceptions occur.
 * Implemented as a Java 21 record for immutability.
 *
 * @param timestamp     The timestamp when the error occurred.
 * @param status        The HTTP status code.
 * @param error         The short error type description.
 * @param message       The detailed error message.
 * @param path          The request path where the error occurred.
 * @param correlationId The correlation ID for tracing the request in log files.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String correlationId
) {}
