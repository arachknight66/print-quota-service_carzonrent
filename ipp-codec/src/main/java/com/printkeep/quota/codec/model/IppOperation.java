package com.printkeep.quota.codec.model;

/**
 * Standard IPP Operations as defined by RFC 8011.
 */
public enum IppOperation {
    PRINT_JOB((short) 0x0002),
    PRINT_URI((short) 0x0003),
    VALIDATE_JOB((short) 0x0004),
    CREATE_JOB((short) 0x0005),
    SEND_DOCUMENT((short) 0x0006),
    SEND_URI((short) 0x0007),
    CANCEL_JOB((short) 0x0008),
    GET_JOB_ATTRIBUTES((short) 0x0009),
    GET_JOBS((short) 0x000A),
    GET_PRINTER_ATTRIBUTES((short) 0x000B);

    private final short code;

    IppOperation(final short code) {
        this.code = code;
    }

    public short getCode() {
        return code;
    }

    /**
     * Resolves an IppOperation from its binary representation.
     *
     * @param code the binary operation identifier.
     * @return the matched IppOperation or null if unrecognized.
     */
    public static IppOperation fromCode(final short code) {
        for (final IppOperation op : values()) {
            if (op.code == code) {
                return op;
            }
        }
        return null;
    }
}
