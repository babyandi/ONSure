package io.onsure.platform;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Verifies that a validation run is bound to the exact source bytes it claims. */
public final class SourceReferenceBinding {
    public record Verification(String mode, String reference, String treeDigest) {}

    private SourceReferenceBinding() {}

    public static String treeReference(Path sourceRoot) throws Exception {
        return "sha256:" + Hashing.tree(sourceRoot.toAbsolutePath().normalize());
    }

    public static Verification verify(Path sourceRoot, String reference, String treeDigest)
            throws Exception {
        Path root = sourceRoot.toAbsolutePath().normalize();
        if (reference.startsWith("sha256:")) {
            String declared = reference.substring("sha256:".length());
            if (!declared.matches("[0-9a-f]{64}") || !declared.equals(treeDigest)) {
                throw new IllegalStateException("IMMUTABLE_SOURCE_TREE_DIGEST_MISMATCH");
            }
            return new Verification("SHA256_TREE", reference, treeDigest);
        }
        if (reference.startsWith("git:")) {
            String commit = reference.substring("git:".length());
            if (!commit.matches("[0-9a-f]{40}")) {
                throw new IllegalStateException("IMMUTABLE_GIT_REFERENCE_INVALID");
            }
            String head = runGit(root, List.of("rev-parse", "HEAD")).strip();
            if (!commit.equals(head)) {
                throw new IllegalStateException("IMMUTABLE_GIT_HEAD_MISMATCH");
            }
            String dirty = runGit(root, List.of("status", "--porcelain", "--untracked-files=no"));
            if (!dirty.isBlank()) {
                throw new IllegalStateException("IMMUTABLE_GIT_WORKTREE_DIRTY");
            }
            return new Verification("GIT_COMMIT", reference, treeDigest);
        }
        throw new IllegalStateException("IMMUTABLE_SOURCE_REFERENCE_UNVERIFIED");
    }

    private static String runGit(Path root, List<String> arguments) throws Exception {
        List<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(root.toString());
        command.addAll(arguments);
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        Map<String, String> environment = builder.environment();
        String path = environment.get("PATH");
        environment.clear();
        if (path != null) environment.put("PATH", path);
        Process process = builder.start();
        boolean completed = process.waitFor(15, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            throw new IllegalStateException("IMMUTABLE_GIT_COMMAND_TIMEOUT");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) {
            throw new IllegalStateException("IMMUTABLE_GIT_REFERENCE_UNVERIFIED:" + output.strip());
        }
        return output;
    }
}
