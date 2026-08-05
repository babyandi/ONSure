package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BoundedProcessRunnerTest {
    @Test
    void drainsLargeOutputWithoutPipeDeadlockAndMarksTruncation() throws Exception {
        BoundedProcessRunner.Result result = BoundedProcessRunner.run(
                List.of("sh", "-c", "yes X | head -c 200000"),
                Path.of("."), Duration.ofSeconds(10), 8192,
                pathOnlyEnvironment(), "TEST_OUTPUT_FLOOD");
        assertEquals(0, result.exitCode());
        assertTrue(result.outputTruncated());
        assertEquals(8192, result.output().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
    }

    @Test
    void killsHungProcessAtWallClockDeadline() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> BoundedProcessRunner.run(
                        List.of("sh", "-c", "sleep 30"),
                        Path.of("."), Duration.ofMillis(300), 4096,
                        pathOnlyEnvironment(), "TEST_HUNG"));
        assertEquals("TEST_HUNG_COMMAND_TIMEOUT", failure.getMessage());
    }

    @Test
    void preservesNonzeroExitAndBoundedDiagnosticOutput() throws Exception {
        BoundedProcessRunner.Result result = BoundedProcessRunner.run(
                List.of("sh", "-c", "printf failure; exit 7"),
                Path.of("."), Duration.ofSeconds(5), 4096,
                pathOnlyEnvironment(), "TEST_FAILURE");
        assertEquals(7, result.exitCode());
        assertEquals("failure", result.output());
    }

    private static Map<String, String> pathOnlyEnvironment() {
        String path = System.getenv("PATH");
        return path == null ? Map.of() : Map.of("PATH", path);
    }
}
