package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalManagementAuditLedgerTest {
    @TempDir Path temp;

    @Test
    void chainsContentFreeEventsAndRejectsTampering() throws Exception {
        LocalManagementAuditLedger ledger = new LocalManagementAuditLedger(temp);
        var identity = new LocalAccessControl.Identity("operator", LocalAccessControl.Role.OPERATOR, "a".repeat(64));
        ledger.append(identity, "PROGRAM_REGISTER", "ACCEPTED", Map.of("target_id", "target-1"));
        ledger.append(identity, "VALIDATION_RUN", "COMPLETED", Map.of("run_id", "run-1"));
        Map<String, Object> recent = ledger.recent(10);
        assertEquals(2, recent.get("event_count"));
        assertTrue(Boolean.TRUE.equals(recent.get("chain_valid")));
        Path file = temp.resolve(".onsure/management/audit.jsonl");
        Files.writeString(file, Files.readString(file).replace("target-1", "target-2"));
        assertThrows(IllegalStateException.class, () -> ledger.recent(10));
    }

    @Test
    void refusesSecretBearingDetails() throws Exception {
        LocalManagementAuditLedger ledger = new LocalManagementAuditLedger(temp);
        var identity = new LocalAccessControl.Identity("admin", LocalAccessControl.Role.ADMIN, "b".repeat(64));
        assertThrows(IllegalArgumentException.class, () -> ledger.append(
                identity, "SETTING_REQUEST", "ACCEPTED", Map.of("api_key", "forbidden")));
    }
}
