package io.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Applies only fully approved patches in an isolated Git worktree and records the delivery chain. */
public final class WorktreeGitEngine {
    public static final String CONTRACT = "ONSURE_GIT_CHANGE_SET_V1";
    private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(5);
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public Map<String, Object> prepareAndApply(
            Path repositoryRoot,
            Path approvedPatchPlan,
            Path patchFile,
            String branch,
            Path worktree,
            Path output) throws Exception {
        Path repository = repositoryRoot.toAbsolutePath().normalize();
        requireRepository(repository);
        requireBranch(branch);
        JsonNode plan = mapper.readTree(approvedPatchPlan.toFile());
        requireApprovedPlan(plan);
        Path patch = patchFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(patch)) throw new IllegalArgumentException("PATCH_FILE_MISSING");
        String patchDigest = Hashing.file(patch);
        if (!patchDigest.equals(plan.path("patch_sha256").asText())) {
            throw new IllegalArgumentException("PATCH_DIGEST_MISMATCH");
        }
        String sourceRef = plan.path("source_ref").asText();
        String head = git(repository, "rev-parse", "HEAD").strip();
        if (!sourceRef.equals(head)) throw new IllegalStateException("GIT_SOURCE_HEAD_MISMATCH");
        if (!git(repository, "status", "--porcelain", "--untracked-files=all").isBlank()) {
            throw new IllegalStateException("GIT_SOURCE_WORKTREE_DIRTY_OR_UNTRACKED");
        }
        Path target = worktree.toAbsolutePath().normalize();
        if (target.startsWith(repository)) {
            throw new IllegalArgumentException("WORKTREE_MUST_BE_OUTSIDE_SOURCE_REPOSITORY");
        }
        if (Files.exists(target)) throw new IllegalArgumentException("WORKTREE_PATH_EXISTS");
        Files.createDirectories(target.getParent());

