package com.printkeep.quota.codec.parser;

import com.printkeep.quota.codec.exception.IppParserException;
import com.printkeep.quota.codec.model.IppAttribute;
import com.printkeep.quota.codec.model.IppAttributeGroup;
import com.printkeep.quota.codec.model.IppPacket;
import com.printkeep.quota.codec.model.IppTag;
import com.printkeep.quota.codec.util.BinaryUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * High-performance parser to decode binary IPP streams into structured {@link IppPacket} models.
 */
public final class IppParser {

    private static final int BUFFER_SIZE = 4096;
    private static final int INTEGER_VALUE_LENGTH = 4;
    private static final int BOOLEAN_VALUE_LENGTH = 1;

    private IppParser() {
        // Prevent instantiation
    }

    /**
     * Parses an IPP packet from an input stream.
     *
     * @param in the input stream containing the raw IPP packet.
     * @return the parsed IppPacket.
     * @throws IOException if a read error occurs.
     */
    public static IppPacket parse(final InputStream in) throws IOException {
        try {
            final byte majorVersion = BinaryUtils.readByte(in);
            final byte minorVersion = BinaryUtils.readByte(in);
            final short operationOrStatus = BinaryUtils.readShort(in);
            final int transactionId = BinaryUtils.readInt(in);

            final List<IppAttributeGroup> attributeGroups = new ArrayList<>();
            List<IppAttribute> currentAttributes = new ArrayList<>();
            IppTag currentGroupTag = null;
            IppAttribute lastAttribute = null;

            while (true) {
                final byte tagByte = BinaryUtils.readByte(in);
                final IppTag tag = IppTag.fromValue(tagByte);

                if (tag == null) {
                    throw new IppParserException(String.format("Unknown tag byte encountered: 0x%02X", tagByte));
                }

                if (tag.isDelimiter()) {
                    // If there's an existing group being parsed, close it and add to the list
                    if (currentGroupTag != null) {
                        attributeGroups.add(new IppAttributeGroup(currentGroupTag, currentAttributes));
                        currentAttributes = new ArrayList<>();
                    }

                    if (tag == IppTag.END_OF_ATTRIBUTES) {
                        break;
                    }
                    currentGroupTag = tag;
                    lastAttribute = null;
                } else {
                    // Parse attribute name
                    final String name = BinaryUtils.readString(in);

                    // Parse value length and content
                    final short valLen = BinaryUtils.readShort(in);
                    if (valLen < 0) {
                        throw new IppParserException("Invalid negative attribute value length: " + valLen);
                    }
                    final byte[] valBytes = new byte[valLen];
                    int read = 0;
                    while (read < valLen) {
                        final int count = in.read(valBytes, read, valLen - read);
                        if (count == -1) {
                            throw new IppParserException("Unexpected EOF while reading attribute value");
                        }
                        read += count;
                    }

                    final Object parsedValue = parseValue(tag, valBytes);

                    if (name.isEmpty() && lastAttribute != null) {
                        // This is an additional value for the last attribute (multi-value attribute)
                        final List<Object> values = new ArrayList<>(lastAttribute.values());
                        values.add(parsedValue);
                        final IppAttribute updatedAttribute = new IppAttribute(lastAttribute.name(), lastAttribute.tag(), values);
                        currentAttributes.set(currentAttributes.size() - 1, updatedAttribute);
                        lastAttribute = updatedAttribute;
                    } else if (!name.isEmpty()) {
                        // New attribute
                        final List<Object> values = new ArrayList<>();
                        values.add(parsedValue);
                        final IppAttribute newAttr = new IppAttribute(name, tag, values);
                        currentAttributes.add(newAttr);
                        lastAttribute = newAttr;
                    } else {
                        throw new IppParserException("Encountered value without attribute name and no preceding attribute");
                    }
                }
            }

            // Read remaining stream as raw print payload (document data)
            final ByteArrayOutputStream payloadStream = new ByteArrayOutputStream();
            final byte[] buffer = new byte[BUFFER_SIZE];
            int count = in.read(buffer);
            while (count != -1) {
                payloadStream.write(buffer, 0, count);
                count = in.read(buffer);
            }
            final byte[] payload = payloadStream.toByteArray();

            return new IppPacket(majorVersion, minorVersion, operationOrStatus, transactionId, attributeGroups, payload);
        } catch (final java.io.EOFException e) {
            throw new IppParserException("Unexpected end of stream while parsing IPP packet", e);
        }
    }

    private static Object parseValue(final IppTag tag, final byte[] bytes) {
        if (bytes.length == 0) {
            return null;
        }
        if (tag == IppTag.INTEGER || tag == IppTag.ENUM) {
            if (bytes.length != INTEGER_VALUE_LENGTH) {
                throw new IppParserException(
                        "Integer/Enum tag must have " + INTEGER_VALUE_LENGTH + " bytes value. Found: " + bytes.length);
            }
            return ByteBuffer.wrap(bytes).getInt();
        }
        if (tag == IppTag.BOOLEAN) {
            if (bytes.length != BOOLEAN_VALUE_LENGTH) {
                throw new IppParserException(
                        "Boolean tag must have " + BOOLEAN_VALUE_LENGTH + " byte value. Found: " + bytes.length);
            }
            return bytes[0] != 0x00;
        }
        // Fallback: UTF-8 String representation
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
