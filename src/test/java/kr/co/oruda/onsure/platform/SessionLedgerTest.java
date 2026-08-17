package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** NFR-SESSION (03 Security Review / DRAFT C12) verification criteria. */
class SessionLedgerTest {
    @TempDir Path temp;
    private final Instant now = Instant.parse("2026-08-15T00:00:00Z");

    @Test
    void aFreshlyCreatedSessionIsValid() throws Exception {
        SessionLedger ledger = new SessionLedger(temp);
        ledger.create("session-1", "user-a", now.plusSeconds(3600), 5, now);
        assertTrue(ledger.isValid("session-1", "user-a", now.plusSeconds(60)));
    }

    @Test
    void anExpiredSessionRequestIsRejected() throws Exception {
        // NFR-SESSION verification method 1: "만료된 세션으로의 요청은 거부되어야 한다".
        SessionLedger ledger = new SessionLedger(temp);
        ledger.create("session-2", "user-a", now.plusSeconds(60), 5, now);
        assertFalse(ledger.isValid("session-2", "user-a", now.plusSeconds(120)));
    }

    @Test
    void concurrentSessionCountOverTheCeilingEvictsTheOldestSession() throws Exception {
        // NFR-SESSION verification method 2: "동일 사용자의 동시 활성 세션 수가 상한을 넘으면
        // 가장 오래된 세션이 무효화되어야 한다".
        SessionLedger ledger = new SessionLedger(temp);
        ledger.create("session-a", "user-b", now.plusSeconds(3600), 2, now);
        ledger.create("session-b", "user-b", now.plusSeconds(3600), 2, now.plusSeconds(10));
        SessionLedger.CreateResult third = ledger.create(
                "session-c", "user-b", now.plusSeconds(3600), 2, now.plusSeconds(20));

        assertEquals("session-a", third.evictedSessionId());
        assertFalse(ledger.isValid("session-a", "user-b", now.plusSeconds(30)));
        assertTrue(ledger.isValid("session-b", "user-b", now.plusSeconds(30)));
        assertTrue(ledger.isValid("session-c", "user-b", now.plusSeconds(30)));
    }

    @Test
    void stayingUnderTheCeilingNeverEvictsAnything() throws Exception {
        SessionLedger ledger = new SessionLedger(temp);
        ledger.create("session-x", "user-c", now.plusSeconds(3600), 5, now);
        SessionLedger.CreateResult second = ledger.create(
                "session-y", "user-c", now.plusSeconds(3600), 5, now.plusSeconds(10));
        assertNull(second.evictedSessionId());
        assertEquals(2, ledger.activeSessionsFor("user-c", now.plusSeconds(20)).size());
    }

    @Test
    void anAlreadyExpiredSessionDoesNotCountTowardTheCeiling() throws Exception {
        SessionLedger ledger = new SessionLedger(temp);
        ledger.create("session-p", "user-d", now.plusSeconds(30), 1, now);
        // session-p expires at now+30; creating session-q at now+60 finds session-p already
        // expired, so it must NOT be evicted (there is nothing active to evict).
        SessionLedger.CreateResult second = ledger.create(
                "session-q", "user-d", now.plusSeconds(3600), 1, now.plusSeconds(60));
        assertNull(second.evictedSessionId());
    }

    @Test
    void duplicateSessionIdIsRejected() throws Exception {
        SessionLedger ledger = new SessionLedger(temp);
        ledger.create("session-dup", "user-e", now.plusSeconds(3600), 5, now);
        assertThrows(IllegalArgumentException.class,
                () -> ledger.create("session-dup", "user-e", now.plusSeconds(3600), 5, now.plusSeconds(10)));
    }

    @Test
    void expiryMustBeStrictlyInTheFuture() {
        SessionLedger ledger = new SessionLedger(temp);
        assertThrows(IllegalArgumentException.class,
                () -> ledger.create("session-z", "user-f", now.minusSeconds(1), 5, now));
    }

    // Tamper-evidence, built in from the start rather than retrofitted.
    @Test
    void aTamperedExpiryIsDetectedOnNextRead() throws Exception {
        SessionLedger ledger = new SessionLedger(temp);
        ledger.create("session-tamper-1", "user-g", now.plusSeconds(60), 5, now);
        Path file = temp.resolve("user-g.json");
        String tampered = Files.readString(file).replace(now.plusSeconds(60).toString(), now.plusSeconds(360000).toString());
        Files.writeString(file, tampered);
        IllegalStateException detected = assertThrows(IllegalStateException.class,
                () -> ledger.isValid("session-tamper-1", "user-g", now.plusSeconds(120)));
        assertTrue(detected.getMessage().contains("SESSION_LEDGER_CHAIN_INVALID"));
    }
}
