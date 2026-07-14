package com.printkeep.quota.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for dashboard-side DOM injection hardening.
 */
class DashboardJavaScriptSecurityTests {

    private static final Path DASHBOARD_SCRIPT = Path.of(
            "src",
            "main",
            "resources",
            "static",
            "js",
            "dashboard.js"
    );

    @Test
    void testDashboardDoesNotInterpolateApiObjectsIntoHtmlTemplates() throws IOException {
        final String script = Files.readString(DASHBOARD_SCRIPT);

        assertThat(script).doesNotContain("${user.domainUsername}");
        assertThat(script).doesNotContain("${quota.user.domainUsername}");
        assertThat(script).doesNotContain("${log.user.domainUsername}");
        assertThat(script).doesNotContain("${log.documentName}");
        assertThat(script).doesNotContain("${deptName}");
    }

    @Test
    void testDashboardUsesUrlSearchParamsForAdminFilters() throws IOException {
        final String script = Files.readString(DASHBOARD_SCRIPT);

        assertThat(script).contains("new URLSearchParams");
        assertThat(script).doesNotContain("username=${state.usersParams.search}");
        assertThat(script).doesNotContain("correlationId=${state.logsParams.correlationId}");
    }

    @Test
    void testDashboardAvoidsUnsafeDomInjectionPatterns() throws IOException {
        final String script = Files.readString(DASHBOARD_SCRIPT);

        assertThat(script).doesNotContain("innerHTML");
        assertThat(script).doesNotContain(".onclick");
        assertThat(script).doesNotContain("javascript:void");
        assertThat(script).doesNotContain("String.prototype");
        assertThat(script).contains("createElement");
        assertThat(script).contains("textContent");
        assertThat(script).contains("replaceChildren");
    }
}
