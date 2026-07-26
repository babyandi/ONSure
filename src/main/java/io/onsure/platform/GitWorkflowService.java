package io.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.onsure.assurance.ApprovalReceiptVerifier;
import io.onsure.assurance.Decision;
import io.onsure.assurance.ValidationResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Performs bounded Git delivery. It never merges and never force-pushes. */
public final class GitWorkflowService {
    public static final String DELIVERY_APPROVAL_CONTRACT = "ONSURE_GIT_DELIVERY_APPROVAL_V1";
    public static final String CHANGE_SET_CONTRACT = "ONSURE_GIT_CHANGE_SET_V1";
    public static final String DRAFT_PR_RECEIPT_CONTRACT = "ONSURE_DRAFT_PR_RECEIPT_V1";
    public static final String APPROVAL_PURPOSE = "GIT_DELIVERY";
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    /** Unsafe legacy entry point is intentionally disabled. */
    @Deprecated
    public Map<String, Object> commitApprovedWorktree(
            Path worktreeRoot,
            Path patchApplyReceiptFile,
            Path deliveryApprovalFile,
            String commitMessage,
            Path outputFile) {
        throw new IllegalStateException("APPROVAL_TRUST_AND_IMPROVEMENT_PROOF_REQUIRED");
    }

    public Map<String, Object> commitApprovedWorktree(
            Path worktreeRoot,
            Path patchApplyReceiptFile,
            Path improvementProofFile,
            Path deliveryApprovalFile,
            Path approvalKeyRegistry,
            Path approvalReplayLedger,
            String commitMessage,
            Path outputFile) throws Exception {
        Path worktree = worktreeRoot.toAbsolutePath().normalize();
        JsonNode patch = readContract(
                patchApplyReceiptFile, ImprovementWorkflowService.APPLY_RECEIPT_CONTRACT, "PATCH_APPLY");
        JsonNode proof = readContract(
                improvementProofFile, ImprovementProofService.CONTRACT, "IMPROVEMENT_PROOF");
        JsonNode approval = readContract(
                deliveryApprovalFile, DELIVERY_APPROVAL_CONTRACT, "GIT_DELIVERY_APPROVAL");
        String patchDigest = sha256(Files.readAllBytes(patchApplyReceiptFile));
        if (!patchDigest.equals(proof.path("patch_apply_receipt_sha256").asText())) {
            throw new IllegalStateException("IMPROVEMENT_PROOF_PATCH_RECEIPT_MISMATCH");
        }
        if (!patchDigest.equals(approval.path("patch_apply_receipt_sha256").asText())) {
            throw new IllegalStateException("GIT_APPROVAL_PATCH_RECEIPT_MISMATCH");
        }
        if (!"IMPROVEMENT_PROVEN".equals(proof.path("decision").asText())
                || !proof.path("commit_allowed").asBoolean(false)
                || !"PASS".equals(proof.path("focused_fixture_validation").asText())
                || !"PASS".equals(proof.path("full_regression").asText())) {
            throw new IllegalStateException("IMPROVEMENT_NOT_PROVEN_FOR_COMMIT");
        }
        if (!approval.path("allow_commit").asBoolean(false)) {
            throw new IllegalStateException("GIT_COMMIT_NOT_APPROVED");
        }
        requireSafeApprovalPermissions(approval);
        String branch = git(worktree, List.of("branch", "--show-current"), 20).strip();
        if (!branch.equals(patch.path("branch").asText())) {
            throw new IllegalStateException("GIT_BRANCH_PATCH_RECEIPT_MISMATCH");
        }
        if (!branch.equals(approval.path("branch").asText())) {
            throw new IllegalStateException("GIT_BRANCH_APPROVAL_MISMATCH");
        }
        if (isProtectedBranch(branch)) throw new IllegalStateException("PROTECTED_BRANCH_COMMIT_PROHIBITED");
        if (commitMessage == null || commitMessage.isBlank() || commitMessage.length() > 200
                || commitMessage.indexOf('\n') >= 0 || commitMessage.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("COMMIT_MESSAGE_INVALID");
        }
        String status = git(worktree, List.of("status", "--porcelain", "--untracked-files=all"), 20);
        if (status.isBlank()) throw new IllegalStateException("NO_CHANGES_TO_COMMIT");
        if (status.lines().anyMatch(line -> line.startsWith("??"))) {
            throw new IllegalStateException("UNTRACKED_FILE_COMMIT_PROHIBITED");
        }
        Set<String> actualFiles = new LinkedHashSet<>(git(
                worktree, List.of("diff", "--name-only"), 20).lines().filter(value -> !value.isBlank()).toList());
        Set<String> approvedFiles = new LinkedHashSet<>();
        patch.path("applied_hunks").forEach(value -> approvedFiles.add(value.path("relative_path").asText()));
        if (!actualFiles.equals(approvedFiles)) {
            throw new IllegalStateException("GIT_CHANGED_FILE_SET_NOT_APPROVED");
        }
        git(worktree, List.of("diff", "--check"), 20);

        ApprovalReceiptVerifier verifier = new ApprovalReceiptVerifier(
                approvalKeyRegistry, approvalReplayLedger);
        ValidationResult approvalValidation = verifier.verify(
                deliveryApprovalFile, DELIVERY_APPROVAL_CONTRACT, APPROVAL_PURPOSE, Instant.now());
        if (approvalValidation.decision() != Decision.PASS) {
            throw new IllegalStateException(
                    "GIT_DELIVERY_APPROVAL_INVALID:" + String.join(",", approvalValidation.violations()));
        }
        verifier.requireValidAndConsume(
                deliveryApprovalFile, DELIVERY_APPROVAL_CONTRACT, APPROVAL_PURPOSE, Instant.now());

        git(worktree, List.of("add", "--update"), 20);
        git(worktree, List.of("commit", "-m", commitMessage), 60);
        String commit = git(worktree, List.of("rev-parse", "HEAD"), 20).strip();
        String tree = git(worktree, List.of("rev-parse", "HEAD^{tree}"), 20).strip();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contract", CHANGE_SET_CONTRACT);
        result.put("change_set_id", "CHANGESET-" + commit.substring(0, 16));
        result.put("patch_apply_receipt_sha256", patchDigest);
        result.put("improvement_proof_sha256", sha256(Files.readAllBytes(improvementProofFile)));
        result.put("delivery_approval_sha256", sha256(Files.readAllBytes(deliveryApprovalFile)));
        result.put("approval_id", approval.path("approval_id").asText());
        result.put("approval_actor", approval.path("actor").asText());
        result.put("approval_key_id", approval.path("key_id").asText());
        result.put("branch", branch);
        result.put("commit_sha", commit);
        result.put("tree_sha", tree);
        result.put("changed_files", List.copyOf(actualFiles));
        result.put("commit_message", commitMessage);
        result.put("push_state", "NOT_RUN");
        result.put("draft_pr_state", "NOT_RUN");
        result.put("merge_state", "PROHIBITED");
        result.put("created_at", Instant.now().toString());
        result.put("final_claim_allowed", false);
        result.put("change_set_sha256", sha256(mapper.writeValueAsBytes(result)));
        writeAtomic(outputFile, result);
        return Map.copyOf(result);
    }

