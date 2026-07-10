package com.printkeep.quota.core.processing.extractor;

import com.printkeep.quota.codec.model.IppAttribute;
import com.printkeep.quota.codec.model.IppAttributeGroup;
import com.printkeep.quota.codec.model.IppPacket;
import com.printkeep.quota.codec.model.IppTag;
import com.printkeep.quota.core.processing.dto.PrintJobMetadata;
import com.printkeep.quota.core.processing.exception.PrintProcessingException;
import java.time.Instant;
import java.util.Optional;

/**
 * Utility extractor to resolve metadata and calculate page estimations from parsed IPP packets.
 */
public final class IppMetadataExtractor {

    private IppMetadataExtractor() {
        // Prevent instantiation
    }

    /**
     * Extracts print job metadata from an IPP packet.
     *
     * @param packet           the decoded IPP packet.
     * @param clientHostname   sender host.
     * @param duplexMultiplier reduction cost multiplier for two-sided printing.
     * @param colorMultiplier  cost multiplier for color printing.
     * @return populated PrintJobMetadata.
     */
    public static PrintJobMetadata extract(
            final IppPacket packet,
            final String clientHostname,
            final double duplexMultiplier,
            final double colorMultiplier) {

        final Optional<IppAttributeGroup> opGroupOpt = packet.getGroup(IppTag.OPERATION_ATTRIBUTES);
        final IppAttributeGroup opGroup = opGroupOpt.orElseThrow(() ->
                new PrintProcessingException("Missing Operation Attributes group"));

        // Resolve Username
        final String username = opGroup.getAttribute("requesting-user-name")
                .map(attr -> attr.getValue().toString())
                .orElseThrow(() -> new PrintProcessingException("Missing requesting-user-name"));

        // Resolve Printer name from URI
        final String printerUri = opGroup.getAttribute("printer-uri")
                .map(attr -> attr.getValue().toString())
                .orElseThrow(() -> new PrintProcessingException("Missing printer-uri"));
        
        final String printerName = resolvePrinterName(printerUri);

        // Job attributes resolution
        final Optional<IppAttributeGroup> jobGroupOpt = packet.getGroup(IppTag.JOB_ATTRIBUTES);
        final IppAttributeGroup jobGroup = jobGroupOpt.orElse(null);

        final String jobName = getAttribute(opGroup, jobGroup, "job-name")
                .map(attr -> attr.getValue().toString())
                .orElse("Untitled Job");

        final String documentName = getAttribute(opGroup, jobGroup, "document-name")
                .map(attr -> attr.getValue().toString())
                .orElse(jobName);

        // Resolve Copies
        int copies = 1;
        final Optional<IppAttribute> copiesOpt = getAttribute(opGroup, jobGroup, "copies");
        if (copiesOpt.isPresent() && copiesOpt.get().getValue() != null) {
            try {
                copies = Integer.parseInt(copiesOpt.get().getValue().toString());
            } catch (final NumberFormatException e) {
                throw new PrintProcessingException("Invalid integer format for copies");
            }
        }

        // Resolve Duplex / Sides
        boolean duplex = false;
        final Optional<IppAttribute> sidesOpt = getAttribute(opGroup, jobGroup, "sides");
        if (sidesOpt.isPresent() && sidesOpt.get().getValue() != null) {
            final String sides = sidesOpt.get().getValue().toString();
            if (sides.contains("two-sided")) {
                duplex = true;
            }
        }

        // Resolve Color mode
        boolean color = false;
        final Optional<IppAttribute> colorOpt = getAttribute(opGroup, jobGroup, "print-color-mode");
        if (colorOpt.isPresent() && colorOpt.get().getValue() != null) {
            final String colorMode = colorOpt.get().getValue().toString();
            if (colorMode.contains("color")) {
                color = true;
            }
        }

        // Resolve Base Page Count
        int basePages = 1;
        final Optional<IppAttribute> pagesOpt = getAttribute(opGroup, jobGroup, "job-pages");
        if (pagesOpt.isPresent() && pagesOpt.get().getValue() != null) {
            try {
                basePages = Integer.parseInt(pagesOpt.get().getValue().toString());
            } catch (final NumberFormatException e) {
                throw new PrintProcessingException("Invalid integer format for job-pages");
            }
        }

        if (basePages <= 0) {
            throw new PrintProcessingException("Invalid page count: " + basePages);
        }

        // Run Page Estimation Calculation
        double calculated = basePages * copies;
        if (duplex) {
            calculated *= duplexMultiplier;
        }
        if (color) {
            calculated *= colorMultiplier;
        }

        // Round up to full pages
        final int estimatedPages = (int) Math.ceil(calculated);

        return new PrintJobMetadata(
                username,
                documentName,
                printerName,
                copies,
                duplex,
                color,
                estimatedPages,
                jobName,
                clientHostname,
                Instant.now()
        );
    }

    private static String resolvePrinterName(final String uri) {
        if (uri == null || uri.isBlank()) {
            return "unknown";
        }
        final int idx = uri.lastIndexOf('/');
        if (idx != -1 && idx < uri.length() - 1) {
            return uri.substring(idx + 1);
        }
        return uri;
    }

    private static Optional<IppAttribute> getAttribute(
            final IppAttributeGroup op,
            final IppAttributeGroup job,
            final String name) {
        if (op != null) {
            final Optional<IppAttribute> attr = op.getAttribute(name);
            if (attr.isPresent()) {
                return attr;
            }
        }
        if (job != null) {
            return job.getAttribute(name);
        }
        return Optional.empty();
    }
}
