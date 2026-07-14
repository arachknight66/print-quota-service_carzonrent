package com.printkeep.quota.core;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/**
 * JUnit 5 extension condition to skip integration tests if Docker is not available on the host machine.
 * Prevents loading Spring Application Context when tests are skipped, avoiding connection failures.
 */
public class DockerCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(final ExtensionContext context) {
        try {
            if (DockerClientFactory.instance().isDockerAvailable()) {
                return ConditionEvaluationResult.enabled("Docker is available. Running integration test.");
            }
        } catch (final Exception e) {
            // Ignore and fall through to disabled state
        }
        return ConditionEvaluationResult.disabled("Docker environment not detected. Skipping integration test.");
    }
}
