package com.printkeep.quota.core.processing.extractor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.printkeep.quota.codec.model.IppAttribute;
import com.printkeep.quota.codec.model.IppAttributeGroup;
import com.printkeep.quota.codec.model.IppPacket;
import com.printkeep.quota.codec.model.IppTag;
import com.printkeep.quota.core.processing.dto.PrintJobMetadata;
import com.printkeep.quota.core.processing.exception.PrintProcessingException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests verifying extraction of attributes and calculation of pages from IPP packets.
 */
class IppMetadataExtractorTests {

    @Test
    void testSuccessfulExtractionAndCalculations() {
        // Build Operation attributes
        final IppAttribute reqUser = new IppAttribute("requesting-user-name", IppTag.NAME_WITHOUT_LANGUAGE, List.of("jdoe"));
        final IppAttribute printerUri = new IppAttribute("printer-uri", IppTag.URI, List.of("ipp://localhost/printers/LaserJet_5"));
        final IppAttributeGroup opGroup = new IppAttributeGroup(IppTag.OPERATION_ATTRIBUTES, List.of(reqUser, printerUri));

        // Build Job attributes
        final IppAttribute jobName = new IppAttribute("job-name", IppTag.NAME_WITHOUT_LANGUAGE, List.of("Financial Report.pdf"));
        final IppAttribute copies = new IppAttribute("copies", IppTag.INTEGER, List.of(3));
        final IppAttribute pages = new IppAttribute("job-pages", IppTag.INTEGER, List.of(5));
        final IppAttribute sides = new IppAttribute("sides", IppTag.KEYWORD, List.of("two-sided-long-edge"));
        final IppAttribute color = new IppAttribute("print-color-mode", IppTag.KEYWORD, List.of("color"));
        final IppAttributeGroup jobGroup = new IppAttributeGroup(IppTag.JOB_ATTRIBUTES, List.of(jobName, copies, pages, sides, color));

        final IppPacket packet = new IppPacket((byte) 2, (byte) 0, (short) 0x0002, 1, List.of(opGroup, jobGroup), new byte[0]);

        // Estimated page calculation: 5 pages * 3 copies = 15 pages base.
        // Duplex: 15 * 0.8 = 12.0
        // Color: 12.0 * 2.0 = 24.0
        final PrintJobMetadata metadata = IppMetadataExtractor.extract(packet, "local-client", 0.8, 2.0);

        assertThat(metadata.username()).isEqualTo("jdoe");
        assertThat(metadata.printerName()).isEqualTo("LaserJet_5");
        assertThat(metadata.documentName()).isEqualTo("Financial Report.pdf");
        assertThat(metadata.requestedCopies()).isEqualTo(3);
        assertThat(metadata.duplex()).isTrue();
        assertThat(metadata.color()).isTrue();
        assertThat(metadata.estimatedPages()).isEqualTo(24);
    }

    @Test
    void testExtractionRejectsInvalidPageCount() {
        final IppAttribute reqUser = new IppAttribute("requesting-user-name", IppTag.NAME_WITHOUT_LANGUAGE, List.of("jdoe"));
        final IppAttribute printerUri = new IppAttribute("printer-uri", IppTag.URI, List.of("ipp://localhost/printers/LaserJet"));
        final IppAttributeGroup opGroup = new IppAttributeGroup(IppTag.OPERATION_ATTRIBUTES, List.of(reqUser, printerUri));

        // Invalid job-pages = 0
        final IppAttribute pages = new IppAttribute("job-pages", IppTag.INTEGER, List.of(0));
        final IppAttributeGroup jobGroup = new IppAttributeGroup(IppTag.JOB_ATTRIBUTES, List.of(pages));

        final IppPacket packet = new IppPacket((byte) 2, (byte) 0, (short) 0x0002, 1, List.of(opGroup, jobGroup), new byte[0]);

        assertThatThrownBy(() -> IppMetadataExtractor.extract(packet, "localhost", 0.8, 2.0))
                .isInstanceOf(PrintProcessingException.class)
                .hasMessageContaining("Invalid page count");
    }
}
