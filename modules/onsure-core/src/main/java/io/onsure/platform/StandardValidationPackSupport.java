package io.onsure.platform;

import io.onsure.platform.UniversalValidationProfile.Phase;
import io.onsure.platform.UniversalValidationProfile.Step;
import io.onsure.platform.UniversalValidationProfile.StepKind;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Bounded read-only helpers shared by installed standard validation packs. */
final class StandardValidationPackSupport {
    static final Duration BUILD_TIMEOUT = Duration.ofMinutes(20);
    static final Duration TEST_TIMEOUT = Duration.ofMinutes(15);
    private static final long MAX_CONFIG_BYTES = 5L * 1024 * 1024;
    private static final int MAX_DETECTION_ENTRIES = 50_000;
    private static final Set<String> SKIPPED_DIRECTORIES = Set.of(
            ".git", ".onsure", "target", "build", "node_modules", "__pycache__", ".venv", "venv");

    private StandardValidationPackSupport() {}

    static Step step(String id, Phase phase, StepKind kind, List<String> command,
            Duration timeout, List<String> dependencies) {
        return new Step(id, phase, kind, true, command, Path.of(""), timeout, dependencies);
    }

    static boolean file(Path root, String relative) {
        Path value = root.resolve(relative).normalize();
        return value.startsWith(root) && Files.isRegularFile(value, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(value);
    }

    static boolean directory(Path root, String relative) {
        Path value = root.resolve(relative).normalize();
        return value.startsWith(root) && Files.isDirectory(value, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(value);
    }

    static String readConfig(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)
                || Files.size(file) > MAX_CONFIG_BYTES) {
            throw new IllegalArgumentException("VALIDATION_CONFIG_INVALID_OR_TOO_LARGE:" + file.getFileName());
        }
        return Files.readString(file);
    }

    static boolean contains(Path file, String token) {
        try {
            return readConfig(file).toLowerCase(java.util.Locale.ROOT)
                    .contains(token.toLowerCase(java.util.Locale.ROOT));
        } catch (Exception ignored) {
            return false;
        }
    }

    static Path firstFile(Path root, String... candidates) {
        for (String candidate : candidates) if (file(root, candidate)) return root.resolve(candidate);
        return null;
    }

    static Path findMigrationDirectory(Path root) throws IOException {
        for (String candidate : List.of("db/migration", "src/main/resources/db/migration", "migrations")) {
            if (directory(root, candidate)) return root.resolve(candidate);
        }
        List<Path> found = new ArrayList<>();
        int[] inspected = {0};
        Files.walkFileTree(root, Set.of(), 8, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                    throws IOException {
                if (++inspected[0] > MAX_DETECTION_ENTRIES) {
                    throw new IllegalArgumentException("VALIDATION_DETECTION_ENTRY_LIMIT_EXCEEDED");
                }
                if (!directory.equals(root) && (Files.isSymbolicLink(directory)
                        || SKIPPED_DIRECTORIES.contains(directory.getFileName().toString()))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                String relative = root.relativize(directory).toString().replace('\\', '/');
                if (relative.endsWith("/src/main/resources/db/migration")
                        || relative.endsWith("/db/migration") || relative.endsWith("/migrations")) {
                    found.add(directory);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (++inspected[0] > MAX_DETECTION_ENTRIES) {
                    throw new IllegalArgumentException("VALIDATION_DETECTION_ENTRY_LIMIT_EXCEEDED");
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return found.stream().sorted(Comparator.comparing(Path::toString)).findFirst().orElse(null);
    }
}
