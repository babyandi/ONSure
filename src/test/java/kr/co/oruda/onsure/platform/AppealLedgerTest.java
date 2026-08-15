package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 127_SAFETY_HAZARD_AND_CONTESTABILITY_GOVERNANCE.md SS2.4/2.6/2.7 negative cases. */
class AppealLedgerTest {
    @TempDir Path temp;

    private void toIndependentReview(AppealLedger ledger, String appealCaseId) throws Exception {
        ledger.file(appealCaseId, "customer-a", "reviewer-original", "MATERIAL_EVIDENCE_OMITTED");
        ledger.transition(appealCaseId, "ADMISSIBILITY_REVIEW", "coordinator-a", "admissible");
        ledger.transition(appealCaseId, "EVIDENCE_LOCKED", "coordinator-a", "evidence locked");
        ledger.assignReviewer(appealCaseId, "reviewer-independent");
    }

    @Test
    void anAppellantCannotChallengeTheirOwnDecision() {
        AppealLedger ledger = new AppealLedger(temp);
        SecurityException denied = assertThrows(SecurityException.class,
                () -> ledger.file("appeal-1", "reviewer-original", "reviewer-original", "REASON"));
        assertEquals("APPELLANT_CANNOT_BE_THE_ORIGINAL_DECISION_PRINCIPAL", denied.getMessage());
    }

    @Test
    void theOriginalDecisionPrincipalCannotBeAssignedAsReviewer() throws Exception {
        // 127 SS8 negative case: "original reviewer decides appeal".
        AppealLedger ledger = new AppealLedger(temp);
        ledger.file("appeal-2", "customer-a", "reviewer-original", "REASON");
        ledger.transition("appeal-2", "ADMISSIBILITY_REVIEW", "coordinator-a", "admissible");
        ledger.transition("appeal-2", "EVIDENCE_LOCKED", "coordinator-a", "evidence locked");
        SecurityException denied = assertThrows(SecurityException.class,
                () -> ledger.assignReviewer("appeal-2", "reviewer-original"));
        assertEquals("ORIGINAL_DECISION_PRINCIPAL_CANNOT_REVIEW_OWN_APPEAL", denied.getMessage());
    }

    @Test
    void onlyTheAssignedReviewerCanDecide() throws Exception {
        AppealLedger ledger = new AppealLedger(temp);
        toIndependentReview(ledger, "appeal-3");
        SecurityException denied = assertThrows(SecurityException.class,
                () -> ledger.decide("appeal-3", "REVERSE", "reviewer-original", "not qualified to decide"));
        assertEquals("APPEAL_DECISION_MUST_COME_FROM_THE_ASSIGNED_REVIEWER", denied.getMessage());
    }

    @Test
    void theAssignedReviewerCanDecide() throws Exception {
        AppealLedger ledger = new AppealLedger(temp);
        toIndependentReview(ledger, "appeal-4");
        var event = ledger.decide("appeal-4", "REVERSE", "reviewer-independent", "material evidence changes the outcome");
        assertEquals("DECIDED", event.status());
    }

    @Test
    void theOriginalFiledEventSurvivesUnchangedAfterAReversal() throws Exception {
        // 127 SS8 negative case: "original decision deleted after reversal" -- must not happen.
        AppealLedger ledger = new AppealLedger(temp);
        toIndependentReview(ledger, "appeal-5");
        ledger.decide("appeal-5", "REVERSE", "reviewer-independent", "material evidence changes the outcome");
        var history = ledger.history("appeal-5");
        assertEquals("FILED", history.get(0).eventType());
        assertEquals("customer-a", history.get(0).actorId());
        assertTrue(history.size() >= 5);
    }

    @Test
    void multipleEvidenceSubmissionsAreAllPreservedNotReplaced() throws Exception {
        // 127 SS8 negative case: "new evidence replaces old bytes" -- must not happen.
        AppealLedger ledger = new AppealLedger(temp);
        ledger.file("appeal-6", "customer-a", "reviewer-original", "REASON");
        ledger.submitEvidence("appeal-6", "customer-a", "43ac647142dac29a5a3105ed53d8b08638e06e288044d7228ef8c985ab79dfa1");
        ledger.submitEvidence("appeal-6", "customer-a", "70213192283560990cc7315457795d1af358aafdb8d1e97c06cbf21dd03d889b");
        long evidenceEvents = ledger.history("appeal-6").stream()
                .filter(event -> "EVIDENCE_SUBMITTED".equals(event.eventType())).count();
        assertEquals(2, evidenceEvents);
    }

    @Test
    void evidenceCannotBeSubmittedAfterTheAppealIsClosed() throws Exception {
        AppealLedger ledger = new AppealLedger(temp);
        toIndependentReview(ledger, "appeal-7");
        ledger.decide("appeal-7", "UPHOLD", "reviewer-independent", "no material change");
        assertThrows(IllegalArgumentException.class,
                () -> ledger.submitEvidence("appeal-7", "customer-a", "43ac647142dac29a5a3105ed53d8b08638e06e288044d7228ef8c985ab79dfa1"));
    }

    @Test
    void anAppealCannotSkipStraightToDecidedWithoutIndependentReview() throws Exception {
        AppealLedger ledger = new AppealLedger(temp);
        ledger.file("appeal-8", "customer-a", "reviewer-original", "REASON");
        assertThrows(IllegalArgumentException.class,
                () -> ledger.decide("appeal-8", "REVERSE", "reviewer-independent", "shortcut"));
    }
}
