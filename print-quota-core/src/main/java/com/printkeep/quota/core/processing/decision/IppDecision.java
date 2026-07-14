package com.printkeep.quota.core.processing.decision;

/**
 * Enumeration representing decision outcomes of the print processing engine.
 */
public enum IppDecision {
    /**
     * Print request is allowed and quota is reserved.
     */
    ALLOW,

    /**
     * Print request rejected due to insufficient quota balance.
     */
    REJECT_INSUFFICIENT_QUOTA,

    /**
     * Print request rejected because user is not found.
     */
    REJECT_UNKNOWN_USER,

    /**
     * Print request rejected because the user domain account is disabled.
     */
    REJECT_DISABLED_USER,

    /**
     * Print request rejected due to parsing, malformed parameters, or invalid attribute groups.
     */
    REJECT_INVALID_REQUEST,

    /**
     * Print request rejected due to system, network, database, or directory server failures.
     */
    SYSTEM_ERROR
}
