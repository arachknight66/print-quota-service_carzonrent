package com.carzonrent.platform.qadashboard.web.dto;

public record InfoResponse(
        String environment,
        String hostname,
        String javaVersion,
        String springBootVersion,
        String operatingSystem,
        String apacheStatus,
        String reverseProxyStatus,
        String backendAddress,
        String deploymentStatus,
        String currentTime,
        String buildVersion,
        String containerName
) {
}
