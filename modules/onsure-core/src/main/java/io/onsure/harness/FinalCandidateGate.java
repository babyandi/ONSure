package io.onsure.harness;

import io.onsure.harness.HarnessModels.Decision;
import io.onsure.harness.HarnessModels.FinalCandidate;
import io.onsure.harness.HarnessModels.RunSummary;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class FinalCandidateGate {
    public FinalCandidate evaluate(Path run1, Path run2) {
        List<String> reasons = new ArrayList<>();
        RunVerifier.Verification first = new RunVerifier().verify(run1);
        RunVerifier.Verification second = new RunVerifier().verify(run2);
        if (!first.valid()) first.reasons().forEach(value -> reasons.add("RUN1_" + value));
        if (!second.valid()) second.reasons().forEach(value -> reasons.add("RUN2_" + value));

        RunSummary one = first.summary();
        RunSummary two = second.summary();
        String run1Id = one == null ? "UNKNOWN-RUN-1" : one.runId();
        String run2Id = two == null ? "UNKNOWN-RUN-2" : two.runId();
        String seed = run1Id + "|" + run2Id;

        if (one != null && two != null) {
            if (Objects.equals(run1.toAbsolutePath().normalize(), run2.toAbsolutePath().normalize())) {
                reasons.add("RUN_ROOT_REUSED");
            }
            if (Objects.equals(one.runId(), two.runId())) reasons.add("RUN_ID_REUSED");
            if (Objects.equals(one.operatorId(), two.operatorId())) reasons.add("OPERATOR_NOT_INDEPENDENT");
            if (!Objects.equals(one.environmentDigest(), two.environmentDigest())) {
                reasons.add("ENVIRONMENT_DIGEST_MISMATCH");
            }
            requireClean("RUN1", one, reasons);
            requireClean("RUN2", two, reasons);
            if (!Objects.equals(one.normalizedResultDigest(), two.normalizedResultDigest())) {
                reasons.add("NORMALIZED_RESULT_DIGEST_MISMATCH");
            }
            if (!Objects.equals(one.targetId(), two.targetId())) reasons.add("TARGET_ID_MISMATCH");
            seed = one.targetId() + "|" + one.runId() + "|" + two.runId() + "|"
                    + one.environmentDigest() + "|" + one.normalizedResultDigest();
        }

        List<String> uniqueReasons = reasons.stream().distinct().sorted().toList();
        boolean eligible = uniqueReasons.isEmpty();
        String digest = Hashing.sha256(seed + "|" + uniqueReasons);
        return new FinalCandidate(
                "ONSURE_UNIVERSAL_FINAL_CANDIDATE_V1",
                "CANDIDATE-" + digest.substring(0, 24),
                run1Id,
                run2Id,
                eligible,
                eligible ? Decision.PASS : Decision.BLOCKED,
                uniqueReasons,
                digest,
                false,
                Instant.now());
    }

    public FinalCandidate evaluateAndWrite(Path run1, Path run2, Path outputFile) throws Exception {
        FinalCandidate candidate = evaluate(run1, run2);
        JsonSupport.writeAtomic(outputFile, candidate);
        return candidate;
    }

    private static void requireClean(String prefix, RunSummary summary, List<String> reasons) {
        if (summary.decision() != Decision.PASS) reasons.add(prefix + "_DECISION_NOT_PASS");
        if (summary.notRunCount() != 0) reasons.add(prefix + "_NOT_RUN_REMAINS");
        if (summary.blockedCount() != 0) reasons.add(prefix + "_BLOCKED_REMAINS");
        if (summary.criticalDefects() != 0) reasons.add(prefix + "_CRITICAL_DEFECT_REMAINS");
        if (summary.majorDefects() != 0) reasons.add(prefix + "_MAJOR_DEFECT_REMAINS");
        if (summary.axisResults().size() != 30) reasons.add(prefix + "_AXIS_COUNT_MISMATCH");
        if (summary.axisResults().stream().anyMatch(value -> value.decision() != Decision.PASS)) {
            reasons.add(prefix + "_AXIS_NON_PASS_REMAINS");
        }
    }
}
