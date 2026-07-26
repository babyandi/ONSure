package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.onsure.assurance.ApprovalReceiptVerifier;
import io.onsure.assurance.Decision;
import io.onsure.assurance.LocalKeyRegistry;
import io.onsure.assurance.LocalReceiptCrypto;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExecutionPlanApprovalServiceTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void trustedApprovalProducesSourceBoundUserApprovedPlanAndRejectsReplay() throws Exception {
        Path planFile = temp.resolve("plan.json");
        Map<String, Object> plan = plan();
        mapper.writeValue(planFile.toFile(), plan);

        Instant now = Instant.now();
        var pair = LocalReceiptCrypto.generate();
        Path publicKey = temp.resolve("plan-approval.public");
        LocalReceiptCrypto.writePublicKey(publicKey, pair.getPublic());
        Path registry = temp.resolve("plan-key-registry.json");
        LocalKeyRegistry keys = new LocalKeyRegistry(registry);
        assertEquals(Decision.PASS, keys.register(new LocalKeyRegistry.KeyRecord(
                "plan-key-001", ApprovalReceiptVerifier.AUTHORITY, publicKey.toString(),
                now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS), false, null)).decision());

        Map<String, Object> approval = new LinkedHashMap<>();
        approval.put("contract", ExecutionPlanApprovalService.APPROVAL_CONTRACT);
        approval.put("approval_id", "approval-plan-test-001");
        approval.put("authority_class", ApprovalReceiptVerifier.AUTHORITY_CLASS);
        approval.put("approval_purpose", ExecutionPlanApprovalService.PURPOSE);
        approval.put("nonce", "nonce-plan-test-approval-0001");
        approval.put("plan_id", plan.get("plan_id"));
        approval.put("plan_file_sha256", Hashing.file(planFile));
        approval.put("target_id", plan.get("target_id"));
        approval.put("source_tree_sha256", plan.get("source_tree_sha256"));
        approval.put("approved_actions", List.of("STATIC_ANALYSIS", "FIXTURE_EXECUTION", "EVIDENCE_GENERATION"));
        approval.put("actor", "reviewer@example.invalid");
        approval.put("key_id", "plan-key-001");
        approval.put("signature_algorithm", "Ed25519");
        approval.put("approved_at", now.toString());
        approval.put("expires_at", now.plus(1, ChronoUnit.HOURS).toString());
        approval.put("allow_final_claim", false);
        approval.put("allow_merge", false);
        approval.put("allow_deploy", false);
        approval.put("signature", LocalReceiptCrypto.sign(approval, pair.getPrivate()));
        Path approvalFile = temp.resolve("approval.json");
        mapper.writeValue(approvalFile.toFile(), approval);

        ExecutionPlanApprovalService service = new ExecutionPlanApprovalService();
        Path output = temp.resolve("approved-plan.json");
        Map<String, Object> approved = service.approve(
                planFile, approvalFile, registry, temp.resolve("approval-replay.jsonl"), output);
        assertEquals("USER_APPROVED", approved.get("approval_state"));
        assertEquals("approval-plan-test-001", approved.get("approval_id"));
        assertEquals(plan.get("source_tree_sha256"), approved.get("source_tree_sha256"));
        assertThrows(IllegalStateException.class, () -> service.approve(
                planFile, approvalFile, registry, temp.resolve("approval-replay.jsonl"),
                temp.resolve("approved-plan-replay.json")));
    }

    private static Map<String, Object> plan() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("contract", ExecutionPlanService.CONTRACT);
        value.put("plan_id", "PLAN-target-001-aaaaaaaaaaaa");
        value.put("target_id", "target-001");
        value.put("source_tree_sha256", "a".repeat(64));
        value.put("program_profile_id", "profile-001");
        value.put("risk_level", "HIGH");
        value.put("risk_reasons", List.of("AI_TARGET"));
        value.put("review_domains", List.of("REQUIREMENTS", "SECURITY"));
        value.put("verification_scenarios", List.of("REGISTERED_FIXTURES"));
        value.put("required_environments", List.of("SANDBOX"));
        value.put("allowed_actions", List.of(
                "PROGRAM_PROFILE", "STATIC_ANALYSIS", "FIXTURE_EXECUTION", "EVIDENCE_GENERATION"));
        value.put("prohibited_actions", List.of("MERGE", "DEPLOY", "FINAL_CLAIM"));
        value.put("stop_conditions", List.of("SOURCE_DRIFT", "UNAPPROVED_SCOPE_CHANGE"));
        value.put("estimated_cost", Map.of(
                "currency", "KRW", "amount_minor", 0, "credit_upper_bound", 10,
                "estimated_runtime_seconds", 120, "basis", "REGISTERED_FIXTURE_COUNT"));
        value.put("approval_required", true);
        value.put("approval_state", "AWAITING_APPROVAL");
        value.put("created_at", Instant.now().toString());
        value.put("final_claim_allowed", false);
        value.put("plan_sha256", "b".repeat(64));
        return value;
    }
}