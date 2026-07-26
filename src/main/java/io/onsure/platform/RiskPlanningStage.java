package io.onsure.platform;

import io.onsure.assurance.Decision;
import io.onsure.platform.ValidationModel.Evidence;
import io.onsure.platform.ValidationModel.StageResult;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Creates and enforces an execution plan before review and runtime validation. */
public final class RiskPlanningStage implements ValidatorStage {
    @Override public String stageId() { return "RISK_BASED_EXECUTION_PLANNING"; }

    @Override public boolean supports(ValidationContext context) { return true; }

    @Override
    public StageResult execute(ValidationContext context) throws Exception {
        Instant started = Instant.now();
        Path profile = context.runRoot().resolve("program-profile.json");
        Path output = context.runRoot().resolve("execution-plan.json");
        int fixtureCount = context.adapter().loadFixtures(context.target()).size();
        ExecutionPlanService service = new ExecutionPlanService();
        Map<String, Object> plan = service.plan(context.target(), profile, fixtureCount, output);
        service.requireApproved(plan);
        String digest = Hashing.file(output);
        String evidenceId = "EV-PLAN-" + digest.substring(0, 16);
        context.addEvidence(new Evidence(
                evidenceId,
                "EXECUTION_PLAN",
                context.runRoot().relativize(output).toString().replace('\\', '/'),
                digest,
                Instant.now(),
                Map.of(
                        "plan_id", plan.get("plan_id"),
                        "risk", plan.get("risk"),
                        "approval", plan.get("approval"),
                        "final_claim_allowed", false)));
        context.putAttribute("execution_plan_id", plan.get("plan_id"));
        context.putAttribute("execution_plan_sha256", digest);
        context.putAttribute("execution_plan_approval", ((Map<?, ?>) plan.get("approval")).get("state"));
        return new StageResult(
                stageId(), Decision.PASS, started, Instant.now(), List.of(),
                Map.of(
                        "plan_id", plan.get("plan_id"),
                        "risk", plan.get("risk"),
                        "fixture_count", fixtureCount,
                        "approval_state", ((Map<?, ?>) plan.get("approval")).get("state"),
                        "product_full_chain", "NOT_RUN"));
    }
}
