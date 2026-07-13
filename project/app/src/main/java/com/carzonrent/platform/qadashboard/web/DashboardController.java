package com.carzonrent.platform.qadashboard.web;

import com.carzonrent.platform.qadashboard.web.dto.InfoResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootVersion;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class DashboardController {

    private final String applicationVersion;
    private final String containerName;

    public DashboardController(
            @Value("${carzonrent.application-version}") String applicationVersion,
            @Value("${carzonrent.container-name:qa.carzonrent}") String containerName
    ) {
        this.applicationVersion = applicationVersion;
        this.containerName = containerName;
    }

    @GetMapping("/")
    public String dashboard(Model model, HttpServletRequest request) {
        InfoResponse info = buildInfoResponse(request);
        model.addAttribute("info", info);
        model.addAttribute("hostname", info.hostname());
        model.addAttribute("operatingSystem", info.operatingSystem());
        model.addAttribute("javaVersion", info.javaVersion());
        model.addAttribute("springBootVersion", info.springBootVersion());
        model.addAttribute("currentTime", info.currentTime());
        model.addAttribute("applicationVersion", applicationVersion);
        return "dashboard";
    }

    @GetMapping("/health")
    @ResponseBody
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping("/info")
    @ResponseBody
    public InfoResponse info(HttpServletRequest request) {
        return buildInfoResponse(request);
    }

    @GetMapping("/not-found")
    public ResponseEntity<Map<String, String>> notFound() {
        return ResponseEntity.status(404).body(Map.of(
                "status", "NOT_FOUND",
                "environment", "QA"
        ));
    }

    private InfoResponse buildInfoResponse(HttpServletRequest request) {
        boolean proxied = request.getHeader("X-Forwarded-For") != null
                || request.getHeader("X-Forwarded-Proto") != null
                || request.getHeader("X-Forwarded-Port") != null;
        return new InfoResponse(
                "QA",
                resolveHostname(),
                System.getProperty("java.version"),
                SpringBootVersion.getVersion(),
                resolveOperatingSystem(),
                proxied ? "RUNNING" : "NOT OBSERVED",
                proxied ? "ACTIVE" : "DIRECT BACKEND REQUEST",
                "127.0.0.1:8085",
                proxied ? "HEALTHY" : "BACKEND HEALTHY",
                OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                applicationVersion,
                containerName
        );
    }

    private String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ex) {
            return "qa.carzonrent";
        }
    }

    private String resolveOperatingSystem() {
        Path osRelease = Path.of("/etc/os-release");
        if (Files.isReadable(osRelease)) {
            try {
                List<String> lines = Files.readAllLines(osRelease);
                String prettyName = findOsReleaseValue(lines, "PRETTY_NAME");
                if (!prettyName.isBlank()) {
                    return prettyName;
                }
                String name = findOsReleaseValue(lines, "NAME");
                String version = findOsReleaseValue(lines, "VERSION");
                if (!name.isBlank()) {
                    return (name + " " + version).trim();
                }
            } catch (IOException ignored) {
                // Fall back to JVM kernel properties when distro metadata is unavailable.
            }
        }
        return System.getProperty("os.name") + " " + System.getProperty("os.version");
    }

    private String findOsReleaseValue(List<String> lines, String key) {
        String prefix = key + "=";
        return lines.stream()
                .filter(line -> line.startsWith(prefix))
                .findFirst()
                .map(line -> line.substring(prefix.length()))
                .map(this::stripOsReleaseQuotes)
                .orElse("");
    }

    private String stripOsReleaseQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
