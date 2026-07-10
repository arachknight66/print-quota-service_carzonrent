package com.printkeep.quota.core.proxy.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests verifying PrinterRoutingService logic.
 */
class PrinterRoutingServiceTests {

    private PrinterRoutingService routingService;
    private PrinterConfig config;

    @BeforeEach
    void setUp() {
        config = new PrinterConfig();
        config.setPrinters(Map.of(
                "LaserJet_5", "http://printer1.company.local:631/ipp/print",
                "OfficeJet_3", "https://printer2.company.local:631/ipp/print"
        ));
        routingService = new PrinterRoutingService(config);
    }

    @Test
    void testResolveLogicalPrinterUri() {
        final Optional<String> uri = routingService.resolvePrinterUri("LaserJet_5");
        assertThat(uri).isPresent().contains("http://printer1.company.local:631/ipp/print");
    }

    @Test
    void testResolveNonExistentPrinterReturnsEmpty() {
        final Optional<String> uri = routingService.resolvePrinterUri("Unknown_Printer");
        assertThat(uri).isEmpty();
    }
}
