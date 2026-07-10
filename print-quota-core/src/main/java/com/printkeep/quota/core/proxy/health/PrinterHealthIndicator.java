package com.printkeep.quota.core.proxy.health;

import com.printkeep.quota.core.proxy.routing.PrinterConfig;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * HealthIndicator checking connection statuses of all configured printers.
 */
@Component
public class PrinterHealthIndicator implements HealthIndicator {

    private final PrinterConfig config;
    private final HttpClient httpClient;

    public PrinterHealthIndicator(final PrinterConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
                .build();
    }

    @Override
    public Health health() {
        final Map<String, String> printers = config.getPrinters();
        if (printers.isEmpty()) {
            return Health.up().withDetail("message", "No printers configured for routing").build();
        }

        final Map<String, Object> details = new HashMap<>();
        boolean overallUp = true;

        for (final Map.Entry<String, String> entry : printers.entrySet()) {
            final String name = entry.getKey();
            final String uriStr = entry.getValue();
            final Map<String, Object> printerDetails = new HashMap<>();
            printerDetails.put("uri", uriStr);

            try {
                final HttpRequest pingRequest = HttpRequest.newBuilder()
                        .uri(URI.create(uriStr))
                        .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                        .timeout(Duration.ofMillis(1000))
                        .build();

                // OPTIONS request or HEAD check
                final HttpResponse<Void> response = httpClient.send(pingRequest, HttpResponse.BodyHandlers.discarding());
                final boolean online = response.statusCode() < 500;
                printerDetails.put("status", online ? "ONLINE" : "OFFLINE");
                printerDetails.put("httpStatusCode", response.statusCode());
                if (!online) {
                    overallUp = false;
                }
            } catch (final Exception e) {
                printerDetails.put("status", "UNREACHABLE");
                printerDetails.put("error", e.getMessage());
                overallUp = false;
            }
            details.put(name, printerDetails);
        }

        return overallUp 
                ? Health.up().withDetails(details).build() 
                : Health.down().withDetails(details).build();
    }
}
