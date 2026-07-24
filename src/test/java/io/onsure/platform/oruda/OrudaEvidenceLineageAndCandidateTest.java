package io.onsure.platform.oruda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.onsure.assurance.Decision;
import io.onsure.platform.OrudaTargetAdapter;
import io.onsure.platform.SourceReferenceBinding;
import io.onsure.platform.ValidationEngine;
import io.onsure.platform.ValidationModel.TargetType;
import io.onsure.platform.ValidationModel.ValidationTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OrudaEvidenceLineageAndCandidateTest {
    private static final Path TARGET_ROOT = OrudaCandidateTestSupport.TARGET_ROOT;
    private static final String ENVIRONMENT_DIGEST = "9".repeat(64);
    @TempDir Path temp;

    @Test
    void evidenceRegistryAndReceiptLineageAreRecalculatedFromRunArtifacts() throws Exception {
        ValidationEngine.RunResult run = execute(temp.resolve("runs"));
        assertEquals(Decision.PASS,
                new ReceiptLineageVerifier().verify(run.runRoot(), TARGET_ROOT).decision());
        assertTrue(Files.isRegularFile(run.runRoot().resolve("oruda-evidence-registry.json")));
        assertTrue(Files.isRegularFile(run.runRoot().resolve("harness-command-manifest.json")));

        String registry = Files.readString(run.runRoot().resolve("oruda-evidence-registry.json"));
        Files.writeString(run.runRoot().resolve("oruda-evidence-registry.json"),
                registry.replaceFirst("EXPECTED_PASS", "UNEXPECTED_FAIL"));
        assertEquals(Decision.FAIL,
                new ReceiptLineageVerifier().verify(run.runRoot(), TARGET_ROOT).decision());
    }

    @Test
    void twoCleanRunsRemainBlockedUntilPackagesIndependentRunsAndBlindReviewsExist() throws Exception {
        ValidationEngine.RunResult run1 = execute(temp.resolve("candidate-runs"));
        ValidationEngine.RunResult run2 = execute(temp.resolve("candidate-runs"));

        FinalCandidateGate gate = new FinalCandidateGate();
        var blocked = gate.evaluate(run1.runRoot(), run2.runRoot(), TARGET_ROOT);
        assertFalse(blocked.eligible());
        assertEquals("BLOCKED", blocked.decision());
        assertTrue(blocked.reasons().stream()
                .anyMatch(value -> value.contains("PACKAGE_EXECUTION_REGISTRY_MISSING")));
        assertTrue(blocked.reasons().stream()
                .anyMatch(value -> value.contains("BLIND_REVIEW_RECEIPT_MISSING")));
        assertTrue(blocked.reasons().stream()
                .anyMatch(value -> value.contains("INDEPENDENT_RUN_RECEIPT_MISSING")));
        assertFalse(blocked.finalLockAllowed());

        OrudaCandidateTestSupport.addCompleteCandidateEvidence(
                run1, "operator-one", "reviewer-one", ENVIRONMENT_DIGEST);
        OrudaCandidateTestSupport.addCompleteCandidateEvidence(
                run2, "operator-two", "reviewer-two", ENVIRONMENT_DIGEST);

        Path output = temp.resolve("candidate/final-candidate-gate.json");
        var passed = gate.evaluateAndWrite(run1.runRoot(), run2.runRoot(), TARGET_ROOT, output);
        assertTrue(passed.eligible());
        assertEquals("PASS", passed.decision());
        assertTrue(passed.reasons().isEmpty());
        assertFalse(passed.finalLockAllowed());
        assertTrue(Files.isRegularFile(output));
    }

    private ValidationEngine.RunResult execute(Path store) throws Exception {
        ValidationTarget target = new ValidationTarget(
                "ORUDA-MVF-001",
                "ORUDA Minimum Viable Fixture Set",
                TargetType.AI_AGENTIC_PLATFORM,
                TARGET_ROOT,
                SourceReferenceBinding.treeReference(TARGET_ROOT),
                OrudaTargetAdapter.ID,
                "ONSURE_ORUDA_MVF_POLICY_V1",
                "LOCAL_MVF_E2E");
        return ValidationEngine.defaultEngine(store).run(target);
    }
}
