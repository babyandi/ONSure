package io.onsure.platform;

import io.onsure.assurance.Decision;
import io.onsure.platform.ValidationModel.StageResult;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Maps validation stages to the immutable execution-plan action that authorizes them. */
public final class ExecutionPlanActionPolicy {
    private static final Map<String, String> STAGE_ACTIONS = Map.ofEntries(
            Map.entry("STATIC_ANALYSIS", "STATIC_ANALYSIS"),
            Map.entry("AI_BEHAVIOR_VALIDATION", "AI_BEHAVIOR_VALIDATION"),
            Map.entry("BEHAVIOR_LEARNING", "BEHAVIOR_LEARNING"),
            Map.entry("FIXTURE_ORACLE_REGISTRY", "FIXTURE_EXECUTION"),
            Map.entry("FIXTURE_HARNESS_ORACLE", "FIXTURE_EXECUTION"),
            Map.entry("OREVIEW", "REVIEW"),
            Map.entry("FAILURE_MODE_AND_RCA", "RCA"),
            Map.entry("EVIDENCE_BASED_RCA", "RCA"),
            Map.entry("PATCH_PLANNING", "IMPROVEMENT_PLAN"),
            Map.entry("REMEDIATION_PLANNING", "IMPROVEMENT_PLAN"),
            Map.entry("REGRESSION_LOCK", "REGRESSION_LOCK"),
            Map.entry("INTERNAL_PRODUCT_VERIFIER", "EVIDENCE_GENERATION"),
            Map.entry("INTERNAL_PRODUCT_AUDIT", "EVIDENCE_GENERATION"));

    private ExecutionPlanActionPolicy() {}

    public static String requiredAction(String stageId) {
        return STAGE_ACTIONS.get(stageId);
    }

    public static Set<String> approvedActions(ValidationContext context) {
        Object value = context.attributes().get("execution_plan_approved_actions");
        if (!(value instanceof List<?> list)) return Set.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Object item : list) {
            if (item instanceof String text && !text.isBlank()) result.add(text);
        }
        return Set.copyOf(result);
    }

    public static boolean isApproved(ValidationContext context, String action) {
        return action == null || approvedActions(context).contains(action);
    }

    public static StageResult notApproved(String stageId, String action) {
        Instant now = Instant.now();
        return new StageResult(
                stageId,
                Decision.HOLD,
                now,
                now,
                List.of(),
                Map.of(
                        "execution_state", "NOT_RUN_NOT_APPROVED",
                        "required_action", action,
                        "quality_decision", "HOLD_PARTIAL_EXECUTION_PLAN"));
    }
}
