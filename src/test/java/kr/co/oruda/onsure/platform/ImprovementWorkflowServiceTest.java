package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class ImprovementWorkflowServiceTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void trustedApprovalAppliesExactHunkAndRollbackRestoresTargetTree() throws Exception {
        Path repository = temp.resolve("repository");
        Path targetRoot = repository.resolve("services/target-001");
        Files.createDirectories(targetRoot.resolve("src"));
        git(repository, "init");
        git(repository, "config", "user.email", "test@example.invalid");
        git(repository, "config", "user.name", "ONSure Test");
        Path source = targetRoot.resolve("src/policy.txt");
        Files.writeString(source, "ALLOW_UNTRUSTED_TOOL\n");
        git(repository, "add", "services/target-001/src/policy.txt");
        git(repository, "commit", "-m", "baseline");

        String originalTree = Hashing.tree(targetRoot);
        String preimage = Hashing.file(source);
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("contract", ImprovementWorkflowService.PATCH_PLAN_CONTRACT);
        plan.put("patch_plan_id", "PATCH-test-001");
        plan.put("target_id", "target-001");
        plan.put("repository_root_reference", repository.toString());
        plan.put("target_relative_root", "services/target-001");
        plan.put("source_tree_sha256", originalTree);
        plan.put("review_id", "review-001");
        plan.put("evidence_based_rca_sha256", "a".repeat(64));
        plan.put("hunks", List.of(Map.ofEntries(
                Map.entry("hunk_id", "HUNK-test-001"),
                Map.entry("finding_id", "FINDING-test-001"),
                Map.entry("relative_path", "src/policy.txt"),
                Map.entry("preimage_sha256", preimage),
                Map.entry("match_text", "ALLOW_UNTRUSTED_TOOL"),
                Map.entry("replacement_text", "DENY_UNTRUSTED_TOOL"),
                Map.entry("occurrence", 1),
                Map.entry("change_class", "APPROVAL_REQUIRED"),
                Map.entry("approval_state", "PENDING"),
                Map.entry("expected_effect", "Deny untrusted tool execution."),
                Map.entry("required_tests", List.of("FOCUSED_FINDING_FIXTURE", "FULL_REGRESSION")))));
        plan.put("default_approval", "DENY");
        plan.put("worktree_required", true);
        plan.put("direct_main_write_allowed", false);
        plan.put("force_push_allowed", false);
        plan.put("merge_allowed", false);
        plan.put("created_at", Instant.now().toString());
        plan.put("execution_state", "AWAITING_HUNK_APPROVAL");
        plan.put("final_claim_allowed", false);
        plan.put("patch_plan_sha256", "b".repeat(64));
        Path planFile = temp.resolve("patch-plan.json");
        mapper.writeValue(planFile.toFile(), plan);

        var pair = LocalReceiptCrypto.generate();
        Path publicKey = temp.resolve("approval-public.key");
        LocalReceiptCrypto.writePublicKey(publicKey, pair.getPublic());
        Path registryFile = temp.resolve("key-registry.json");
        LocalKeyRegistry registry = new LocalKeyRegistry(registryFile);
        Instant now = Instant.now();
        assertEquals(Decision.PASS, registry.register(new LocalKeyRegistry.KeyRecord(
                "approval-key-001", ApprovalReceiptVerifier.AUTHORITY, publicKey.toString(),
                now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS), false, null)).decision());

        Map<String, Object> approval = new LinkedHashMap<>();
        approval.put("contract", ImprovementWorkflowService.APPROVAL_CONTRACT);
        approval.put("approval_id", "approval-patch-001");
        approval.put("authority_class", ApprovalReceiptVerifier.AUTHORITY_CLASS);
        approval.put("approval_purpose", ImprovementWorkflowService.APPROVAL_PURPOSE);
        approval.put("nonce", "nonce-patch-approval-0001");
        approval.put("patch_plan_id", "PATCH-test-001");
        approval.put("patch_plan_file_sha256", Hashing.file(planFile));
        approval.put("approved_hunk_ids", List.of("HUNK-test-001"));
        approval.put("branch_name", "fix/test-approved-patch");
        approval.put("actor", "reviewer@example.invalid");
        approval.put("key_id", "approval-key-001");
        approval.put("signature_algorithm", "Ed25519");
        approval.put("approved_at", now.toString());
        approval.put("expires_at", now.plus(1, ChronoUnit.HOURS).toString());
        approval.put("allow_direct_main_write", false);
        approval.put("allow_force_push", false);
        approval.put("allow_merge", false);
        approval.put("signature", LocalReceiptCrypto.sign(approval, pair.getPrivate()));
        Path approvalFile = temp.resolve("approval.json");
        mapper.writeValue(approvalFile.toFile(), approval);

        ImprovementWorkflowService service = new ImprovementWorkflowService();
        assertThrows(IllegalStateException.class, () -> service.applyApprovedPlan(
                repository, planFile, approvalFile, temp.resolve("unsafe-worktree"), temp.resolve("unsafe-evidence")));

        Path worktree = temp.resolve("worktree");
        Path evidence = temp.resolve("evidence");
        Map<String, Object> receipt = service.applyApprovedPlan(
                repository, planFile, approvalFile, registryFile, temp.resolve("approval-replay.jsonl"),
                worktree, evidence);
        assertEquals("APPLIED_NONFINAL", receipt.get("state"));
        assertEquals("services/target-001", receipt.get("target_relative_root"));
        assertEquals(originalTree, receipt.get("source_tree_sha256"));
        assertNotEquals(originalTree, receipt.get("postimage_source_tree_sha256"));
        assertTrue(Files.readString(worktree.resolve("services/target-001/src/policy.txt"))
                .contains("DENY_UNTRUSTED_TOOL"));
        assertEquals("ALLOW_UNTRUSTED_TOOL\n", Files.readString(source));

        Map<String, Object> rollback = service.rollback(
                worktree, evidence.resolve("patch-apply-receipt.json"),
                evidence.resolve("patch-rollback-receipt.json"));
        assertEquals("ROLLED_BACK_NONFINAL", rollback.get("state"));
        assertEquals(originalTree, rollback.get("restored_source_tree_sha256"));
        assertEquals("ALLOW_UNTRUSTED_TOOL\n",
                Files.readString(worktree.resolve("services/target-001/src/policy.txt")));
    }

    private static void git(Path root, String... arguments) throws Exception {
        java.util.ArrayList<String> command = new java.util.ArrayList<>();
        command.add("git"); command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).directory(root.toFile())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) throw new IllegalStateException(output);
    }
}
