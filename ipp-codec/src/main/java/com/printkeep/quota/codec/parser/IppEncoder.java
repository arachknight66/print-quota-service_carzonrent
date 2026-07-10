package com.printkeep.quota.codec.parser;

import com.printkeep.quota.codec.model.IppAttribute;
import com.printkeep.quota.codec.model.IppAttributeGroup;
import com.printkeep.quota.codec.model.IppPacket;
import com.printkeep.quota.codec.model.IppTag;
import com.printkeep.quota.codec.util.BinaryUtils;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Encodes structured {@link IppPacket} objects back into binary IPP format (RFC 8010).
 */
public final class IppEncoder {

    private IppEncoder() {
        // Prevent instantiation
    }

    /**
     * Encodes the IppPacket and writes the output bytes to the specified stream.
     *
     * @param packet the packet to encode.
     * @param out    the output stream to write bytes to.
     * @throws IOException if a write error occurs.
     */
    public static void encode(final IppPacket packet, final OutputStream out) throws IOException {
        out.write(packet.majorVersion());
        out.write(packet.minorVersion());
        BinaryUtils.writeShort(out, packet.operationOrStatus());
        BinaryUtils.writeInt(out, packet.transactionId());

        for (final IppAttributeGroup group : packet.attributeGroups()) {
            out.write(group.tag().getValue());
            for (final IppAttribute attr : group.attributes()) {
                boolean first = true;
                for (final Object val : attr.values()) {
                    // Write value tag
                    out.write(attr.tag().getValue());

                    // Write attribute name: name-length + name bytes
                    if (first) {
                        BinaryUtils.writeString(out, attr.name());
                        first = false;
                    } else {
                        // Subsequent values in 1-to-many list have name length of 0
                        BinaryUtils.writeShort(out, (short) 0);
                    }

                    // Write value bytes
                    final byte[] valBytes = encodeValue(attr.tag(), val);
                    BinaryUtils.writeShort(out, (short) valBytes.length);
                    out.write(valBytes);
                }
            }
        }

        // Write end of attributes delimiter tag
        out.write(IppTag.END_OF_ATTRIBUTES.getValue());

        // Write payload if present
        if (packet.payload() != null && packet.payload().length > 0) {
            out.write(packet.payload());
        }
    }

    private static byte[] encodeValue(final IppTag tag, final Object val) {
        if (val == null) {
            return new byte[0];
        }
        if (tag == IppTag.INTEGER || tag == IppTag.ENUM) {
            final int intVal = (val instanceof Number) ? ((Number) val).intValue() : Integer.parseInt(val.toString());
            return ByteBuffer.allocate(4).putInt(intVal).array();
        }
        if (tag == IppTag.BOOLEAN) {
            final boolean boolVal = (val instanceof Boolean) ? (Boolean) val : Boolean.parseBoolean(val.toString());
            return new byte[]{(byte) (boolVal ? 0x01 : 0x00)};
        }
        // Default: write as UTF-8 string bytes
        return val.toString().getBytes(StandardCharsets.UTF_8);
    }
}
