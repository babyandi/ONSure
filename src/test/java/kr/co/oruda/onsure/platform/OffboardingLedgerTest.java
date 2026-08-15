package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** FR-FRESH-003 (126_FINAL_FRESH_PRODUCT_DESIGN_REVIEW_AND_SCOPE_CLOSURE.md). */
class OffboardingLedgerTest {
    @TempDir Path temp;

    @Test
    void requestStartsTerminationRequested() throws Exception {
        OffboardingLedger ledger = new OffboardingLedger(temp);
        var event = ledger.request("tenant-1", "admin-a");
        assertEquals("TERMINATION_REQUESTED", event.stage());
    }

    @Test
    void stagesCannotBeSkipped() throws Exception {
        OffboardingLedger ledger = new OffboardingLedger(temp);
        ledger.request("tenant-2", "admin-a");
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                () -> ledger.advance("tenant-2", "CREDENTIAL_REVOCATION", "admin-a", false, "skip"));
        assertEquals("OFFBOARDING_STAGE_CANNOT_BE_SKIPPED:TERMINATION_REQUESTED->CREDENTIAL_REVOCATION", rejected.getMessage());
    }

    @Test
    void legalHoldForbidsDeletion() throws Exception {
        OffboardingLedger ledger = new OffboardingLedger(temp);
        ledger.request("tenant-3", "admin-a");
        ledger.advance("tenant-3", "NEW_EFFECT_BLOCKED", "admin-a", false, "blocked");
        ledger.advance("tenant-3", "EXPORT_WINDOW", "admin-a", false, "export window open");
        ledger.advance("tenant-3", "CREDENTIAL_REVOCATION", "admin-a", false, "revoked");
        ledger.advance("tenant-3", "PENDING_JOB_SETTLEMENT", "admin-a", false, "settled");
        ledger.advance("tenant-3", "RETENTION_CLASSIFICATION", "admin-a", false, "classified");
        ledger.advance("tenant-3", "CUSTOMER_EXPORT", "admin-a", false, "exported");
        SecurityException denied = assertThrows(SecurityException.class,
                () -> ledger.advance("tenant-3", "DELETION_OR_LEGAL_HOLD_PROCESSING", "admin-a", true, "DELETED"));
        assertEquals("LEGAL_HOLD_FORBIDS_DELETION", denied.getMessage());
    }

    @Test
    void withoutLegalHoldDeletionProceeds() throws Exception {
        OffboardingLedger ledger = new OffboardingLedger(temp);
        ledger.request("tenant-4", "admin-a");
        ledger.advance("tenant-4", "NEW_EFFECT_BLOCKED", "admin-a", false, "blocked");
        ledger.advance("tenant-4", "EXPORT_WINDOW", "admin-a", false, "export window open");
        ledger.advance("tenant-4", "CREDENTIAL_REVOCATION", "admin-a", false, "revoked");
        ledger.advance("tenant-4", "PENDING_JOB_SETTLEMENT", "admin-a", false, "settled");
        ledger.advance("tenant-4", "RETENTION_CLASSIFICATION", "admin-a", false, "classified");
        ledger.advance("tenant-4", "CUSTOMER_EXPORT", "admin-a", false, "exported");
        var event = ledger.advance("tenant-4", "DELETION_OR_LEGAL_HOLD_PROCESSING", "admin-a", false, "DELETED");
        assertEquals("DELETION_OR_LEGAL_HOLD_PROCESSING", event.stage());
    }

    @Test
    void aTenantIdentifierCannotBeReusedWhileOffboardingIsInProgress() throws Exception {
        // FR-FRESH-003: "offboarding 완료 전 tenant identifier 재사용 금지".
        OffboardingLedger ledger = new OffboardingLedger(temp);
        ledger.request("tenant-5", "admin-a");
        assertThrows(IllegalArgumentException.class, () -> ledger.request("tenant-5", "admin-b"));
    }

    @Test
    void isTerminalIsFalseUntilTheReceiptStageIsReached() throws Exception {
        OffboardingLedger ledger = new OffboardingLedger(temp);
        ledger.request("tenant-6", "admin-a");
        assertTrue(!ledger.isTerminal("tenant-6"));
    }
}
