package io.onsure.platform.oruda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.onsure.assurance.Decision;
import org.junit.jupiter.api.Test;

class ExecutionResultClassifierTest {
    private final ExecutionResultClassifier classifier = new ExecutionResultClassifier();

    @Test
    void negativeFixtureBlockedAsExpectedIsValidatorPass() {
        var result = classifier.classify(new ExecutionResultClassifier.Input(
                true, true, false, false, 0, true, true, true, false));
        assertEquals(ExecutionResultClassifier.Verdict.EXPECTED_FAIL, result.verdict());
        assertEquals(Decision.PASS, result.decision());
        assertFalse(result.rcaRequired());
    }

    @Test
    void negativeFixtureAllowedIsUnexpectedPassAndRcaRequired() {
        var result = classifier.classify(new ExecutionResultClassifier.Input(
                true, true, false, false, 0, false, true, true, false));
        assertEquals(ExecutionResultClassifier.Verdict.UNEXPECTED_PASS, result.verdict());
        assertEquals(Decision.FAIL, result.decision());
        assertTrue(result.rcaRequired());
    }

    @Test
    void notRunSkippedInfrastructureAndEvidenceGapRemainDistinct() {
        assertEquals(ExecutionResultClassifier.Verdict.NOT_RUN,
                classifier.classify(new ExecutionResultClassifier.Input(
                        false, false, false, false, 0, false, false, false, false)).verdict());
        assertEquals(ExecutionResultClassifier.Verdict.SKIPPED,
                classifier.classify(new ExecutionResultClassifier.Input(
                        true, false, true, false, 0, false, false, false, false)).verdict());
        assertEquals(ExecutionResultClassifier.Verdict.HARNESS_ERROR,
                classifier.classify(new ExecutionResultClassifier.Input(
                        true, true, false, true, 124, false, false, false, false)).verdict());
        assertEquals(ExecutionResultClassifier.Verdict.INCONCLUSIVE,
                classifier.classify(new ExecutionResultClassifier.Input(
                        true, true, false, false, 0, true, false, false, false)).verdict());
    }
}
