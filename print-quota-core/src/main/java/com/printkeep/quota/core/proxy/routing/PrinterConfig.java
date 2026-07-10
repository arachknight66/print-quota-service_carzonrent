package com.printkeep.quota.core.proxy.routing;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class binding IPP proxy, printer routing, and connection parameters.
 */
@Configuration
@ConfigurationProperties(prefix = "app.proxy")
@Getter
@Setter
public class PrinterConfig {

    private Map<String, String> printers = new HashMap<>();
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 30000;
    private long maxDocumentSize = 104857600; // 100 MB default
    private int maxRetryCount = 3;
    private int retryDelayMs = 1000;
    private int bufferSize = 8192;
}
