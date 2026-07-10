package com.printkeep.quota.codec.util;

import com.printkeep.quota.codec.exception.IppParserException;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * High-performance binary parsing utilities handling Big-Endian byte order conversions.
 */
public final class BinaryUtils {

    private BinaryUtils() {
        // Prevent instantiation
    }

    /**
     * Reads a single byte from the stream.
     */
    public static byte readByte(final InputStream in) throws IOException {
        final int b = in.read();
        if (b == -1) {
            throw new EOFException("Unexpected end of stream while reading byte");
        }
        return (byte) b;
    }

    /**
     * Reads a 16-bit short value in Big-Endian order.
     */
    public static short readShort(final InputStream in) throws IOException {
        final int b1 = in.read();
        final int b2 = in.read();
        if ((b1 | b2) < 0) {
            throw new EOFException("Unexpected end of stream while reading short");
        }
        return (short) ((b1 << 8) | b2);
    }

    /**
     * Reads a 32-bit integer value in Big-Endian order.
     */
    public static int readInt(final InputStream in) throws IOException {
        final int b1 = in.read();
        final int b2 = in.read();
        final int b3 = in.read();
        final int b4 = in.read();
        if ((b1 | b2 | b3 | b4) < 0) {
            throw new EOFException("Unexpected end of stream while reading int");
        }
        return (b1 << 24) | (b2 << 16) | (b3 << 8) | b4;
    }

    /**
     * Reads a string prefixed by a 16-bit length.
     */
    public static String readString(final InputStream in) throws IOException {
        final short length = readShort(in);
        if (length < 0) {
            throw new IppParserException("Invalid negative string length: " + length);
        }
        if (length == 0) {
            return "";
        }
        final byte[] buffer = new byte[length];
        int read = 0;
        while (read < length) {
            final int count = in.read(buffer, read, length - read);
            if (count == -1) {
                throw new EOFException("Unexpected end of stream while reading string content");
            }
            read += count;
        }
        return new String(buffer, StandardCharsets.UTF_8);
    }

    /**
     * Writes a short value in Big-Endian order.
     */
    public static void writeShort(final OutputStream out, final short val) throws IOException {
        out.write((val >>> 8) & 0xFF);
        out.write(val & 0xFF);
    }

    /**
     * Writes an integer value in Big-Endian order.
     */
    public static void writeInt(final OutputStream out, final int val) throws IOException {
        out.write((val >>> 24) & 0xFF);
        out.write((val >>> 16) & 0xFF);
        out.write((val >>> 8) & 0xFF);
        out.write(val & 0xFF);
    }

    /**
     * Writes a string prefixed by its 16-bit length.
     */
    public static void writeString(final OutputStream out, final String str) throws IOException {
        final byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > Short.MAX_VALUE) {
            throw new IllegalArgumentException("String exceeds maximum length supported by IPP");
        }
        writeShort(out, (short) bytes.length);
        out.write(bytes);
    }
}
