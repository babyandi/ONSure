package kr.co.oruda.onsure.assurance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

public final class LocalSourceLockVerifier {
    public static final String DIGEST_CONTRACT = "ONSURE_SOURCE_DIGEST_V1";
    private final ObjectMapper mapper = new ObjectMapper();

    public ValidationResult verify(Path sourceLock) {
        return verify(sourceLock, null);
    }

    public ValidationResult verifyAgainstRepository(Path sourceLock, Path repositoryRoot) {
        if (repositoryRoot == null) {
            return ValidationResult.fail(List.of("SOURCE_REPOSITORY_ROOT_MISSING"));
        }
        return verify(sourceLock, repositoryRoot);
    }

    public ValidationResult verify(Path sourceLock, Path repositoryRoot) {
        List<String> violations = new ArrayList<>();
        if (!Files.isRegularFile(sourceLock)) {
            return ValidationResult.fail(List.of("SOURCE_LOCK_MISSING"));
        }
        try {
            JsonNode node = mapper.readTree(sourceLock.toFile());
            String contract = node.path("digest_contract").asText();
            String commit = node.path("commit_sha").asText();
            String tree = node.path("tree_sha256").asText();
            String policy = node.path("policy_sha256").asText();
            boolean clean = node.path("worktree_clean").asBoolean(false);
            if (!DIGEST_CONTRACT.equals(contract)) violations.add("SOURCE_DIGEST_CONTRACT_MISMATCH");
            if (!commit.matches("[0-9a-f]{40}")) violations.add("INVALID_SOURCE_COMMIT");
            if (!tree.matches("[0-9a-f]{64}")) violations.add("INVALID_SOURCE_TREE_DIGEST");
            if (!policy.matches("[0-9a-f]{64}")) violations.add("INVALID_POLICY_SET_DIGEST");
            if (!clean) violations.add("DIRTY_SOURCE_WORKTREE");

            if (repositoryRoot != null && violations.isEmpty()) {
                Path root = repositoryRoot.toAbsolutePath().normalize();
                if (!Files.isDirectory(root.resolve(".git")) && !Files.isRegularFile(root.resolve(".git"))) {
                    violations.add("SOURCE_REPOSITORY_INVALID");
                } else {
                    if (!commit.equals(currentCommit(root))) violations.add("SOURCE_COMMIT_DRIFT");
                    if (!isTrackedWorktreeClean(root)) violations.add("SOURCE_WORKTREE_DRIFT");
                    if (!tree.equals(digestTrackedFiles(root))) violations.add("SOURCE_TREE_DRIFT");
                    if (!policy.equals(digestPolicyFiles(root))) violations.add("POLICY_SET_DRIFT");
                }
            }
        } catch (Exception e) {
            violations.add("SOURCE_LOCK_UNREADABLE");
        }
        return violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations);
    }

    public static String currentCommit(Path root) throws Exception {
        return run(root, "git", "rev-parse", "HEAD").trim();
    }

    public static boolean isTrackedWorktreeClean(Path root) throws Exception {
        return run(root, "git", "status", "--porcelain", "--untracked-files=no").isBlank();
    }

    public static String digestTrackedFiles(Path root) throws Exception {
        String files = run(root, "git", "ls-files");
        List<Path> paths = files.lines().filter(s -> !s.isBlank()).map(root::resolve)
                .sorted(Comparator.comparing(path -> canonicalRelative(root, path))).toList();
        return digestFileList(root, paths);
    }

    public static String digestPolicyFiles(Path root) throws Exception {
        List<Path> paths = new ArrayList<>();
        for (String relative : List.of("contracts", "fixtures/design", "docs/security", "findings")) {
            Path base = root.resolve(relative);
            if (Files.isDirectory(base)) {
                try (var stream = Files.walk(base)) {
                    stream.filter(Files::isRegularFile).forEach(paths::add);
                }
            }
        }
        paths.sort(Comparator.comparing(path -> canonicalRelative(root, path)));
        return digestFileList(root, paths);
    }

    static String digestFileList(Path root, List<Path> paths) throws Exception {
        MessageDigest aggregate = MessageDigest.getInstance("SHA-256");
        for (Path path : paths) {
            String relative = canonicalRelative(root, path);
            byte[] fileHash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
            aggregate.update(relative.getBytes(StandardCharsets.UTF_8));
            aggregate.update((byte) 0);
            aggregate.update(fileHash);
            aggregate.update((byte) 0);
        }
        return HexFormat.of().formatHex(aggregate.digest());
    }

    private static String canonicalRelative(Path root, Path path) {
        return root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize())
                .toString().replace('\\', '/');
    }

    private static String run(Path root, String... command) throws Exception {
        Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        process.getInputStream().transferTo(output);
        int exit = process.waitFor();
        if (exit != 0) throw new IllegalStateException("command failed: " + String.join(" ", command));
        return output.toString(StandardCharsets.UTF_8);
    }
}
