package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** LC-P0-009 HumanOverrideTrendReport aggregate view. */
class HumanOverrideTrendLedgerTest {
    @TempDir Path temp;

    @Test
    void anUntouchedCandidateHasAnEmptyReport() throws Exception {
        HumanOverrideTrendLedger ledger = new HumanOverrideTrendLedger(temp);
        var report = ledger.report("candidate-never-touched");
        assertEquals(0, report.totalOverrides());
        assertEquals(0.0, report.promotionRate());
    }

    @Test
    void theAggregateReflectsRealRecordedHistoryNotACallerClaim() throws Exception {
        HumanOverrideTrendLedger ledger = new HumanOverrideTrendLedger(temp);
        ledger.record("candidate-1", "override-1", true, false);
        ledger.record("candidate-1", "override-2", false, true);
        ledger.record("candidate-1", "override-3", true, false);

        var report = ledger.report("candidate-1");
        assertEquals(3, report.totalOverrides());
        assertEquals(2, report.promotedCount());
        assertEquals(1, report.selfConfirmationRejectedCount());
        assertEquals(2.0 / 3.0, report.promotionRate(), 0.0001);
    }

    @Test
    void duplicateOverrideIdIsRejected() throws Exception {
        HumanOverrideTrendLedger ledger = new HumanOverrideTrendLedger(temp);
        ledger.record("candidate-2", "override-dup", true, false);
        assertThrows(IllegalArgumentException.class,
                () -> ledger.record("candidate-2", "override-dup", false, false));
    }

    @Test
    void differentCandidatesHaveIndependentTrendHistories() throws Exception {
        HumanOverrideTrendLedger ledger = new HumanOverrideTrendLedger(temp);
        ledger.record("candidate-3", "override-a", true, false);
        ledger.record("candidate-4", "override-b", false, true);
        assertEquals(1, ledger.report("candidate-3").totalOverrides());
        assertEquals(1, ledger.report("candidate-4").totalOverrides());
        assertEquals(1, ledger.report("candidate-3").promotedCount());
        assertEquals(0, ledger.report("candidate-4").promotedCount());
    }

    // Tamper-evidence, built in from the start.
    @Test
    void aTamperedRecordIsDetectedOnNextRead() throws Exception {
        HumanOverrideTrendLedger ledger = new HumanOverrideTrendLedger(temp);
        ledger.record("candidate-tamper-1", "override-tamper-1", true, false);
        Path file = temp.resolve("candidate-tamper-1.json");
        String tampered = Files.readString(file).replace("\"promoted\" : true", "\"promoted\" : false");
        Files.writeString(file, tampered);
        IllegalStateException detected = assertThrows(IllegalStateException.class,
                () -> ledger.report("candidate-tamper-1"));
        assertTrue(detected.getMessage().contains("OVERRIDE_TREND_LEDGER_CHAIN_INVALID"));
    }
}
