package io.onsure.platform;

import io.onsure.assurance.Decision;
import io.onsure.platform.BehaviorLearningService.ValidationTargetBundle;
import io.onsure.platform.ValidationModel.Evidence;
import io.onsure.platform.ValidationModel.StageResult;
import io.onsure.platform.ValidationModel.TargetType;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Repeats executable scenarios to materialize an AI Behavior Profile candidate. */
public final class BehaviorLearningStage implements ValidatorStage {
    private static final int DEFAULT_REPETITIONS = 2;

    @Override public String stageId() { return "BEHAVIOR_LEARNING"; }

    @Override
    public boolean supports(ValidationContext context) {
        return context.target().targetType() == TargetType.AI_APPLICATION
                || context.target().targetType() == TargetType.AI_AGENTIC_PLATFORM;
    }

    @Override
    public StageResult execute(ValidationContext context) throws Exception {
        Instant started = Instant.now();
        Object profileId = context.attributes().get("program_profile_id");
        if (!(profileId instanceof String text) || text.isBlank()) {
            throw new IllegalStateException("PROGRAM_PROFILE_REQUIRED_FOR_BEHAVIOR_LEARNING");
        }
        Path output = context.runRoot().resolve("behavior-profile.json");
        int repetitions = repetitionCount(context);
        Map<String, Object> profile = new BehaviorLearningService().learn(
                new ValidationTargetBundle(context.target(), context.adapter()),
                text, repetitions, output);
        String behaviorProfileId = profile.get("profile_id").toString();
        String coverageClass = profile.get("coverage_class").toString();
        String digest = Hashing.file(output);
        String evidenceId = "EV-BEHAVIOR-PROFILE-" + digest.substring(0, 16);
        Map<?, ?> variability = (Map<?, ?>) profile.get("variability");
        context.addEvidence(new Evidence(
                evidenceId,
                "BEHAVIOR_PROFILE_CANDIDATE",
                context.runRoot().relativize(output).toString().replace('\\', '/'),
                digest,
                Instant.now(),
                Map.of(
                        "profile_id", behaviorProfileId,
                        "state", profile.get("state"),
                        "coverage_class", coverageClass,
                        "repeated_run_count", variability.get("repeated_run_count"),
                        "stable", variability.get("stable"),
                        "receipt_directory", profile.get("receipt_directory"),
                        "independent_validation", "NOT_RUN",
                        "final_claim_allowed", false)));
        context.putAttribute("behavior_profile_id", behaviorProfileId);
        context.putAttribute("behavior_profile_sha256", digest);
        context.putAttribute("behavior_profile_state", profile.get("state"));
        context.putAttribute("behavior_profile_path", output.toString());
        context.putAttribute("behavior_profile_stable", variability.get("stable"));
        context.putAttribute("behavior_profile_coverage_class", coverageClass);
        context.putAttribute("behavior_receipt_directory", profile.get("receipt_directory"));
        return new StageResult(
                stageId(), Decision.PASS, started, Instant.now(), List.of(),
                Map.of(
                        "profile_id", behaviorProfileId,
                        "coverage_class", coverageClass,
                        "observations", ((List<?>) profile.get("observations")).size(),
                        "repetitions", repetitions,
                        "stable", variability.get("stable"),
                        "unstable_scenarios", variability.get("unstable_scenarios"),
                        "quality_decision", "NOT_APPLICABLE_PROFILE_GENERATION_ONLY"));
    }

    private static int repetitionCount(ValidationContext context) {
        Object value = context.attributes().get("behavior_learning_repetitions");
        if (value instanceof Number number) {
            int repetitions = number.intValue();
            if (repetitions >= 2 && repetitions <= 10) return repetitions;
        }
        return DEFAULT_REPETITIONS;
    }
}
