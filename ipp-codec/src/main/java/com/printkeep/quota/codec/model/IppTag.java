package com.printkeep.quota.codec.model;

/**
 * Value and delimiter tags defined in the IPP protocol specifications (RFC 8010).
 */
public enum IppTag {
    // Delimiter tags
    OPERATION_ATTRIBUTES((byte) 0x01),
    JOB_ATTRIBUTES((byte) 0x02),
    END_OF_ATTRIBUTES((byte) 0x03),
    PRINTER_ATTRIBUTES((byte) 0x04),
    UNSUPPORTED_ATTRIBUTES((byte) 0x0F),

    // Out-of-band/value tags
    UNSUPPORTED_VALUE((byte) 0x10),
    DEFAULT_VALUE((byte) 0x11),
    UNKNOWN_VALUE((byte) 0x12),
    NO_VALUE((byte) 0x13),

    // Integer/Boolean/Enum tags
    INTEGER((byte) 0x21),
    BOOLEAN((byte) 0x22),
    ENUM((byte) 0x23),

    // String value tags
    OCTET_STRING((byte) 0x30),
    DATE_TIME((byte) 0x31),
    RESOLUTION((byte) 0x32),
    RANGE_OF_INTEGER((byte) 0x33),
    TEXT_WITH_LANGUAGE((byte) 0x35),
    NAME_WITH_LANGUAGE((byte) 0x36),
    TEXT_WITHOUT_LANGUAGE((byte) 0x41),
    NAME_WITHOUT_LANGUAGE((byte) 0x42),
    KEYWORD((byte) 0x44),
    URI((byte) 0x45),
    URI_SCHEME((byte) 0x46),
    CHARSET((byte) 0x47),
    NATURAL_LANGUAGE((byte) 0x48),
    MIME_MEDIA_TYPE((byte) 0x49);

    private final byte value;

    IppTag(final byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }

    /**
     * Resolves an IppTag from its binary representation.
     *
     * @param value the binary tag representation.
     * @return the matched IppTag or null if unrecognized.
     */
    public static IppTag fromValue(final byte value) {
        for (final IppTag tag : values()) {
            if (tag.value == value) {
                return tag;
            }
        }
        return null;
    }

    /**
     * Helper to verify if the tag represents a group delimiter.
     */
    public boolean isDelimiter() {
        return this.value >= 0x01 && this.value <= 0x0F;
    }
}
