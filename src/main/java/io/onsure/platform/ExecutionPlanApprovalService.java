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
import java.util.ArrayList;
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
            Path planFile, Path approvalReceiptFile, Path trustedKeyRegistry,
            Path approvalReplayLedger, Path outputFile) throws Exception {
        Map<String, Object> plan = read(planFile, ExecutionPlanService.CONTRACT, "EXECUTION_PLAN");
        new ExecutionPlanService().verifyPlanHash(plan);
        Map<String, Object> approval = read(
                approvalReceiptFile, APPROVAL_CONTRACT, "EXECUTION_PLAN_APPROVAL");
        String planFileSha = Hashing.file(planFile);
        if (!planFileSha.equals(approval.get("plan_file_sha256"))) {
            throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_FILE_DIGEST_MISMATCH");
        }
        requireReceiptPlanBinding(plan, approval);
        if (Boolean.TRUE.equals(approval.get("allow_final_claim"))
                || Boolean.TRUE.equals(approval.get("allow_merge"))
                || Boolean.TRUE.equals(approval.get("allow_deploy"))) {
            throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_UNSAFE_AUTHORITY");
        }
        Set<String> plannedActions = strings(plan.get("allowed_actions"));
        Set<String> approvedActions = strings(approval.get("approved_actions"));
        requireApprovedSubset(plannedActions, approvedActions);

        ApprovalReceiptVerifier verifier = new ApprovalReceiptVerifier(
                trustedKeyRegistry, approvalReplayLedger);
        ValidationResult verification = verifier.verify(
                approvalReceiptFile, APPROVAL_CONTRACT, PURPOSE, Instant.now());
        if (verification.decision() != Decision.PASS) {
            throw new IllegalStateException(
                    "EXECUTION_PLAN_APPROVAL_INVALID:" + String.join(",", verification.violations()));
        }

        boolean partial = !plannedActions.equals(approvedActions);
        Map<String, Object> approvalState = approvalState(
                approval, approvedActions, partial, planFileSha, Hashing.file(approvalReceiptFile));
        Map<String, Object> approved = derivedApprovedPlan(plan, approvalState);

        verifier.requireValidAndConsume(
                approvalReceiptFile, APPROVAL_CONTRACT, PURPOSE, Instant.now());
        try {
            writeAtomic(outputFile, approved);
        } catch (Exception failure) {
            throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_PUBLISH_FAILED_AFTER_CONSUME", failure);
        }
        return Map.copyOf(approved);
    }

    /** Verifies the complete consumed approval bundle at the engine boundary. */
    public Map<String, Object> verifyApprovedPlanBundle(
            Path approvedPlanFile,
            Path originalPlanFile,
            Path signedApprovalReceipt,
            Path trustedKeyRegistry,
            Path approvalReplayLedger,
            ValidationModel.ValidationTarget target,
            String expectedSourceTreeSha256) throws Exception {
        Map<String, Object> original = read(
                originalPlanFile, ExecutionPlanService.CONTRACT, "ORIGINAL_EXECUTION_PLAN");
        Map<String, Object> approved = read(
                approvedPlanFile, ExecutionPlanService.CONTRACT, "APPROVED_EXECUTION_PLAN");
        Map<String, Object> signed = read(
                signedApprovalReceipt, APPROVAL_CONTRACT, "SIGNED_EXECUTION_PLAN_APPROVAL");
        ExecutionPlanService service = new ExecutionPlanService();
        service.verifyPlanHash(original);
        service.verifyPlanHash(approved);

        if (!target.targetId().equals(original.get("target_id"))
                || !target.targetId().equals(approved.get("target_id"))) {
            throw new IllegalStateException("EXECUTION_PLAN_TARGET_MISMATCH");
        }
        if (!expectedSourceTreeSha256.equals(original.get("source_tree_sha256"))
                || !expectedSourceTreeSha256.equals(approved.get("source_tree_sha256"))) {
            throw new IllegalStateException("EXECUTION_PLAN_SOURCE_DRIFT");
        }
        if (Boolean.TRUE.equals(approved.get("final_claim_allowed"))) {
            throw new IllegalStateException("EXECUTION_PLAN_FINAL_AUTHORITY_INVALID");
        }
        requireReceiptPlanBinding(original, signed);
        String originalFileSha = Hashing.file(originalPlanFile);
        if (!originalFileSha.equals(signed.get("plan_file_sha256"))) {
            throw new IllegalStateException("EXECUTION_PLAN_ORIGINAL_FILE_DIGEST_MISMATCH");
        }

        Object approvalValue = approved.get("approval");
        if (!(approvalValue instanceof Map<?, ?> approval)) {
            throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_MISSING");
        }
        if (!"USER_APPROVED".equals(String.valueOf(approval.get("state")))) {
            throw new IllegalStateException("EXECUTION_PLAN_USER_APPROVAL_REQUIRED");
        }
        if (!originalFileSha.equals(String.valueOf(approval.get("original_plan_file_sha256")))) {
            throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_ORIGINAL_DIGEST_MISMATCH");
        }
        String receiptSha = Hashing.file(signedApprovalReceipt);
        if (!receiptSha.equals(String.valueOf(approval.get("approval_receipt_sha256")))) {
            throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_RECEIPT_DIGEST_MISMATCH");
        }
        if (!String.valueOf(signed.get("approval_id")).equals(String.valueOf(approval.get("approval_id")))
                || !String.valueOf(signed.get("actor")).equals(String.valueOf(approval.get("approval_actor")))
                || !String.valueOf(signed.get("key_id")).equals(String.valueOf(approval.get("approval_key_id")))) {
            throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_IDENTITY_MISMATCH");
        }
        Set<String> planned = strings(original.get("allowed_actions"));
        Set<String> approvedActions = strings(approval.get("approved_actions"));
        Set<String> signedActions = strings(signed.get("approved_actions"));
        requireApprovedSubset(planned, approvedActions);
        if (!approvedActions.equals(signedActions)) {
            throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_SIGNED_SCOPE_MISMATCH");
        }
        String expectedScope = planned.equals(approvedActions)
                ? "EXACT_PLAN_ACTION_SET" : "PARTIAL_PLAN_ACTION_SET";
        if (!expectedScope.equals(String.valueOf(approval.get("scope")))) {
            throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_SCOPE_MISMATCH");
        }
        if (!Instant.now().isBefore(Instant.parse(String.valueOf(approval.get("expires_at"))))) {
            throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_EXPIRED");
        }

        ApprovalReceiptVerifier verifier = new ApprovalReceiptVerifier(
                trustedKeyRegistry, approvalReplayLedger);
        ValidationResult verification = verifier.verify(
                signedApprovalReceipt, APPROVAL_CONTRACT, PURPOSE, Instant.now());
        List<String> residual = new ArrayList<>(verification.violations());
        boolean consumed = residual.remove("APPROVAL_RECEIPT_REPLAY");
        if (!consumed || !residual.isEmpty()) {
            throw new IllegalStateException(
                    "EXECUTION_PLAN_CONSUMED_APPROVAL_INVALID:" + String.join(",", verification.violations()));
        }

        Map<String, Object> expected = derivedApprovedPlan(original, objectMap(approval));
        if (!expected.equals(approved)) {
            throw new IllegalStateException("EXECUTION_PLAN_APPROVED_ARTIFACT_DERIVATION_MISMATCH");
        }
        return Map.copyOf(approved);
    }

    /** Legacy path-only verification is intentionally fail-closed. */
    @Deprecated
    public Map<String, Object> verifyApprovedPlan(
            Path approvedPlanFile,
            ValidationModel.ValidationTarget target,
            String expectedSourceTreeSha256) {
        throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_BUNDLE_REQUIRED");
    }

    private Map<String, Object> approvalState(
            Map<String, Object> signed, Set<String> approvedActions, boolean partial,
            String originalPlanFileSha, String signedReceiptSha) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("state", "USER_APPROVED");
        state.put("scope", partial ? "PARTIAL_PLAN_ACTION_SET" : "EXACT_PLAN_ACTION_SET");
        state.put("approver", signed.get("actor"));
        state.put("revocable", true);
        state.put("approved_actions", approvedActions.stream().sorted().toList());
        state.put("signed_receipt_required", true);
        state.put("approval_id", signed.get("approval_id"));
        state.put("approval_actor", signed.get("actor"));
        state.put("approval_key_id", signed.get("key_id"));
        state.put("original_plan_file_sha256", originalPlanFileSha);
        state.put("approval_receipt_sha256", signedReceiptSha);
        state.put("approved_at", signed.get("approved_at"));
        state.put("expires_at", signed.get("expires_at"));
        return Map.copyOf(state);
    }

    private Map<String, Object> derivedApprovedPlan(
            Map<String, Object> original, Map<String, Object> approvalState) throws Exception {
        Map<String, Object> approved = new LinkedHashMap<>(original);
        approved.put("approval", Map.copyOf(approvalState));
        approved.put("final_claim_allowed", false);
        approved.remove("plan_sha256");
        approved.put("plan_sha256", new ExecutionPlanService().planHash(approved));
        return Map.copyOf(approved);
    }

    private static void requireReceiptPlanBinding(
            Map<String, Object> plan, Map<String, Object> approval) {
        if (!plan.get("plan_id").equals(approval.get("plan_id"))) {
            throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_ID_MISMATCH");
        }
        if (!plan.get("target_id").equals(approval.get("target_id"))) {
            throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_TARGET_MISMATCH");
        }
        if (!plan.get("source_tree_sha256").equals(approval.get("source_tree_sha256"))) {
            throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_SOURCE_MISMATCH");
        }
    }

    private static void requireApprovedSubset(Set<String> planned, Set<String> approved) {
        if (planned.isEmpty() || approved.isEmpty() || !planned.containsAll(approved)
                || !ExecutionPlanService.APPROVABLE_ACTIONS.containsAll(approved)) {
            throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_ACTION_SCOPE_INVALID");
        }
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

    private static Map<String, Object> objectMap(Map<?, ?> value) {
        Map<String, Object> result = new LinkedHashMap<>();
        value.forEach((key, item) -> result.put(String.valueOf(key), item));
        return Map.copyOf(result);
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
