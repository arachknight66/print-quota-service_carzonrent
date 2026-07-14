package com.printkeep.quota.core.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller to expose system status, health, liveness, and build metadata.
 * Uses constructor injection to populate build-specific variables.
 */
@RestController
@RequestMapping("/api/v1/status")
public class LivenessController {

    private final String appVersion;
    private final String appName;

    /**
     * Constructs the status controller.
     *
     * @param appVersion version of the application from configurations.
     * @param appName    name of the application from configurations.
     */
    public LivenessController(
            @Value("${info.app.version:1.0.0}") final String appVersion,
            @Value("${info.app.name:print-quota-service}") final String appName
    ) {
        this.appVersion = appVersion;
        this.appName = appName;
    }

    /**
     * Heartbeat liveness endpoint.
     *
     * @return response containing status UP.
     */
    @GetMapping("/liveness")
    public ResponseEntity<Map<String, String>> checkLiveness() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    /**
     * Readiness endpoint.
     *
     * @return response containing status UP.
     */
    @GetMapping("/readiness")
    public ResponseEntity<Map<String, String>> checkReadiness() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    /**
     * Version endpoint.
     *
     * @return response containing version metadata.
     */
    @GetMapping("/version")
    public ResponseEntity<Map<String, String>> getVersion() {
        return ResponseEntity.ok(Map.of("version", appVersion));
    }

    /**
     * Detailed build information endpoint.
     *
     * @return response containing build and service attributes.
     */
    @GetMapping("/build")
    public ResponseEntity<Map<String, String>> getBuildInfo() {
        return ResponseEntity.ok(Map.of(
                "name", appName,
                "version", appVersion,
                "javaVersion", System.getProperty("java.version"),
                "osName", System.getProperty("os.name")
        ));
    }
}
