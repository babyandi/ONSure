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
        Map<String, Object> plan;
        Decision stageDecision = Decision.PASS;
        try {
            plan = new ImprovementWorkflowService().createPatchPlan(context, output);
        } catch (IllegalStateException noGit) {
            if (!String.valueOf(noGit.getMessage()).startsWith("GIT_COMMAND_FAILED:rev-parse")) {
                throw noGit;
            }
            plan = writeNonGitPlan(context, output);
            stageDecision = Decision.HOLD;
        }
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
                stageId(), stageDecision, started, Instant.now(), List.of(),
                Map.of(
                        "patch_plan_id", plan.get("patch_plan_id"),
                        "hunks", ((List<?>) plan.get("hunks")).size(),
                        "state", plan.get("execution_state"),
                        "approval", "NOT_RUN",
                        "target_modified", false));
    }

    private static Map<String, Object> writeNonGitPlan(
            ValidationContext context, Path output) throws Exception {
        String sourceDigest = String.valueOf(context.attributes().get("source_tree_sha256"));
        if (!sourceDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("PATCH_PLAN_SOURCE_DIGEST_MISSING");
        }
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("contract", ImprovementWorkflowService.PATCH_PLAN_CONTRACT);
        plan.put("patch_plan_id", "PATCH-" + context.job().jobId());
        plan.put("target_id", context.target().targetId());
        plan.put("repository_root_reference", "NOT_AVAILABLE_NON_GIT_TARGET");
        plan.put("target_relative_root", ".");
        plan.put("source_tree_sha256", sourceDigest);
        plan.put("review_id", context.attributes().getOrDefault("review_id", "NOT_RUN"));
        plan.put("evidence_based_rca_sha256",
                context.attributes().getOrDefault("evidence_based_rca_sha256", "NOT_RUN"));
        plan.put("hunks", List.of());
        plan.put("preapply_assessment",
                ImprovementWorkflowService.buildPreapplyAssessment(List.of(), context.findings()));
        plan.put("default_approval", "DENY");
        plan.put("worktree_required", true);
        plan.put("direct_main_write_allowed", false);
        plan.put("force_push_allowed", false);
        plan.put("merge_allowed", false);
        plan.put("created_at", Instant.now().toString());
        plan.put("execution_state", "NO_SAFE_PATCH_CANDIDATE");
        plan.put("limitations", List.of("GIT_REPOSITORY_REQUIRED_FOR_APPROVED_PATCH_APPLICATION"));
        plan.put("final_claim_allowed", false);
        ObjectMapper mapper = new ObjectMapper()
                .findAndRegisterModules()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        plan.put("patch_plan_sha256", Hashing.sha256(mapper.writeValueAsString(plan)));
        Path normalized = output.toAbsolutePath().normalize();
        Files.createDirectories(normalized.getParent());
        Path temporary = normalized.resolveSibling(normalized.getFileName() + ".tmp");
        mapper.writeValue(temporary.toFile(), plan);
        try {
            Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING);
        }
        return Map.copyOf(plan);
    }
}
