package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** LC-P0-011 GroundTruthAuthority: only a real authority-confirmed actor may declare a new epoch. */
class GroundTruthEpochLedgerTest {
    @TempDir Path temp;

    @Test
    void anUntouchedLedgerHasNoLatestEpoch() throws Exception {
        GroundTruthEpochLedger ledger = new GroundTruthEpochLedger(temp);
        assertNull(ledger.latestEpoch());
    }

    @Test
    void anAuthorityConfirmedDeclarationSucceeds() throws Exception {
        GroundTruthEpochLedger ledger = new GroundTruthEpochLedger(temp);
        var declaration = ledger.declare("EPOCH-1", "auditor-a", true);
        assertEquals("EPOCH-1", declaration.epochId());
        assertEquals("EPOCH-1", ledger.latestEpoch());
        assertTrue(ledger.isDeclaredEpoch("EPOCH-1"));
    }

    @Test
    void aDeclarationWithoutRealAuthorityIsRejected() throws Exception {
        // 158 SS11 GroundTruthAuthority: declaring a new epoch is not a caller-declared claim.
        GroundTruthEpochLedger ledger = new GroundTruthEpochLedger(temp);
        assertThrows(SecurityException.class, () -> ledger.declare("EPOCH-1", "learner-a", false));
        assertNull(ledger.latestEpoch());
    }

    @Test
    void latestEpochAdvancesAcrossMultipleDeclarations() throws Exception {
        GroundTruthEpochLedger ledger = new GroundTruthEpochLedger(temp);
        ledger.declare("EPOCH-1", "auditor-a", true);
        ledger.declare("EPOCH-2", "auditor-a", true);
        assertEquals("EPOCH-2", ledger.latestEpoch());
        assertTrue(ledger.isDeclaredEpoch("EPOCH-1"));
        assertTrue(ledger.isDeclaredEpoch("EPOCH-2"));
        assertFalse(ledger.isDeclaredEpoch("EPOCH-3"));
    }

    @Test
    void duplicateEpochDeclarationIsRejected() throws Exception {
        GroundTruthEpochLedger ledger = new GroundTruthEpochLedger(temp);
        ledger.declare("EPOCH-1", "auditor-a", true);
        assertThrows(IllegalArgumentException.class, () -> ledger.declare("EPOCH-1", "auditor-b", true));
    }
}
