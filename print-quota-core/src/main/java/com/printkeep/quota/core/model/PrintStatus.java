package com.printkeep.quota.core.model;

/**
 * Enumeration representing the execution status of a print job request.
 */
public enum PrintStatus {
    
    /**
     * The print job succeeded, was validated against quota, and forwarded to the printer.
     */
    SUCCESS,

    /**
     * The print job was rejected because the user did not have sufficient pages in their quota.
     */
    REJECTED_QUOTA,

    /**
     * An error occurred during print job parsing, database communication, or forwarding.
     */
    ERROR
}
