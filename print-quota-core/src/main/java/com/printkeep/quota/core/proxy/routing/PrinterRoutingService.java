package com.printkeep.quota.core.proxy.routing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service responsible for resolving logical printer names to physical destination URIs.
 */
@Service
public class PrinterRoutingService {

    private static final Logger log = LoggerFactory.getLogger(PrinterRoutingService.class);
    private final PrinterConfig config;

    public PrinterRoutingService(final PrinterConfig config) {
        this.config = config;
    }

    /**
     * Resolves the target destination URI for the logical printer.
     *
     * @param printerName the logical name of the printer.
     * @return an Optional containing the printer destination URI if resolved.
     */
    public Optional<String> resolvePrinterUri(final String printerName) {
        if (printerName == null || printerName.isBlank()) {
            return Optional.empty();
        }

        final String uri = config.getPrinters().get(printerName);
        if (uri == null) {
            log.warn("Target printer logical name could not be resolved: {}", printerName);
            return Optional.empty();
        }

        log.debug("Resolved logical printer '{}' to URI '{}'", printerName, uri);
        return Optional.of(uri);
    }
}
