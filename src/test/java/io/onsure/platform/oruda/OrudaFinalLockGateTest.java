package io.onsure.platform.oruda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.onsure.assurance.Decision;
import io.onsure.platform.OrudaTargetAdapter;
import io.onsure.platform.ValidationEngine;
import io.onsure.platform.ValidationModel.TargetType;
import io.onsure.platform.ValidationModel.ValidationTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OrudaFinalLockGateTest {
    private static final Path TARGET_ROOT = OrudaCandidateTestSupport.TARGET_ROOT;
    private static final String ENVIRONMENT_DIGEST = "8".repeat(64);
    @TempDir Path temp;

    @Test
    void finalLockIsNotCreatedWithoutHumanFinalApproval() throws Exception {
        ValidationEngine.RunResult run1 = execute(temp.resolve("runs"));
        ValidationEngine.RunResult run2 = execute(temp.resolve("runs"));
        OrudaCandidateTestSupport.addCompleteCandidateEvidence(
                run1, "operator-one", "reviewer-one", ENVIRONMENT_DIGEST);
        OrudaCandidateTestSupport.addCompleteCandidateEvidence(
                run2, "operator-two", "reviewer-two", ENVIRONMENT_DIGEST);

        Path missingApproval = temp.resolve("missing-approval.json");
        Path output = temp.resolve("locks/oruda-final-lock.json");
        OrudaFinalLockGate.Outcome result = new OrudaFinalLockGate().create(
                run1.runRoot(), run2.runRoot(), TARGET_ROOT, missingApproval, output);

        assertEquals(Decision.FAIL, result.result().decision());
        assertTrue(result.result().violations().contains("ORUDA_FINAL_APPROVAL_RECEIPT_MISSING"));
        assertFalse(Files.exists(output));
    }

    @Test
    void finalLockRequiresAllPackagesFreshCandidateAndHumanApproval() throws Exception {
        ValidationEngine.RunResult run1 = execute(temp.resolve("approved-runs"));
        ValidationEngine.RunResult run2 = execute(temp.resolve("approved-runs"));
        OrudaCandidateTestSupport.addCompleteCandidateEvidence(
                run1, "operator-one", "reviewer-one", ENVIRONMENT_DIGEST);
        OrudaCandidateTestSupport.addCompleteCandidateEvidence(
                run2, "operator-two", "reviewer-two", ENVIRONMENT_DIGEST);

        FinalCandidateGate.GateResult candidate = new FinalCandidateGate().evaluate(
                run1.runRoot(), run2.runRoot(), TARGET_ROOT);
        assertTrue(candidate.eligible());
        Path approval = temp.resolve("approval/final-approval.json");
        OrudaCandidateTestSupport.writeFinalApproval(
                candidate, approval, "human-final-authority-one");

        Path output = temp.resolve("locks/oruda-final-lock.json");
        OrudaFinalLockGate gate = new OrudaFinalLockGate();
        OrudaFinalLockGate.Outcome created = gate.create(
                run1.runRoot(), run2.runRoot(), TARGET_ROOT, approval, output);

        assertEquals(Decision.PASS, created.result().decision());
        assertNotNull(created.finalLock());
        assertEquals("LOCKED", created.finalLock().decision());
        assertTrue(Files.isRegularFile(output));
        assertEquals(Decision.PASS,
                gate.verify(run1.runRoot(), run2.runRoot(), TARGET_ROOT, approval, output).decision());

        String content = Files.readString(approval);
        Files.writeString(approval, content.replace(candidate.candidateDigest(), "0".repeat(64)));
        assertEquals(Decision.FAIL,
                gate.verify(run1.runRoot(), run2.runRoot(), TARGET_ROOT, approval, output).decision());
    }

    @Test
    void approvalCannotBypassMissingExecutionPackages() throws Exception {
        ValidationEngine.RunResult run1 = execute(temp.resolve("incomplete-runs"));
        ValidationEngine.RunResult run2 = execute(temp.resolve("incomplete-runs"));
        OrudaCandidateTestSupport.writeBlindReviewReceipt(run1, "reviewer-one");
        OrudaCandidateTestSupport.writeBlindReviewReceipt(run2, "reviewer-two");
        OrudaCandidateTestSupport.writeIndependentRunReceipt(
                run1, "operator-one", ENVIRONMENT_DIGEST);
        OrudaCandidateTestSupport.writeIndependentRunReceipt(
                run2, "operator-two", ENVIRONMENT_DIGEST);

        FinalCandidateGate.GateResult blockedCandidate = new FinalCandidateGate().evaluate(
                run1.runRoot(), run2.runRoot(), TARGET_ROOT);
        assertFalse(blockedCandidate.eligible());
        Path approval = temp.resolve("invalid-approval/final-approval.json");
        OrudaCandidateTestSupport.writeFinalApproval(
                blockedCandidate, approval, "human-final-authority-one");

        Path output = temp.resolve("invalid-lock/oruda-final-lock.json");
        OrudaFinalLockGate.Outcome result = new OrudaFinalLockGate().create(
                run1.runRoot(), run2.runRoot(), TARGET_ROOT, approval, output);
        assertEquals(Decision.FAIL, result.result().decision());
        assertFalse(Files.exists(output));
    }

    private ValidationEngine.RunResult execute(Path store) throws Exception {
        ValidationTarget target = new ValidationTarget(
                "ORUDA-MVF-001",
                "ORUDA Minimum Viable Fixture Set",
                TargetType.AI_AGENTIC_PLATFORM,
                TARGET_ROOT,
                "f".repeat(40),
                OrudaTargetAdapter.ID,
                "ONSURE_ORUDA_MVF_POLICY_V1",
                "LOCAL_MVF_E2E");
        return ValidationEngine.defaultEngine(store).run(target);
    }
}
