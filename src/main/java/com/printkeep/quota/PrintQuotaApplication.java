package com.printkeep.quota;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PrintQuotaApplication {
    public static void main(String[] args) {
        SpringApplication.run(PrintQuotaApplication.class, args);
    }
}
