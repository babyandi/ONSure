package io.onsure.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.onsure.assurance.Decision;
import io.onsure.platform.ValidationModel.Evidence;
import io.onsure.platform.ValidationModel.StageResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Creates and enforces an execution plan before review and runtime validation. */
public final class RiskPlanningStage implements ValidatorStage {
    public static final String APPROVAL_EVIDENCE_CONTRACT =
            "ONSURE_EXECUTION_PLAN_APPROVAL_EVIDENCE_V1";
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    @Override public String stageId() { return "RISK_BASED_EXECUTION_PLANNING"; }
    @Override public boolean supports(ValidationContext context) { return true; }

    @Override
    public StageResult execute(ValidationContext context) throws Exception {
        Instant started = Instant.now();
        Path profile = context.runRoot().resolve("program-profile.json");
        Path output = context.runRoot().resolve("execution-plan.json");
        Path approvalOutput = context.runRoot().resolve("execution-plan-approval.json");
        int fixtureCount = context.adapter().loadFixtures(context.target()).size();
        ExecutionPlanService service = new ExecutionPlanService();
        Map<String, Object> plan;

        Object approvedPlanValue = context.attributes().get("approved_execution_plan_file");
        if (approvedPlanValue instanceof String text && !text.isBlank()) {
            Path approvedPlan = Path.of(text).toAbsolutePath().normalize();
            Path originalPlan = requiredBundlePath(context, "original_execution_plan_file");
            Path signedReceipt = requiredBundlePath(context, "signed_plan_approval_receipt");
            Path keyRegistry = requiredBundlePath(context, "plan_approval_key_registry");
            Path replayLedger = requiredBundlePath(context, "plan_approval_replay_ledger");
            String sourceDigest = String.valueOf(context.attributes().get("source_tree_sha256"));
            plan = new ExecutionPlanApprovalService().verifyApprovedPlanBundle(
                    approvedPlan, originalPlan, signedReceipt, keyRegistry, replayLedger,
                    context.target(), sourceDigest);
            Files.copy(approvedPlan, output, StandardCopyOption.REPLACE_EXISTING);
        } else {
            plan = service.plan(context.target(), profile, fixtureCount, output);
            service.requireApproved(plan);
        }

        service.requireApproved(plan);
        String digest = Hashing.file(output);
        Map<?, ?> approval = requireApproval(plan);
        List<String> allowedActions = stringList(plan.get("allowed_actions"));
        List<String> approvedActions = stringList(approval.get("approved_actions"));
        Set<String> approvedSet = Set.copyOf(approvedActions);
        List<String> unapprovedActions = allowedActions.stream()
                .filter(value -> !approvedSet.contains(value)).sorted().toList();
        boolean partial = !unapprovedActions.isEmpty();

        Map<String, Object> approvalEvidence = approvalEvidence(plan, approval, digest);
        mapper.writeValue(approvalOutput.toFile(), approvalEvidence);
        String approvalDigest = Hashing.file(approvalOutput);

        context.addEvidence(new Evidence(
                "EV-PLAN-" + digest.substring(0, 16),
                "EXECUTION_PLAN",
                context.runRoot().relativize(output).toString().replace('\\', '/'),
                digest,
                Instant.now(),
                Map.of(
                        "plan_id", plan.get("plan_id"),
                        "risk", plan.get("risk"),
                        "approval_state", approval.get("state"),
                        "approval_scope", approval.get("scope"),
                        "allowed_actions", allowedActions,
                        "approved_actions", approvedActions,
                        "unapproved_actions", unapprovedActions,
                        "approval_evidence_sha256", approvalDigest,
                        "final_claim_allowed", false)));
        context.addEvidence(new Evidence(
                "EV-PLAN-APPROVAL-" + approvalDigest.substring(0, 16),
                "EXECUTION_PLAN_APPROVAL_EVIDENCE",
                context.runRoot().relativize(approvalOutput).toString().replace('\\', '/'),
                approvalDigest,
                Instant.now(),
                Map.of(
                        "plan_id", plan.get("plan_id"),
                        "state", approval.get("state"),
                        "scope", approval.get("scope"),
                        "authority_class", approvalEvidence.get("authority_class"),
                        "approved_actions", approvedActions,
                        "final_claim_allowed", false)));
        context.putAttribute("execution_plan_id", plan.get("plan_id"));
        context.putAttribute("execution_plan_sha256", digest);
        context.putAttribute("execution_plan_approval", approval.get("state"));
        context.putAttribute("execution_plan_approval_scope", approval.get("scope"));
        context.putAttribute("execution_plan_allowed_actions", allowedActions);
        context.putAttribute("execution_plan_approved_actions", approvedActions);
        context.putAttribute("execution_plan_unapproved_actions", unapprovedActions);
        context.putAttribute("execution_plan_partial", partial);
        context.putAttribute("execution_plan_approval_sha256", approvalDigest);
        context.putAttribute("execution_plan_approval_file", approvalOutput.toString());
        return new StageResult(
                stageId(), partial ? Decision.HOLD : Decision.PASS,
                started, Instant.now(), List.of(),
                Map.of(
                        "plan_id", plan.get("plan_id"),
                        "risk", plan.get("risk"),
                        "fixture_count", fixtureCount,
                        "allowed_action_count", allowedActions.size(),
                        "approved_action_count", approvedActions.size(),
                        "unapproved_action_count", unapprovedActions.size(),
                        "approval_state", approval.get("state"),
                        "approval_scope", approval.get("scope"),
                        "approval_evidence_sha256", approvalDigest,
                        "product_full_chain", "NOT_RUN"));
    }

