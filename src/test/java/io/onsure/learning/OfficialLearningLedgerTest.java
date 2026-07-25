package io.onsure.learning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.onsure.learning.OfficialLearningLedger.AppliedLock;
import io.onsure.learning.OfficialLearningLedger.CompletionStatus;
import io.onsure.learning.OfficialLearningLedger.LearningCandidate;
import io.onsure.learning.OfficialLearningLedger.Promotion;
import io.onsure.learning.OfficialLearningLedger.ValidationPack;
import io.onsure.learning.OfficialLearningLedger.ValidationReceipt;
import io.onsure.learning.OfficialLearningLedger.ValidationRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OfficialLearningLedgerTest {
    private static final String A = "a".repeat(64);
    private static final String B = "b".repeat(64);
    private static final String C = "c".repeat(64);
    private static final String D = "d".repeat(64);
    private static final String E = "e".repeat(64);

    @TempDir Path temp;
    private OfficialLearningLedger ledger;

    @BeforeEach
    void setUp() {
        ledger = new OfficialLearningLedger(temp.resolve("official-learning-ledger.jsonl"));
    }

    @Test
    void completeHashBoundChainBecomesAppliedLocked() {
        candidate();
        requestAndPack();
        pass("receipt-001", "run-0001", "otester-a", D);
        pass("receipt-002", "run-0002", "otester-b", E);
        promoteAndLock();

        assertEquals(CompletionStatus.APPLIED_LOCKED, ledger.completionStatus("candidate-001"));
        assertEquals(1, ledger.appliedCount());
        assertTrue(ledger.verifyChain().valid());
    }

    @Test
    void scheduledResultCannotBypassOfficialLedger() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> ledger.requireAppliedLocked("candidate-001"));
        assertTrue(failure.getMessage().contains("HOLD_NO_CANDIDATE"));
    }

    @Test
    void promotionRequiresTwoIndependentPassRuns() {
        candidate();
        requestAndPack();
        pass("receipt-001", "run-0001", "otester-a", D);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class, this::promote);
        assertEquals("TWO_PASS_RECEIPTS_REQUIRED", failure.getMessage());
    }

    @Test
    void learnerCannotValidateOwnCandidate() {
        candidate();
        requestAndPack();

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> pass("receipt-001", "run-0001", "learner-a", D));
        assertEquals("LEARNER_CANNOT_VALIDATE", failure.getMessage());
    }

    @Test
    void copiedLearnerOutputCannotBecomePassReceipt() {
        candidate();
        requestAndPack();
        ValidationReceipt copied = new ValidationReceipt(
                "receipt-001", "pack-001", "candidate-001", "run-0001",
                "otester-a", "PASS", C, D, true, true);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> ledger.recordValidationReceipt(copied));
        assertEquals("COPIED_LEARNER_OUTPUT_CANNOT_PASS", failure.getMessage());
    }

    @Test
    void inconclusiveReceiptDoesNotCountAsPass() {
        candidate();
        requestAndPack();
        ledger.recordValidationReceipt(new ValidationReceipt(
                "receipt-001", "pack-001", "candidate-001", "run-0001",
                "otester-a", "INCONCLUSIVE", C, D, false, false));

        assertEquals(
                CompletionStatus.HOLD_TWO_PASS_RECEIPTS_MISSING,
                ledger.completionStatus("candidate-001"));
    }

    @Test
    void mismatchedTwoRunProjectionCannotPromote() {
        candidate();
        requestAndPack();
        passWithProjection("receipt-001", "run-0001", "otester-a", D, C);
        passWithProjection("receipt-002", "run-0002", "otester-b", E, B);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class, this::promote);
        assertEquals("TWO_RUN_PROJECTION_MISMATCH", failure.getMessage());
    }

    @Test
    void reviewerAndApproverMustBeSeparated() {
        candidate();
        requestAndPack();
        pass("receipt-001", "run-0001", "otester-a", D);
        pass("receipt-002", "run-0002", "otester-b", E);
        Promotion invalid = new Promotion(
                "promotion-001", "candidate-001", B, "VALIDATION_PACK_APPLY",
                "same-person", "same-person", "rollback-plan-001");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> ledger.approvePromotion(invalid));
        assertEquals("REVIEWER_APPROVER_SEPARATION_REQUIRED", failure.getMessage());
    }

    @Test
    void activeSelectorMustReferencePromotedArtifact() {
        candidate();
        requestAndPack();
        pass("receipt-001", "run-0001", "otester-a", D);
        pass("receipt-002", "run-0002", "otester-b", E);
        promote();
        AppliedLock invalid = new AppliedLock(
                "lock-001", "candidate-001", B, "registry:active", A, C, D,
                "post-apply-001", "registry:previous", E, true);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> ledger.lockApplied(invalid));
        assertEquals("ACTIVE_SELECTOR_NOT_PROMOTED_ARTIFACT", failure.getMessage());
    }

    @Test
    void oneByteLedgerMutationIsDetected() throws Exception {
        candidate();
        Path file = temp.resolve("official-learning-ledger.jsonl");
        String original = Files.readString(file);
        Files.writeString(file, original.replace("candidate-001", "candidate-002"));

        assertTrue(ledger.verifyChain().violations().contains("LEDGER_ENTRY_TAMPERED"));
        assertThrows(
                IllegalStateException.class,
                () -> ledger.completionStatus("candidate-001"));
    }

    private void candidate() {
        ledger.registerCandidate(new LearningCandidate(
                "candidate-001", "VALIDATOR_RULE_CANDIDATE", A, B,
                "dataset-v1", true, "learner-a"));
    }

    private void requestAndPack() {
        ledger.requestValidation(new ValidationRequest(
                "request-001", "candidate-001", "queue-001", "policy-v1",
                A, "validator-v1", "scheduler-a"));
        ledger.issueValidationPack(new ValidationPack(
                "pack-001", "request-001", "candidate-001", A, B, C, D));
    }

    private void pass(String receipt, String run, String verifier, String evidence) {
        passWithProjection(receipt, run, verifier, evidence, C);
    }

    private void passWithProjection(
            String receipt, String run, String verifier, String evidence, String projection) {
        ledger.recordValidationReceipt(new ValidationReceipt(
                receipt, "pack-001", "candidate-001", run, verifier,
                "PASS", projection, evidence, true, false));
    }

    private void promote() {
        ledger.approvePromotion(new Promotion(
                "promotion-001", "candidate-001", B, "VALIDATION_PACK_APPLY",
                "reviewer-a", "approver-b", "rollback-plan-001"));
    }

    private void promoteAndLock() {
        promote();
        ledger.lockApplied(new AppliedLock(
                "lock-001", "candidate-001", B, "registry:active", B, C, D,
                "post-apply-001", "registry:previous", E, true));
    }
}
