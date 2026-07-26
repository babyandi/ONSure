package io.onsure.platform;

import io.onsure.assurance.Decision;
import io.onsure.platform.ValidationModel.Evidence;
import io.onsure.platform.ValidationModel.StageResult;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Persists the multi-domain OReview result without converting review quality into final authority. */
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
        context.addEvidence(new Evidence(
                evidenceId,
                "OREVIEW_RESULT",
                context.runRoot().relativize(output).toString().replace('\\', '/'),
                digest,
                Instant.now(),
                Map.of(
                        "review_id", review.get("review_id"),
                        "quality_decision", review.get("quality_decision"),
                        "review_execution", review.get("review_execution"),
                        "independent_reviewer", "NOT_RUN",
                        "merge_authorized", false)));
        context.putAttribute("review_id", review.get("review_id"));
        context.putAttribute("review_sha256", digest);
        context.putAttribute("review_quality_decision", review.get("quality_decision"));
        return new StageResult(
                stageId(), Decision.PASS, started, Instant.now(), List.of(),
                Map.of(
                        "review_id", review.get("review_id"),
                        "domain_count", ((List<?>) review.get("domains")).size(),
                        "quality_decision", review.get("quality_decision"),
                        "review_execution", "PASS",
                        "merge_authorized", false));
    }
}
