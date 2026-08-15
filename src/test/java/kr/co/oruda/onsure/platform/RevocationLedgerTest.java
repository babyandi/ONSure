package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * assurance-revocation-event.candidate.v2.schema.json ledger, previously only exercised
 * indirectly via SemanticAssuranceV2WorkflowServiceTest/DispatcherBridgeTest. Covers the
 * Autonomous Development Mode (2026-08-15) hash-chain tamper-evidence retrofit directly.
 */
class RevocationLedgerTest {
    @TempDir Path temp;

    private Map<String, Object> event(String revocationId, String subjectId) {
        return Map.of(
                "revocation_id", revocationId,
                "revocation_sha256", "43ac647142dac29a5a3105ed53d8b08638e06e288044d7228ef8c985ab79dfa1",
                "subject", Map.of("subject_type", "PLUGIN", "subject_id", subjectId),
                "severity", "HIGH",
                "reason", "compromised",
                "issued_at", "2026-08-14T00:00:00Z");
    }

    @Test
    void anIssuedEventIsReadableBackWithAllFields() throws Exception {
        RevocationLedger ledger = new RevocationLedger(temp);
        ledger.issue(event("rev-1", "plugin-a"));
        List<Map<String, Object>> matches = ledger.forSubject("PLUGIN", "plugin-a");
        assertEquals(1, matches.size());
        assertEquals("rev-1", matches.get(0).get("revocation_id"));
        assertEquals("HIGH", matches.get(0).get("severity"));
    }

    @Test
    void chainLinkageFieldsAreNotLeakedIntoTheReturnedEvent() throws Exception {
        RevocationLedger ledger = new RevocationLedger(temp);
        ledger.issue(event("rev-2", "plugin-b"));
        Map<String, Object> returned = ledger.forSubject("PLUGIN", "plugin-b").get(0);
        assertTrue(!returned.containsKey("sequence"));
        assertTrue(!returned.containsKey("previous_hash"));
        assertTrue(!returned.containsKey("entry_hash"));
    }

    @Test
    void duplicateRevocationIdIsRejected() throws Exception {
        RevocationLedger ledger = new RevocationLedger(temp);
        ledger.issue(event("rev-3", "plugin-c"));
        assertThrows(IllegalArgumentException.class, () -> ledger.issue(event("rev-3", "plugin-d")));
    }

    // Autonomous Development Mode (2026-08-15) tamper-evidence hardening: a severity or subject
    // edited outside issue() must fail to read back rather than being silently trusted.
    @Test
    void aTamperedSeverityIsDetectedOnNextRead() throws Exception {
        RevocationLedger ledger = new RevocationLedger(temp);
        ledger.issue(event("rev-tamper-1", "plugin-e"));
        Path file = temp.resolve("rev-tamper-1.json");
        String tampered = Files.readString(file).replace("HIGH", "LOW");
        Files.writeString(file, tampered);
        IllegalStateException detected = assertThrows(IllegalStateException.class,
                () -> ledger.forSubject("PLUGIN", "plugin-e"));
        assertTrue(detected.getMessage().contains("REVOCATION_LEDGER_CHAIN_INVALID"));
    }

    @Test
    void allReturnsEveryPersistedEventAcrossSubjects() throws Exception {
        RevocationLedger ledger = new RevocationLedger(temp);
        ledger.issue(event("rev-4", "plugin-f"));
        ledger.issue(event("rev-5", "plugin-g"));
        assertEquals(2, ledger.all().size());
    }
}
