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

/** Creates bounded patch candidates and applies only explicitly approved hunks in an isolated worktree. */
public final class ImprovementWorkflowService {
    public static final String PATCH_PLAN_CONTRACT = "ONSURE_PATCH_PLAN_V1";
    public static final String APPROVAL_CONTRACT = "ONSURE_HUNK_APPROVAL_RECEIPT_V1";
    public static final String APPLY_RECEIPT_CONTRACT = "ONSURE_PATCH_APPLY_RECEIPT_V1";
    public static final String ROLLBACK_RECEIPT_CONTRACT = "ONSURE_PATCH_ROLLBACK_RECEIPT_V1";
    public static final String APPROVAL_PURPOSE = "HUNK_APPROVAL";
    private static final Map<String, String> SAFE_MARKER_REPLACEMENTS = Map.of(
            "ALLOW_UNTRUSTED_TOOL", "DENY_UNTRUSTED_TOOL",
            "SELF_APPROVE", "REQUIRE_INDEPENDENT_APPROVAL",
            "PROMPT_INJECTION_BYPASS", "PROMPT_INJECTION_BLOCK",
            "EXPORT_FULL_CONTEXT", "EXPORT_REDACTED_CONTEXT",
            "TODO_BUG", "TODO_RESOLVED_WITH_REGRESSION_TEST");

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public Map<String, Object> createPatchPlan(ValidationContext context, Path outputFile) throws Exception {
        Path targetRoot = context.target().sourceRoot().toAbsolutePath().normalize();
        Path repositoryRoot = gitTopLevel(targetRoot);
        String targetRelativeRoot = repositoryRoot.relativize(targetRoot).toString().replace('\\', '/');
        if (targetRelativeRoot.isBlank()) targetRelativeRoot = ".";
        List<Map<String, Object>> hunks = new ArrayList<>();
        for (ValidationModel.Finding finding : context.findings()) {
            if (finding.location().startsWith("fixture:")) continue;
            Path file = targetRoot.resolve(finding.location()).normalize();
            if (!file.startsWith(targetRoot)
                    || Files.isSymbolicLink(file)
                    || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
            String source = Files.readString(file, StandardCharsets.UTF_8);
            String marker = matchedMarker(source);
            if (marker == null || count(source, marker) != 1) continue;
            String relative = Hashing.relative(targetRoot, file);
            Map<String, Object> hunk = new LinkedHashMap<>();
            hunk.put("hunk_id", "HUNK-" + finding.fingerprint().substring(0, 16));
            hunk.put("finding_id", finding.findingId());
            hunk.put("relative_path", relative);
            hunk.put("preimage_sha256", Hashing.file(file));
            hunk.put("match_text", marker);
            hunk.put("replacement_text", SAFE_MARKER_REPLACEMENTS.get(marker));
            hunk.put("occurrence", 1);
            hunk.put("change_class", "APPROVAL_REQUIRED");
            hunk.put("approval_state", "PENDING");
            hunk.put("expected_effect", "Remove the explicit unsafe marker while preserving unrelated bytes.");
            hunk.put("required_tests", List.of(
                    "FOCUSED_FINDING_FIXTURE", "FULL_REGRESSION", "BEFORE_AFTER_PROOF"));
            hunks.add(Map.copyOf(hunk));
        }
        String sourceDigest = String.valueOf(context.attributes().get("source_tree_sha256"));
        if (!sourceDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("PATCH_PLAN_SOURCE_DIGEST_MISSING");
        }
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("contract", PATCH_PLAN_CONTRACT);
        plan.put("patch_plan_id", "PATCH-" + context.job().jobId());
        plan.put("target_id", context.target().targetId());
        plan.put("repository_root_reference", repositoryRoot.toString());
        plan.put("target_relative_root", targetRelativeRoot);
        plan.put("source_tree_sha256", sourceDigest);
        plan.put("review_id", context.attributes().getOrDefault("review_id", "NOT_RUN"));
        plan.put("evidence_based_rca_sha256",
                context.attributes().getOrDefault("evidence_based_rca_sha256", "NOT_RUN"));
        plan.put("hunks", List.copyOf(hunks));
        plan.put("default_approval", "DENY");
        plan.put("worktree_required", true);
        plan.put("direct_main_write_allowed", false);
        plan.put("force_push_allowed", false);
        plan.put("merge_allowed", false);
        plan.put("created_at", Instant.now().toString());
        plan.put("execution_state", hunks.isEmpty() ? "NO_SAFE_PATCH_CANDIDATE" : "AWAITING_HUNK_APPROVAL");
        plan.put("final_claim_allowed", false);
        plan.put("patch_plan_sha256", sha256(mapper.writeValueAsBytes(plan)));
        writeAtomic(outputFile, plan);
        return Map.copyOf(plan);
    }

    @Deprecated
    public Map<String, Object> applyApprovedPlan(
            Path repositoryRoot, Path planFile, Path approvalReceiptFile,
            Path worktreeRoot, Path evidenceRoot) {
        throw new IllegalStateException("APPROVAL_TRUST_REGISTRY_REQUIRED");
    }

    public Map<String, Object> applyApprovedPlan(
            Path repositoryRoot,
            Path planFile,
            Path approvalReceiptFile,
            Path approvalKeyRegistry,
            Path approvalReplayLedger,
            Path worktreeRoot,
            Path evidenceRoot) throws Exception {
        Path repository = repositoryRoot.toAbsolutePath().normalize();
        requireCleanRepository(repository);
        JsonNode plan = readContract(planFile, PATCH_PLAN_CONTRACT, "PATCH_PLAN");
        JsonNode approval = readContract(approvalReceiptFile, APPROVAL_CONTRACT, "HUNK_APPROVAL");
        String planDigest = sha256(Files.readAllBytes(planFile));
        if (!planDigest.equals(approval.path("patch_plan_file_sha256").asText())) {
            throw new IllegalStateException("HUNK_APPROVAL_PLAN_DIGEST_MISMATCH");
        }
        if (!plan.path("patch_plan_id").asText().equals(approval.path("patch_plan_id").asText())) {
            throw new IllegalStateException("HUNK_APPROVAL_PLAN_ID_MISMATCH");
        }
        if (approval.path("allow_direct_main_write").asBoolean(true)
                || approval.path("allow_force_push").asBoolean(true)
                || approval.path("allow_merge").asBoolean(true)) {
            throw new IllegalStateException("UNSAFE_PATCH_PERMISSION_REQUESTED");
        }
        String targetRelativeRoot = requireTargetRelativeRoot(plan.path("target_relative_root").asText());
        Path targetRoot = ".".equals(targetRelativeRoot)
                ? repository : repository.resolve(targetRelativeRoot).normalize();
        if (!targetRoot.startsWith(repository)
                || !Files.isDirectory(targetRoot, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(targetRoot)) {
            throw new IllegalStateException("PATCH_TARGET_ROOT_INVALID");
        }
        String currentSourceDigest = Hashing.tree(targetRoot);
        if (!currentSourceDigest.equals(plan.path("source_tree_sha256").asText())) {
            throw new IllegalStateException("PATCH_PLAN_SOURCE_TREE_DRIFT");
        }
        Set<String> approved = new LinkedHashSet<>();
        approval.path("approved_hunk_ids").forEach(value -> approved.add(value.asText()));
        if (approved.isEmpty()) throw new IllegalStateException("APPROVED_HUNK_SET_EMPTY");
        Set<String> declared = new LinkedHashSet<>();
        plan.path("hunks").forEach(value -> declared.add(value.path("hunk_id").asText()));
        if (!declared.containsAll(approved)) throw new IllegalStateException("APPROVED_HUNK_NOT_FOUND_IN_PLAN");

        String branch = requireBranch(approval.path("branch_name").asText());
        if (isProtectedBranch(branch)) throw new IllegalStateException("PROTECTED_BRANCH_PATCH_PROHIBITED");
        Path worktree = worktreeRoot.toAbsolutePath().normalize();
        Path evidence = evidenceRoot.toAbsolutePath().normalize();
        if (Files.exists(worktree, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("WORKTREE_ALREADY_EXISTS");
        }
        if (Files.exists(evidence.resolve("backups"), LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("PATCH_BACKUP_ROOT_ALREADY_EXISTS");
        }

        ApprovalReceiptVerifier verifier = new ApprovalReceiptVerifier(
                approvalKeyRegistry, approvalReplayLedger);
        ValidationResult approvalValidation = verifier.verify(
                approvalReceiptFile, APPROVAL_CONTRACT, APPROVAL_PURPOSE, Instant.now());
        if (approvalValidation.decision() != Decision.PASS) {
            throw new IllegalStateException(
                    "HUNK_APPROVAL_INVALID:" + String.join(",", approvalValidation.violations()));
        }
        verifier.requireValidAndConsume(
                approvalReceiptFile, APPROVAL_CONTRACT, APPROVAL_PURPOSE, Instant.now());

        String sourceCommit = git(repository, List.of("rev-parse", "HEAD"), 20).strip();
        git(repository, List.of("worktree", "add", "-b", branch, worktree.toString(), sourceCommit), 60);
        Path worktreeTargetRoot = ".".equals(targetRelativeRoot)
                ? worktree : worktree.resolve(targetRelativeRoot).normalize();
        if (!worktreeTargetRoot.startsWith(worktree)
                || !Files.isDirectory(worktreeTargetRoot, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(worktreeTargetRoot)) {
            throw new IllegalStateException("PATCH_WORKTREE_TARGET_ROOT_INVALID");
        }
        List<Map<String, Object>> applied = new ArrayList<>();
        Path backupRoot = evidence.resolve("backups");
        try {
            for (JsonNode hunk : plan.path("hunks")) {
                String hunkId = hunk.path("hunk_id").asText();
                if (!approved.contains(hunkId)) continue;
                String relative = hunk.path("relative_path").asText();
                Path file = worktreeTargetRoot.resolve(relative).normalize();
                if (!file.startsWith(worktreeTargetRoot)
                        || Files.isSymbolicLink(file)
                        || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IllegalStateException("PATCH_TARGET_INVALID:" + hunkId);
                }
                byte[] original = Files.readAllBytes(file);
                String preimage = sha256(original);
                if (!preimage.equals(hunk.path("preimage_sha256").asText())) {
                    throw new IllegalStateException("PATCH_PREIMAGE_MISMATCH:" + hunkId);
                }
                String source = new String(original, StandardCharsets.UTF_8);
                String match = hunk.path("match_text").asText();
                String replacement = hunk.path("replacement_text").asText();
                if (count(source, match) != 1) {
                    throw new IllegalStateException("PATCH_MATCH_NOT_UNIQUE:" + hunkId);
                }
                Path backup = backupRoot.resolve(targetRelativeRoot).resolve(relative).normalize();
                if (!backup.startsWith(backupRoot)) throw new IllegalStateException("BACKUP_PATH_ESCAPE");
                Files.createDirectories(backup.getParent());
                Files.write(backup, original);
                Files.writeString(file, source.replace(match, replacement), StandardCharsets.UTF_8);
                Map<String, Object> appliedHunk = new LinkedHashMap<>();
                appliedHunk.put("hunk_id", hunkId);
                appliedHunk.put("relative_path", relative);
                appliedHunk.put("preimage_sha256", preimage);
                appliedHunk.put("postimage_sha256", sha256(Files.readAllBytes(file)));
                appliedHunk.put("backup_sha256", sha256(Files.readAllBytes(backup)));
                applied.add(Map.copyOf(appliedHunk));
            }
            Set<String> appliedIds = applied.stream().map(item -> item.get("hunk_id").toString())
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            if (!approved.equals(appliedIds)) throw new IllegalStateException("APPROVED_HUNK_APPLICATION_MISMATCH");
            git(worktree, List.of("diff", "--check"), 20);
            String status = git(worktree, List.of("status", "--porcelain", "--untracked-files=all"), 20);
            if (status.isBlank()) throw new IllegalStateException("PATCH_PRODUCED_NO_CHANGE");
            if (status.lines().anyMatch(line -> line.startsWith("??"))) {
                throw new IllegalStateException("PATCH_PRODUCED_UNTRACKED_FILE");
            }
            String postimageSourceDigest = Hashing.tree(worktreeTargetRoot);
            if (postimageSourceDigest.equals(currentSourceDigest)) {
                throw new IllegalStateException("PATCH_POSTIMAGE_SOURCE_TREE_UNCHANGED");
            }
            Map<String, Object> receipt = new LinkedHashMap<>();
            receipt.put("contract", APPLY_RECEIPT_CONTRACT);
            receipt.put("patch_plan_id", plan.path("patch_plan_id").asText());
            receipt.put("patch_plan_file_sha256", planDigest);
            receipt.put("approval_receipt_sha256", sha256(Files.readAllBytes(approvalReceiptFile)));
            receipt.put("approval_actor", approval.path("actor").asText());
            receipt.put("approval_key_id", approval.path("key_id").asText());
            receipt.put("source_commit", sourceCommit);
            receipt.put("target_relative_root", targetRelativeRoot);
            receipt.put("source_tree_sha256", currentSourceDigest);
            receipt.put("postimage_source_tree_sha256", postimageSourceDigest);
            receipt.put("branch", branch);
            receipt.put("worktree", worktree.toString());
            receipt.put("applied_hunks", List.copyOf(applied));
            receipt.put("git_status", status.lines().toList());
            receipt.put("rollback_pointer", backupRoot.toString());
            receipt.put("state", "APPLIED_NONFINAL");
            receipt.put("focused_tests", "NOT_RUN");
            receipt.put("full_regression", "NOT_RUN");
            receipt.put("before_after_proof", "NOT_RUN");
            receipt.put("commit_allowed", false);
            receipt.put("push_allowed", false);
            receipt.put("merge_allowed", false);
            receipt.put("created_at", Instant.now().toString());
            receipt.put("final_claim_allowed", false);
            receipt.put("receipt_sha256", sha256(mapper.writeValueAsBytes(receipt)));
            Files.createDirectories(evidence);
            writeAtomic(evidence.resolve("patch-apply-receipt.json"), receipt);
            return Map.copyOf(receipt);
        } catch (Exception failure) {
            try { git(repository, List.of("worktree", "remove", "--force", worktree.toString()), 60); }
            catch (Exception ignored) {}
            try { git(repository, List.of("branch", "-D", branch), 30); }
            catch (Exception ignored) {}
            throw failure;
        }
    }

    public Map<String, Object> rollback(
            Path worktreeRoot, Path applyReceiptFile, Path rollbackReceiptFile) throws Exception {
        JsonNode receipt = readContract(
                applyReceiptFile, APPLY_RECEIPT_CONTRACT, "PATCH_APPLY_RECEIPT");
        Path worktree = worktreeRoot.toAbsolutePath().normalize();
        if (!worktree.toString().equals(receipt.path("worktree").asText())) {
            throw new IllegalStateException("ROLLBACK_WORKTREE_RECEIPT_MISMATCH");
        }
        String targetRelativeRoot = requireTargetRelativeRoot(receipt.path("target_relative_root").asText());
        Path targetRoot = ".".equals(targetRelativeRoot)
                ? worktree : worktree.resolve(targetRelativeRoot).normalize();
        Path backupRoot = Path.of(receipt.path("rollback_pointer").asText()).toAbsolutePath().normalize();
        List<Map<String, Object>> restored = new ArrayList<>();
        for (JsonNode hunk : receipt.path("applied_hunks")) {
            String relative = hunk.path("relative_path").asText();
            Path target = targetRoot.resolve(relative).normalize();
            Path backup = backupRoot.resolve(targetRelativeRoot).resolve(relative).normalize();
            if (!target.startsWith(targetRoot) || !backup.startsWith(backupRoot)
                    || !Files.isRegularFile(backup, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(backup)) {
                throw new IllegalStateException("ROLLBACK_POINTER_INVALID");
            }
            if (!Hashing.file(target).equals(hunk.path("postimage_sha256").asText())) {
                throw new IllegalStateException("ROLLBACK_POSTIMAGE_DRIFT:" + relative);
            }
            Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING);
            if (!Hashing.file(target).equals(hunk.path("preimage_sha256").asText())) {
                throw new IllegalStateException("ROLLBACK_RESTORE_MISMATCH:" + relative);
            }
            restored.add(Map.of(
                    "hunk_id", hunk.path("hunk_id").asText(),
                    "relative_path", relative,
                    "restored_sha256", Hashing.file(target)));
        }
        String restoredTree = Hashing.tree(targetRoot);
        if (!restoredTree.equals(receipt.path("source_tree_sha256").asText())) {
            throw new IllegalStateException("ROLLBACK_SOURCE_TREE_MISMATCH");
        }
        Map<String, Object> rollback = new LinkedHashMap<>();
        rollback.put("contract", ROLLBACK_RECEIPT_CONTRACT);
        rollback.put("patch_apply_receipt_sha256", sha256(Files.readAllBytes(applyReceiptFile)));
        rollback.put("worktree", worktree.toString());
        rollback.put("target_relative_root", targetRelativeRoot);
        rollback.put("restored_source_tree_sha256", restoredTree);
        rollback.put("restored_hunks", List.copyOf(restored));
        rollback.put("git_status", git(worktree,
                List.of("status", "--porcelain", "--untracked-files=all"), 20).lines().toList());
        rollback.put("state", "ROLLED_BACK_NONFINAL");
        rollback.put("created_at", Instant.now().toString());
        rollback.put("final_claim_allowed", false);
        rollback.put("receipt_sha256", sha256(mapper.writeValueAsBytes(rollback)));
        writeAtomic(rollbackReceiptFile, rollback);
        return Map.copyOf(rollback);
    }

    private static Path gitTopLevel(Path root) throws Exception {
        String value = git(root, List.of("rev-parse", "--show-toplevel"), 20).strip();
        Path result = Path.of(value).toAbsolutePath().normalize();
        if (!root.startsWith(result)) throw new IllegalStateException("PATCH_SOURCE_NOT_IN_GIT_REPOSITORY");
        return result;
    }

    private static String requireTargetRelativeRoot(String value) {
        if (value == null || value.isBlank() || value.startsWith("/")
                || value.contains("..") || value.contains("\\")) {
            throw new IllegalArgumentException("TARGET_RELATIVE_ROOT_INVALID");
        }
        return value;
    }

    private static String matchedMarker(String source) {
        return SAFE_MARKER_REPLACEMENTS.keySet().stream()
                .filter(source::contains).sorted().findFirst().orElse(null);
    }

    private static int count(String source, String token) {
        int count = 0;
        for (int offset = 0; (offset = source.indexOf(token, offset)) >= 0; offset += token.length()) count++;
        return count;
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

    private static void requireCleanRepository(Path repository) throws Exception {
        String status = git(repository, List.of("status", "--porcelain", "--untracked-files=all"), 20);
        if (!status.isBlank()) throw new IllegalStateException("SOURCE_REPOSITORY_DIRTY_OR_UNTRACKED");
    }

    private static String requireBranch(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._/-]{2,120}")
                || value.contains("..") || value.endsWith("/") || value.startsWith("-")) {
            throw new IllegalArgumentException("BRANCH_NAME_INVALID");
        }
        return value;
    }

    private static boolean isProtectedBranch(String branch) {
        return List.of("main", "master", "production", "release").contains(branch.toLowerCase());
    }

    private static String git(Path root, List<String> arguments, long timeoutSeconds) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git"); command.add("-C"); command.add(root.toString()); command.addAll(arguments);
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        Map<String, String> environment = builder.environment();
        String path = environment.get("PATH");
        environment.clear();
        if (path != null) environment.put("PATH", path);
        environment.put("GIT_TERMINAL_PROMPT", "0");
        Process process = builder.start();
        boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            throw new IllegalStateException("GIT_COMMAND_TIMEOUT:" + arguments.get(0));
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) {
            throw new IllegalStateException("GIT_COMMAND_FAILED:" + arguments.get(0) + ":" + output.strip());
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
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
