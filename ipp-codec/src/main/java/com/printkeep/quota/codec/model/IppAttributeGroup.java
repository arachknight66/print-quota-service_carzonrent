package com.printkeep.quota.codec.model;

import java.util.List;
import java.util.Optional;

/**
 * Represents a group of IPP attributes demarcated by a delimiter tag.
 */
public record IppAttributeGroup(IppTag tag, List<IppAttribute> attributes) {

    public IppAttributeGroup {
        if (tag == null || !tag.isDelimiter()) {
            throw new IllegalArgumentException("Tag must be a valid delimiter tag");
        }
        if (attributes == null) {
            throw new IllegalArgumentException("Attributes list must not be null");
        }
        attributes = List.copyOf(attributes);
    }

    /**
     * Looks up an attribute in the group by name.
     *
     * @param name the attribute name to find.
     * @return an Optional containing the matching attribute if found.
     */
    public Optional<IppAttribute> getAttribute(final String name) {
        return attributes.stream()
                .filter(attr -> attr.name().equals(name))
                .findFirst();
    }
}
