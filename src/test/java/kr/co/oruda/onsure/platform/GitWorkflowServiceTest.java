package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.oruda.onsure.assurance.ApprovalReceiptVerifier;
import kr.co.oruda.onsure.assurance.Decision;
import kr.co.oruda.onsure.assurance.LocalKeyRegistry;
import kr.co.oruda.onsure.assurance.LocalReceiptCrypto;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitWorkflowServiceTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void commitRequiresTrustedApprovalAndImprovementProof() throws Exception {
        Path repository = temp.resolve("repository");
        Path worktree = temp.resolve("worktree");
        Files.createDirectories(repository.resolve("src"));
        git(repository, "init");
        git(repository, "config", "user.email", "test@example.invalid");
        git(repository, "config", "user.name", "ONSure Test");
        Files.writeString(repository.resolve("src/value.txt"), "before\n");
        git(repository, "add", "src/value.txt");
        git(repository, "commit", "-m", "baseline");
        git(repository, "worktree", "add", "-b", "fix/proof-bound", worktree.toString(), "HEAD");
        Files.writeString(worktree.resolve("src/value.txt"), "after\n");

        Path patchFile = temp.resolve("patch-apply.json");
        mapper.writeValue(patchFile.toFile(), Map.ofEntries(
                Map.entry("contract", ImprovementWorkflowService.APPLY_RECEIPT_CONTRACT),
                Map.entry("branch", "fix/proof-bound"),
                Map.entry("applied_hunks", List.of(Map.of(
                        "hunk_id", "HUNK-001", "relative_path", "src/value.txt"))),
                Map.entry("state", "APPLIED_NONFINAL")));
        String patchDigest = Hashing.file(patchFile);

        Path proofFile = temp.resolve("improvement-proof.json");
        mapper.writeValue(proofFile.toFile(), Map.ofEntries(
                Map.entry("contract", ImprovementProofService.CONTRACT),
                Map.entry("patch_apply_receipt_sha256", patchDigest),
                Map.entry("decision", "IMPROVEMENT_PROVEN"),
                Map.entry("focused_fixture_validation", "PASS"),
                Map.entry("full_regression", "PASS"),
                Map.entry("commit_allowed", true)));

        var pair = LocalReceiptCrypto.generate();
        Path publicKey = temp.resolve("git-approval.public");
        LocalReceiptCrypto.writePublicKey(publicKey, pair.getPublic());
        Path registryFile = temp.resolve("git-key-registry.json");
        LocalKeyRegistry registry = new LocalKeyRegistry(registryFile);
        Instant now = Instant.now();
        assertEquals(Decision.PASS, registry.register(new LocalKeyRegistry.KeyRecord(
                "git-approval-key", ApprovalReceiptVerifier.AUTHORITY, publicKey.toString(),
                now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS), false, null)).decision());

        Map<String, Object> approval = new LinkedHashMap<>();
        approval.put("contract", GitWorkflowService.DELIVERY_APPROVAL_CONTRACT);
        approval.put("approval_id", "approval-git-test-001");
        approval.put("authority_class", ApprovalReceiptVerifier.AUTHORITY_CLASS);
        approval.put("approval_purpose", GitWorkflowService.APPROVAL_PURPOSE);
        approval.put("nonce", "nonce-git-test-approval-0001");
        approval.put("patch_apply_receipt_sha256", patchDigest);
        approval.put("branch", "fix/proof-bound");
        approval.put("remote", "origin");
        approval.put("remote_url_sha256", "a".repeat(64));
        approval.put("base_branch", "main");
        approval.put("actor", "reviewer@example.invalid");
        approval.put("key_id", "git-approval-key");
        approval.put("signature_algorithm", "Ed25519");
        approval.put("approved_at", now.toString());
        approval.put("expires_at", now.plus(1, ChronoUnit.HOURS).toString());
        approval.put("allow_commit", true);
        approval.put("allow_push", false);
        approval.put("allow_draft_pr", false);
        approval.put("allow_force_push", false);
        approval.put("allow_merge", false);
        approval.put("signature", LocalReceiptCrypto.sign(approval, pair.getPrivate()));
        Path approvalFile = temp.resolve("git-approval.json");
        mapper.writeValue(approvalFile.toFile(), approval);

        GitWorkflowService service = new GitWorkflowService();
        assertThrows(IllegalStateException.class, () -> service.commitApprovedWorktree(
                worktree, patchFile, approvalFile, "Unsafe legacy", temp.resolve("legacy.json")));
        Map<String, Object> result = service.commitApprovedWorktree(
                worktree, patchFile, proofFile, approvalFile, registryFile,
                temp.resolve("git-approval-replay.jsonl"),
                "Fix proof-bound finding", temp.resolve("change-set.json"));
        assertEquals("fix/proof-bound", result.get("branch"));
        assertEquals("PROHIBITED", result.get("merge_state"));
        assertEquals(approval.get("expires_at"), result.get("approval_expires_at"));
        assertTrue(result.get("commit_sha").toString().matches("[0-9a-f]{40,64}"));
        assertTrue(gitOutput(worktree, "status", "--porcelain", "--untracked-files=all").isBlank());
    }

    @Test
    void expiredDeliveryApprovalCannotReachPushTransition() {
        JsonNode approval = mapper.valueToTree(Map.of(
                "expires_at", Instant.now().minusSeconds(1).toString()));
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> GitWorkflowService.requireApprovalNotExpired(approval, Instant.now()));
        assertEquals("GIT_DELIVERY_APPROVAL_EXPIRED", failure.getMessage());
    }

    @Test
    void nonProvenImprovementCannotCommit() throws Exception {
        Path worktree = temp.resolve("invalid-worktree");
        Files.createDirectories(worktree);
        Path patch = temp.resolve("invalid-patch.json");
        Path proof = temp.resolve("invalid-proof.json");
        Path approval = temp.resolve("invalid-approval.json");
        mapper.writeValue(patch.toFile(), Map.of("contract", ImprovementWorkflowService.APPLY_RECEIPT_CONTRACT));
        mapper.writeValue(proof.toFile(), Map.of(
                "contract", ImprovementProofService.CONTRACT,
                "patch_apply_receipt_sha256", Hashing.file(patch),
                "decision", "REGRESSION_DETECTED",
                "focused_fixture_validation", "FAIL",
                "full_regression", "FAIL",
                "commit_allowed", false));
        mapper.writeValue(approval.toFile(), Map.of(
                "contract", GitWorkflowService.DELIVERY_APPROVAL_CONTRACT,
                "patch_apply_receipt_sha256", Hashing.file(patch)));
        assertThrows(IllegalStateException.class, () -> new GitWorkflowService().commitApprovedWorktree(
                worktree, patch, proof, approval,
                temp.resolve("registry.json"), temp.resolve("replay.jsonl"),
                "Should fail", temp.resolve("change-set-invalid.json")));
    }

    private static void git(Path root, String... arguments) throws Exception {
        String output = gitOutput(root, arguments);
        if (output.startsWith("EXIT:")) throw new IllegalStateException(output);
    }

    private static String gitOutput(Path root, String... arguments) throws Exception {
        java.util.ArrayList<String> command = new java.util.ArrayList<>();
        command.add("git"); command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).directory(root.toFile())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        int exit = process.waitFor();
        return exit == 0 ? output : "EXIT:" + exit + ":" + output;
    }
}
