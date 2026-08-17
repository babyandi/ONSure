package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** FR-COM-013 Separation of Duties core enforcement, independent of the RBAC-wired v2 op. */
class SeparationOfDutiesLedgerTest {
    @TempDir Path temp;

    @Test
    void enforcedPolicyRejectsTheSameActorPerformingAConflictingStage() throws Exception {
        SeparationOfDutiesLedger ledger = new SeparationOfDutiesLedger(temp);
        ledger.recordStage("req-1", "DEVELOP", "actor-a", true);
        SecurityException violation = assertThrows(SecurityException.class,
                () -> ledger.recordStage("req-1", "VERIFY", "actor-a", true));
        assertEquals("SOD_VIOLATION:actor_already_performed:DEVELOP:cannot_also_perform:VERIFY", violation.getMessage());
    }

    @Test
    void enforcedConflictLeavesNoRecordOfTheRejectedStage() throws Exception {
        SeparationOfDutiesLedger ledger = new SeparationOfDutiesLedger(temp);
        ledger.recordStage("req-2", "DEVELOP", "actor-a", true);
        assertThrows(SecurityException.class, () -> ledger.recordStage("req-2", "VERIFY", "actor-a", true));
        assertEquals(1, ledger.stagesFor("req-2").size());
        assertEquals("DEVELOP", ledger.stagesFor("req-2").get(0).stage());
    }

    @Test
    void advisoryPolicyRecordsTheConflictInsteadOfBlockingIt() throws Exception {
        SeparationOfDutiesLedger ledger = new SeparationOfDutiesLedger(temp);
        ledger.recordStage("req-3", "DEVELOP", "actor-a", false);
        var result = ledger.recordStage("req-3", "VERIFY", "actor-a", false);
        assertEquals(SeparationOfDutiesLedger.Outcome.ADVISORY_VIOLATION, result.outcome());
        assertEquals("DEVELOP", result.conflictingStage());
        assertEquals(2, ledger.stagesFor("req-3").size());
    }

    @Test
    void differentActorsPerformingDifferentStagesIsNeverAConflict() throws Exception {
        SeparationOfDutiesLedger ledger = new SeparationOfDutiesLedger(temp);
        var develop = ledger.recordStage("req-4", "DEVELOP", "actor-a", true);
        var verify = ledger.recordStage("req-4", "VERIFY", "actor-b", true);
        var accept = ledger.recordStage("req-4", "ACCEPT", "actor-c", true);
        assertEquals(SeparationOfDutiesLedger.Outcome.RECORDED, develop.outcome());
        assertEquals(SeparationOfDutiesLedger.Outcome.RECORDED, verify.outcome());
        assertEquals(SeparationOfDutiesLedger.Outcome.RECORDED, accept.outcome());
        assertEquals(3, ledger.stagesFor("req-4").size());
    }

    @Test
    void theSameActorRecordingTheSameStageTwiceIsNotAConflict() throws Exception {
        SeparationOfDutiesLedger ledger = new SeparationOfDutiesLedger(temp);
        ledger.recordStage("req-5", "DEVELOP", "actor-a", true);
        var again = ledger.recordStage("req-5", "DEVELOP", "actor-a", true);
        assertEquals(SeparationOfDutiesLedger.Outcome.RECORDED, again.outcome());
    }

    @Test
    void unrecordedRequestHasNoStages() throws Exception {
        SeparationOfDutiesLedger ledger = new SeparationOfDutiesLedger(temp);
        assertTrue(ledger.stagesFor("req-never-touched").isEmpty());
    }

    // Autonomous Development Mode (2026-08-15) tamper-evidence hardening.
    @Test
    void aTamperedActorIsDetectedOnNextRead() throws Exception {
        SeparationOfDutiesLedger ledger = new SeparationOfDutiesLedger(temp);
        ledger.recordStage("req-tamper-1", "DEVELOP", "actor-a", true);
        Path file = temp.resolve("req-tamper-1.json");
        String tampered = Files.readString(file).replace("actor-a", "actor-x");
        Files.writeString(file, tampered);
        IllegalStateException detected = assertThrows(IllegalStateException.class,
                () -> ledger.stagesFor("req-tamper-1"));
        assertTrue(detected.getMessage().contains("SOD_LEDGER_CHAIN_INVALID"));
    }
}
