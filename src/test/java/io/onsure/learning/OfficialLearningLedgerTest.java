package io.onsure.learning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.onsure.learning.OfficialLearningLedger.AppliedLock;
import io.onsure.learning.OfficialLearningLedger.CompletionStatus;
import io.onsure.learning.OfficialLearningLedger.LearningCandidate;
import io.onsure.learning.OfficialLearningLedger.Promotion;
import io.onsure.learning.OfficialLearningLedger.ReceiptPurpose;
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
    private static final String F = "f".repeat(64);
    private static final String G = "1".repeat(64);
    private static final String SOURCE = "git:" + "9".repeat(40);

    @TempDir Path temp;
    private OfficialLearningLedger ledger;

    @BeforeEach
    void setUp() {
        ledger = new OfficialLearningLedger(temp.resolve("official-learning-ledger.jsonl"));
    }

    @Test
    void completeHashBoundChainBecomesAppliedLockedNonfinal() {
        candidate();
        requestAndPack();
        candidatePass("receipt-001", "run-0001", "otester-a", "key-a", D);
        candidatePass("receipt-002", "run-0002", "otester-b", "key-b", E);
        promote();
        postApplyPass();
        lock();

        assertEquals(CompletionStatus.APPLIED_LOCKED_NONFINAL,
                ledger.completionStatus("candidate-001"));
        assertEquals(1, ledger.appliedCount());
        assertTrue(ledger.verifyChain().valid());
        assertEquals("LOCAL_FILE_NONFINAL", ledger.verifyChain().anchorMode());
    }

    @Test
    void scheduledResultCannotBypassOfficialLedger() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> ledger.requireAppliedLocked("candidate-001"));
        assertTrue(failure.getMessage().contains("HOLD_NO_CANDIDATE"));
    }

    @Test
    void learnerOrRequesterCannotValidate() {
        candidate();
        requestAndPack();
        assertEquals("LEARNER_CANNOT_VALIDATE", assertThrows(
                IllegalStateException.class,
                () -> candidatePass("receipt-001", "run-0001", "learner-a", "key-a", D))
                .getMessage());
        assertEquals("REQUESTER_CANNOT_VALIDATE", assertThrows(
                IllegalStateException.class,
                () -> candidatePass("receipt-002", "run-0002", "scheduler-a", "key-b", E))
                .getMessage());
    }

    @Test
    void promotionRequiresDifferentRunsIdentitiesAndKeys() {
        candidate();
        requestAndPack();
        candidatePass("receipt-001", "run-0001", "otester-a", "key-a", D);
        candidatePass("receipt-002", "run-0002", "otester-a", "key-b", E);
        assertEquals("TWO_DISTINCT_VERIFIER_IDENTITIES_REQUIRED",
                assertThrows(IllegalStateException.class, this::promote).getMessage());

        OfficialLearningLedger keyLedger = new OfficialLearningLedger(
                temp.resolve("same-key-ledger.jsonl"));
        seed(keyLedger);
        candidatePass(keyLedger, "receipt-101", "run-0101", "otester-a", "key-z", D, C);
        candidatePass(keyLedger, "receipt-102", "run-0102", "otester-b", "key-z", E, C);
        Promotion promotion = new Promotion(
                "promotion-101", "candidate-001", "pack-001", C,
                "VALIDATION_PACK_APPLY", "reviewer-a", "approver-b", "rollback-plan-001");
        assertEquals("TWO_DISTINCT_VERIFIER_KEYS_REQUIRED",
                assertThrows(IllegalStateException.class,
                        () -> keyLedger.approvePromotion(promotion)).getMessage());
    }

    @Test
    void receiptAndPromotionMustBindLatestPack() {
        candidate();
        requestAndPack();
        ledger.requestValidation(new ValidationRequest(
                "request-002", "candidate-001", "queue-002", "policy-v2",
                E, "validator-v2", "scheduler-b"));
        ledger.issueValidationPack(new ValidationPack(
                "pack-002", "request-002", "candidate-001", SOURCE,
                A, B, C, E));
        ValidationReceipt stale = receipt(
                "receipt-001", "pack-001", "run-0001", "otester-a", "key-a",
                ReceiptPurpose.CANDIDATE_VALIDATION, "PASS", C, D, E, true, false);
        assertEquals("VALIDATION_RECEIPT_MUST_BIND_LATEST_PACK",
                assertThrows(IllegalStateException.class,
                        () -> ledger.recordValidationReceipt(stale)).getMessage());
    }

    @Test
    void copiedOrUnrecalculatedOutputCannotPass() {
        candidate();
        requestAndPack();
        ValidationReceipt copied = receipt(
                "receipt-001", "pack-001", "run-0001", "otester-a", "key-a",
                ReceiptPurpose.CANDIDATE_VALIDATION, "PASS", C, D, E, true, true);
        assertEquals("COPIED_LEARNER_OUTPUT_CANNOT_PASS",
                assertThrows(IllegalStateException.class,
                        () -> ledger.recordValidationReceipt(copied)).getMessage());

        ValidationReceipt unrecalculated = receipt(
                "receipt-002", "pack-001", "run-0002", "otester-b", "key-b",
                ReceiptPurpose.CANDIDATE_VALIDATION, "PASS", C, E, F, false, false);
        assertEquals("INDEPENDENT_RECALCULATION_REQUIRED",
                assertThrows(IllegalStateException.class,
                        () -> ledger.recordValidationReceipt(unrecalculated)).getMessage());
    }

    @Test
    void reviewerApproverCannotOverlapProducerRequesterOrVerifier() {
        candidate();
        requestAndPack();
        candidatePass("receipt-001", "run-0001", "otester-a", "key-a", D);
        candidatePass("receipt-002", "run-0002", "otester-b", "key-b", E);
        Promotion invalid = new Promotion(
                "promotion-001", "candidate-001", "pack-001", C,
                "VALIDATION_PACK_APPLY", "otester-a", "approver-b", "rollback-plan-001");
        assertEquals("PROMOTION_ROLE_SEPARATION_REQUIRED",
                assertThrows(IllegalStateException.class,
                        () -> ledger.approvePromotion(invalid)).getMessage());
    }

    @Test
    void appliedLockRequiresRealPassingPostApplyReceipt() {
        candidate();
        requestAndPack();
        candidatePass("receipt-001", "run-0001", "otester-a", "key-a", D);
        candidatePass("receipt-002", "run-0002", "otester-b", "key-b", E);
        promote();
        assertEquals(CompletionStatus.HOLD_POST_APPLY_RECEIPT_MISSING,
                ledger.completionStatus("candidate-001"));
        assertEquals("POST_APPLY_RECEIPT_MISSING",
                assertThrows(IllegalStateException.class, this::lock).getMessage());
    }

    @Test
    void appliedLockAcceptsFortyCharacterGitObjectIdAndRejectsArtifactMismatch() {
        candidate();
        requestAndPack();
        candidatePass("receipt-001", "run-0001", "otester-a", "key-a", D);
        candidatePass("receipt-002", "run-0002", "otester-b", "key-b", E);
        promote();
        postApplyPass();
        AppliedLock invalid = new AppliedLock(
                "lock-001", "candidate-001", C, "registry:active", A,
                "f".repeat(40), D, "post-apply-001", "registry:previous", E, true);
        assertEquals("ACTIVE_SELECTOR_NOT_PROMOTED_ARTIFACT",
                assertThrows(IllegalStateException.class,
                        () -> ledger.lockApplied(invalid)).getMessage());
    }

    @Test
    void oneByteLedgerOrAnchorMutationIsDetected() throws Exception {
        candidate();
        Path file = temp.resolve("official-learning-ledger.jsonl");
        Files.writeString(file, Files.readString(file).replace("candidate-001", "candidate-002"));
        assertTrue(ledger.verifyChain().violations().contains("LEDGER_ENTRY_TAMPERED"));

        OfficialLearningLedger anchorLedger = new OfficialLearningLedger(
                temp.resolve("anchor-ledger.jsonl"));
        anchorLedger.registerCandidate(new LearningCandidate(
                "candidate-001", "VALIDATOR_RULE_CANDIDATE", A, B,
                "dataset-v1", true, "learner-a"));
        Path anchor = temp.resolve("anchor-ledger.jsonl.head.json");
        Files.writeString(anchor, Files.readString(anchor).replaceFirst("[0-9a-f]{64}", A));
        assertTrue(anchorLedger.verifyChain().violations().stream()
                .anyMatch(value -> value.contains("ANCHOR")));
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
                "pack-001", "request-001", "candidate-001", SOURCE,
                A, B, C, D));
    }

    private static void seed(OfficialLearningLedger target) {
        target.registerCandidate(new LearningCandidate(
                "candidate-001", "VALIDATOR_RULE_CANDIDATE", A, B,
                "dataset-v1", true, "learner-a"));
        target.requestValidation(new ValidationRequest(
                "request-001", "candidate-001", "queue-001", "policy-v1",
                A, "validator-v1", "scheduler-a"));
        target.issueValidationPack(new ValidationPack(
                "pack-001", "request-001", "candidate-001", SOURCE,
                A, B, C, D));
    }

    private void candidatePass(
            String receiptId, String runId, String verifier, String keyId, String evidence) {
        candidatePass(ledger, receiptId, runId, verifier, keyId, evidence, C);
    }

    private static void candidatePass(
            OfficialLearningLedger target, String receiptId, String runId,
            String verifier, String keyId, String evidence, String projection) {
        target.recordValidationReceipt(receipt(
                receiptId, "pack-001", runId, verifier, keyId,
                ReceiptPurpose.CANDIDATE_VALIDATION, "PASS", projection,
                evidence, G, true, false));
    }

    private void promote() {
        ledger.approvePromotion(new Promotion(
                "promotion-001", "candidate-001", "pack-001", C,
                "VALIDATION_PACK_APPLY", "reviewer-a", "approver-b", "rollback-plan-001"));
    }

    private void postApplyPass() {
        ledger.recordValidationReceipt(receipt(
                "post-apply-001", "pack-001", "run-post-apply-0001",
                "post-verifier-c", "post-key-c",
                ReceiptPurpose.POST_APPLY_REVERIFICATION, "PASS", C,
                F, G, true, false));
    }

    private void lock() {
        ledger.lockApplied(new AppliedLock(
                "lock-001", "candidate-001", C, "registry:active", C,
                "f".repeat(40), D, "post-apply-001", "registry:previous", E, true));
    }

    private static ValidationReceipt receipt(
            String receiptId, String packId, String runId, String verifier, String keyId,
            ReceiptPurpose purpose, String decision, String projection, String evidence,
            String recalculationReceipt, boolean independentlyRecalculated,
            boolean copiedLearnerOutput) {
        return new ValidationReceipt(
                receiptId, packId, "candidate-001", runId, verifier, keyId,
                purpose, decision, projection, evidence, recalculationReceipt,
                independentlyRecalculated, copiedLearnerOutput);
    }
}
