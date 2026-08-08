package kr.co.oruda.onsure.platform;

import kr.co.oruda.onsure.assurance.Decision;
import kr.co.oruda.onsure.platform.ValidationModel.Evidence;
import kr.co.oruda.onsure.platform.ValidationModel.StageResult;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Materializes a static evidence-bound Program Profile candidate for every target. */
public final class ProgramLearningStage implements ValidatorStage {
    @Override public String stageId() { return "PROGRAM_LEARNING"; }

    @Override public boolean supports(ValidationContext context) { return true; }

    @Override
    public StageResult execute(ValidationContext context) throws Exception {
        Instant started = Instant.now();
        Path output = context.runRoot().resolve("program-profile.json");
        String projectId = stringAttribute(context, "project_id", "PROJECT-" + context.target().targetId());
        Map<String, Object> profile = new ProgramLearningService().learn(
                context.target().sourceRoot(), projectId, context.target().targetId(), output);
        String profileId = profile.get("profile_id").toString();
        String profileDigest = Hashing.file(output);
        String evidenceId = "EV-PROGRAM-PROFILE-" + profileDigest.substring(0, 16);
        context.addEvidence(new Evidence(
                evidenceId,
                "PROGRAM_PROFILE_CANDIDATE",
                context.runRoot().relativize(output).toString().replace('\\', '/'),
                profileDigest,
                Instant.now(),
                Map.of(
                        "profile_id", profileId,
                        "state", profile.get("state"),
                        "learning_method", profile.get("learning_method"),
                        "dynamic_trace", profile.get("dynamic_trace"),
                        "final_claim_allowed", false)));
        context.putAttribute("program_profile_id", profileId);
        context.putAttribute("program_profile_sha256", profileDigest);
        context.putAttribute("program_profile_state", profile.get("state"));
        context.putAttribute("program_profile_path", output.toString());
        return new StageResult(
                stageId(), Decision.PASS, started, Instant.now(), List.of(),
                Map.of(
                        "profile_id", profileId,
                        "components", ((List<?>) profile.get("components")).size(),
                        "dependencies", ((List<?>) profile.get("dependencies")).size(),
                        "ai_components", ((List<?>) profile.get("ai_components")).size(),
                        "data_flows", ((List<?>) profile.get("data_flows")).size(),
                        "state", profile.get("state"),
                        "quality_decision", "NOT_APPLICABLE_PROFILE_GENERATION_ONLY"));
    }

    private static String stringAttribute(ValidationContext context, String key, String fallback) {
        Object value = context.attributes().get(key);
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }
}
