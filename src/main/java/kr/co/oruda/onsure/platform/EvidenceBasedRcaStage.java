package kr.co.oruda.onsure.platform;

import kr.co.oruda.onsure.assurance.Decision;
import kr.co.oruda.onsure.platform.ValidationModel.Evidence;
import kr.co.oruda.onsure.platform.ValidationModel.StageResult;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Produces candidate/confirmed RCA records after runtime evidence exists. */
public final class EvidenceBasedRcaStage implements ValidatorStage {
    @Override public String stageId() { return "EVIDENCE_BASED_RCA"; }

    @Override public boolean supports(ValidationContext context) { return true; }

    @Override
    public StageResult execute(ValidationContext context) throws Exception {
        Instant started = Instant.now();
        Path output = context.runRoot().resolve("evidence-based-rca.json");
        Map<String, Object> result = new EvidenceBasedRcaService().analyze(context, output);
        String digest = Hashing.file(output);
        String evidenceId = "EV-RCA-" + digest.substring(0, 16);
        context.addEvidence(new Evidence(
                evidenceId,
                "EVIDENCE_BASED_RCA_SET",
                context.runRoot().relativize(output).toString().replace('\\', '/'),
                digest,
                Instant.now(),
                Map.of(
                        "confirmed_count", result.get("confirmed_count"),
                        "candidate_count", result.get("candidate_count"),
                        "independent_confirmation", "NOT_RUN",
                        "final_claim_allowed", false)));
        context.putAttribute("evidence_based_rca_sha256", digest);
        context.putAttribute("evidence_based_rca_confirmed_count", result.get("confirmed_count"));
        context.putAttribute("evidence_based_rca_candidate_count", result.get("candidate_count"));
        return new StageResult(
                stageId(), Decision.PASS, started, Instant.now(), List.of(),
                Map.of(
                        "records", ((List<?>) result.get("records")).size(),
                        "confirmed", result.get("confirmed_count"),
                        "candidates", result.get("candidate_count"),
                        "quality_decision", "ARTIFACT_GENERATED_NONFINAL"));
    }
}
