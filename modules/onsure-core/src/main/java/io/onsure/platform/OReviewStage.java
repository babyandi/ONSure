package io.onsure.platform;

import io.onsure.assurance.Decision;
import io.onsure.platform.ValidationModel.Evidence;
import io.onsure.platform.ValidationModel.StageResult;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Persists multi-domain OReview and propagates its quality decision without final authority. */
public final class OReviewStage implements ValidatorStage {
    @Override public String stageId() { return "OREVIEW"; }
    @Override public boolean supports(ValidationContext context) { return true; }

    @Override
    public StageResult execute(ValidationContext context) throws Exception {
        Instant started = Instant.now();
        Path output = context.runRoot().resolve("review-result.json");
        Map<String, Object> review = new OReviewService().review(context, output);
        String digest = Hashing.file(output);
        String evidenceId = "EV-REVIEW-" + digest.substring(0, 16);
        String quality = String.valueOf(review.get("quality_decision"));
        Decision decision = switch (quality) {
            case "PASS" -> Decision.PASS;
            case "HOLD" -> Decision.HOLD;
            case "FAIL" -> Decision.FAIL;
            default -> throw new IllegalStateException("OREVIEW_QUALITY_DECISION_INVALID:" + quality);
        };
        context.addEvidence(new Evidence(
                evidenceId,
                "OREVIEW_RESULT",
                context.runRoot().relativize(output).toString().replace('\\', '/'),
                digest,
                Instant.now(),
                Map.of(
                        "review_id", review.get("review_id"),
                        "quality_decision", quality,
                        "review_execution", review.get("review_execution"),
                        "execution_plan_approval_sha256", review.get("execution_plan_approval_sha256"),
                        "independent_reviewer", "NOT_RUN",
                        "merge_authorized", false)));
        context.putAttribute("review_id", review.get("review_id"));
        context.putAttribute("review_sha256", digest);
        context.putAttribute("review_quality_decision", quality);
        return new StageResult(
                stageId(), decision, started, Instant.now(), List.of(),
                Map.of(
                        "review_id", review.get("review_id"),
                        "domain_count", ((List<?>) review.get("domains")).size(),
                        "quality_decision", quality,
                        "review_execution", "PASS",
                        "independent_reviewer", "NOT_RUN",
                        "merge_authorized", false));
    }
}