    public Map<String, Object> pushAndOpenDraftPr(
            Path worktreeRoot,
            Path changeSetFile,
            Path deliveryApprovalFile,
            String baseBranch,
            String title,
            Path bodyFile,
            Path outputFile) throws Exception {
        Path worktree = worktreeRoot.toAbsolutePath().normalize();
        JsonNode changeSet = readContract(changeSetFile, CHANGE_SET_CONTRACT, "GIT_CHANGE_SET");
        JsonNode approval = readContract(
                deliveryApprovalFile, DELIVERY_APPROVAL_CONTRACT, "GIT_DELIVERY_APPROVAL");
        String approvalDigest = sha256(Files.readAllBytes(deliveryApprovalFile));
        if (!approvalDigest.equals(changeSet.path("delivery_approval_sha256").asText())) {
            throw new IllegalStateException("GIT_CHANGE_SET_APPROVAL_DIGEST_MISMATCH");
        }
        if (!approval.path("allow_push").asBoolean(false)
                || !approval.path("allow_draft_pr").asBoolean(false)) {
            throw new IllegalStateException("PUSH_OR_DRAFT_PR_NOT_APPROVED");
        }
        requireSafeApprovalPermissions(approval);
        String branch = changeSet.path("branch").asText();
        if (!branch.equals(approval.path("branch").asText())) {
            throw new IllegalStateException("GIT_BRANCH_APPROVAL_MISMATCH");
        }
        requireBranch(baseBranch);
        if (!baseBranch.equals(approval.path("base_branch").asText())) {
            throw new IllegalStateException("GIT_BASE_BRANCH_APPROVAL_MISMATCH");
        }
        if (!git(worktree, List.of("status", "--porcelain", "--untracked-files=all"), 20).isBlank()) {
            throw new IllegalStateException("WORKTREE_NOT_CLEAN_AFTER_COMMIT");
        }
        String head = git(worktree, List.of("rev-parse", "HEAD"), 20).strip();
        if (!head.equals(changeSet.path("commit_sha").asText())) {
            throw new IllegalStateException("GIT_CHANGE_SET_HEAD_DRIFT");
        }
        String remote = approval.path("remote").asText();
        if (!remote.matches("[A-Za-z0-9._-]{1,64}")) throw new IllegalArgumentException("REMOTE_INVALID");
        String remoteUrl = git(worktree, List.of("remote", "get-url", remote), 20).strip();
        String approvedRemoteDigest = approval.path("remote_url_sha256").asText();
        if (!sha256(remoteUrl.getBytes(StandardCharsets.UTF_8)).equals(approvedRemoteDigest)) {
            throw new IllegalStateException("REMOTE_URL_APPROVAL_MISMATCH");
        }
        if (title == null || title.isBlank() || title.length() > 250
                || title.indexOf('\n') >= 0 || title.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("PR_TITLE_INVALID");
        }
        if (!Files.isRegularFile(bodyFile, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(bodyFile)) {
            throw new IllegalArgumentException("PR_BODY_FILE_INVALID");
        }
        if (Files.size(bodyFile) > 1_048_576L) throw new IllegalArgumentException("PR_BODY_TOO_LARGE");
        git(worktree, List.of("push", "--set-upstream", remote, branch), 180);
        String prUrl = gh(worktree, List.of(
                "pr", "create", "--draft", "--base", baseBranch, "--head", branch,
                "--title", title, "--body-file", bodyFile.toAbsolutePath().normalize().toString()), 180).strip();
        if (!prUrl.startsWith("https://")) throw new IllegalStateException("DRAFT_PR_URL_INVALID");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contract", DRAFT_PR_RECEIPT_CONTRACT);
        result.put("change_set_sha256", sha256(Files.readAllBytes(changeSetFile)));
        result.put("delivery_approval_sha256", approvalDigest);
        result.put("approval_actor", approval.path("actor").asText());
        result.put("approval_key_id", approval.path("key_id").asText());
        result.put("branch", branch);
        result.put("commit_sha", head);
        result.put("remote", remote);
        result.put("remote_url_sha256", approvedRemoteDigest);
        result.put("base_branch", baseBranch);
        result.put("draft_pr_url", prUrl);
        result.put("draft", true);
        result.put("merge_authorized", false);
        result.put("created_at", Instant.now().toString());
        result.put("final_claim_allowed", false);
        result.put("receipt_sha256", sha256(mapper.writeValueAsBytes(result)));
        writeAtomic(outputFile, result);
        return Map.copyOf(result);
    }

    private static void requireSafeApprovalPermissions(JsonNode approval) {
        if (approval.path("allow_force_push").asBoolean(true)
                || approval.path("allow_merge").asBoolean(true)) {
            throw new IllegalStateException("UNSAFE_GIT_PERMISSION_REQUESTED");
        }
    }

    private JsonNode readContract(Path file, String contract, String label) throws Exception {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
            throw new IllegalArgumentException(label + "_FILE_INVALID");
        }
        JsonNode node = mapper.readTree(file.toFile());
        if (!contract.equals(node.path("contract").asText())) {
            throw new IllegalArgumentException(label + "_CONTRACT_MISMATCH");
        }
        return node;
    }

