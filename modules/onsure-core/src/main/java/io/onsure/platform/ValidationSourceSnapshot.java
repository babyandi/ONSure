package io.onsure.platform;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** Creates a bounded writable execution copy while preserving a read-only source boundary. */
public final class ValidationSourceSnapshot {
    public static final int DEFAULT_MAX_FILES = 50_000;
    public static final long DEFAULT_MAX_BYTES = 512L * 1024L * 1024L;
    public record Snapshot(
            Path sourceRoot,
            Path snapshotRoot,
            String sourceDigestBefore,
            String snapshotDigest,
            int fileCount,
            long byteCount) {}

    private ValidationSourceSnapshot() {}

    public static Snapshot create(Path sourceRoot, Path snapshotRoot) throws Exception {
        return create(sourceRoot, snapshotRoot, DEFAULT_MAX_FILES, DEFAULT_MAX_BYTES);
    }

    static Snapshot create(Path sourceRoot, Path snapshotRoot, int maxFiles, long maxBytes) throws Exception {
        Path source = requireDirectory(sourceRoot, "SNAPSHOT_SOURCE_INVALID");
        Path destination = snapshotRoot.toAbsolutePath().normalize();
        if (destination.startsWith(source) || source.startsWith(destination)) {
            throw new IllegalArgumentException("SNAPSHOT_ROOT_OVERLAPS_SOURCE");
        }
        if (maxFiles < 1 || maxBytes < 1) throw new IllegalArgumentException("SNAPSHOT_LIMIT_INVALID");
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("SNAPSHOT_ROOT_ALREADY_EXISTS");
        }

        Inventory inventory = inventory(source, maxFiles, maxBytes);
        Files.createDirectories(destination);
        try {
            for (Path file : inventory.files()) {
                Path relative = source.relativize(file);
                Path target = destination.resolve(relative).normalize();
                if (!target.startsWith(destination)) throw new IllegalStateException("SNAPSHOT_PATH_ESCAPE");
                Files.createDirectories(target.getParent());
                Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES);
            }
            String copiedDigest = inventoryDigest(destination, inventory.relativePaths());
            if (!inventory.digest().equals(copiedDigest)) {
                throw new IllegalStateException("SNAPSHOT_READBACK_DIGEST_MISMATCH");
            }
            return new Snapshot(source, destination, inventory.digest(), copiedDigest,
                    inventory.files().size(), inventory.byteCount());
        } catch (Exception failure) {
            deleteCreatedTree(destination);
            throw failure;
        }
    }

    public static boolean sourceUnchanged(Snapshot snapshot) throws Exception {
        Inventory current = inventory(snapshot.sourceRoot(), DEFAULT_MAX_FILES, DEFAULT_MAX_BYTES);
        return snapshot.sourceDigestBefore().equals(current.digest());
    }

    /** Returns the exact source set used by execution snapshots and provenance bindings. */
    static List<Path> sourceFiles(Path sourceRoot) throws Exception {
        return inventory(requireDirectory(sourceRoot, "SNAPSHOT_SOURCE_INVALID"),
                DEFAULT_MAX_FILES, DEFAULT_MAX_BYTES).files();
    }

    private static Inventory inventory(Path root, int maxFiles, long maxBytes) throws Exception {
        List<Path> files = new ArrayList<>();
        long[] bytes = {0L};
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) {
                if (Files.isSymbolicLink(directory)) {
                    throw new IllegalArgumentException("SNAPSHOT_SYMLINK_FORBIDDEN:" + root.relativize(directory));
                }
                if (!directory.equals(root) && excludedName(directory.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (excludedName(file.getFileName().toString())) {
                    return FileVisitResult.CONTINUE;
                }
                if (Files.isSymbolicLink(file) || !attrs.isRegularFile()) {
                    throw new IllegalArgumentException("SNAPSHOT_NONREGULAR_FILE_FORBIDDEN:" + root.relativize(file));
                }
                files.add(file);
                bytes[0] += attrs.size();
                if (files.size() > maxFiles) throw new IllegalArgumentException("SNAPSHOT_FILE_LIMIT_EXCEEDED");
                if (bytes[0] > maxBytes) throw new IllegalArgumentException("SNAPSHOT_BYTE_LIMIT_EXCEEDED");
                return FileVisitResult.CONTINUE;
            }
        });
        files.sort(Comparator.comparing(file -> normalized(root.relativize(file))));
        List<String> relative = files.stream().map(file -> normalized(root.relativize(file))).toList();
        return new Inventory(List.copyOf(files), relative, bytes[0], inventoryDigest(root, relative));
    }

    private static String inventoryDigest(Path root, List<String> relativePaths) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (String relative : relativePaths) {
            Path file = root.resolve(relative).normalize();
            if (!file.startsWith(root)) throw new IllegalStateException("SNAPSHOT_DIGEST_PATH_ESCAPE");
            digest.update(relative.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(Files.readAllBytes(file));
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static Path requireDirectory(Path value, String code) {
        if (value == null) throw new IllegalArgumentException(code);
        Path path = value.toAbsolutePath().normalize();
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IllegalArgumentException(code);
        }
        return path;
    }

    private static boolean excludedName(String name) {
        return GeneratedPathPolicy.excludes(name);
    }

    private static String normalized(Path value) {
        return value.toString().replace('\\', '/');
    }

    private static void deleteCreatedTree(Path root) {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }

    private record Inventory(List<Path> files, List<String> relativePaths, long byteCount, String digest) {}
}
