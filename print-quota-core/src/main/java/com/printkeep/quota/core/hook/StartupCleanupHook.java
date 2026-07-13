package com.printkeep.quota.core.hook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Startup hook to clean up stale temporary spool files from previous crashes or executions.
 */
@Component
public class StartupCleanupHook implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupCleanupHook.class);

    private final String spoolDirectoryPath;

    public StartupCleanupHook(@Value("${app.spool.directory}") final String spoolDirectoryPath) {
        this.spoolDirectoryPath = spoolDirectoryPath;
    }

    @Override
    public void run(final String... args) {
        log.info("Starting startup cleanup hook for spool directory: {}", spoolDirectoryPath);
        final Path dirPath = Paths.get(spoolDirectoryPath);
        if (!Files.exists(dirPath)) {
            log.info("Spool directory does not exist, creating: {}", spoolDirectoryPath);
            try {
                Files.createDirectories(dirPath);
            } catch (final IOException e) {
                log.error("Failed to create spool directory: {}", spoolDirectoryPath, e);
                return;
            }
        }

        final File dir = dirPath.toFile();
        final File[] files = dir.listFiles();
        if (files != null) {
            int deletedCount = 0;
            for (final File file : files) {
                if (file.isFile()) {
                    final String fileName = file.getName();
                    if (file.delete()) {
                        log.info("Deleted orphaned spool file: {}", fileName);
                        deletedCount++;
                    } else {
                        log.warn("Failed to delete orphaned spool file: {}", fileName);
                    }
                }
            }
            log.info("Startup cleanup completed. Deleted {} orphaned spool files.", deletedCount);
        } else {
            log.info("No orphaned spool files found to delete.");
        }
    }
}
