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
        String containerName,
        String gitCommitId,
        String buildTimestamp,
        String dockerImageTag,
        String jenkinsBuildNumber,
        String helmChartVersion,
        String helmVersion,
        String kubernetesRevision,
        String containerImage,
        String deploymentTime
) {
}
