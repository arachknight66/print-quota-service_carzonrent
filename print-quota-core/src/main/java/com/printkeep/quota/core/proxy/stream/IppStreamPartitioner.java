package com.printkeep.quota.core.proxy.stream;

import com.printkeep.quota.codec.exception.IppParserException;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Utility stream partitioner that reads the binary IPP attributes header block
 * from an incoming print job stream without buffering the document payload.
 */
public final class IppStreamPartitioner {

    private IppStreamPartitioner() {
        // Prevent instantiation
    }

    /**
     * Captures the raw IPP attributes block from the input stream.
     *
     * @param in the client input stream.
     * @return a byte array containing the raw attributes block (up to and including the 0x03 tag).
     * @throws IOException if read or parsing failures occur.
     */
    public static byte[] captureAttributesBlock(final InputStream in) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final DataInputStream din = new DataInputStream(in);

        // Read and capture header: Version (2 bytes), Operation (2 bytes), Transaction ID (4 bytes)
        final byte[] header = new byte[8];
        din.readFully(header);
        out.write(header);

        while (true) {
            final int tag = din.read();
            if (tag == -1) {
                throw new IppParserException("Unexpected EOF while reading IPP tags");
            }
            out.write(tag);

            if (tag >= 0x01 && tag <= 0x0F) {
                // Delimiter tag
                if (tag == 0x03) {
                    // END_OF_ATTRIBUTES
                    break;
                }
            } else {
                // Value tag: read name-length, name, value-length, value
                final int nameLen = din.readUnsignedShort();
                out.write((nameLen >>> 8) & 0xFF);
                out.write(nameLen & 0xFF);
                if (nameLen > 0) {
                    final byte[] nameBytes = new byte[nameLen];
                    din.readFully(nameBytes);
                    out.write(nameBytes);
                }

                final int valLen = din.readUnsignedShort();
                out.write((valLen >>> 8) & 0xFF);
                out.write(valLen & 0xFF);
                if (valLen > 0) {
                    final byte[] valBytes = new byte[valLen];
                    din.readFully(valBytes);
                    out.write(valBytes);
                }
            }
        }

        return out.toByteArray();
    }
}
