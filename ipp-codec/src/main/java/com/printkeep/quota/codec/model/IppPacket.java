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
    public IppPacket(
            final byte majorVersion,
            final byte minorVersion,
            final short operationOrStatus,
            final int transactionId,
            final List<IppAttributeGroup> attributeGroups,
            final byte[] payload) {
        if (attributeGroups == null) {
            throw new IllegalArgumentException("Attribute groups must not be null");
        }
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
        this.operationOrStatus = operationOrStatus;
        this.transactionId = transactionId;
        this.attributeGroups = List.copyOf(attributeGroups);
        this.payload = payload == null ? null : payload.clone();
    }

    @Override
    public byte[] payload() {
        return payload == null ? null : payload.clone();
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
