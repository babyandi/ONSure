package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.onsure.assurance.ApprovalReceiptVerifier;
import io.onsure.assurance.Decision;
import io.onsure.assurance.LocalKeyRegistry;
import io.onsure.assurance.LocalReceiptCrypto;
import io.onsure.platform.ValidationModel.TargetType;
import io.onsure.platform.ValidationModel.ValidationTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExecutionPlanApprovalServiceTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private Path registry;
    private Path replay;
    private java.security.KeyPair pair;
    private Instant now;

    @BeforeEach
    void setUp() throws Exception {
        now = Instant.now();
        pair = LocalReceiptCrypto.generate();
        Path publicKey = temp.resolve("plan-approval.public");
        LocalReceiptCrypto.writePublicKey(publicKey, pair.getPublic());
        registry = temp.resolve("plan-key-registry.json");
        replay = temp.resolve("plan-approval-replay.jsonl");
        LocalKeyRegistry keys = new LocalKeyRegistry(registry);
        assertEquals(Decision.PASS, keys.register(new LocalKeyRegistry.KeyRecord(
                "plan-key-001", ApprovalReceiptVerifier.AUTHORITY, publicKey.toString(),
                now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS), false, null)).decision());
    }

    @Test
    void trustedExactApprovalRequiresOriginalPlanReceiptKeyAndConsumedReplayLedger() throws Exception {
        Path planFile = writePlan("plan.json");
        Map<String, Object> plan = mapper.readValue(planFile.toFile(), Map.class);
        Path approvalFile = writeApproval(planFile, plan,
                List.copyOf((List<String>) plan.get("allowed_actions")), "nonce-plan-exact-0001");

        ExecutionPlanApprovalService service = new ExecutionPlanApprovalService();
        Path output = temp.resolve("approved-plan.json");
        Map<String, Object> approved = service.approve(planFile, approvalFile, registry, replay, output);
        Map<?, ?> approval = (Map<?, ?>) approved.get("approval");
        assertEquals("USER_APPROVED", approval.get("state"));
        assertEquals("EXACT_PLAN_ACTION_SET", approval.get("scope"));
        assertEquals(Hashing.file(planFile), approval.get("original_plan_file_sha256"));
        assertFalse(Boolean.TRUE.equals(approved.get("final_claim_allowed")));

        ValidationTarget target = target();
        assertEquals("target-001", service.verifyApprovedPlanBundle(
                output, planFile, approvalFile, registry, replay, target, "a".repeat(64))
                .get("target_id"));
        assertThrows(IllegalStateException.class,
                () -> service.verifyApprovedPlan(output, target, "a".repeat(64)));

        Map<String, Object> tampered = mapper.readValue(output.toFile(), Map.class);
        tampered.put("risk", Map.of("score", 1, "level", "LOW"));
        tampered.remove("plan_sha256");
        tampered.put("plan_sha256", new ExecutionPlanService().planHash(tampered));
        mapper.writeValue(output.toFile(), tampered);
        assertThrows(IllegalStateException.class, () -> service.verifyApprovedPlanBundle(
                output, planFile, approvalFile, registry, replay, target, "a".repeat(64)));
    }

    @Test
    void signedPartialActionApprovalPublishesAndVerifiesBoundedPlan() throws Exception {
        Path planFile = writePlan("partial-plan.json");
        Map<String, Object> plan = mapper.readValue(planFile.toFile(), Map.class);
        Path approvalFile = writeApproval(planFile, plan,
                List.of("STATIC_ANALYSIS", "REVIEW"), "nonce-plan-partial-0001");
        Path output = temp.resolve("partial-approved-plan.json");

        ExecutionPlanApprovalService service = new ExecutionPlanApprovalService();
        Map<String, Object> approved = service.approve(
                planFile, approvalFile, registry, replay, output);
        Map<?, ?> approval = (Map<?, ?>) approved.get("approval");
        assertEquals("PARTIAL_PLAN_ACTION_SET", approval.get("scope"));
        assertEquals(List.of("REVIEW", "STATIC_ANALYSIS"), approval.get("approved_actions"));
        assertEquals("target-001", service.verifyApprovedPlanBundle(
                output, planFile, approvalFile, registry, replay, target(), "a".repeat(64))
                .get("target_id"));
    }

    @Test
    void approvalCannotAddActionOutsideImmutablePlan() throws Exception {
        Path planFile = writePlan("unsafe-plan.json");
        Map<String, Object> plan = mapper.readValue(planFile.toFile(), Map.class);
        Path approvalFile = writeApproval(planFile, plan,
                List.of("STATIC_ANALYSIS", "AI_BEHAVIOR_VALIDATION"), "nonce-plan-unsafe-0001");
        Path output = temp.resolve("unsafe-approved-plan.json");
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new ExecutionPlanApprovalService().approve(
                        planFile, approvalFile, registry, replay, output));
        assertEquals("EXECUTION_PLAN_APPROVAL_ACTION_SCOPE_INVALID", failure.getMessage());
        assertFalse(Files.exists(output));
    }

    private ValidationTarget target() {
        return new ValidationTarget(
                "target-001", "Target", TargetType.GENERAL_SOFTWARE, temp,
                "sha256:" + "a".repeat(64), GenericManifestTargetAdapter.ID,
                "ONSURE_DEFAULT_POLICY_V1", "REMOTE_REVIEWED");
    }

    private Path writePlan(String name) throws Exception {
        Map<String, Object> plan = plan();
        plan.put("plan_sha256", new ExecutionPlanService().planHash(plan));
        Path file = temp.resolve(name);
        mapper.writeValue(file.toFile(), plan);
        return file;
    }

    private Path writeApproval(
            Path planFile, Map<String, Object> plan, List<String> actions, String nonce) throws Exception {
        Map<String, Object> approval = new LinkedHashMap<>();
        approval.put("contract", ExecutionPlanApprovalService.APPROVAL_CONTRACT);
        approval.put("approval_id", "approval-" + nonce);
        approval.put("authority_class", ApprovalReceiptVerifier.AUTHORITY_CLASS);
        approval.put("approval_purpose", ExecutionPlanApprovalService.PURPOSE);
        approval.put("nonce", nonce);
        approval.put("plan_id", plan.get("plan_id"));
        approval.put("plan_file_sha256", Hashing.file(planFile));
        approval.put("target_id", plan.get("target_id"));
        approval.put("source_tree_sha256", plan.get("source_tree_sha256"));
        approval.put("approved_actions", actions);
        approval.put("actor", "reviewer@example.invalid");
        approval.put("key_id", "plan-key-001");
        approval.put("signature_algorithm", "Ed25519");
        approval.put("approved_at", now.toString());
        approval.put("expires_at", now.plus(1, ChronoUnit.HOURS).toString());
        approval.put("allow_final_claim", false);
        approval.put("allow_merge", false);
        approval.put("allow_deploy", false);
        approval.put("signature", LocalReceiptCrypto.sign(approval, pair.getPrivate()));
        Path file = temp.resolve(nonce + ".json");
        mapper.writeValue(file.toFile(), approval);
        return file;
    }

    private static Map<String, Object> plan() {
        List<String> actions = List.of(
                "EVIDENCE_GENERATION", "FIXTURE_EXECUTION", "IMPROVEMENT_PLAN", "RCA",
                "REGRESSION_LOCK", "REVIEW", "STATIC_ANALYSIS");
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("contract", ExecutionPlanService.CONTRACT);
        value.put("plan_id", "PLAN-aaaaaaaaaaaaaaaa");
        value.put("target_id", "target-001");
        value.put("program_profile_id", "profile-001");
        value.put("source_tree_sha256", "a".repeat(64));
        value.put("risk", Map.of("score", 35, "level", "MEDIUM"));
        value.put("review_packs", List.of(
                "REQUIREMENT_TRACEABILITY", "ARCHITECTURE", "CODE", "SECURITY", "TEST_QUALITY"));
        value.put("scenario_classes", List.of("NORMAL", "BOUNDARY", "FAILURE", "UNAUTHORIZED", "RECOVERY"));
        value.put("allowed_actions", actions);
        value.put("fixture_count", 2);
        value.put("resource_budget", Map.of(
                "estimated_seconds", 60, "memory_limit_mb", 1024, "process_limit", 64,
                "estimated_input_tokens", 0, "estimated_output_tokens", 0,
                "token_estimate_basis", "NO_MODEL_INVOCATION_PLANNED",
                "data_transfer_scope", "WORKSPACE_LOCAL_ONLY",
                "estimated_external_transfer_bytes", 0,
                "network_egress", "DENY_BY_DEFAULT", "paid_service_allowed", false));
        value.put("permissions", Map.of(
                "read_source", true, "execute_reviewed_fixtures", true,
                "write_run_evidence", true, "modify_target", false,
                "git_commit", false, "git_push", false, "merge", false));
        value.put("stop_conditions", List.of("SOURCE_DRIFT", "SANDBOX_UNAVAILABLE", "APPROVAL_REVOKED"));
        value.put("approval", Map.of(
                "state", "AWAITING_USER_APPROVAL", "scope", "NO_EXECUTION",
                "approver", "NOT_ASSIGNED", "revocable", true,
                "approved_actions", List.of(), "signed_receipt_required", true));
        value.put("created_at", Instant.now().toString());
        value.put("product_full_chain", "NOT_RUN");
        value.put("independent_assurance", "NOT_RUN");
        value.put("final_claim_allowed", false);
        return value;
    }
}
