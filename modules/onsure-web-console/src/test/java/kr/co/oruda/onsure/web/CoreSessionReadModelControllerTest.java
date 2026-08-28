package kr.co.oruda.onsure.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import kr.co.oruda.onsure.platform.SessionLedger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoreSessionReadModelControllerTest {
    @TempDir
    Path temp;

    private final Instant base = Instant.parse("2026-08-28T00:00:00Z");

    @Test
    void missingBindingFailsClosed() {
        var snapshot = new CoreSessionReadModelController("", "", fixed(base)).sessions();

        assertEquals("ONSURE_WEB_SESSION_READ_MODEL_V1", snapshot.contract());
        assertEquals("SESSION_AUTHORITY_UNBOUND_NONFINAL", snapshot.state());
        assertFalse(snapshot.available());
        assertEquals("SESSION_AUTHORITY_NOT_CONFIGURED", snapshot.blockedReason());
        assertTrue(snapshot.activeSessions().isEmpty());
        assertFalse(snapshot.independentVerificationComplete());
        assertFalse(snapshot.finalClaimAllowed());
        assertFalse(snapshot.productionGo());
    }

    @Test
    void invalidUserIdentityCannotEscapeTheLedgerRoot() throws Exception {
        Path root = Files.createDirectories(temp.resolve("sessions"));
        var snapshot = new CoreSessionReadModelController(
                root.toString(), "../outside", fixed(base)).sessions();

        assertEquals("SESSION_READ_MODEL_BLOCKED_NONFINAL", snapshot.state());
        assertFalse(snapshot.available());
        assertEquals("SESSION_USER_ID_INVALID", snapshot.blockedReason());
    }

    @Test
    void activeProjectionUsesCoreExpiryAndEvictionSemantics() throws Exception {
        Path root = Files.createDirectories(temp.resolve("sessions"));
        SessionLedger ledger = new SessionLedger(root);

        ledger.create("expired-session", "user-a", base.plusSeconds(10), 5, base);
        ledger.create("old-active", "user-a", base.plusSeconds(3600), 1, base.plusSeconds(11));
        SessionLedger.CreateResult latest = ledger.create(
                "current-active", "user-a", base.plusSeconds(3600), 1, base.plusSeconds(12));
        assertEquals("old-active", latest.evictedSessionId());

        var snapshot = new CoreSessionReadModelController(
                root.toString(), "user-a", fixed(base.plusSeconds(30))).sessions();

        assertEquals("CORE_SESSION_READ_MODEL_NONFINAL", snapshot.state());
        assertTrue(snapshot.available());
        assertNull(snapshot.blockedReason());
        assertEquals(1, snapshot.activeSessions().size());
        assertEquals("current-active", snapshot.activeSessions().get(0).sessionId());
        assertEquals("ACTIVE", snapshot.activeSessions().get(0).status());
        assertFalse(snapshot.independentVerificationComplete());
        assertFalse(snapshot.finalClaimAllowed());
        assertFalse(snapshot.productionGo());
    }

    @Test
    void tamperedHashChainFailsClosedInsteadOfReturningSessions() throws Exception {
        Path root = Files.createDirectories(temp.resolve("sessions"));
        SessionLedger ledger = new SessionLedger(root);
        ledger.create("session-tamper", "user-b", base.plusSeconds(3600), 5, base);

        Path ledgerFile = root.resolve("user-b.json");
        String tampered = Files.readString(ledgerFile)
                .replace(base.plusSeconds(3600).toString(), base.plusSeconds(7200).toString());
        Files.writeString(ledgerFile, tampered);

        var snapshot = new CoreSessionReadModelController(
                root.toString(), "user-b", fixed(base.plusSeconds(60))).sessions();

        assertEquals("SESSION_READ_MODEL_BLOCKED_NONFINAL", snapshot.state());
        assertFalse(snapshot.available());
        assertEquals("SESSION_LEDGER_READ_FAILED", snapshot.blockedReason());
        assertTrue(snapshot.activeSessions().isEmpty());
    }

    private static Clock fixed(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }
}
