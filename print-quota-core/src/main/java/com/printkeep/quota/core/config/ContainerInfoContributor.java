package com.printkeep.quota.core.config;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

/**
 * Exposes container and runtime environment details under /actuator/info.
 */
@Component
public class ContainerInfoContributor implements InfoContributor {

    @Override
    public void contribute(final Info.Builder builder) {
        final Map<String, Object> containerInfo = new HashMap<>();

        try {
            final String hostname = InetAddress.getLocalHost().getHostName();
            containerInfo.put("hostname", hostname);
        } catch (final UnknownHostException e) {
            containerInfo.put("hostname", "unknown");
        }

        containerInfo.put("osName", System.getProperty("os.name"));
        containerInfo.put("osVersion", System.getProperty("os.version"));
        containerInfo.put("javaVersion", System.getProperty("java.version"));
        containerInfo.put("javaVendor", System.getProperty("java.vendor"));
        containerInfo.put("maxMemoryMb", Runtime.getRuntime().maxMemory() / (1024 * 1024));
        containerInfo.put("availableProcessors", Runtime.getRuntime().availableProcessors());

        builder.withDetail("container", containerInfo);
    }
}
