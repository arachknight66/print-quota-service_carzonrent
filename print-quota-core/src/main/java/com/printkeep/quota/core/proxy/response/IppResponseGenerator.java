package com.printkeep.quota.core.proxy.response;

import com.printkeep.quota.codec.model.IppAttribute;
import com.printkeep.quota.codec.model.IppAttributeGroup;
import com.printkeep.quota.codec.model.IppPacket;
import com.printkeep.quota.codec.model.IppTag;
import com.printkeep.quota.codec.parser.IppEncoder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Utility helper generating RFC 8011 compliant binary IPP responses for error/rejection scenarios.
 */
@Component
public class IppResponseGenerator {

    /**
     * Builds and serializes an IPP status error response packet.
     *
     * @param major         major version byte.
     * @param minor         minor version byte.
     * @param statusCode    short code representing the IPP status.
     * @param transactionId transaction identifier matching the request.
     * @param statusMessage description of the exception or rejection reason.
     * @return the serialized IPP packet bytes.
     */
    public byte[] generateErrorResponse(
            final byte major,
            final byte minor,
            final short statusCode,
            final int transactionId,
            final String statusMessage) {

        final List<IppAttribute> attributes = new ArrayList<>();
        attributes.add(new IppAttribute("attributes-charset", IppTag.CHARSET, List.of("utf-8")));
        attributes.add(new IppAttribute("attributes-natural-language", IppTag.NATURAL_LANGUAGE, List.of("en")));

        if (statusMessage != null && !statusMessage.isBlank()) {
            attributes.add(new IppAttribute("status-message", IppTag.TEXT_WITHOUT_LANGUAGE, List.of(statusMessage)));
        }

        final IppAttributeGroup opGroup = new IppAttributeGroup(IppTag.OPERATION_ATTRIBUTES, attributes);
        final IppPacket packet = new IppPacket(major, minor, statusCode, transactionId, List.of(opGroup), new byte[0]);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            IppEncoder.encode(packet, out);
        } catch (final IOException e) {
            // Should not happen for ByteArrayOutputStream
            throw new IllegalStateException("Failed to encode error response IPP packet", e);
        }

        return out.toByteArray();
    }
}
