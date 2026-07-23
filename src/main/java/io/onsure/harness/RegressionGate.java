package io.onsure.harness;

import io.onsure.harness.HarnessModels.Decision;
import io.onsure.harness.HarnessModels.FixtureResult;
import io.onsure.harness.HarnessModels.RegressionReceipt;
import io.onsure.harness.HarnessModels.RunSummary;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class RegressionGate {
    public RegressionReceipt evaluate(Path baselineRun, Path regressionRun1, Path regressionRun2) {
        List<String> reasons = new ArrayList<>();
        RunVerifier.Verification baselineVerification = new RunVerifier().verify(baselineRun);
        RunVerifier.Verification firstVerification = new RunVerifier().verify(regressionRun1);
        RunVerifier.Verification secondVerification = new RunVerifier().verify(regressionRun2);
        if (!baselineVerification.valid()) baselineVerification.reasons().forEach(v -> reasons.add("BASELINE_" + v));
        if (!firstVerification.valid()) firstVerification.reasons().forEach(v -> reasons.add("REGRESSION1_" + v));
        if (!secondVerification.valid()) secondVerification.reasons().forEach(v -> reasons.add("REGRESSION2_" + v));

        RunSummary baseline = baselineVerification.summary();
        RunSummary first = firstVerification.summary();
        RunSummary second = secondVerification.summary();
        Set<String> baselineFailures = failingFixtures(baseline);
        Set<String> firstFailures = failingFixtures(first);
        Set<String> secondFailures = failingFixtures(second);
        Set<String> remaining = new HashSet<>(baselineFailures);
        remaining.retainAll(union(firstFailures, secondFailures));
        Set<String> resolved = new HashSet<>(baselineFailures);
        resolved.removeAll(remaining);

        if (baseline != null && baselineFailures.isEmpty()) reasons.add("BASELINE_HAS_NO_FAILURE_TO_REGRESS");
        if (first != null && second != null) {
            if (Objects.equals(first.runId(), second.runId())) reasons.add("REGRESSION_RUN_ID_REUSED");
            if (Objects.equals(first.operatorId(), second.operatorId())) reasons.add("REGRESSION_OPERATOR_NOT_INDEPENDENT");
            if (!Objects.equals(first.environmentDigest(), second.environmentDigest())) {
                reasons.add("REGRESSION_ENVIRONMENT_MISMATCH");
            }
            if (!Objects.equals(first.normalizedResultDigest(), second.normalizedResultDigest())) {
                reasons.add("REGRESSION_RESULT_DIGEST_MISMATCH");
            }
            requireClean("REGRESSION1", first, reasons);
            requireClean("REGRESSION2", second, reasons);
        }
        if (!remaining.isEmpty()) reasons.add("REGRESSION_FAILURE_REMAINS:" + remaining);

        String baselineId = baseline == null ? "UNKNOWN-BASELINE" : baseline.runId();
        String firstId = first == null ? "UNKNOWN-REGRESSION-1" : first.runId();
        String secondId = second == null ? "UNKNOWN-REGRESSION-2" : second.runId();
        List<String> unique = reasons.stream().distinct().sorted().toList();
        boolean eligible = unique.isEmpty();
        String digest = Hashing.sha256(baselineId + "|" + firstId + "|" + secondId + "|"
                + resolved.stream().sorted().toList() + "|" + remaining.stream().sorted().toList() + "|" + unique);
        return new RegressionReceipt(
                "ONSURE_UNIVERSAL_REGRESSION_RECEIPT_V1",
                baselineId,
                firstId,
                secondId,
                eligible,
                eligible ? Decision.PASS : Decision.BLOCKED,
                resolved.stream().sorted().toList(),
                remaining.stream().sorted().toList(),
                unique,
                digest,
                Instant.now());
    }

    public RegressionReceipt evaluateAndWrite(Path baselineRun, Path regressionRun1,
            Path regressionRun2, Path outputFile) throws Exception {
        RegressionReceipt receipt = evaluate(baselineRun, regressionRun1, regressionRun2);
        JsonSupport.writeAtomic(outputFile, receipt);
        return receipt;
    }

    private static Set<String> failingFixtures(RunSummary summary) {
        Set<String> result = new HashSet<>();
        if (summary == null) return result;
        summary.fixtureResults().stream()
                .filter(value -> value.decision() != Decision.PASS)
                .map(FixtureResult::fixtureId)
                .forEach(result::add);
        return result;
    }

    private static Set<String> union(Set<String> one, Set<String> two) {
        Set<String> result = new HashSet<>(one);
        result.addAll(two);
        return result;
    }

    private static void requireClean(String prefix, RunSummary summary, List<String> reasons) {
        if (summary.decision() != Decision.PASS) reasons.add(prefix + "_NON_PASS");
        if (summary.notRunCount() != 0) reasons.add(prefix + "_NOT_RUN_REMAINS");
        if (summary.blockedCount() != 0) reasons.add(prefix + "_BLOCKED_REMAINS");
        if (summary.criticalDefects() != 0) reasons.add(prefix + "_CRITICAL_REMAINS");
        if (summary.majorDefects() != 0) reasons.add(prefix + "_MAJOR_REMAINS");
    }
}