        git(repository, "show-ref", "--verify", "--quiet", "refs/heads/" + branch, true);
        CommandResult add = run(repository, List.of(
                "git", "worktree", "add", "-b", branch, target.toString(), sourceRef), false);
        if (add.exitCode() != 0) throw commandFailure("GIT_WORKTREE_ADD_FAILED", add);
        boolean success = false;
        try {
            CommandResult check = run(target,
                    List.of("git", "apply", "--check", "--whitespace=error-all", patch.toString()), false);
            if (check.exitCode() != 0) throw commandFailure("PATCH_CHECK_FAILED", check);
            CommandResult apply = run(target,
                    List.of("git", "apply", "--whitespace=error-all", patch.toString()), false);
            if (apply.exitCode() != 0) throw commandFailure("PATCH_APPLY_FAILED", apply);
            String changed = git(target, "status", "--porcelain", "--untracked-files=all");
            if (changed.isBlank()) throw new IllegalStateException("PATCH_APPLIED_NO_CHANGE");

            String changeSetId = "CHANGESET-" + Hashing.sha256(
                    sourceRef + "|" + branch + "|" + patchDigest).substring(0, 20);
            Map<String, Object> value = base(
                    changeSetId, repository, sourceRef, branch, target,
                    plan.path("patch_plan_id").asText(), patchDigest,
                    plan.path("approval").path("approval_receipt_sha256").asText(),
                    plan.path("rollback_ref").asText());
            value.put("state", "CHANGES_APPLIED");
            value.put("worktree_status_sha256", Hashing.sha256(changed));
            value.put("patch_check_stdout_sha256", Hashing.sha256(check.output()));
            value.put("patch_apply_stdout_sha256", Hashing.sha256(apply.output()));
            write(output, value);
            success = true;
            return Map.copyOf(value);
        } finally {
            if (!success) cleanupFailedWorktree(repository, target, branch);
        }
    }

    public Map<String, Object> recordLocalVerification(
            Path changeSetFile,
            List<String> testReceiptIds,
            Path output) throws Exception {
        JsonNode root = requireChangeSet(changeSetFile, "CHANGES_APPLIED");
        if (testReceiptIds == null || testReceiptIds.size() < 2
                || testReceiptIds.stream().distinct().count() != testReceiptIds.size()) {
            throw new IllegalArgumentException("TWO_DISTINCT_TEST_RECEIPTS_REQUIRED");
        }
        Map<String, Object> value = mapper.convertValue(root, LinkedHashMap.class);
        value.put("test_receipt_ids", List.copyOf(testReceiptIds));
        value.put("state", "LOCAL_VERIFIED");
        value.put("local_verification_receipt_sha256", Hashing.sha256(
                root.path("change_set_id").asText() + "|" + testReceiptIds));
        write(output, value);
        return Map.copyOf(value);
    }

    public Map<String, Object> commit(
            Path changeSetFile,
            String commitMessage,
            String authorName,
            String authorEmail,
            Path output) throws Exception {
        JsonNode root = requireChangeSet(changeSetFile, "LOCAL_VERIFIED");
        requireText(commitMessage, "COMMIT_MESSAGE_MISSING");
        requireText(authorName, "COMMIT_AUTHOR_NAME_MISSING");
        if (authorEmail == null || !authorEmail.matches("[^@\\s]+@[^@\\s]+")) {
            throw new IllegalArgumentException("COMMIT_AUTHOR_EMAIL_INVALID");
        }
        Path worktree = Path.of(root.path("worktree").asText()).toAbsolutePath().normalize();
        git(worktree, "add", "--all");
        CommandResult staged = run(worktree, List.of("git", "diff", "--cached", "--quiet"), true);
        if (staged.exitCode() == 0) throw new IllegalStateException("NO_STAGED_CHANGE_TO_COMMIT");
        if (staged.exitCode() != 1) throw commandFailure("GIT_STAGED_DIFF_CHECK_FAILED", staged);
        Map<String, String> extraEnvironment = Map.of(
                "GIT_AUTHOR_NAME", authorName,
                "GIT_AUTHOR_EMAIL", authorEmail,
                "GIT_COMMITTER_NAME", authorName,
                "GIT_COMMITTER_EMAIL", authorEmail);
        CommandResult committed = run(worktree,
                List.of("git", "commit", "--no-gpg-sign", "-m", commitMessage),
                false, extraEnvironment);
        if (committed.exitCode() != 0) throw commandFailure("GIT_COMMIT_FAILED", committed);
        String commit = git(worktree, "rev-parse", "HEAD").strip();
        requireGitRef(commit);

        Map<String, Object> value = mapper.convertValue(root, LinkedHashMap.class);
        value.put("commit_sha", commit);
        value.put("commit_receipt_sha256", Hashing.sha256(
                commit + "|" + committed.output() + "|" + root.path("patch_sha256").asText()));
        value.put("state", "COMMITTED");
        write(output, value);
        return Map.copyOf(value);
    }

    public Map<String, Object> push(
            Path changeSetFile, String remote, Path output) throws Exception {
        JsonNode root = requireChangeSet(changeSetFile, "COMMITTED");
        if (remote == null || !remote.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("GIT_REMOTE_INVALID");
        }
        Path worktree = Path.of(root.path("worktree").asText()).toAbsolutePath().normalize();
        String branch = root.path("branch").asText();
        CommandResult pushed = run(worktree,
                List.of("git", "push", "--set-upstream", remote, "HEAD:refs/heads/" + branch), false);
        if (pushed.exitCode() != 0) throw commandFailure("GIT_PUSH_FAILED", pushed);
        Map<String, Object> value = mapper.convertValue(root, LinkedHashMap.class);
        value.put("push_state", "PUSHED");
        value.put("push_receipt_sha256", Hashing.sha256(
                remote + "|" + branch + "|" + root.path("commit_sha").asText()
                        + "|" + pushed.output()));
        value.put("state", "PUSHED");
        write(output, value);
        return Map.copyOf(value);
    }

    public Map<String, Object> openDraftPullRequest(
            Path changeSetFile,
            String baseBranch,
            String title,
            String body,
            Path output) throws Exception {
        JsonNode root = requireChangeSet(changeSetFile, "PUSHED");
        requireBranch(baseBranch);
        requireText(title, "DRAFT_PR_TITLE_MISSING");
        requireText(body, "DRAFT_PR_BODY_MISSING");
        Path worktree = Path.of(root.path("worktree").asText()).toAbsolutePath().normalize();
        CommandResult pr = run(worktree, List.of(
                "gh", "pr", "create", "--draft", "--base", baseBranch,
                "--head", root.path("branch").asText(), "--title", title, "--body", body), false);
        if (pr.exitCode() != 0) throw commandFailure("DRAFT_PR_CREATE_FAILED", pr);
        String url = pr.output().lines().filter(line -> line.startsWith("http"))
                .reduce((first, second) -> second).orElseThrow(
                        () -> new IllegalStateException("DRAFT_PR_URL_MISSING"));
        String receipt = Hashing.sha256(url + "|" + root.path("commit_sha").asText());
        Map<String, Object> value = mapper.convertValue(root, LinkedHashMap.class);
        value.put("draft_pr", Map.of(
                "state", "DRAFT_PR_OPEN",
                "provider", "GITHUB",
                "url", url,
                "receipt_sha256", receipt));
        value.put("state", "DRAFT_PR_OPEN");
        write(output, value);
        return Map.copyOf(value);
    }

    public Map<String, Object> rollback(Path changeSetFile, Path output) throws Exception {
        JsonNode root = mapper.readTree(changeSetFile.toFile());
        if (!CONTRACT.equals(root.path("contract").asText())) {
            throw new IllegalArgumentException("GIT_CHANGE_SET_CONTRACT_INVALID");
        }
        if ("PUSHED".equals(root.path("push_state").asText())
                || "DRAFT_PR_OPEN".equals(root.path("state").asText())) {
            throw new IllegalStateException("PUSHED_CHANGE_REQUIRES_REMOTE_REVERT_NOT_LOCAL_DELETE");
        }
        Path repository = Path.of(root.path("repository").asText()).toAbsolutePath().normalize();
        Path worktree = Path.of(root.path("worktree").asText()).toAbsolutePath().normalize();
        String branch = root.path("branch").asText();
        cleanupFailedWorktree(repository, worktree, branch);
        Map<String, Object> value = mapper.convertValue(root, LinkedHashMap.class);
        value.put("state", "ROLLED_BACK");
        value.put("rollback_receipt_sha256", Hashing.sha256(
                root.path("rollback_ref").asText() + "|" + branch + "|" + Instant.now()));
        write(output, value);
        return Map.copyOf(value);
    }

    private Map<String, Object> base(
            String id, Path repository, String sourceCommit, String branch, Path worktree,
            String patchPlanId, String patchDigest, String approvalReceipt, String rollbackRef) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("contract", CONTRACT);
        value.put("change_set_id", id);
        value.put("repository", repository.toString());
        value.put("source_commit", sourceCommit);
        value.put("branch", branch);
        value.put("worktree", worktree.toString());
        value.put("patch_plan_id", patchPlanId);
        value.put("patch_sha256", patchDigest);
        value.put("approval_receipt_sha256", approvalReceipt);
        value.put("rollback_ref", rollbackRef);
        value.put("test_receipt_ids", List.of());
        value.put("commit_sha", null);
        value.put("push_state", "NOT_RUN");
        value.put("draft_pr", Map.of(
                "state", "NOT_RUN", "provider", "NONE", "url", null,
                "receipt_sha256", null));
        value.put("state", "WORKTREE_READY");
        value.put("created_at", Instant.now().toString());
        value.put("merge_allowed", false);
        value.put("final_claim_allowed", false);
        return value;
    }

    private JsonNode requireChangeSet(Path file, String requiredState) throws Exception {
        JsonNode root = mapper.readTree(file.toFile());
        if (!CONTRACT.equals(root.path("contract").asText())) {
            throw new IllegalArgumentException("GIT_CHANGE_SET_CONTRACT_INVALID");
        }
        if (!requiredState.equals(root.path("state").asText())) {
            throw new IllegalStateException("GIT_CHANGE_SET_STATE_REQUIRED:" + requiredState);
        }
        return root;
    }

    private static void requireApprovedPlan(JsonNode plan) {
        if (!"ONSURE_PATCH_PLAN_V1".equals(plan.path("contract").asText())
                || !"PATCH_APPROVED".equals(plan.path("state").asText())
                || !"APPROVED".equals(plan.path("approval").path("state").asText())) {
            throw new IllegalArgumentException("FULL_PATCH_APPROVAL_REQUIRED_FOR_AUTO_APPLY");
        }
        int hunks = plan.path("hunks").size();
        int approved = plan.path("approval").path("approved_hunk_ids").size();
        if (hunks <= 0 || approved != hunks) {
            throw new IllegalArgumentException("ALL_HUNKS_MUST_BE_APPROVED_FOR_AUTO_APPLY");
        }
        String receipt = plan.path("approval").path("approval_receipt_sha256").asText();
        if (!receipt.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("PATCH_APPROVAL_RECEIPT_INVALID");
        }
    }

    private static void cleanupFailedWorktree(Path repository, Path worktree, String branch) {
        try { run(repository, List.of("git", "worktree", "remove", "--force", worktree.toString()), true); }
        catch (Exception ignored) {}
        try { run(repository, List.of("git", "branch", "-D", branch), true); }
        catch (Exception ignored) {}
    }

    private static void requireRepository(Path repository) throws Exception {
        if (!Files.isDirectory(repository)) throw new IllegalArgumentException("GIT_REPOSITORY_MISSING");
        CommandResult result = run(repository, List.of("git", "rev-parse", "--is-inside-work-tree"), false);
        if (result.exitCode() != 0 || !result.output().strip().equals("true")) {
            throw new IllegalArgumentException("GIT_REPOSITORY_INVALID");
        }
    }

    private static void requireBranch(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._/-]{2,127}")
                || value.contains("..") || value.endsWith("/") || value.contains("//")) {
            throw new IllegalArgumentException("GIT_BRANCH_INVALID");
        }
    }

    private static void requireGitRef(String value) {
        if (value == null || !value.matches("[0-9a-f]{40}|[0-9a-f]{64}")) {
            throw new IllegalArgumentException("GIT_OBJECT_ID_INVALID");
        }
    }

    private static void requireText(String value, String code) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(code);
    }

    private static String git(Path root, String... arguments) throws Exception {
        CommandResult result = run(root, prepend("git", arguments), false);
        if (result.exitCode() != 0) throw commandFailure("GIT_COMMAND_FAILED", result);
        return result.output();
    }

    private static String git(Path root, String a, String b, String c, boolean allowFailure)
            throws Exception {
        CommandResult result = run(root, List.of("git", a, b, c), allowFailure);
        if (!allowFailure && result.exitCode() != 0) {
            throw commandFailure("GIT_COMMAND_FAILED", result);
        }
        return result.output();
    }

    private static List<String> prepend(String executable, String... arguments) {
        List<String> values = new ArrayList<>();
        values.add(executable);
        values.addAll(List.of(arguments));
        return values;
    }

    private static CommandResult run(Path root, List<String> command, boolean allowFailure)
            throws Exception {
        return run(root, command, allowFailure, Map.of());
    }

    private static CommandResult run(
            Path root, List<String> command, boolean allowFailure,
            Map<String, String> extraEnvironment) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(root.toFile()).redirectErrorStream(true);
        Map<String, String> environment = builder.environment();
        String path = environment.get("PATH");
        environment.clear();
        if (path != null) environment.put("PATH", path);
        environment.put("GIT_TERMINAL_PROMPT", "0");
        environment.putAll(extraEnvironment);
        Process process = builder.start();
        boolean finished = process.waitFor(COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("GIT_COMMAND_TIMEOUT:" + String.join(" ", command));
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!allowFailure && process.exitValue() != 0) {
            throw commandFailure("COMMAND_FAILED", new CommandResult(process.exitValue(), output, command));
        }
        return new CommandResult(process.exitValue(), output, command);
    }

    private static IllegalStateException commandFailure(String code, CommandResult result) {
        return new IllegalStateException(code + ":exit=" + result.exitCode()
                + ":command=" + String.join(" ", result.command())
                + ":output=" + result.output().strip());
    }

    private void write(Path output, Object value) throws Exception {
        Path normalized = output.toAbsolutePath().normalize();
        Files.createDirectories(normalized.getParent());
        Path temporary = normalized.resolveSibling(normalized.getFileName() + ".tmp");
        mapper.writeValue(temporary.toFile(), value);
        try {
            Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private record CommandResult(int exitCode, String output, List<String> command) {
        CommandResult { command = List.copyOf(command); }
    }
}
