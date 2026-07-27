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
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Approves one immutable execution plan without authorizing final, merge or deploy actions. */
public final class ExecutionPlanApprovalService {
    public static final String APPROVAL_CONTRACT = "ONSURE_EXECUTION_PLAN_APPROVAL_V1";
    public static final String PURPOSE = "EXECUTION_PLAN_APPROVAL";

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
        new ExecutionPlanService().verifyPlanHash(plan);
        Map<String, Object> approval = read(
                approvalReceiptFile, APPROVAL_CONTRACT, "EXECUTION_PLAN_APPROVAL");
        String planFileSha = Hashing.file(planFile);
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
        Set<String> plannedActions = strings(plan.get("allowed_actions"));
        Set<String> approvedActions = strings(approval.get("approved_actions"));
        if (plannedActions.isEmpty()
                || !plannedActions.equals(approvedActions)
                || !ExecutionPlanService.APPROVABLE_ACTIONS.containsAll(approvedActions)) {
            throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_ACTION_SCOPE_INCOMPLETE");
        }

        ApprovalReceiptVerifier verifier = new ApprovalReceiptVerifier(
                trustedKeyRegistry, approvalReplayLedger);
        ValidationResult verification = verifier.verify(
                approvalReceiptFile, APPROVAL_CONTRACT, PURPOSE, Instant.now());
        if (verification.decision() != Decision.PASS) {
            throw new IllegalStateException(
                    "EXECUTION_PLAN_APPROVAL_INVALID:" + String.join(",", verification.violations()));
        }

        Map<String, Object> approvalState = new LinkedHashMap<>();
        approvalState.put("state", "USER_APPROVED");
        approvalState.put("scope", "EXACT_PLAN_ACTION_SET");
        approvalState.put("approver", approval.get("actor"));
        approvalState.put("revocable", true);
        approvalState.put("approved_actions", approvedActions.stream().sorted().toList());
        approvalState.put("signed_receipt_required", true);
        approvalState.put("approval_id", approval.get("approval_id"));
        approvalState.put("approval_actor", approval.get("actor"));
        approvalState.put("approval_key_id", approval.get("key_id"));
        approvalState.put("approval_receipt_sha256", Hashing.file(approvalReceiptFile));
        approvalState.put("approved_at", approval.get("approved_at"));
        approvalState.put("expires_at", approval.get("expires_at"));

        Map<String, Object> approved = new LinkedHashMap<>(plan);
        approved.put("approval", Map.copyOf(approvalState));
        approved.put("final_claim_allowed", false);
        approved.remove("plan_sha256");
        approved.put("plan_sha256", new ExecutionPlanService().planHash(approved));

        // Security first: consume the nonce before an approved artifact becomes visible.
        verifier.requireValidAndConsume(
                approvalReceiptFile, APPROVAL_CONTRACT, PURPOSE, Instant.now());
        try {
            writeAtomic(outputFile, approved);
        } catch (Exception failure) {
            // A consumed approval with no artifact is safe and requires a new approval to retry.
            throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_PUBLISH_FAILED_AFTER_CONSUME", failure);
        }
        return Map.copyOf(approved);
    }

    public Map<String, Object> verifyApprovedPlan(
            Path approvedPlanFile,
            ValidationModel.ValidationTarget target,
            String expectedSourceTreeSha256) throws Exception {
        Map<String, Object> plan = read(
                approvedPlanFile, ExecutionPlanService.CONTRACT, "APPROVED_EXECUTION_PLAN");
        new ExecutionPlanService().verifyPlanHash(plan);
        if (!target.targetId().equals(plan.get("target_id"))) {
            throw new IllegalStateException("EXECUTION_PLAN_TARGET_MISMATCH");
        }
        if (!expectedSourceTreeSha256.equals(plan.get("source_tree_sha256"))) {
            throw new IllegalStateException("EXECUTION_PLAN_SOURCE_DRIFT");
        }
        if (Boolean.TRUE.equals(plan.get("final_claim_allowed"))) {
            throw new IllegalStateException("EXECUTION_PLAN_FINAL_AUTHORITY_INVALID");
        }
        Object value = plan.get("approval");
        if (!(value instanceof Map<?, ?> approval)) {
            throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_MISSING");
        }
        String state = String.valueOf(approval.get("state"));
        if (!"USER_APPROVED".equals(state)
                && !"AUTO_APPROVED_DEVELOPMENT_NONFINAL".equals(state)) {
            throw new IllegalStateException("EXECUTION_PLAN_NOT_APPROVED");
        }
        Set<String> planned = strings(plan.get("allowed_actions"));
        Set<String> approved = strings(approval.get("approved_actions"));
        if (planned.isEmpty() || !planned.equals(approved)
                || !ExecutionPlanService.APPROVABLE_ACTIONS.containsAll(approved)) {
            throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_ACTION_SET_INCOMPLETE");
        }
        if ("USER_APPROVED".equals(state)) {
            for (String field : List.of(
                    "approval_id", "approval_actor", "approval_key_id",
                    "approval_receipt_sha256", "approved_at", "expires_at")) {
                Object item = approval.get(field);
                if (!(item instanceof String text) || text.isBlank()) {
                    throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_LINEAGE_MISSING:" + field);
                }
            }
            if (!String.valueOf(approval.get("approval_receipt_sha256")).matches("[0-9a-f]{64}")) {
                throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_RECEIPT_DIGEST_INVALID");
            }
            if (!Instant.now().isBefore(Instant.parse(String.valueOf(approval.get("expires_at"))))) {
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

    private static Set<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return Set.of();
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        for (Object item : list) {
            if (item instanceof String text && !text.isBlank()) result.add(text);
        }
        return Set.copyOf(result);
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
}
