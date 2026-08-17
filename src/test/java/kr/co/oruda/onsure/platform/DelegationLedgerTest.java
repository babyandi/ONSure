package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DelegationLedgerTest {
    @TempDir Path temp;
    private final Instant now = Instant.parse("2026-08-14T00:00:00Z");

    @Test
    void anActiveGrantIsFoundBeforeItExpires() throws Exception {
        DelegationLedger ledger = new DelegationLedger(temp);
        ledger.grant("del-1", "actor-a", "actor-b", "APPROVER", now.plusSeconds(3600), "coverage", now);
        var active = ledger.activeGrantsFor("actor-b", "APPROVER", now.plusSeconds(1800));
        assertEquals(1, active.size());
    }

    @Test
    void anExpiredGrantIsNeverReturnedAsActive() throws Exception {
        DelegationLedger ledger = new DelegationLedger(temp);
        ledger.grant("del-2", "actor-a", "actor-b", "APPROVER", now.plusSeconds(3600), "coverage", now);
        var afterExpiry = ledger.activeGrantsFor("actor-b", "APPROVER", now.plusSeconds(7200));
        assertTrue(afterExpiry.isEmpty());
    }

    @Test
    void expiryMustBeStrictlyInTheFuture() {
        DelegationLedger ledger = new DelegationLedger(temp);
        assertThrows(IllegalArgumentException.class,
                () -> ledger.grant("del-3", "actor-a", "actor-b", "APPROVER", now.minusSeconds(1), "coverage", now));
    }

    @Test
    void selfDelegationIsRejected() {
        DelegationLedger ledger = new DelegationLedger(temp);
        assertThrows(IllegalArgumentException.class,
                () -> ledger.grant("del-4", "actor-a", "actor-a", "APPROVER", now.plusSeconds(3600), "coverage", now));
    }

    @Test
    void aGrantForADifferentRoleIsNotReturnedForAnUnrelatedRoleQuery() throws Exception {
        DelegationLedger ledger = new DelegationLedger(temp);
        ledger.grant("del-5", "actor-a", "actor-b", "APPROVER", now.plusSeconds(3600), "coverage", now);
        var operatorGrants = ledger.activeGrantsFor("actor-b", "OPERATOR", now.plusSeconds(60));
        assertTrue(operatorGrants.isEmpty());
    }

    @Test
    void duplicateDelegationIdIsRejected() throws Exception {
        DelegationLedger ledger = new DelegationLedger(temp);
        ledger.grant("del-6", "actor-a", "actor-b", "APPROVER", now.plusSeconds(3600), "coverage", now);
        assertThrows(IllegalArgumentException.class,
                () -> ledger.grant("del-6", "actor-a", "actor-c", "OPERATOR", now.plusSeconds(3600), "coverage", now));
    }

    // Autonomous Development Mode (2026-08-15) tamper-evidence hardening: an expires_at extended
    // outside grant() -- the exact attack this ledger exists to prevent -- must fail to read back.
    @Test
    void aTamperedExpiryIsDetectedOnNextRead() throws Exception {
        DelegationLedger ledger = new DelegationLedger(temp);
        ledger.grant("del-tamper-1", "actor-a", "actor-b", "APPROVER", now.plusSeconds(60), "coverage", now);
        Path file = temp.resolve("del-tamper-1.json");
        String tampered = Files.readString(file).replace(now.plusSeconds(60).toString(), now.plusSeconds(360000).toString());
        Files.writeString(file, tampered);
        IllegalStateException detected = assertThrows(IllegalStateException.class,
                () -> ledger.activeGrantsFor("actor-b", "APPROVER", now.plusSeconds(120)));
        assertTrue(detected.getMessage().contains("DELEGATION_LEDGER_CHAIN_INVALID"));
    }
}
