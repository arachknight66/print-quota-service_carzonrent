package com.printkeep.quota.codec.model;

import java.util.List;

/**
 * Represents an IPP attribute containing a name, a type tag, and one or more values.
 */
public record IppAttribute(String name, IppTag tag, List<Object> values) {

    public IppAttribute {
        if (name == null) {
            throw new IllegalArgumentException("Attribute name must not be null");
        }
        if (tag == null) {
            throw new IllegalArgumentException("Attribute tag must not be null");
        }
        if (values == null) {
            throw new IllegalArgumentException("Attribute values must not be null");
        }
        values = List.copyOf(values);
    }

    /**
     * Helper to retrieve the primary value (the first one) if present.
     *
     * @return the first value or null if empty.
     */
    public Object getValue() {
        return values.isEmpty() ? null : values.get(0);
    }
}
