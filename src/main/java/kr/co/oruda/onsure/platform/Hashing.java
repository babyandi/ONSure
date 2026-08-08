package kr.co.oruda.onsure.platform;

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
import java.util.Map;
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

    /** Returns the exact deterministic file set used for source identity. */
    static List<Path> sourceFiles(Path root) throws Exception {
        Path normalized = root.toAbsolutePath().normalize();
        List<Path> tracked = gitTrackedFiles(normalized);
        return tracked != null ? tracked : archiveFiles(normalized);
    }

    /** Produces a deterministic digest over {@link #sourceFiles(Path)}. */
    static String tree(Path root) throws Exception {
        Path normalized = root.toAbsolutePath().normalize();
        return digestTree(normalized, sourceFiles(normalized));
    }

    private static List<Path> gitTrackedFiles(Path root) throws Exception {
        GitResult top = git(root, List.of("rev-parse", "--show-toplevel"), 15);
        if (top.exitCode() != 0) return null;
        Path gitRoot = Path.of(top.outputText().strip()).toAbsolutePath().normalize();

        GitResult listed = git(root, List.of("ls-files", "-z", "--full-name", "--", "."), 30);
        if (listed.exitCode() != 0) throw new IllegalStateException("GIT_LS_FILES_FAILED");
        byte[] output = listed.output();
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

    private static GitResult git(Path root, List<String> arguments, long timeoutSeconds) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(root.toString());
        command.addAll(arguments);
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        Map<String, String> environment = builder.environment();
        String path = environment.get("PATH");
        environment.clear();
        if (path != null) environment.put("PATH", path);
        environment.put("GIT_TERMINAL_PROMPT", "0");
        environment.put("GIT_CONFIG_NOSYSTEM", "1");
        Process process = builder.start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            try { process.getInputStream().transferTo(output); }
            catch (Exception ignored) {}
        }, "onsure-git-output");
        reader.setDaemon(true);
        reader.start();
        boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!completed) {
            process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            reader.join(5000);
            throw new IllegalStateException("GIT_SOURCE_COMMAND_TIMEOUT");
        }
        reader.join(5000);
        if (reader.isAlive()) {
            reader.interrupt();
            throw new IllegalStateException("GIT_SOURCE_OUTPUT_READ_TIMEOUT");
        }
        return new GitResult(process.exitValue(), output.toByteArray());
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

    private record GitResult(int exitCode, byte[] output) {
        private String outputText() { return new String(output, StandardCharsets.UTF_8); }
    }
}
