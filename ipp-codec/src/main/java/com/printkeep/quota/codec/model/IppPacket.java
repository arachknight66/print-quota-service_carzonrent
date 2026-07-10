package com.printkeep.quota.codec.model;

import java.util.List;
import java.util.Optional;

/**
 * Encapsulates a complete Internet Printing Protocol (IPP) message packet (RFC 8010).
 */
public record IppPacket(
        byte majorVersion,
        byte minorVersion,
        short operationOrStatus,
        int transactionId,
        List<IppAttributeGroup> attributeGroups,
        byte[] payload
) {
    public IppPacket {
        if (attributeGroups == null) {
            throw new IllegalArgumentException("Attribute groups must not be null");
        }
    }

    /**
     * Helper to retrieve a specific attribute group by its delimiter tag.
     *
     * @param groupTag the group delimiter tag.
     * @return an Optional containing the matched group.
     */
    public Optional<IppAttributeGroup> getGroup(final IppTag groupTag) {
        return attributeGroups.stream()
                .filter(group -> group.tag() == groupTag)
                .findFirst();
    }

    /**
     * Helper to retrieve an attribute directly from the Operation Attributes group.
     *
     * @param name the name of the attribute.
     * @return an Optional containing the matched attribute.
     */
    public Optional<IppAttribute> getOperationAttribute(final String name) {
        return getGroup(IppTag.OPERATION_ATTRIBUTES)
                .flatMap(group -> group.getAttribute(name));
    }
}
