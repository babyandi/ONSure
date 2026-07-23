package io.onsure.assurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFinalLockVerifierTest {
    @TempDir Path temp;

    @Test
    void acceptsCompleteUntamperedLock() throws Exception {
        Path run = createRequiredFiles();
        writeLock(run, required(run));
        assertEquals(Decision.PASS, new LocalFinalLockVerifier().verify(run).decision());
    }

    @Test
    void rejectsChangedLockedFile() throws Exception {
        Path run = createRequiredFiles();
        writeLock(run, required(run));
        Files.writeString(run.resolve("regression-1/test-summary.txt"), "tampered");
        assertTrue(new LocalFinalLockVerifier().verify(run).violations()
                .contains("FINAL_LOCK_DIGEST_MISMATCH"));
    }

    @Test
    void rejectsMissingRequiredEntry() throws Exception {
        Path run = createRequiredFiles();
        List<Path> files = new ArrayList<>(required(run));
        files.remove(run.resolve("regression-2/adversarial-fixtures.tsv"));
        writeLock(run, files);
        assertTrue(new LocalFinalLockVerifier().verify(run).violations()
                .contains("FINAL_LOCK_REQUIRED_ENTRY_MISSING"));
    }

    @Test
    void rejectsPathOutsideRunRoot() throws Exception {
        Path run = createRequiredFiles();
        Path outside = temp.resolve("outside.txt");
        Files.writeString(outside, "outside");
        List<Path> files = new ArrayList<>(required(run));
        files.add(outside);
        writeLock(run, files);
        assertTrue(new LocalFinalLockVerifier().verify(run).violations()
                .contains("FINAL_LOCK_PATH_OUTSIDE_RUN_ROOT"));
    }

    private Path createRequiredFiles() throws Exception {
        Path run = temp.resolve("receipts/local/run-context-0001");
        for (Path file : required(run)) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, file.getFileName().toString());
        }
        return run;
    }

    private List<Path> required(Path run) {
        return LocalFinalLockVerifier.requiredFiles(run);
    }

    private void writeLock(Path run, List<Path> files) throws Exception {
        StringBuilder content = new StringBuilder();
        for (Path file : files) {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(file)));
            content.append(digest).append("  ").append(file.toAbsolutePath().normalize()).append('\n');
        }
        Files.writeString(run.resolve("final-lock.sha256"), content);
    }
}
