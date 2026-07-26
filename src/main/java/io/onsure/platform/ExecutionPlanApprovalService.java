package io.onsure.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.onsure.assurance.ApprovalReceiptVerifier;
import io.onsure.assurance.Decision;
import io.onsure.assurance.ValidationResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Approves one immutable execution plan without authorizing final, merge or deploy actions. */
public final class ExecutionPlanApprovalService {
    public static final String APPROVAL_CONTRACT = "ONSURE_EXECUTION_PLAN_APPROVAL_V1";
    public static final String PURPOSE = "EXECUTION_PLAN_APPROVAL";
    private static final Set<String> APPROVABLE_ACTIONS = Set.of(
            "PROGRAM_PROFILE", "STATIC_ANALYSIS", "AI_BEHAVIOR_VALIDATION",
            "FIXTURE_EXECUTION", "REVIEW", "RCA", "IMPROVEMENT_PLAN",
            "REGRESSION_LOCK", "EVIDENCE_GENERATION");

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public Map<String, Object> approve(
            Path planFile,
            Path approvalReceiptFile,
            Path trustedKeyRegistry,
            Path approvalReplayLedger,
            Path outputFile) throws Exception {
        Map<String, Object> plan = read(planFile, ExecutionPlanService.CONTRACT, "EXECUTION_PLAN");
        Map<String, Object> approval = read(
                approvalReceiptFile, APPROVAL_CONTRACT, "EXECUTION_PLAN_APPROVAL");
        String planFileSha = fileSha(planFile);
        if (!planFileSha.equals(approval.get("plan_file_sha256"))) {
            throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_FILE_DIGEST_MISMATCH");
        }
        if (!plan.get("plan_id").equals(approval.get("plan_id"))) {
            throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_ID_MISMATCH");
        }
        if (!plan.get("target_id").equals(approval.get("target_id"))) {
            throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_TARGET_MISMATCH");
        }
        if (!plan.get("source_tree_sha256").equals(approval.get("source_tree_sha256"))) {
            throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_SOURCE_MISMATCH");
        }
        if (Boolean.TRUE.equals(approval.get("allow_final_claim"))
                || Boolean.TRUE.equals(approval.get("allow_merge"))
                || Boolean.TRUE.equals(approval.get("allow_deploy"))) {
            throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_UNSAFE_AUTHORITY");
        }
        List<String> plannedActions = strings(plan.get("allowed_actions"));
        List<String> approvedActions = strings(approval.get("approved_actions"));
        if (approvedActions.isEmpty() || !plannedActions.containsAll(approvedActions)
                || !APPROVABLE_ACTIONS.containsAll(approvedActions)) {
            throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_ACTION_SCOPE_INVALID");
        }
        ApprovalReceiptVerifier verifier = new ApprovalReceiptVerifier(
                trustedKeyRegistry, approvalReplayLedger);
        ValidationResult verification = verifier.verify(
                approvalReceiptFile, APPROVAL_CONTRACT, PURPOSE, Instant.now());
        if (verification.decision() != Decision.PASS) {
            throw new IllegalStateException(
                    "EXECUTION_PLAN_APPROVAL_INVALID:" + String.join(",", verification.violations()));
        }
        verifier.requireValidAndConsume(
                approvalReceiptFile, APPROVAL_CONTRACT, PURPOSE, Instant.now());

        Map<String, Object> approved = new LinkedHashMap<>(plan);
        approved.put("approval_state", "USER_APPROVED");
        approved.put("approved_actions", List.copyOf(approvedActions));
        approved.put("approval_id", approval.get("approval_id"));
        approved.put("approval_actor", approval.get("actor"));
        approved.put("approval_key_id", approval.get("key_id"));
        approved.put("approval_receipt_sha256", fileSha(approvalReceiptFile));
        approved.put("approved_at", approval.get("approved_at"));
        approved.put("approval_expires_at", approval.get("expires_at"));
        approved.put("final_claim_allowed", false);
        approved.remove("plan_sha256");
        approved.put("plan_sha256", sha256(mapper.writeValueAsBytes(approved)));
        writeAtomic(outputFile, approved);
        return Map.copyOf(approved);
    }

    public Map<String, Object> verifyApprovedPlan(
            Path approvedPlanFile,
            ValidationModel.ValidationTarget target,
            String expectedSourceTreeSha256) throws Exception {
        Map<String, Object> plan = read(
                approvedPlanFile, ExecutionPlanService.CONTRACT, "APPROVED_EXECUTION_PLAN");
        if (!"USER_APPROVED".equals(plan.get("approval_state"))
                && !"AUTO_APPROVED_DEVELOPMENT_NONFINAL".equals(plan.get("approval_state"))) {
            throw new IllegalStateException("EXECUTION_PLAN_NOT_APPROVED");
        }
        if (!target.targetId().equals(plan.get("target_id"))) {
            throw new IllegalStateException("EXECUTION_PLAN_TARGET_MISMATCH");
        }
        if (!expectedSourceTreeSha256.equals(plan.get("source_tree_sha256"))) {
            throw new IllegalStateException("EXECUTION_PLAN_SOURCE_DRIFT");
        }
        if (Boolean.TRUE.equals(plan.get("final_claim_allowed"))) {
            throw new IllegalStateException("EXECUTION_PLAN_FINAL_AUTHORITY_INVALID");
        }
        if ("USER_APPROVED".equals(plan.get("approval_state"))) {
            if (string(plan, "approval_id").isBlank()
                    || string(plan, "approval_actor").isBlank()
                    || string(plan, "approval_key_id").isBlank()
                    || !string(plan, "approval_receipt_sha256").matches("[0-9a-f]{64}")) {
                throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_LINEAGE_MISSING");
            }
            Instant expiry = Instant.parse(string(plan, "approval_expires_at"));
            if (!Instant.now().isBefore(expiry)) {
                throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_EXPIRED");
            }
        }
        return Map.copyOf(plan);
    }

    private Map<String, Object> read(Path file, String contract, String label) throws Exception {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
            throw new IllegalArgumentException(label + "_FILE_INVALID");
        }
        Map<String, Object> value = mapper.readValue(file.toFile(), new TypeReference<>() {});
        if (!contract.equals(value.get("contract"))) {
            throw new IllegalArgumentException(label + "_CONTRACT_MISMATCH");
        }
        return value;
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof String text && !text.isBlank()) result.add(text);
        }
        return List.copyOf(result);
    }

    private static String string(Map<String, Object> value, String key) {
        Object item = value.get(key);
        return item instanceof String text ? text : "";
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

    private static String fileSha(Path file) throws Exception {
        return sha256(Files.readAllBytes(file));
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}