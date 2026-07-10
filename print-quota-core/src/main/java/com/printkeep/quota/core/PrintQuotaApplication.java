package com.printkeep.quota.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main bootstrapper class for the Print Quota Management System.
 * This class initializes the Spring application context, web server, and configuration layers.
 */
@SpringBootApplication
public class PrintQuotaApplication {

    /**
     * Main entry point of the application.
     * Starts the embedded Jetty web container and Spring Boot framework.
     *
     * @param args Command line arguments passed to the application.
     */
    public static void main(final String[] args) {
        SpringApplication.run(PrintQuotaApplication.class, args);
    }
}
