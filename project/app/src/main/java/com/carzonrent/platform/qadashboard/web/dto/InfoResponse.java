package com.carzonrent.platform.qadashboard.web.dto;

public record InfoResponse(
        String hostname,
        String javaVersion,
        String springBootVersion,
        String currentTime,
        String operatingSystem,
        String environment
) {
}
