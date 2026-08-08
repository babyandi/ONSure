package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import kr.co.oruda.onsure.platform.DurableJobService.BacklogSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DurableJobBacklogAndBackupTest {
    @TempDir Path temp;

    @Test
    void backlogSummaryCountsQueuedAndRunningAndFlagsStaleJobs() throws Exception {
        Path jobsRoot = temp.resolve("jobs");
        DurableJobService jobs = new DurableJobService(jobsRoot);
        jobs.create("job-queued-001", "validation.run", "a".repeat(64), "operator-001");
        jobs.create("job-running-001", "validation.run", "b".repeat(64), "operator-001");
        jobs.start("job-running-001", 1, "operator-001");
        jobs.create("job-completed-001", "validation.run", "c".repeat(64), "operator-001");
        jobs.start("job-completed-001", 1, "operator-001");
        jobs.complete("job-completed-001", 2, "operator-001");

        BacklogSummary immediate = jobs.backlogSummary(Instant.now(), Duration.ZERO);
        assertEquals(1, immediate.queuedCount());
        assertEquals(1, immediate.runningCount());
        assertTrue(immediate.staleJobIds().containsAll(List.of("job-queued-001", "job-running-001")));
        assertTrue(immediate.oldestQueuedAgeSeconds() >= 0);

        BacklogSummary notStaleYet = jobs.backlogSummary(Instant.now(), Duration.ofDays(1));
        assertEquals(List.of(), notStaleYet.staleJobIds());
    }

    @Test
    void backlogSummaryRejectsNegativeThreshold() throws Exception {
        DurableJobService jobs = new DurableJobService(temp.resolve("jobs-invalid"));
        assertThrows(IllegalArgumentException.class,
                () -> jobs.backlogSummary(Instant.now(), Duration.ofSeconds(-1)));
    }

    @Test
    void backupThenRestoreRoundTripsAJobStore() throws Exception {
        Path jobsRoot = temp.resolve("jobs-source");
        DurableJobService jobs = new DurableJobService(jobsRoot);
        jobs.create("job-001", "validation.run", "a".repeat(64), "operator-001");
        jobs.start("job-001", 1, "operator-001");

        Path backupDir = temp.resolve("backup");
        DurableJobBackupService.BackupResult backupResult = DurableJobBackupService.backup(jobsRoot, backupDir);
        assertTrue(backupResult.fileCount() > 0);
        assertTrue(Files.isRegularFile(backupResult.manifestFile()));

        Path restoredRoot = temp.resolve("jobs-restored");
        DurableJobBackupService.RestoreResult restoreResult =
                DurableJobBackupService.restore(backupDir, restoredRoot);
        assertEquals(backupResult.fileCount(), restoreResult.fileCount());
        assertEquals(backupResult.manifestSha256(), restoreResult.manifestSha256());

        DurableJobService restored = new DurableJobService(restoredRoot);
        assertEquals("RUNNING", restored.read("job-001").get("status"));
    }

    @Test
    void restoreFailsClosedOnTamperedBackupFile() throws Exception {
        Path jobsRoot = temp.resolve("jobs-tamper-source");
        DurableJobService jobs = new DurableJobService(jobsRoot);
        jobs.create("job-001", "validation.run", "a".repeat(64), "operator-001");

        Path backupDir = temp.resolve("backup-tamper");
        DurableJobBackupService.backup(jobsRoot, backupDir);

        try (var files = Files.walk(backupDir)) {
            Path stateFile = files.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().equals("backup-manifest.json"))
                    .findFirst().orElseThrow();
            Files.writeString(stateFile, "{\"tampered\": true}");
        }

        assertThrows(IllegalStateException.class,
                () -> DurableJobBackupService.restore(backupDir, temp.resolve("jobs-tamper-restored")));
    }

    @Test
    void restoreRefusesToOverwriteANonEmptyTarget() throws Exception {
        Path jobsRoot = temp.resolve("jobs-refuse-source");
        new DurableJobService(jobsRoot).create("job-001", "validation.run", "a".repeat(64), "operator-001");
        Path backupDir = temp.resolve("backup-refuse");
        DurableJobBackupService.backup(jobsRoot, backupDir);

        Path occupiedTarget = temp.resolve("jobs-occupied");
        Files.createDirectories(occupiedTarget);
        Files.writeString(occupiedTarget.resolve("existing-file.txt"), "pre-existing live data");

        assertThrows(IllegalStateException.class,
                () -> DurableJobBackupService.restore(backupDir, occupiedTarget));
    }
}
