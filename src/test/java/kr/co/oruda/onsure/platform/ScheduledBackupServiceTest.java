package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScheduledBackupServiceTest {
    @TempDir Path temp;

    @Test
    void runOnceProducesABackupUnderTheBackupRoot() throws Exception {
        Path jobsRoot = temp.resolve("jobs");
        new DurableJobService(jobsRoot).create("job-001", "validation.run", "a".repeat(64), "operator-001");
        Path backupRoot = temp.resolve("backups");

        try (ScheduledBackupService service = new ScheduledBackupService(jobsRoot, backupRoot, 5)) {
            service.runOnce();
        }

        try (var entries = Files.list(backupRoot)) {
            assertEquals(1, entries.count());
        }
    }

    @Test
    void pruningKeepsOnlyTheMostRecentBackupsUpToTheRetainCount() throws Exception {
        Path jobsRoot = temp.resolve("jobs-prune");
        new DurableJobService(jobsRoot).create("job-001", "validation.run", "a".repeat(64), "operator-001");
        Path backupRoot = temp.resolve("backups-prune");

        try (ScheduledBackupService service = new ScheduledBackupService(jobsRoot, backupRoot, 2)) {
            service.runOnce();
            Thread.sleep(2);
            service.runOnce();
            Thread.sleep(2);
            service.runOnce();
        }

        try (var entries = Files.list(backupRoot)) {
            assertEquals(2, entries.count());
        }
    }

    @Test
    void startSchedulesRepeatedBackupsAtTheGivenInterval() throws Exception {
        Path jobsRoot = temp.resolve("jobs-scheduled");
        new DurableJobService(jobsRoot).create("job-001", "validation.run", "a".repeat(64), "operator-001");
        Path backupRoot = temp.resolve("backups-scheduled");

        try (ScheduledBackupService service = new ScheduledBackupService(jobsRoot, backupRoot, 10)) {
            service.start(Duration.ofMillis(20));
            long deadline = System.currentTimeMillis() + 2000;
            while (service.successCount() < 3 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertTrue(service.successCount() >= 3, "expected at least 3 scheduled backups, got "
                    + service.successCount() + " (failures: " + service.failureCount()
                    + ", last: " + service.lastFailureMessage() + ")");
        }
    }

    @Test
    void rejectsInvalidRetainCountAndInterval() {
        Path jobsRoot = temp.resolve("jobs-invalid");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ScheduledBackupService(jobsRoot, temp.resolve("backups-invalid"), 0));

        try (ScheduledBackupService service = new ScheduledBackupService(jobsRoot, temp.resolve("backups-invalid-2"), 1)) {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> service.start(Duration.ZERO));
        }
    }
}
