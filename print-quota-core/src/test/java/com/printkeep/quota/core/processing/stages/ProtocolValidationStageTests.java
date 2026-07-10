package com.printkeep.quota.core.processing.stages;

import static org.assertj.core.api.Assertions.assertThat;

import com.printkeep.quota.codec.model.IppAttribute;
import com.printkeep.quota.codec.model.IppAttributeGroup;
import com.printkeep.quota.codec.model.IppPacket;
import com.printkeep.quota.codec.model.IppTag;
import com.printkeep.quota.core.processing.decision.IppDecision;
import com.printkeep.quota.core.processing.pipeline.PipelineContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests verifying ProtocolValidationStage.
 */
class ProtocolValidationStageTests {

    private ProtocolValidationStage stage;

    @BeforeEach
    void setUp() {
        stage = new ProtocolValidationStage();
    }

    @Test
    void testValidPacketPassesValidation() {
        final IppAttribute reqUser = new IppAttribute("requesting-user-name", IppTag.NAME_WITHOUT_LANGUAGE, List.of("jdoe"));
        final IppAttribute printerUri = new IppAttribute("printer-uri", IppTag.URI, List.of("ipp://localhost/printers/Laser"));
        final IppAttributeGroup opGroup = new IppAttributeGroup(IppTag.OPERATION_ATTRIBUTES, List.of(reqUser, printerUri));
        final IppPacket packet = new IppPacket((byte) 2, (byte) 0, (short) 0x0002, 1, List.of(opGroup), new byte[0]);

        final PipelineContext context = new PipelineContext(packet, "corr-123", "localhost");
        stage.process(context);

        assertThat(context.getDecision()).isNull(); // No decision means it passed successfully
    }

    @Test
    void testMissingUserRejects() {
        final IppAttribute printerUri = new IppAttribute("printer-uri", IppTag.URI, List.of("ipp://localhost/printers/Laser"));
        final IppAttributeGroup opGroup = new IppAttributeGroup(IppTag.OPERATION_ATTRIBUTES, List.of(printerUri));
        final IppPacket packet = new IppPacket((byte) 2, (byte) 0, (short) 0x0002, 1, List.of(opGroup), new byte[0]);

        final PipelineContext context = new PipelineContext(packet, "corr-123", "localhost");
        stage.process(context);

        assertThat(context.getDecision()).isEqualTo(IppDecision.REJECT_INVALID_REQUEST);
        assertThat(context.getReason()).contains("requesting-user-name");
    }
}
