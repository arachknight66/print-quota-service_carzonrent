package com.carzonrent.platform.qadashboard.web;

import com.carzonrent.platform.qadashboard.web.dto.InfoResponse;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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

    public DashboardController(@Value("${carzonrent.application-version}") String applicationVersion) {
        this.applicationVersion = applicationVersion;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        InfoResponse info = buildInfoResponse();
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
    public InfoResponse info() {
        return buildInfoResponse();
    }

    @GetMapping("/not-found")
    public ResponseEntity<Map<String, String>> notFound() {
        return ResponseEntity.status(404).body(Map.of(
                "status", "NOT_FOUND",
                "environment", "QA"
        ));
    }

    private InfoResponse buildInfoResponse() {
        return new InfoResponse(
                resolveHostname(),
                System.getProperty("java.version"),
                SpringBootVersion.getVersion(),
                OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                System.getProperty("os.name") + " " + System.getProperty("os.version"),
                "QA"
        );
    }

    private String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ex) {
            return "qa.carzonrent";
        }
    }
}
