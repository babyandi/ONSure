package io.onsure.platform;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class Hashing {
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

    /**
     * Produces a deterministic tree digest.
     *
     * <p>When the target is inside a Git worktree, only Git-tracked regular files below the target
     * root are included. This prevents .git metadata, untracked files, build output and receipts
     * from silently changing the source identity. Non-Git archive targets use a fail-closed
     * filesystem walk with runtime-output and symbolic-link exclusions.
     */
    static String tree(Path root) throws Exception {
        Path normalized = root.toAbsolutePath().normalize();
        List<Path> tracked = gitTrackedFiles(normalized);
        return digestTree(normalized, tracked != null ? tracked : archiveFiles(normalized));
    }

    private static List<Path> gitTrackedFiles(Path root) throws Exception {
        ProcessBuilder topBuilder = new ProcessBuilder(
                "git", "-C", root.toString(), "rev-parse", "--show-toplevel")
                .redirectErrorStream(true);
        Process topProcess = topBuilder.start();
        boolean topCompleted = topProcess.waitFor(15, TimeUnit.SECONDS);
        if (!topCompleted) {
            topProcess.destroyForcibly();
            throw new IllegalStateException("GIT_TOPLEVEL_TIMEOUT");
        }
        if (topProcess.exitValue() != 0) return null;
        Path gitRoot = Path.of(new String(
                topProcess.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip())
                .toAbsolutePath().normalize();

        ProcessBuilder listBuilder = new ProcessBuilder(
                "git", "-C", root.toString(), "ls-files", "-z", "--", ".")
                .redirectErrorStream(true);
        Process listProcess = listBuilder.start();
        boolean listCompleted = listProcess.waitFor(30, TimeUnit.SECONDS);
        if (!listCompleted) {
            listProcess.destroyForcibly();
            throw new IllegalStateException("GIT_LS_FILES_TIMEOUT");
        }
        byte[] output = listProcess.getInputStream().readAllBytes();
        if (listProcess.exitValue() != 0) {
            throw new IllegalStateException("GIT_LS_FILES_FAILED");
        }
        List<Path> files = new ArrayList<>();
        int start = 0;
        for (int index = 0; index <= output.length; index++) {
            if (index == output.length || output[index] == 0) {
                if (index > start) {
                    String value = new String(output, start, index - start, StandardCharsets.UTF_8);
                    Path file = gitRoot.resolve(value).toAbsolutePath().normalize();
                    if (file.startsWith(root)
                            && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                            && !Files.isSymbolicLink(file)) {
                        files.add(file);
                    }
                }
                start = index + 1;
            }
        }
        files.sort(Comparator.comparing(path -> relative(root, path)));
        return List.copyOf(files);
    }

    private static List<Path> archiveFiles(Path root) throws Exception {
        List<Path> files = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            stream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> !excluded(root, path))
                    .sorted(Comparator.comparing(path -> relative(root, path)))
                    .forEach(files::add);
        }
        return List.copyOf(files);
    }

    private static boolean excluded(Path root, Path path) {
        String value = relative(root, path);
        return value.equals(".git") || value.startsWith(".git/")
                || value.equals("target") || value.startsWith("target/")
                || value.equals("receipts") || value.startsWith("receipts/")
                || value.equals(".onsure") || value.startsWith(".onsure/")
                || value.equals("validation-data") || value.startsWith("validation-data/")
                || value.contains("/__pycache__/") || value.startsWith("__pycache__/");
    }

    private static String digestTree(Path root, List<Path> files) throws Exception {
        ByteArrayOutputStream aggregate = new ByteArrayOutputStream();
        for (Path file : files) {
            aggregate.write(relative(root, file).getBytes(StandardCharsets.UTF_8));
            aggregate.write(0);
            aggregate.write(Files.readAllBytes(file));
            aggregate.write(0);
        }
        return sha256(aggregate.toByteArray());
    }

    static String relative(Path root, Path file) {
        return root.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize())
                .toString().replace('\\', '/');
    }
}
