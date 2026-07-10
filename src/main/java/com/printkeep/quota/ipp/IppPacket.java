package com.printkeep.quota.ipp;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Getter
@AllArgsConstructor
public class IppPacket {

    private final short version;
    private final short operationOrStatus;
    private final int requestId;
    private final Map<String, List<IppAttribute>> attributes;

    public static IppPacket parse(InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(in);
        byte major = dis.readByte();
        byte minor = dis.readByte();
        short version = (short) ((major << 8) | (minor & 0xFF));
        short operationOrStatus = dis.readShort();
        int requestId = dis.readInt();

        Map<String, List<IppAttribute>> attributes = new LinkedHashMap<>();
        String currentAttributeName = null;

        while (true) {
            int tagByte = dis.read();
            if (tagByte == -1) {
                throw new IOException("Unexpected end of stream while parsing IPP packet");
            }
            byte tag = (byte) tagByte;
            if (tag == 0x03) { // end-of-attributes-tag
                break;
            }

            // Delimiter tags are from 0x00 to 0x0F
            if (tag >= 0x00 && tag <= 0x0F) {
                continue;
            }

            // Read Name Length
            int nameLen = dis.readUnsignedShort();
            String name = "";
            if (nameLen > 0) {
                byte[] nameBytes = new byte[nameLen];
                dis.readFully(nameBytes);
                name = new String(nameBytes, StandardCharsets.UTF_8);
                currentAttributeName = name;
            } else if (currentAttributeName != null) {
                // Multi-value attribute
                name = currentAttributeName;
            }

            // Read Value Length
            int valueLen = dis.readUnsignedShort();
            byte[] valueBytes = new byte[valueLen];
            dis.readFully(valueBytes);

            IppAttribute attr = new IppAttribute(tag, name, valueBytes);
            if (!name.isEmpty()) {
                attributes.computeIfAbsent(name, k -> new ArrayList<>()).add(attr);
            }
        }

        return new IppPacket(version, operationOrStatus, requestId, attributes);
    }

    public static byte[] createErrorResponse(short version, int requestId, String errorMessage) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        try {
            // Version
            dos.writeShort(version);
            // Status code: client-error-not-possible (0x040B)
            dos.writeShort(0x040B);
            // Request ID
            dos.writeInt(requestId);

            // Operation attributes group tag
            dos.writeByte(0x01);

            // attributes-charset (charset value tag: 0x47)
            dos.writeByte(0x47);
            writeAttributeHeader(dos, "attributes-charset");
            writeAttributeValue(dos, "utf-8");

            // attributes-natural-language (naturalLanguage value tag: 0x48)
            dos.writeByte(0x48);
            writeAttributeHeader(dos, "attributes-natural-language");
            writeAttributeValue(dos, "en-us");

            // status-message (textWithoutLanguage value tag: 0x41)
            if (errorMessage != null && !errorMessage.isEmpty()) {
                dos.writeByte(0x41);
                writeAttributeHeader(dos, "status-message");
                writeAttributeValue(dos, errorMessage);
            }

            // End-of-attributes-tag (0x03)
            dos.writeByte(0x03);

        } catch (IOException e) {
            // Should not happen with ByteArrayOutputStream
        }
        return baos.toByteArray();
    }

    private static void writeAttributeHeader(DataOutputStream dos, String name) throws IOException {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        dos.writeShort(nameBytes.length);
        dos.write(nameBytes);
    }

    private static void writeAttributeValue(DataOutputStream dos, String value) throws IOException {
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
        dos.writeShort(valueBytes.length);
        dos.write(valueBytes);
    }

    public String getSingleStringAttribute(String name) {
        List<IppAttribute> list = attributes.get(name);
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(0).getValueAsString();
    }

    public Integer getSingleIntegerAttribute(String name) {
        List<IppAttribute> list = attributes.get(name);
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(0).getValueAsInteger();
    }

    // Helper classes to wrap stream and record bytes read
    public static class RecordingInputStream extends InputStream {
        private final InputStream in;
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();

        public RecordingInputStream(InputStream in) {
            this.in = in;
        }

        @Override
        public int read() throws IOException {
            int b = in.read();
            if (b != -1) {
                buf.write(b);
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int numRead = in.read(b, off, len);
            if (numRead > 0) {
                buf.write(b, off, numRead);
            }
            return numRead;
        }

        public byte[] getRecordedBytes() {
            return buf.toByteArray();
        }
    }
}
