package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FourEyesLedgerTest {
    @TempDir Path temp;

    @Test
    void aSingleApproverDoesNotSatisfyFourEyes() throws Exception {
        FourEyesLedger ledger = new FourEyesLedger(temp);
        var result = ledger.recordApproval("subject-1", "actor-a");
        assertFalse(result.satisfied());
        assertEquals(1, result.approvals().size());
    }

    @Test
    void twoDistinctApproversSatisfyFourEyes() throws Exception {
        FourEyesLedger ledger = new FourEyesLedger(temp);
        ledger.recordApproval("subject-2", "actor-a");
        var result = ledger.recordApproval("subject-2", "actor-b");
        assertTrue(result.satisfied());
        assertEquals(2, result.approvals().size());
    }

    @Test
    void theSameActorCannotCountAsTwoOfTheFourEyes() throws Exception {
        FourEyesLedger ledger = new FourEyesLedger(temp);
        ledger.recordApproval("subject-3", "actor-a");
        SecurityException denied = assertThrows(SecurityException.class,
                () -> ledger.recordApproval("subject-3", "actor-a"));
        assertEquals("FOUR_EYES_SAME_ACTOR_CANNOT_COUNT_TWICE:actor-a", denied.getMessage());
        assertFalse(ledger.approvalsFor("subject-3").satisfied());
        assertEquals(1, ledger.approvalsFor("subject-3").approvals().size());
    }

    @Test
    void approvalsForAnUntouchedSubjectIsUnsatisfiedAndEmpty() throws Exception {
        FourEyesLedger ledger = new FourEyesLedger(temp);
        var result = ledger.approvalsFor("subject-never-touched");
        assertFalse(result.satisfied());
        assertTrue(result.approvals().isEmpty());
    }
}
