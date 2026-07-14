package com.printkeep.quota.core.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringBootVersion;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Controller providing the enterprise QA dashboard and the health/info endpoints.
 */
@Controller("qaDashboardController")
public class DashboardController {

    private static final int APACHE_PORT = 80;
    private static final String LOOPBACK_IP = "127.0.0.1";

    /**
     * Renders the enterprise QA dashboard.
     */
    @GetMapping("/")
    public String showDashboard(final HttpServletRequest request, final Model model) {
        model.addAttribute("environment", "QA (Phase 1)");
        model.addAttribute("hostname", getHostname());
        model.addAttribute("javaVersion", System.getProperty("java.version"));
        model.addAttribute("springBootVersion", SpringBootVersion.getVersion());
        model.addAttribute("os", System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")");
        
        final boolean apacheUp = isApacheRunning();
        model.addAttribute("apacheStatus", apacheUp ? "RUNNING (Port 80)" : "STOPPED / UNREACHABLE");
        model.addAttribute("apacheStatusClass", apacheUp ? "status-up" : "status-down");

        final boolean hasProxyHeaders = request.getHeader("X-Forwarded-For") != null 
                || request.getHeader("X-Forwarded-Host") != null 
                || request.getHeader("X-Forwarded-Proto") != null;
        
        model.addAttribute("reverseProxyStatus", hasProxyHeaders ? "ACTIVE (Routed via Apache)" : "DIRECT ACCESS (No Proxy)");
        model.addAttribute("reverseProxyClass", hasProxyHeaders ? "status-up" : "status-warning");
        model.addAttribute("backendAddress", "http://127.0.0.1:8085/");
        model.addAttribute("deploymentStatus", (apacheUp && hasProxyHeaders) ? "VERIFIED / HEALTHY" : "DEGRADED");
        model.addAttribute("deploymentClass", (apacheUp && hasProxyHeaders) ? "status-up" : "status-warning");
        
        model.addAttribute("currentTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        
        return "dashboard";
    }

    /**
     * Health endpoint forwarding directly to Spring Boot Actuator health endpoint.
     */
    @GetMapping("/health")
    public String getHealth() {
        return "forward:/actuator/health";
    }

    /**
     * Info endpoint forwarding directly to Spring Boot Actuator info endpoint.
     */
    @GetMapping("/info")
    public String getInfo() {
        return "forward:/actuator/info";
    }

    private String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "qa.carzonrent";
        }
    }

    private boolean isApacheRunning() {
        try (Socket socket = new Socket(LOOPBACK_IP, APACHE_PORT)) {
            return socket.isConnected();
        } catch (IOException e) {
            return false;
        }
    }
}