    private static Path requiredBundlePath(ValidationContext context, String attribute) {
        Object value = context.attributes().get(attribute);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_BUNDLE_MISSING:" + attribute);
        }
        return Path.of(text).toAbsolutePath().normalize();
    }

    private static Map<?, ?> requireApproval(Map<String, Object> plan) {
        Object value = plan.get("approval");
        if (!(value instanceof Map<?, ?> approval)) {
            throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_MISSING");
        }
        return approval;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Object item : list) {
            if (item instanceof String text && !text.isBlank()) result.add(text);
        }
        return result.stream().sorted().toList();
    }

    private Map<String, Object> approvalEvidence(
            Map<String, Object> plan, Map<?, ?> approval, String planFileSha) throws Exception {
        boolean userApproved = "USER_APPROVED".equals(approval.get("state"));
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("contract", APPROVAL_EVIDENCE_CONTRACT);
        receipt.put("plan_id", plan.get("plan_id"));
        receipt.put("target_id", plan.get("target_id"));
        receipt.put("source_tree_sha256", plan.get("source_tree_sha256"));
        receipt.put("plan_file_sha256", planFileSha);
        receipt.put("original_plan_file_sha256", userApproved
                ? String.valueOf(approval.get("original_plan_file_sha256")) : "NOT_APPLICABLE");
        receipt.put("plan_sha256", plan.get("plan_sha256"));
        receipt.put("approval_state", approval.get("state"));
        receipt.put("approval_scope", approval.get("scope"));
        receipt.put("approved_actions", approval.get("approved_actions"));
        receipt.put("authority_class", userApproved
                ? "HUMAN_OR_EXTERNAL_APPROVER" : "INTERNAL_POLICY_AUTO_NONFINAL");
        receipt.put("approval_id", userApproved
                ? String.valueOf(approval.get("approval_id")) : "POLICY:LOCAL_DEVELOPMENT");
        receipt.put("approval_actor", userApproved
                ? String.valueOf(approval.get("approval_actor")) : "POLICY:LOCAL_DEVELOPMENT");
        receipt.put("approval_key_id", userApproved
                ? String.valueOf(approval.get("approval_key_id")) : "NOT_APPLICABLE");
        receipt.put("signed_receipt_sha256", userApproved
                ? String.valueOf(approval.get("approval_receipt_sha256")) : "NOT_APPLICABLE");
        receipt.put("created_at", Instant.now().toString());
        receipt.put("final_claim_allowed", false);
        receipt.put("receipt_sha256", Hashing.sha256(
                mapper.writeValueAsBytes(new TreeMap<>(receipt))));
        return Map.copyOf(receipt);
    }
}
