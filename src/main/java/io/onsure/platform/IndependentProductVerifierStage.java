package io.onsure.platform;

import io.onsure.assurance.Decision;
import io.onsure.platform.ValidationModel.Finding;
import io.onsure.platform.ValidationModel.FixtureResult;
import io.onsure.platform.ValidationModel.StageResult;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Same-process, nonfinal cross-check. This is not an independent external attestation. */
public final class IndependentProductVerifierStage implements ValidatorStage {
    @Override public String stageId() { return "INTERNAL_PRODUCT_VERIFIER"; }
    @Override public boolean supports(ValidationContext context) { return true; }

    @Override
    public StageResult execute(ValidationContext context) throws Exception {
        Instant start = Instant.now();
        ValidationCompletionGate.requireRuntimeCoverage(context, "INDEPENDENT_VERIFIER");
        if (context.regressionLock() == null) throw new IllegalStateException("REGRESSION_LOCK_MISSING");
        String sourceDigest = Hashing.tree(context.target().sourceRoot());
        if (!sourceDigest.equals(context.regressionLock().sourceDigest())) {
            throw new IllegalStateException("INDEPENDENT_SOURCE_DIGEST_MISMATCH");
        }
        for (FixtureResult fixture : context.fixtureResults()) {
            Decision recalculated = switch (fixture.oracleId()) {
                case "EQUALS" -> Objects.equals(fixture.expected(), fixture.observed())
                        ? Decision.PASS : Decision.FAIL;
                case "CONTAINS" -> fixture.observed().contains(fixture.expected())
                        ? Decision.PASS : Decision.FAIL;
                default -> throw new IllegalStateException("INDEPENDENT_UNKNOWN_ORACLE");
            };
            if (recalculated != fixture.decision()) {
                throw new IllegalStateException("INDEPENDENT_FIXTURE_DECISION_MISMATCH: " + fixture.fixtureId());
            }
        }
        String findings = context.findings().stream()
                .sorted(Comparator.comparing(Finding::fingerprint))
                .map(value -> value.fingerprint() + ":" + value.status() + ":" + value.severity())
                .reduce("", (a, b) -> a + "|" + b);
        String fixtures = context.fixtureResults().stream()
                .sorted(Comparator.comparing(FixtureResult::fixtureId))
                .map(value -> value.fixtureId() + ":" + value.harnessId() + ":" + value.oracleId()
                        + ":" + value.expected() + ":" + value.observed() + ":" + value.decision())
                .reduce("", (a, b) -> a + "|" + b);
        String resultDigest = Hashing.sha256(findings + fixtures);
        if (!resultDigest.equals(context.regressionLock().resultDigest())) {
            throw new IllegalStateException("INDEPENDENT_RESULT_DIGEST_MISMATCH");
        }
        String lockDigest = Hashing.sha256("ONSURE_REGRESSION_LOCK_V1|" + context.target().targetId()
                + "|" + sourceDigest + "|" + resultDigest);
        if (!lockDigest.equals(context.regressionLock().lockDigest())) {
            throw new IllegalStateException("INDEPENDENT_REGRESSION_LOCK_MISMATCH");
        }
        ProductReceiptWriter.write(
                context.runRoot().resolve("internal-verifier-receipt.json"),
                "ONSURE_INTERNAL_VERIFIER_RECEIPT_V1", "ONSURE_INTERNAL_VERIFIER",
                context.job().jobId(), Map.of(
                        "assurance_class", "INTERNAL_NONFINAL",
                        "execution_context", "SAME_PROCESS_MUTABLE_CONTEXT",
                        "source_digest", sourceDigest,
                        "result_digest", resultDigest,
                        "regression_lock_digest", lockDigest,
                        "finding_count", context.findings().size(),
                        "fixture_count", context.fixtureResults().size()));
        return new StageResult(stageId(), Decision.PASS, start, Instant.now(), List.of(),
                Map.of("source_digest", sourceDigest, "result_digest", resultDigest));
    }
}
