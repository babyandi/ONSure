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
import java.util.LinkedHashSet;
import java.util.Set;

/** Bounded read-only helpers shared by installed standard validation packs. */
final class StandardValidationPackSupport {
    static final Duration BUILD_TIMEOUT = Duration.ofMinutes(20);
    static final Duration TEST_TIMEOUT = Duration.ofMinutes(15);
    private static final long MAX_CONFIG_BYTES = 5L * 1024 * 1024;
    private static final int MAX_DETECTION_ENTRIES = 50_000;
    private static final int MAX_TEST_SIGNAL_FILES = 5_000;
    private static final long MAX_TEST_SIGNAL_FILE_BYTES = 1024L * 1024L;
    private static final long MAX_TEST_SIGNAL_TOTAL_BYTES = 64L * 1024L * 1024L;
    private static final Set<String> SKIPPED_DIRECTORIES = Set.of(
            ".git", ".onsure", "target", "build", "node_modules", "__pycache__", ".venv", "venv",
            "fixtures", "test", "tests");

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

    static boolean testSignal(Path root, String relativeRoot, Set<String> extensions,
            List<String> tokens) throws IOException {
        Path testRoot = root.resolve(relativeRoot).normalize();
        if (!testRoot.startsWith(root) || !Files.isDirectory(testRoot, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(testRoot)) {
            return false;
        }
        Set<String> normalizedExtensions = extensions.stream()
                .map(value -> value.toLowerCase(java.util.Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
        List<String> normalizedTokens = tokens.stream()
                .map(value -> value.toLowerCase(java.util.Locale.ROOT)).toList();
        int[] files = {0};
        long[] bytes = {0};
        boolean[] found = {false};
        Files.walkFileTree(testRoot, Set.of(), 16, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                if (Files.isSymbolicLink(directory)) return FileVisitResult.SKIP_SUBTREE;
                return found[0] ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (found[0]) return FileVisitResult.TERMINATE;
                if (!attributes.isRegularFile() || Files.isSymbolicLink(file)) return FileVisitResult.CONTINUE;
                if (++files[0] > MAX_TEST_SIGNAL_FILES) {
                    throw new IllegalArgumentException("VALIDATION_TEST_SIGNAL_FILE_LIMIT_EXCEEDED");
                }
                String name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                if (normalizedExtensions.stream().noneMatch(name::endsWith)) return FileVisitResult.CONTINUE;
                if (normalizedTokens.stream().anyMatch(name::contains)) {
                    found[0] = true;
                    return FileVisitResult.TERMINATE;
                }
                if (attributes.size() > MAX_TEST_SIGNAL_FILE_BYTES) return FileVisitResult.CONTINUE;
                bytes[0] += attributes.size();
                if (bytes[0] > MAX_TEST_SIGNAL_TOTAL_BYTES) {
                    throw new IllegalArgumentException("VALIDATION_TEST_SIGNAL_BYTE_LIMIT_EXCEEDED");
                }
                String content = Files.readString(file).toLowerCase(java.util.Locale.ROOT);
                if (normalizedTokens.stream().anyMatch(content::contains)) {
                    found[0] = true;
                    return FileVisitResult.TERMINATE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return found[0];
    }

    static boolean contains(Path file, String token) {
        try {
            return readConfig(file).toLowerCase(java.util.Locale.ROOT)
                    .contains(token.toLowerCase(java.util.Locale.ROOT));
        } catch (Exception ignored) {
            return false;
        }
    }

    static List<Path> findOpenApiContracts(Path root) throws IOException {
        Set<Path> found = new LinkedHashSet<>();
        for (String candidate : List.of("openapi.yaml", "openapi.yml", "openapi.json",
                "contracts/openapi/onsure-local-api.v1.json",
                "contracts/openapi/onsure-llm-gateway.v1.json")) {
            if (file(root, candidate)) found.add(root.resolve(candidate));
        }
        int[] inspected = {0};
        Files.walkFileTree(root, Set.of(), 8, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                if (++inspected[0] > MAX_DETECTION_ENTRIES) {
                    throw new IllegalArgumentException("VALIDATION_DETECTION_ENTRY_LIMIT_EXCEEDED");
                }
                if (!directory.equals(root) && (Files.isSymbolicLink(directory)
                        || SKIPPED_DIRECTORIES.contains(directory.getFileName().toString()))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (++inspected[0] > MAX_DETECTION_ENTRIES) {
                    throw new IllegalArgumentException("VALIDATION_DETECTION_ENTRY_LIMIT_EXCEEDED");
                }
                if (!attributes.isRegularFile() || Files.isSymbolicLink(file)) return FileVisitResult.CONTINUE;
                String name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                String parent = file.getParent() == null ? ""
                        : root.relativize(file.getParent()).toString().replace('\\', '/');
                boolean extension = name.endsWith(".json") || name.endsWith(".yaml") || name.endsWith(".yml");
                if (extension && (name.contains("openapi") || parent.equals("contracts/openapi"))) {
                    found.add(file);
                    if (found.size() > 256) throw new IllegalArgumentException("OPENAPI_CONTRACT_LIMIT_EXCEEDED");
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return found.stream().sorted(Comparator.comparing(path ->
                root.relativize(path).toString().replace('\\', '/'))).toList();
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
