package com.carzonrent.platform.qadashboard.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "carzonrent")
public record CarzonrentProperties(
        @NotBlank String environment,
        @NotBlank String applicationVersion,
        @NotBlank String containerName,
        @Pattern(regexp = "127\\.0\\.0\\.1:\\d+", message = "backendAddress must remain on loopback")
        String backendAddress,
        @NotBlank String gitCommitId,
        @NotBlank String buildTimestamp,
        @NotBlank String dockerImageTag,
        @NotBlank String jenkinsBuildNumber,
        String helmChartVersion,
        String helmVersion,
        String kubernetesRevision,
        String containerImage,
        String deploymentTime
) {
}
