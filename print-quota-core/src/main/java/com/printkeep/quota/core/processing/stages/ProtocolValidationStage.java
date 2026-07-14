package com.printkeep.quota.core.processing.stages;

import com.printkeep.quota.codec.model.IppAttribute;
import com.printkeep.quota.codec.model.IppAttributeGroup;
import com.printkeep.quota.codec.model.IppPacket;
import com.printkeep.quota.codec.model.IppTag;
import com.printkeep.quota.core.processing.decision.IppDecision;
import com.printkeep.quota.core.processing.pipeline.PipelineContext;
import com.printkeep.quota.core.processing.pipeline.PipelineStage;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Validation stage checking basic IPP packet structure and mandatory header fields.
 */
@Component
@Order(1)
public class ProtocolValidationStage implements PipelineStage {

    @Override
    public void process(final PipelineContext context) {
        final IppPacket packet = context.getIppPacket();

        if (packet == null) {
            context.setDecision(IppDecision.REJECT_INVALID_REQUEST);
            context.setReason("Missing IPP packet context");
            return;
        }

        // Validate basic IPP protocol version bounds (1.x or 2.x)
        if (packet.majorVersion() < 1 || packet.majorVersion() > 2) {
            context.setDecision(IppDecision.REJECT_INVALID_REQUEST);
            context.setReason("Unsupported IPP version: " + packet.majorVersion() + "." + packet.minorVersion());
            return;
        }

        // Validate operation is either Print-Job (0x0002) or Validate-Job (0x0004)
        final short op = packet.operationOrStatus();
        if (op != 0x0002 && op != 0x0004) {
            context.setDecision(IppDecision.REJECT_INVALID_REQUEST);
            context.setReason("Unsupported IPP operation code: 0x" + Integer.toHexString(op));
            return;
        }

        final Optional<IppAttributeGroup> opGroupOpt = packet.getGroup(IppTag.OPERATION_ATTRIBUTES);
        if (opGroupOpt.isEmpty()) {
            context.setDecision(IppDecision.REJECT_INVALID_REQUEST);
            context.setReason("Missing Operation Attributes group");
            return;
        }

        final IppAttributeGroup opGroup = opGroupOpt.get();

        // Ensure requesting-user-name is present
        final Optional<IppAttribute> userAttr = opGroup.getAttribute("requesting-user-name");
        if (userAttr.isEmpty() || userAttr.get().getValue() == null || userAttr.get().getValue().toString().isBlank()) {
            context.setDecision(IppDecision.REJECT_INVALID_REQUEST);
            context.setReason("Missing mandatory attribute: requesting-user-name");
            return;
        }

        // Ensure printer-uri is present
        final Optional<IppAttribute> printerAttr = opGroup.getAttribute("printer-uri");
        if (printerAttr.isEmpty() || printerAttr.get().getValue() == null || printerAttr.get().getValue().toString().isBlank()) {
            context.setDecision(IppDecision.REJECT_INVALID_REQUEST);
            context.setReason("Missing mandatory attribute: printer-uri");
            return;
        }
    }
}
