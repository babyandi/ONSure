package kr.co.oruda.onsure.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import kr.co.oruda.onsure.harness.HarnessModels.Decision;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UniversalHarnessRunnerTest {
    private static final Path REPO = Path.of(".").toAbsolutePath().normalize();
    private static final Path AXES = REPO.resolve("harness/universal-v1/axes/verification-axes.v1.json");
    private static final Path FIXTURES = REPO.resolve("fixtures/universal-v1/sample-target/fixtures.v1.json");
    private static final Path ORACLES = REPO.resolve("harness/universal-v1/oracles/default-oracles.v1.json");
    @TempDir Path temp;

    @Test
    void sampleTargetProducesPassEvidenceReceiptsAndThirtyAxisResults() throws Exception {
        UniversalHarnessRunner.RunResult run = execute(FIXTURES, "operator-one", temp.resolve("runs"));
        assertEquals(Decision.PASS, run.summary().decision());
        assertEquals(30, run.summary().axisResults().size());
        assertTrue(run.summary().axisResults().stream().allMatch(value -> value.decision() == Decision.PASS));
        assertEquals(7, run.summary().fixtureResults().size());
        assertTrue(Files.isRegularFile(run.runRoot().resolve("run-summary.json")));
        assertTrue(Files.isRegularFile(run.runRoot().resolve("run-receipt.json")));
        assertTrue(Files.isRegularFile(run.runRoot().resolve("evidence-manifest.sha256")));
        assertTrue(new RunVerifier().verify(run.runRoot()).valid());
    }

    @Test
    void evidenceTamperingFailsReadOnlyVerification() throws Exception {
        UniversalHarnessRunner.RunResult run = execute(FIXTURES, "operator-one", temp.resolve("tamper"));
        Path evidence = run.runRoot().resolve(run.summary().fixtureResults().get(0).evidencePath());
        Files.writeString(evidence, Files.readString(evidence) + "\n");
        RunVerifier.Verification verification = new RunVerifier().verify(run.runRoot());
        assertFalse(verification.valid());
        assertTrue(verification.reasons().stream().anyMatch(value -> value.contains("HASH_MISMATCH")));
    }

    @Test
    void unexpectedFixtureFailureCreatesRcaAndBlocksPass() throws Exception {
        JsonNode root = JsonSupport.MAPPER.readTree(FIXTURES.toFile());
        ((ObjectNode) root.path("fixtures").get(0)).put("expected", "FAIL:normal");
        Path mutated = temp.resolve("mutated-fixtures.json");
        JsonSupport.writeAtomic(mutated, root);

        UniversalHarnessRunner.RunResult run = execute(mutated, "operator-one", temp.resolve("failed"));
        assertEquals(Decision.FAIL, run.summary().decision());
        assertTrue(run.summary().fixtureResults().stream()
                .anyMatch(value -> value.fixtureId().equals("FX-NORMAL-001") && value.decision() == Decision.FAIL));
        assertTrue(Files.isRegularFile(run.runRoot().resolve("rca/FX-NORMAL-001.json")));
        assertTrue(new RunVerifier().verify(run.runRoot()).valid());
    }

    private UniversalHarnessRunner.RunResult execute(Path fixtures, String operator, Path output) throws Exception {
        return new UniversalHarnessRunner().run(
                REPO, AXES, fixtures, ORACLES, output, operator, "test-jdk17");
    }
}
