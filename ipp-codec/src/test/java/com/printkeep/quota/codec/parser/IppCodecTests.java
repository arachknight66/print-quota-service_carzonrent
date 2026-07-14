package com.printkeep.quota.codec.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.printkeep.quota.codec.exception.IppParserException;
import com.printkeep.quota.codec.model.IppAttribute;
import com.printkeep.quota.codec.model.IppAttributeGroup;
import com.printkeep.quota.codec.model.IppPacket;
import com.printkeep.quota.codec.model.IppTag;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests verifying serialization and deserialization of IPP binary packets.
 */
class IppCodecTests {

    @Test
    void testBasicSerializationAndDeserialization() throws IOException {
        final IppAttribute attrVersion = new IppAttribute("attributes-charset", IppTag.CHARSET, List.of("utf-8"));
        final IppAttribute attrInteger = new IppAttribute("copies", IppTag.INTEGER, List.of(2));
        final IppAttribute attrBoolean = new IppAttribute("my-bool", IppTag.BOOLEAN, List.of(true));

        final IppAttributeGroup group = new IppAttributeGroup(
                IppTag.OPERATION_ATTRIBUTES,
                List.of(attrVersion, attrInteger, attrBoolean)
        );

        final byte[] dummyPayload = "DUMMY_PDF_STREAM".getBytes();
        final IppPacket originalPacket = new IppPacket(
                (byte) 2, (byte) 0, (short) 0x0002, 12345, List.of(group), dummyPayload
        );

        // Encode to byte array
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        IppEncoder.encode(originalPacket, out);
        final byte[] encodedBytes = out.toByteArray();

        // Decode back
        final ByteArrayInputStream in = new ByteArrayInputStream(encodedBytes);
        final IppPacket decodedPacket = IppParser.parse(in);

        // Verify matches
        assertThat(decodedPacket.majorVersion()).isEqualTo((byte) 2);
        assertThat(decodedPacket.minorVersion()).isEqualTo((byte) 0);
        assertThat(decodedPacket.operationOrStatus()).isEqualTo((short) 0x0002);
        assertThat(decodedPacket.transactionId()).isEqualTo(12345);
        assertThat(decodedPacket.payload()).isEqualTo(dummyPayload);

        final IppAttributeGroup decodedGroup = decodedPacket.getGroup(IppTag.OPERATION_ATTRIBUTES).orElseThrow();
        assertThat(decodedGroup.attributes()).hasSize(3);

        final IppAttribute decodedInteger = decodedGroup.getAttribute("copies").orElseThrow();
        assertThat(decodedInteger.tag()).isEqualTo(IppTag.INTEGER);
        assertThat(decodedInteger.getValue()).isEqualTo(2);

        final IppAttribute decodedBoolean = decodedGroup.getAttribute("my-bool").orElseThrow();
        assertThat(decodedBoolean.tag()).isEqualTo(IppTag.BOOLEAN);
        assertThat(decodedBoolean.getValue()).isEqualTo(true);

        final IppAttribute decodedCharset = decodedGroup.getAttribute("attributes-charset").orElseThrow();
        assertThat(decodedCharset.getValue()).isEqualTo("utf-8");
    }

    @Test
    void testMultiValuedAttributeCodec() throws IOException {
        final IppAttribute multiAttr = new IppAttribute(
                "requested-attributes", IppTag.KEYWORD, List.of("copies", "media", "sides")
        );
        final IppAttributeGroup group = new IppAttributeGroup(IppTag.OPERATION_ATTRIBUTES, List.of(multiAttr));
        final IppPacket originalPacket = new IppPacket(
                (byte) 2, (byte) 0, (short) 0x000A, 9876, List.of(group), new byte[0]
        );

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        IppEncoder.encode(originalPacket, out);

        final ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        final IppPacket decodedPacket = IppParser.parse(in);

        final IppAttribute decodedAttr = decodedPacket.getOperationAttribute("requested-attributes").orElseThrow();
        assertThat(decodedAttr.values()).containsExactly("copies", "media", "sides");
    }

    @Test
    void testInvalidPacketParserException() {
        final byte[] invalidBytes = new byte[]{1, 1, 0, 2, 0, 0, 0, 1, 0x0F}; // invalid group tag

        final ByteArrayInputStream in = new ByteArrayInputStream(invalidBytes);
        assertThatThrownBy(() -> IppParser.parse(in))
                .isInstanceOf(IppParserException.class);
    }
}
