package example.neutral;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class OperationalResilienceValidationTest {
    @Test void interruption() throws Exception {
        Process process = new ProcessBuilder("sh", "-c", "sleep 2").start();
        assertFalse(process.waitFor(Duration.ofMillis(50).toMillis(), TimeUnit.MILLISECONDS));
        process.destroyForcibly();
        process.waitFor();
    }

    @Test void resume() {
        var states = new ArrayList<>(java.util.List.of("checkpoint"));
        states.add("resumed");
        states.add("complete");
        assertEquals(java.util.List.of("checkpoint", "resumed", "complete"), states);
    }

    @Test void rollback() throws Exception {
        Path state = Files.createTempFile("neutral-state", ".txt");
        try {
            Files.writeString(state, "before");
            String baseline = sha(state);
            Files.writeString(state, "after");
            Files.writeString(state, "before");
            assertEquals(baseline, sha(state));
        } finally {
            Files.deleteIfExists(state);
        }
    }

    @Test void rerun() throws Exception {
        byte[] value = "deterministic-artifact".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(sha(value), sha(value));
    }

    private static String sha(Path path) throws Exception { return sha(Files.readAllBytes(path)); }
    private static String sha(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
