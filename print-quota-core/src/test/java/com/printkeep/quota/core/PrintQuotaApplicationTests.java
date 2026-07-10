package com.printkeep.quota.core;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Basic application tests to verify Spring context loads successfully.
 */
@SpringBootTest
@ActiveProfiles("dev")
class PrintQuotaApplicationTests {

    /**
     * Verifies that the Spring Boot context initializes without errors.
     */
    @Test
    void contextLoads() {
        // Assert that context starts successfully.
    }
}
