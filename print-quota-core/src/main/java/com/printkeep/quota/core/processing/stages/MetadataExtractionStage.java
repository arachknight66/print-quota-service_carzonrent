package com.printkeep.quota.core.processing.stages;

import com.printkeep.quota.core.processing.decision.IppDecision;
import com.printkeep.quota.core.processing.dto.PrintJobMetadata;
import com.printkeep.quota.core.processing.exception.PrintProcessingException;
import com.printkeep.quota.core.processing.extractor.IppMetadataExtractor;
import com.printkeep.quota.core.processing.pipeline.PipelineContext;
import com.printkeep.quota.core.processing.pipeline.PipelineStage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Pipeline stage responsible for extracting document metadata and calculating page costs.
 */
@Component
@Order(3)
public class MetadataExtractionStage implements PipelineStage {

    private final double duplexMultiplier;
    private final double colorMultiplier;

    public MetadataExtractionStage(
            @Value("${app.quota.multipliers.duplex:0.8}") final double duplexMultiplier,
            @Value("${app.quota.multipliers.color:2.0}") final double colorMultiplier) {
        this.duplexMultiplier = duplexMultiplier;
        this.colorMultiplier = colorMultiplier;
    }

    @Override
    public void process(final PipelineContext context) {
        try {
            final PrintJobMetadata metadata = IppMetadataExtractor.extract(
                    context.getIppPacket(),
                    context.getClientHostname(),
                    duplexMultiplier,
                    colorMultiplier
            );
            context.setMetadata(metadata);
        } catch (final PrintProcessingException e) {
            context.setDecision(IppDecision.REJECT_INVALID_REQUEST);
            context.setReason("Metadata extraction failed: " + e.getMessage());
            context.setException(e);
        } catch (final Exception e) {
            context.setDecision(IppDecision.SYSTEM_ERROR);
            context.setReason("Unexpected error during metadata extraction: " + e.getMessage());
            context.setException(e);
        }
    }
}
