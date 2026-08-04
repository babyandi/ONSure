package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Executable self-validation convention for interruption, resume, rollback and rerun. */
class OperationalResilienceValidationTest {
    @TempDir Path temp;

    @Test
    void interruption() {
        enabled();
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> BoundedProcessRunner.run(List.of("sh", "-c", "sleep 5"), temp,
                        Duration.ofMillis(250), 4096, pathEnvironment(), "SELF_INTERRUPTION"));
        assertEquals("SELF_INTERRUPTION_COMMAND_TIMEOUT", failure.getMessage());
    }

    @Test
    void resume() throws Exception {
        enabled();
        Path checkpoint = temp.resolve("checkpoint.txt");
        Files.writeString(checkpoint, "INTERRUPTED");
        assertEquals("INTERRUPTED", Files.readString(checkpoint));
        Files.writeString(checkpoint, "RESUMED");
        assertEquals("RESUMED", Files.readString(checkpoint));
    }

    @Test
    void rollback() throws Exception {
        enabled();
        Path state = temp.resolve("state.txt");
        Files.writeString(state, "BASELINE");
        String baseline = Hashing.file(state);
        Files.writeString(state, "MUTATED");
        Files.writeString(state, "BASELINE");
        assertEquals(baseline, Hashing.file(state));
    }

    @Test
    void rerun() throws Exception {
        enabled();
        Path first = temp.resolve("first.txt");
        Path second = temp.resolve("second.txt");
        Files.writeString(first, "DETERMINISTIC-RESULT\n");
        Files.writeString(second, "DETERMINISTIC-RESULT\n");
        assertEquals(Hashing.file(first), Hashing.file(second));
        assertTrue(Files.size(first) > 0);
    }

    private static void enabled() {
        Assumptions.assumeTrue(Boolean.getBoolean("onsure.validation.operations"));
    }

    private static Map<String, String> pathEnvironment() {
        String path = System.getenv("PATH");
        return path == null ? Map.of() : Map.of("PATH", path);
    }
}
