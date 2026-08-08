package kr.co.oruda.onsure.platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs {@link DurableJobBackupService#backup} on a fixed interval and prunes old backups beyond a
 * retention count (OBSERVABILITY-OPERATIONS: NO_SCHEDULED_AUTOMATIC_BACKUP...). Each scheduled run
 * writes into its own timestamped subdirectory so a failed or partial backup never overwrites the
 * last good one. Offsite replication of these backups is NOT handled here -- that remains an
 * explicit remaining gap, not something this class claims to do.
 */
public final class ScheduledBackupService implements AutoCloseable {
    private final Path jobsRoot;
    private final Path backupRoot;
    private final int retainCount;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
            runnable -> {
                Thread thread = new Thread(runnable, "onsure-scheduled-backup");
                thread.setDaemon(true);
                return thread;
            });
    private final AtomicInteger successCount = new AtomicInteger();
    private final AtomicInteger failureCount = new AtomicInteger();
    private final AtomicReference<String> lastFailureMessage = new AtomicReference<>();

    public ScheduledBackupService(Path jobsRoot, Path backupRoot, int retainCount) {
        this.jobsRoot = Objects.requireNonNull(jobsRoot, "jobsRoot").toAbsolutePath().normalize();
        this.backupRoot = Objects.requireNonNull(backupRoot, "backupRoot").toAbsolutePath().normalize();
        if (retainCount < 1) throw new IllegalArgumentException("SCHEDULED_BACKUP_RETAIN_COUNT_INVALID");
        this.retainCount = retainCount;
    }

    public void start(Duration interval) {
        if (interval == null || interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("SCHEDULED_BACKUP_INTERVAL_INVALID");
        }
        executor.scheduleAtFixedRate(this::runOnceSafely, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Runs one backup cycle synchronously, outside the schedule; useful for on-demand/testing invocation. */
    public void runOnce() throws Exception {
        Path destination = backupRoot.resolve("backup-" + Instant.now().toEpochMilli() + "-" + System.nanoTime());
        DurableJobBackupService.backup(jobsRoot, destination);
        pruneOldBackups();
    }

    private void runOnceSafely() {
        try {
            runOnce();
            successCount.incrementAndGet();
        } catch (Exception failure) {
            failureCount.incrementAndGet();
            lastFailureMessage.set(failure.getMessage() == null
                    ? failure.getClass().getSimpleName() : failure.getMessage());
        }
    }

    private void pruneOldBackups() throws IOException {
        if (!Files.isDirectory(backupRoot, LinkOption.NOFOLLOW_LINKS)) return;
        List<Path> backups = new ArrayList<>();
        try (var entries = Files.list(backupRoot)) {
            entries.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> !Files.isSymbolicLink(path))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(backups::add);
        }
        while (backups.size() > retainCount) {
            deleteRecursively(backups.remove(0));
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try (var stream = Files.walk(root)) {
            List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
            for (Path path : paths) Files.deleteIfExists(path);
        }
    }

    public int successCount() { return successCount.get(); }

    public int failureCount() { return failureCount.get(); }

    public String lastFailureMessage() { return lastFailureMessage.get(); }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
