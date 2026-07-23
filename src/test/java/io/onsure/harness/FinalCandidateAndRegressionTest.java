package io.onsure.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.onsure.harness.HarnessModels.Decision;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FinalCandidateAndRegressionTest {
    private static final Path REPO = Path.of(".").toAbsolutePath().normalize();
    private static final Path AXES = REPO.resolve("harness/universal-v1/axes/verification-axes.v1.json");
    private static final Path FIXTURES = REPO.resolve("fixtures/universal-v1/sample-target/fixtures.v1.json");
    private static final Path ORACLES = REPO.resolve("harness/universal-v1/oracles/default-oracles.v1.json");
    @TempDir Path temp;

    @Test
    void twoIndependentCleanRunsBecomeCandidateButNeverAutomaticFinalLock() throws Exception {
        var first = execute(FIXTURES, "operator-one", temp.resolve("candidate-runs"));
        var second = execute(FIXTURES, "operator-two", temp.resolve("candidate-runs"));
        var candidate = new FinalCandidateGate().evaluate(first.runRoot(), second.runRoot());
        assertTrue(candidate.eligible());
        assertEquals(Decision.PASS, candidate.decision());
        assertTrue(candidate.reasons().isEmpty());
        assertFalse(candidate.finalLockAllowed());
    }

    @Test
    void reusedOperatorBlocksIndependentCandidate() throws Exception {
        var first = execute(FIXTURES, "operator-one", temp.resolve("operator-runs"));
        var second = execute(FIXTURES, "operator-one", temp.resolve("operator-runs"));
        var candidate = new FinalCandidateGate().evaluate(first.runRoot(), second.runRoot());
        assertFalse(candidate.eligible());
        assertEquals(Decision.BLOCKED, candidate.decision());
        assertTrue(candidate.reasons().contains("OPERATOR_NOT_INDEPENDENT"));
    }

    @Test
    void failedBaselineClosesOnlyAfterTwoIndependentCleanRegressionRuns() throws Exception {
        JsonNode root = JsonSupport.MAPPER.readTree(FIXTURES.toFile());
        ((ObjectNode) root.path("fixtures").get(0)).put("expected", "FAIL:normal");
        Path failedFixtures = temp.resolve("failed-fixtures.json");
        JsonSupport.writeAtomic(failedFixtures, root);

        var baseline = execute(failedFixtures, "operator-baseline", temp.resolve("regression"));
        var regression1 = execute(FIXTURES, "operator-regression-one", temp.resolve("regression"));
        var regression2 = execute(FIXTURES, "operator-regression-two", temp.resolve("regression"));
        var receipt = new RegressionGate().evaluate(
                baseline.runRoot(), regression1.runRoot(), regression2.runRoot());

        assertTrue(receipt.eligible());
        assertEquals(Decision.PASS, receipt.decision());
        assertTrue(receipt.resolvedFixtures().contains("FX-NORMAL-001"));
        assertTrue(receipt.remainingFixtures().isEmpty());
    }

    private UniversalHarnessRunner.RunResult execute(Path fixtures, String operator, Path output) throws Exception {
        return new UniversalHarnessRunner().run(
                REPO, AXES, fixtures, ORACLES, output, operator, "test-jdk17");
    }
}
