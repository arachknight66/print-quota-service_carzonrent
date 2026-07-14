package com.printkeep.quota.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Regression checks for CSP-friendly Thymeleaf templates.
 */
class FrontendTemplateSecurityTests {

    private static final Path TEMPLATE_DIR = Path.of("src", "main", "resources", "templates");

    @Test
    void testDashboardAndErrorTemplatesDoNotUseInlineStylesOrHandlers() throws IOException {
        final List<Path> templates;
        try (var stream = Files.walk(TEMPLATE_DIR)) {
            templates = stream
                    .filter(path -> path.toString().endsWith(".html"))
                    .toList();
        }

        assertThat(templates).isNotEmpty();
        for (final Path template : templates) {
            final String html = Files.readString(template);
            assertThat(html)
                    .as("template %s should not contain inline style blocks", template)
                    .doesNotContain("<style>");
            assertThat(html)
                    .as("template %s should not contain inline style attributes", template)
                    .doesNotContain("style=");
            assertThat(html)
                    .as("template %s should not contain inline JavaScript handlers", template)
                    .doesNotContainPattern("\\son[a-zA-Z]+=");
        }
    }

    @Test
    void testErrorPagesUseSharedStaticStylesheet() throws IOException {
        for (final String code : List.of("403", "404", "500")) {
            final Path template = TEMPLATE_DIR.resolve("error").resolve(code + ".html");
            final String html = Files.readString(template);

            assertThat(html).contains("th:href=\"@{/css/error.css}\"");
        }
    }
}
