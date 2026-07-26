package io.onsure.platform;

import io.onsure.assurance.Decision;
import io.onsure.platform.ValidationModel.Evidence;
import io.onsure.platform.ValidationModel.StageResult;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Creates approval-required byte-bounded patch candidates without modifying target source. */
public final class PatchPlanningStage implements ValidatorStage {
    @Override public String stageId() { return "PATCH_PLANNING"; }

    @Override public boolean supports(ValidationContext context) { return true; }

    @Override
    public StageResult execute(ValidationContext context) throws Exception {
        Instant started = Instant.now();
        Path output = context.runRoot().resolve("patch-plan.json");
        Map<String, Object> plan = new ImprovementWorkflowService().createPatchPlan(context, output);
        String digest = Hashing.file(output);
        String evidenceId = "EV-PATCH-PLAN-" + digest.substring(0, 16);
        context.addEvidence(new Evidence(
                evidenceId,
                "PATCH_PLAN_CANDIDATE",
                context.runRoot().relativize(output).toString().replace('\\', '/'),
                digest,
                Instant.now(),
                Map.of(
                        "patch_plan_id", plan.get("patch_plan_id"),
                        "hunk_count", ((List<?>) plan.get("hunks")).size(),
                        "execution_state", plan.get("execution_state"),
                        "approval", "NOT_RUN",
                        "target_modified", false,
                        "merge_allowed", false)));
        context.putAttribute("patch_plan_id", plan.get("patch_plan_id"));
        context.putAttribute("patch_plan_sha256", digest);
        context.putAttribute("patch_plan_hunk_count", ((List<?>) plan.get("hunks")).size());
        context.putAttribute("patch_plan_state", plan.get("execution_state"));
        return new StageResult(
                stageId(), Decision.PASS, started, Instant.now(), List.of(),
                Map.of(
                        "patch_plan_id", plan.get("patch_plan_id"),
                        "hunks", ((List<?>) plan.get("hunks")).size(),
                        "state", plan.get("execution_state"),
                        "approval", "NOT_RUN",
                        "target_modified", false));
    }
}
