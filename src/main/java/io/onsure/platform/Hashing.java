package io.onsure.platform;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

final class Hashing {
    private static final Set<String> EXCLUDED_DIRECTORY_NAMES = Set.of(
            ".git", ".onsure", "target", "build", "dist", "node_modules", "receipts",
            "validation-data", ".gradle", ".idea", ".vscode-test");
    private static final int MAX_ARCHIVE_FILES = 100_000;
    private static final long MAX_ARCHIVE_BYTES = 2L * 1024 * 1024 * 1024;
    private static final Duration GIT_TIMEOUT = Duration.ofSeconds(30);

    private Hashing() {}

    static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String file(Path file) throws Exception {
        return sha256(Files.readAllBytes(file));
    }

    static String tree(Path root) throws Exception {
        Path normalized = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) throw new IllegalArgumentException("source root missing");
        GitScope gitScope = detectGitScope(normalized);
        List<Path> files = gitScope == null
                ? archiveFiles(normalized)
                : trackedGitFiles(normalized, gitScope);
        return digestFileSet(normalized, files);
    }

    private static List<Path> trackedGitFiles(Path root, GitScope scope) throws Exception {
        String rootPathspec = scope.repositoryRoot().equals(root)
                ? "."
                : relative(scope.repositoryRoot(), root);
        GitResult status = runGit(scope.repositoryRoot(),
                "status", "--porcelain", "--untracked-files=all", "--", rootPathspec);
        if (!status.text().isBlank()) {
            throw new IllegalStateException(
                    "SOURCE_TREE_DIRTY_OR_UNTRACKED:" + status.text().strip());
        }

        GitResult listed = runGit(scope.repositoryRoot(), "ls-files", "-z", "--", rootPathspec);
        List<Path> files = new ArrayList<>();
        for (byte[] value : splitNull(listed.bytes())) {
            if (value.length == 0) continue;
            String relativeToRepository = new String(value, StandardCharsets.UTF_8);
            Path file = scope.repositoryRoot().resolve(relativeToRepository).normalize();
            if (!file.startsWith(root)) continue;
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(file)) {
                throw new IllegalStateException(
                        "TRACKED_SOURCE_FILE_INVALID:" + relativeToRepository);
            }
            files.add(file);
        }
        files.sort(Comparator.comparing(path -> relative(root, path)));
        if (files.isEmpty()) throw new IllegalStateException("TRACKED_SOURCE_SET_EMPTY");
        return List.copyOf(files);
    }

    private static List<Path> archiveFiles(Path root) throws Exception {
        List<Path> files = new ArrayList<>();
        long totalBytes = 0;
        try (var stream = Files.walk(root)) {
            for (Path path : stream.sorted(
                    Comparator.comparing(value -> relative(root, value))).toList()) {
                if (path.equals(root)) continue;
                Path relative = root.relativize(path);
                if (containsExcludedDirectory(relative)) continue;
                if (Files.isSymbolicLink(path)) {
                    throw new IllegalStateException(
                            "ARCHIVE_SOURCE_SYMLINK_PROHIBITED:" + relative(root, path));
                }
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue;
                files.add(path);
                totalBytes = Math.addExact(totalBytes, Files.size(path));
                if (files.size() > MAX_ARCHIVE_FILES) {
                    throw new IllegalStateException("ARCHIVE_SOURCE_FILE_LIMIT_EXCEEDED");
                }
                if (totalBytes > MAX_ARCHIVE_BYTES) {
                    throw new IllegalStateException("ARCHIVE_SOURCE_BYTE_LIMIT_EXCEEDED");
                }
            }
        }
        if (files.isEmpty()) throw new IllegalStateException("ARCHIVE_SOURCE_SET_EMPTY");
        return List.copyOf(files);
    }

    private static boolean containsExcludedDirectory(Path relative) {
        for (Path segment : relative) {
            if (EXCLUDED_DIRECTORY_NAMES.contains(segment.toString())) return true;
        }
        return false;
    }

    private static String digestFileSet(Path root, List<Path> files) throws Exception {
        MessageDigest aggregate = MessageDigest.getInstance("SHA-256");
        for (Path file : files) {
            String relative = relative(root, file);
            byte[] fileHash = MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(file));
            aggregate.update(relative.getBytes(StandardCharsets.UTF_8));
            aggregate.update((byte) 0);
            aggregate.update(fileHash);
            aggregate.update((byte) 0);
        }
        return HexFormat.of().formatHex(aggregate.digest());
    }

    private static GitScope detectGitScope(Path root) throws Exception {
        GitResult result = runGitAllowFailure(root, "rev-parse", "--show-toplevel");
        if (result.exitCode() != 0) return null;
        Path repositoryRoot = Path.of(result.text().strip()).toAbsolutePath().normalize();
        if (!root.startsWith(repositoryRoot)) return null;
        return new GitScope(repositoryRoot);
    }

    private static GitResult runGit(Path root, String... arguments) throws Exception {
        GitResult result = runGitAllowFailure(root, arguments);
        if (result.exitCode() != 0) {
            throw new IllegalStateException("GIT_SOURCE_COMMAND_FAILED:"
                    + String.join(" ", arguments) + ":" + result.text().strip());
        }
        return result;
    }

    private static GitResult runGitAllowFailure(Path root, String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(root.toString());
        command.addAll(Arrays.asList(arguments));
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        var environment = builder.environment();
        String path = environment.get("PATH");
        environment.clear();
        if (path != null) environment.put("PATH", path);
        Process process = builder.start();
        boolean finished = process.waitFor(GIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("GIT_SOURCE_COMMAND_TIMEOUT");
        }
        byte[] output = process.getInputStream().readAllBytes();
        return new GitResult(process.exitValue(), output,
                new String(output, StandardCharsets.UTF_8));
    }

    private static List<byte[]> splitNull(byte[] bytes) {
        List<byte[]> values = new ArrayList<>();
        int start = 0;
        for (int index = 0; index < bytes.length; index++) {
            if (bytes[index] == 0) {
                values.add(Arrays.copyOfRange(bytes, start, index));
                start = index + 1;
            }
        }
        if (start < bytes.length) values.add(Arrays.copyOfRange(bytes, start, bytes.length));
        return values;
    }

    static String relative(Path root, Path file) {
        return root.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize())
                .toString().replace('\\', '/');
    }

    private record GitScope(Path repositoryRoot) {}
    private record GitResult(int exitCode, byte[] bytes, String text) {}
}
