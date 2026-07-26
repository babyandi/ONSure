package io.onsure.assurance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class LocalSourceLockVerifier {
    public static final String DIGEST_CONTRACT = "ONSURE_SOURCE_DIGEST_V1";
    public static final String SOURCE_SCOPE = "GIT_TRACKED_FILES_ONLY";
    public static final String POLICY_SCOPE = "GIT_TRACKED_POLICY_FILES_ONLY";
    private static final Duration GIT_TIMEOUT = Duration.ofSeconds(30);
    private static final Set<String> POLICY_PREFIXES = Set.of(
            "contracts/", "fixtures/design/", "docs/security/", "findings/");
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
            if (!DIGEST_CONTRACT.equals(contract)) {
                violations.add("SOURCE_DIGEST_CONTRACT_MISMATCH");
            }
            if (!commit.matches("[0-9a-f]{40}|[0-9a-f]{64}")) {
                violations.add("INVALID_SOURCE_COMMIT");
            }
            if (!tree.matches("[0-9a-f]{64}")) violations.add("INVALID_SOURCE_TREE_DIGEST");
            if (!policy.matches("[0-9a-f]{64}")) violations.add("INVALID_POLICY_SET_DIGEST");
            if (!SOURCE_SCOPE.equals(node.path("source_scope").asText())) {
                violations.add("SOURCE_SCOPE_NOT_TRACKED_ONLY");
            }
            if (!POLICY_SCOPE.equals(node.path("policy_scope").asText())) {
                violations.add("POLICY_SCOPE_NOT_TRACKED_ONLY");
            }
            if (!node.path("untracked_files_blocked").asBoolean(false)) {
                violations.add("UNTRACKED_FILES_NOT_BLOCKED");
            }
            if (!clean) violations.add("DIRTY_SOURCE_WORKTREE");

            if (repositoryRoot != null && violations.isEmpty()) {
                Path root = repositoryRoot.toAbsolutePath().normalize();
                if (!isGitRepository(root)) {
                    violations.add("SOURCE_REPOSITORY_INVALID");
                } else {
                    if (!commit.equals(currentCommit(root))) violations.add("SOURCE_COMMIT_DRIFT");
                    if (!isTrackedWorktreeClean(root)) {
                        violations.add("SOURCE_WORKTREE_DIRTY_OR_UNTRACKED");
                    }
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
        return runText(root, "rev-parse", "HEAD").trim();
    }

    public static boolean isTrackedWorktreeClean(Path root) throws Exception {
        return runText(root, "status", "--porcelain", "--untracked-files=all").isBlank();
    }

    public static String digestTrackedFiles(Path root) throws Exception {
        List<Path> paths = trackedFiles(root);
        if (paths.isEmpty()) throw new IllegalStateException("TRACKED_SOURCE_SET_EMPTY");
        return digestFileList(root, paths);
    }

    public static String digestPolicyFiles(Path root) throws Exception {
        List<Path> paths = trackedFiles(root).stream()
                .filter(path -> {
                    String relative = canonicalRelative(root, path);
                    return POLICY_PREFIXES.stream().anyMatch(relative::startsWith);
                })
                .toList();
        if (paths.isEmpty()) throw new IllegalStateException("TRACKED_POLICY_SET_EMPTY");
        return digestFileList(root, paths);
    }

    static String digestFileList(Path root, List<Path> paths) throws Exception {
        MessageDigest aggregate = MessageDigest.getInstance("SHA-256");
        for (Path path : paths) {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(path)) {
                throw new IllegalStateException(
                        "TRACKED_FILE_INVALID:" + canonicalRelative(root, path));
            }
            String relative = canonicalRelative(root, path);
            byte[] fileHash = MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(path));
            aggregate.update(relative.getBytes(StandardCharsets.UTF_8));
            aggregate.update((byte) 0);
            aggregate.update(fileHash);
            aggregate.update((byte) 0);
        }
        return HexFormat.of().formatHex(aggregate.digest());
    }

    private static List<Path> trackedFiles(Path root) throws Exception {
        byte[] output = runBytes(root, "ls-files", "-z");
        List<Path> paths = new ArrayList<>();
        int start = 0;
        for (int index = 0; index <= output.length; index++) {
            if (index == output.length || output[index] == 0) {
                if (index > start) {
                    String relative = new String(
                            Arrays.copyOfRange(output, start, index), StandardCharsets.UTF_8);
                    Path path = root.resolve(relative).toAbsolutePath().normalize();
                    if (!path.startsWith(root.toAbsolutePath().normalize())) {
                        throw new IllegalStateException("TRACKED_PATH_ESCAPE:" + relative);
                    }
                    paths.add(path);
                }
                start = index + 1;
            }
        }
        paths.sort(Comparator.comparing(path -> canonicalRelative(root, path)));
        return List.copyOf(paths);
    }

    private static boolean isGitRepository(Path root) throws Exception {
        GitResult result = runAllowFailure(root, "rev-parse", "--is-inside-work-tree");
        return result.exitCode() == 0 && result.text().strip().equals("true");
    }

    private static String canonicalRelative(Path root, Path path) {
        return root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize())
                .toString().replace('\\', '/');
    }

    private static String runText(Path root, String... arguments) throws Exception {
        return run(root, arguments).text();
    }

    private static byte[] runBytes(Path root, String... arguments) throws Exception {
        return run(root, arguments).bytes();
    }

    private static GitResult run(Path root, String... arguments) throws Exception {
        GitResult result = runAllowFailure(root, arguments);
        if (result.exitCode() != 0) {
            throw new IllegalStateException("git command failed: "
                    + String.join(" ", arguments) + ":" + result.text().strip());
        }
        return result;
    }

    private static GitResult runAllowFailure(Path root, String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(Arrays.asList(arguments));
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(root.toFile()).redirectErrorStream(true);
        Map<String, String> environment = builder.environment();
        String path = environment.get("PATH");
        environment.clear();
        if (path != null) environment.put("PATH", path);
        Process process = builder.start();
        boolean complete = process.waitFor(GIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!complete) {
            process.destroyForcibly();
            throw new IllegalStateException("git command timed out");
        }
        byte[] output = process.getInputStream().readAllBytes();
        return new GitResult(process.exitValue(), output,
                new String(output, StandardCharsets.UTF_8));
    }

    private record GitResult(int exitCode, byte[] bytes, String text) {}
}
