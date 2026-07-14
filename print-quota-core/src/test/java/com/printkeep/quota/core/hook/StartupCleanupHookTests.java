package com.printkeep.quota.core.hook;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StartupCleanupHookTests {

    @Test
    void testCleanupDeletesOrphanedFiles(@TempDir final Path tempDir) throws IOException {
        final Path file1 = tempDir.resolve("job-1.tmp");
        final Path file2 = tempDir.resolve("job-2.tmp");
        Files.writeString(file1, "dummy content");
        Files.writeString(file2, "dummy content");

        assertThat(Files.exists(file1)).isTrue();
        assertThat(Files.exists(file2)).isTrue();

        final StartupCleanupHook hook = new StartupCleanupHook(tempDir.toString());
        hook.run();

        assertThat(Files.exists(file1)).isFalse();
        assertThat(Files.exists(file2)).isFalse();
    }
}
