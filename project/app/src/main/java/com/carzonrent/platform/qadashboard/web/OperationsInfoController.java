package com.carzonrent.platform.qadashboard.web;

import com.carzonrent.platform.qadashboard.config.CarzonrentProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OperationsInfoController {

    private final CarzonrentProperties properties;
    private final ObjectProvider<BuildProperties> buildProperties;
    private final ObjectProvider<GitProperties> gitProperties;

    public OperationsInfoController(
            CarzonrentProperties properties,
            ObjectProvider<BuildProperties> buildProperties,
            ObjectProvider<GitProperties> gitProperties
    ) {
        this.properties = properties;
        this.buildProperties = buildProperties;
        this.gitProperties = gitProperties;
    }

    @GetMapping("/actuator/info")
    public Map<String, Object> actuatorInfo() {
        Map<String, Object> response = new LinkedHashMap<>();
        BuildProperties build = buildProperties.getIfAvailable();
        GitProperties git = gitProperties.getIfAvailable();
        response.put("app", Map.of(
                "name", build != null ? build.getName() : "qa-dashboard",
                "version", properties.applicationVersion(),
                "environment", properties.environment(),
                "dockerImageTag", properties.dockerImageTag(),
                "jenkinsBuildNumber", properties.jenkinsBuildNumber()
        ));
        response.put("build", Map.of(
                "artifact", build != null ? build.getArtifact() : "qa-dashboard",
                "group", build != null ? build.getGroup() : "com.carzonrent.platform",
                "version", build != null ? build.getVersion() : properties.applicationVersion(),
                "time", build != null ? build.getTime().toString() : properties.buildTimestamp()
        ));
        response.put("git", Map.of(
                "commit", fallback(properties.gitCommitId()),
                "branch", git != null ? fallback(git.getBranch()) : "unknown",
                "commitTime", git != null ? fallback(git.get("commit.time")) : fallback(properties.buildTimestamp())
        ));
        return response;
    }

    private String fallback(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
