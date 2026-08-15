package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 127_SAFETY_HAZARD_AND_CONTESTABILITY_GOVERNANCE.md SS1.3/1.6 negative cases. */
class HazardLedgerTest {
    @TempDir Path temp;

    @Test
    void newHazardStartsIdentified() throws Exception {
        HazardLedger ledger = new HazardLedger(temp);
        var event = ledger.create("hazard-1", "analyst-a");
        assertEquals("IDENTIFIED", event.disposition());
        assertEquals("IDENTIFIED", ledger.currentDisposition("hazard-1"));
    }

    @Test
    void aHazardCannotSkipStraightToValidatedControlled() throws Exception {
        // 127 SS8 negative case: "unvalidated safe state".
        HazardLedger ledger = new HazardLedger(temp);
        ledger.create("hazard-2", "analyst-a");
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                () -> ledger.advance("hazard-2", "VALIDATED_CONTROLLED", "analyst-b", "shortcut", true));
        assertEquals("HAZARD_TRANSITION_NOT_ALLOWED:IDENTIFIED->VALIDATED_CONTROLLED", rejected.getMessage());
    }

    @Test
    void residualRiskAcceptanceRequiresAnActorDistinctFromTheCreator() throws Exception {
        HazardLedger ledger = new HazardLedger(temp);
        ledger.create("hazard-3", "analyst-a");
        ledger.advance("hazard-3", "ANALYZED", "analyst-a", "analysis complete", false);
        ledger.advance("hazard-3", "CONTROL_REQUIRED", "analyst-a", "controls needed", false);
        ledger.advance("hazard-3", "CONTROLLED_PENDING_VALIDATION", "analyst-a", "controls implemented", false);
        ledger.advance("hazard-3", "RESIDUAL_RISK_ACCEPTANCE_REQUIRED", "analyst-a", "residual risk remains", false);

        // 127 SS1.6 negative case: "waived critical safety requirement" via the same actor.
        SecurityException denied = assertThrows(SecurityException.class,
                () -> ledger.advance("hazard-3", "VALIDATED_CONTROLLED", "analyst-a", "I accept it", true));
        assertEquals("RESIDUAL_RISK_ACCEPTANCE_REQUIRES_ACTOR_DISTINCT_FROM_CREATOR", denied.getMessage());
    }

    @Test
    void residualRiskAcceptanceRequiresSafetyAuthorityConfirmation() throws Exception {
        HazardLedger ledger = new HazardLedger(temp);
        ledger.create("hazard-4", "analyst-a");
        ledger.advance("hazard-4", "ANALYZED", "analyst-a", "analysis complete", false);
        ledger.advance("hazard-4", "CONTROL_REQUIRED", "analyst-a", "controls needed", false);
        ledger.advance("hazard-4", "CONTROLLED_PENDING_VALIDATION", "analyst-a", "controls implemented", false);
        ledger.advance("hazard-4", "RESIDUAL_RISK_ACCEPTANCE_REQUIRED", "analyst-a", "residual risk remains", false);

        SecurityException denied = assertThrows(SecurityException.class,
                () -> ledger.advance("hazard-4", "VALIDATED_CONTROLLED", "analyst-b", "looks fine", false));
        assertEquals("RESIDUAL_RISK_ACCEPTANCE_REQUIRES_SAFETY_AUTHORITY", denied.getMessage());
    }

    @Test
    void aDistinctSafetyAuthorityCanAcceptResidualRisk() throws Exception {
        HazardLedger ledger = new HazardLedger(temp);
        ledger.create("hazard-5", "analyst-a");
        ledger.advance("hazard-5", "ANALYZED", "analyst-a", "analysis complete", false);
        ledger.advance("hazard-5", "CONTROL_REQUIRED", "analyst-a", "controls needed", false);
        ledger.advance("hazard-5", "CONTROLLED_PENDING_VALIDATION", "analyst-a", "controls implemented", false);
        ledger.advance("hazard-5", "RESIDUAL_RISK_ACCEPTANCE_REQUIRED", "analyst-a", "residual risk remains", false);
        var event = ledger.advance("hazard-5", "VALIDATED_CONTROLLED", "safety-authority-b", "accepted with authority", true);
        assertEquals("VALIDATED_CONTROLLED", event.disposition());
        assertEquals(6, ledger.history("hazard-5").size());
    }

    @Test
    void unknownHazardCannotAdvance() {
        HazardLedger ledger = new HazardLedger(temp);
        assertThrows(IllegalArgumentException.class,
                () -> ledger.advance("hazard-never-created", "ANALYZED", "analyst-a", "n/a", false));
    }
}
