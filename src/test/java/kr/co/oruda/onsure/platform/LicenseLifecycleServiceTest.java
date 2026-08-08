package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LicenseLifecycleServiceTest {
    @TempDir Path temp;

    @Test
    void issueActivateAuthorizeReserveCommitAndVerifyLedger() throws Exception {
        LicenseLifecycleService service = new LicenseLifecycleService(temp);
        Instant start = Instant.parse("2026-07-26T00:00:00Z");
        service.issue(tenant(), "license-001", "ONSURE", "DEVELOPER",
                start, start.plus(30, ChronoUnit.DAYS), 100, 24, 60,
                List.of("VALIDATION", "PROGRAM_LEARNING"), "issuer-001");
        service.activate("license-001", start.plusSeconds(10), "operator-001");
        assertEquals("ALLOW", service.authorize(
                "license-001", "VALIDATION", start.plusSeconds(20), true,
                "operator-001").get("decision"));
        service.reserve("license-001", "reservation-001", 20,
                start.plus(1, ChronoUnit.DAYS), start.plusSeconds(30), "operator-001");
        service.commitReservation("license-001", "reservation-001",
                start.plusSeconds(40), "operator-001");
        Map<String, Object> state = service.read("license-001");
        @SuppressWarnings("unchecked")
        Map<String, Object> credits = (Map<String, Object>) state.get("credits");
        assertEquals(80L, ((Number) credits.get("available")).longValue());
        assertEquals(0L, ((Number) credits.get("reserved")).longValue());
        assertEquals(20L, ((Number) credits.get("committed")).longValue());
        assertTrue(service.verify("license-001").valid());
        assertEquals(state.get("ledger_sequence"), state.get("revision"));
    }

    @Test
    void clockRollbackPersistsSuspensionAndDenial() throws Exception {
        LicenseLifecycleService service = new LicenseLifecycleService(temp);
        Instant start = Instant.parse("2026-07-26T00:00:00Z");
        service.issue(tenant(), "license-rollback", "ONSURE", "DEVELOPER",
                start, start.plus(30, ChronoUnit.DAYS), 10, 1, 30,
                List.of("VALIDATION"), "issuer-001");
        service.activate("license-rollback", start.plusSeconds(600), "operator-001");
        Map<String, Object> result = service.validate(
                "license-rollback", start.plusSeconds(100), false, "operator-001");
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) result.get("details");
        assertEquals("DENY", details.get("decision"));
        assertEquals("CLOCK_ROLLBACK_DETECTED", details.get("reason"));
        assertEquals("SUSPENDED", service.read("license-rollback").get("status"));
        assertTrue(service.verify("license-rollback").valid());
    }

    @Test
    void missingTenantFieldAndCreditOverdrawFailClosed() throws Exception {
        LicenseLifecycleService service = new LicenseLifecycleService(temp);
        Instant start = Instant.parse("2026-07-26T00:00:00Z");
        Map<String, Object> incomplete = Map.of(
                "contract", "ONSURE_TENANT_CONTEXT_V1",
                "organization_id", "org-001",
                "tenant_id", "tenant-001",
                "actor_id", "actor-001");
        assertThrows(IllegalArgumentException.class, () -> service.issue(
                incomplete, "license-invalid", "ONSURE", "DEVELOPER",
                start, start.plus(1, ChronoUnit.DAYS), 10, 1, 30,
                List.of("VALIDATION"), "issuer-001"));

        service.issue(tenant(), "license-credit", "ONSURE", "DEVELOPER",
                start, start.plus(1, ChronoUnit.DAYS), 10, 1, 30,
                List.of("VALIDATION"), "issuer-001");
        service.activate("license-credit", start.plusSeconds(1), "operator-001");
        assertThrows(IllegalStateException.class, () -> service.reserve(
                "license-credit", "reservation-too-large", 11,
                start.plusSeconds(100), start.plusSeconds(2), "operator-001"));
    }

    private static Map<String, Object> tenant() {
        return Map.of(
                "contract", "ONSURE_TENANT_CONTEXT_V1",
                "organization_id", "org-001",
                "tenant_id", "tenant-001",
                "workspace_id", "workspace-001",
                "actor_id", "actor-001");
    }
}