    private static String requireBranch(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._/-]{0,120}")
                || value.contains("..") || value.endsWith("/") || value.startsWith("-")) {
            throw new IllegalArgumentException("BRANCH_NAME_INVALID");
        }
        return value;
    }

    private static boolean isProtectedBranch(String branch) {
        return List.of("main", "master", "production", "release").contains(branch.toLowerCase());
    }

    private static String git(Path root, List<String> arguments, long timeout) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git"); command.add("-C"); command.add(root.toString()); command.addAll(arguments);
        return run(command, root, timeout, "GIT");
    }

    private static String gh(Path root, List<String> arguments, long timeout) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("gh"); command.addAll(arguments);
        return run(command, root, timeout, "GH");
    }

    private static String run(List<String> command, Path root, long timeout, String authority) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true);
        Map<String, String> env = builder.environment();
        String path = env.get("PATH");
        String ghToken = env.get("GH_TOKEN");
        env.clear();
        if (path != null) env.put("PATH", path);
        if (ghToken != null && "GH".equals(authority)) env.put("GH_TOKEN", ghToken);
        env.put("GIT_TERMINAL_PROMPT", "0");
        Process process = builder.start();
        boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);
        if (!completed) {
            process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            throw new IllegalStateException(authority + "_COMMAND_TIMEOUT");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) {
            throw new IllegalStateException(authority + "_COMMAND_FAILED:" + output.strip());
        }
        return output;
    }

    private void writeAtomic(Path outputFile, Object value) throws Exception {
        Path output = outputFile.toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
        mapper.writeValue(temporary.toFile(), value);
        try {
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}