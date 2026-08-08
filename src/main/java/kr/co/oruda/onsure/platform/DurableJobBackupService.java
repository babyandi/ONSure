package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Integrity-verified backup and restore of a {@link DurableJobService} job store
 * (NFR-OBS: queue backup/restore operational controls). Operates on the job store's file tree as
 * an opaque set of files, so it stays correct regardless of DurableStateLedger's internal file
 * layout: every regular file under jobsRoot is copied and hashed into a manifest, and restore
 * verifies every hash before writing anything back, refusing to overwrite a non-empty target.
 */
public final class DurableJobBackupService {
    public static final String MANIFEST_CONTRACT = "ONSURE_DURABLE_JOB_BACKUP_MANIFEST_V1";

    public record ManifestEntry(String relativePath, String sha256) {}

    public record BackupResult(int fileCount, String manifestSha256, Path manifestFile) {}

    public record RestoreResult(int fileCount, String manifestSha256) {}

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private DurableJobBackupService() {}

    public static BackupResult backup(Path jobsRoot, Path destinationDir) throws Exception {
        Path source = requireExistingDirectory(jobsRoot, "BACKUP_JOBS_ROOT_MISSING");
        Path destination = destinationDir.toAbsolutePath().normalize();
        requireNoSymlink(destination, "BACKUP_DESTINATION_SYMLINK_PROHIBITED");
        if (hasEntries(destination)) throw new IllegalStateException("BACKUP_DESTINATION_NOT_EMPTY");
        Files.createDirectories(destination);

        List<Path> files = new ArrayList<>();
        try (var stream = Files.walk(source)) {
            stream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> !Files.isSymbolicLink(path))
                    .sorted(Comparator.comparing(path -> Hashing.relative(source, path)))
                    .forEach(files::add);
        }

        List<ManifestEntry> entries = new ArrayList<>();
        for (Path file : files) {
            String relativePath = Hashing.relative(source, file);
            String digest = Hashing.file(file);
            Path copyTarget = destination.resolve(relativePath);
            Files.createDirectories(copyTarget.getParent());
            Files.copy(file, copyTarget, StandardCopyOption.COPY_ATTRIBUTES);
            entries.add(new ManifestEntry(relativePath, digest));
        }

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("contract", MANIFEST_CONTRACT);
        manifest.put("backed_up_at", Instant.now().toString());
        manifest.put("file_count", entries.size());
        manifest.put("entries", entries.stream()
                .map(entry -> Map.of("relative_path", entry.relativePath(), "sha256", entry.sha256()))
                .toList());
        String manifestSha256 = Hashing.sha256(MAPPER.writeValueAsBytes(manifest.get("entries")));
        manifest.put("manifest_sha256", manifestSha256);

        Path manifestFile = destination.resolve("backup-manifest.json");
        MAPPER.writeValue(manifestFile.toFile(), manifest);
        return new BackupResult(entries.size(), manifestSha256, manifestFile);
    }

    public static RestoreResult restore(Path backupDir, Path targetJobsRoot) throws Exception {
        Path source = requireExistingDirectory(backupDir, "RESTORE_BACKUP_DIR_MISSING");
        Path target = targetJobsRoot.toAbsolutePath().normalize();
        requireNoSymlink(target, "RESTORE_TARGET_SYMLINK_PROHIBITED");
        if (hasEntries(target)) throw new IllegalStateException("RESTORE_TARGET_NOT_EMPTY");

        Path manifestFile = source.resolve("backup-manifest.json");
        if (!Files.isRegularFile(manifestFile)) throw new IllegalStateException("RESTORE_MANIFEST_MISSING");
        JsonNode manifest = MAPPER.readTree(manifestFile.toFile());
        if (!MANIFEST_CONTRACT.equals(manifest.path("contract").asText())) {
            throw new IllegalArgumentException("RESTORE_MANIFEST_CONTRACT_INVALID");
        }

        List<ManifestEntry> entries = new ArrayList<>();
        for (JsonNode entry : manifest.path("entries")) {
            entries.add(new ManifestEntry(entry.path("relative_path").asText(), entry.path("sha256").asText()));
        }
        String expectedManifestSha256 = manifest.path("manifest_sha256").asText();
        List<Map<String, String>> canonical = entries.stream()
                .map(entry -> Map.of("relative_path", entry.relativePath(), "sha256", entry.sha256()))
                .toList();
        if (!expectedManifestSha256.equals(Hashing.sha256(MAPPER.writeValueAsBytes(canonical)))) {
            throw new IllegalStateException("RESTORE_MANIFEST_TAMPERED");
        }

        // Verify every file's content before writing anything back, so a corrupt backup fails
        // closed instead of partially restoring a broken job store.
        for (ManifestEntry entry : entries) {
            Path file = source.resolve(entry.relativePath());
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
                throw new IllegalStateException("RESTORE_BACKUP_FILE_MISSING:" + entry.relativePath());
            }
            if (!entry.sha256().equals(Hashing.file(file))) {
                throw new IllegalStateException("RESTORE_BACKUP_FILE_INTEGRITY_MISMATCH:" + entry.relativePath());
            }
        }

        Files.createDirectories(target);
        for (ManifestEntry entry : entries) {
            Path from = source.resolve(entry.relativePath());
            Path to = target.resolve(entry.relativePath());
            Files.createDirectories(to.getParent());
            Files.copy(from, to, StandardCopyOption.COPY_ATTRIBUTES);
        }
        return new RestoreResult(entries.size(), expectedManifestSha256);
    }

    private static boolean hasEntries(Path directory) throws Exception {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return false;
        try (var stream = Files.list(directory)) {
            return stream.findAny().isPresent();
        }
    }

    private static Path requireExistingDirectory(Path path, String code) {
        Path normalized = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException(code);
        return normalized;
    }

    private static void requireNoSymlink(Path path, String code) {
        for (Path current = path; current != null; current = current.getParent()) {
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException(code);
            }
        }
    }
}
