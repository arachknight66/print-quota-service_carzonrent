package com.printkeep.quota.core.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Top-level test controller utilized only within unit tests to trigger exception handlers.
 */
@RestController
class TestController {

    /**
     * Endpoint that always throws a RuntimeException.
     */
    @GetMapping("/test/runtime-exception")
    public void throwRuntimeException() {
        throw new RuntimeException("Simulated runtime error");
    }

    /**
     * Endpoint to test validation exception handling.
     *
     * @param body request body containing validation constraints.
     */
    @PostMapping("/test/validation")
    public void testValidation(@Valid @RequestBody final DummyBody body) {
        // No-op, just for validation test
    }

    /**
     * Static helper class representing request body for validation testing.
     */
    static class DummyBody {
        
        @NotBlank(message = "must not be blank")
        private String name;

        /**
         * Gets the name property.
         *
         * @return the name.
         */
        public String getName() {
            return name;
        }

        /**
         * Sets the name property.
         *
         * @param name the name to set.
         */
        public void setName(final String name) {
            this.name = name;
        }
    }
}
