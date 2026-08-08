package kr.co.oruda.onsure.platform.oruda;

import kr.co.oruda.onsure.assurance.Decision;

/** Classifies harness execution without collapsing NOT_RUN, infrastructure, evidence and product defects. */
public final class ExecutionResultClassifier {
    public enum Verdict {
        EXPECTED_PASS,
        EXPECTED_FAIL,
        UNEXPECTED_PASS,
        UNEXPECTED_FAIL,
        HARNESS_ERROR,
        SKIPPED,
        NOT_RUN,
        INCONCLUSIVE
    }

    public record Input(
            boolean executionStarted,
            boolean commandExecuted,
            boolean skipped,
            boolean timedOut,
            int exitCode,
            boolean oracleMatched,
            boolean expectedFailure,
            boolean evidenceComplete,
            boolean oracleConflict) {}

    public record Classification(Verdict verdict, Decision decision, boolean rcaRequired, String reason) {}

    public Classification classify(Input input) {
        if (input == null) {
            return new Classification(Verdict.INCONCLUSIVE, Decision.INCONCLUSIVE, false,
                    "CLASSIFIER_INPUT_MISSING");
        }
        if (!input.executionStarted()) {
            return new Classification(Verdict.NOT_RUN, Decision.NOT_RUN, false,
                    "EXECUTION_NOT_STARTED");
        }
        if (input.skipped()) {
            return new Classification(Verdict.SKIPPED, Decision.NOT_RUN, false,
                    "EXECUTION_SKIPPED");
        }
        if (input.timedOut()) {
            return new Classification(Verdict.HARNESS_ERROR, Decision.BLOCKED, true,
                    "HARNESS_TIMEOUT");
        }
        if (input.commandExecuted() && input.exitCode() != 0) {
            return new Classification(Verdict.HARNESS_ERROR, Decision.BLOCKED, true,
                    "HARNESS_NON_ZERO_EXIT");
        }
        if (!input.evidenceComplete()) {
            return new Classification(Verdict.INCONCLUSIVE, Decision.INCONCLUSIVE, false,
                    "EXECUTION_EVIDENCE_INCOMPLETE");
        }
        if (input.oracleConflict()) {
            return new Classification(Verdict.INCONCLUSIVE, Decision.INCONCLUSIVE, false,
                    "ORACLE_CONFLICT_UNRESOLVED");
        }
        if (input.oracleMatched()) {
            return input.expectedFailure()
                    ? new Classification(Verdict.EXPECTED_FAIL, Decision.PASS, false,
                            "NEGATIVE_FIXTURE_BLOCKED_AS_EXPECTED")
                    : new Classification(Verdict.EXPECTED_PASS, Decision.PASS, false,
                            "POSITIVE_FIXTURE_PASSED_AS_EXPECTED");
        }
        return input.expectedFailure()
                ? new Classification(Verdict.UNEXPECTED_PASS, Decision.FAIL, true,
                        "NEGATIVE_FIXTURE_WAS_NOT_BLOCKED")
                : new Classification(Verdict.UNEXPECTED_FAIL, Decision.FAIL, true,
                        "POSITIVE_FIXTURE_FAILED");
    }
}
