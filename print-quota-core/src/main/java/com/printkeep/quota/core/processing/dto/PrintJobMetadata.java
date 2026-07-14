package com.printkeep.quota.core.processing.dto;

import java.time.Instant;

/**
 * Immutable DTO representing metadata extracted from an IPP print request.
 */
public record PrintJobMetadata(
        String username,
        String documentName,
        String printerName,
        int requestedCopies,
        boolean duplex,
        boolean color,
        int estimatedPages,
        String jobName,
        String clientHostname,
        Instant timestamp
) {
    public PrintJobMetadata {
        if (username == null) {
            throw new IllegalArgumentException("Username must not be null");
        }
        if (documentName == null) {
            throw new IllegalArgumentException("Document name must not be null");
        }
        if (printerName == null) {
            throw new IllegalArgumentException("Printer name must not be null");
        }
        if (requestedCopies <= 0) {
            throw new IllegalArgumentException("Requested copies must be greater than zero");
        }
        if (estimatedPages <= 0) {
            throw new IllegalArgumentException("Estimated pages must be greater than zero");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("Timestamp must not be null");
        }
    }
}
