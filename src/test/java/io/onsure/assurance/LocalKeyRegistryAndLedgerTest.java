package io.onsure.assurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalKeyRegistryAndLedgerTest {
    @TempDir Path temp;
    private static final String RUN_ID = "run-context-0001";

    @Test
    void registryPersistsJavaTimeAndAppliesRevocationFromItsEffectiveTime() throws Exception {
        Path registryPath = temp.resolve("keys/registry.json");
        Path publicKey = registryPath.getParent().resolve("otester.pub");
        Files.createDirectories(publicKey.getParent());
        Files.writeString(publicKey, "placeholder");
        LocalKeyRegistry registry = new LocalKeyRegistry(registryPath);
        Instant now = Instant.parse("2026-07-21T12:00:00Z");
        Instant validFrom = now.minusSeconds(60);
        Instant validUntil = now.plusSeconds(3600);
        Instant revokedAt = now.plusSeconds(120);
        var key = new LocalKeyRegistry.KeyRecord("otester-v1", "OTESTER", publicKey.toString(),
                validFrom, validUntil, false, null);

        assertEquals(Decision.PASS, registry.register(key).decision());
        assertTrue(Files.readString(registryPath).contains("2026-07-21T11:59:00Z"));
        var reloaded = new LocalKeyRegistry(registryPath).load().get(0);
        assertEquals(validFrom, reloaded.validFrom());
        assertEquals(validUntil, reloaded.validUntil());
        assertEquals(Decision.PASS, registry.validate("otester-v1", "OTESTER", now).decision());
        assertEquals(Decision.PASS, registry.revoke("otester-v1", "otester-v2", revokedAt).decision());
        assertEquals(Decision.PASS, registry.validate("otester-v1", "OTESTER", revokedAt.minusSeconds(1)).decision());
        assertTrue(registry.validate("otester-v1", "OTESTER", revokedAt).violations()
                .contains("REVOKED_SIGNING_KEY"));
        assertTrue(registry.validate("otester-v1", "OTESTER", revokedAt.plusSeconds(1)).violations()
                .contains("REVOKED_SIGNING_KEY"));
        assertEquals(revokedAt, registry.load().get(0).revokedAt());
    }

    @Test
    void legacyRevokedRecordWithoutRevocationTimeFailsClosed() throws Exception {
        Path publicKey = temp.resolve("legacy.pub");
        Files.writeString(publicKey, "placeholder");
        Path registryPath = temp.resolve("legacy/registry.json");
        Files.createDirectories(registryPath.getParent());
        Files.writeString(registryPath, """
                [{
                  "keyId":"legacy-key",
                  "authority":"OTESTER",
                  "publicKeyFile":"%s",
                  "validFrom":"2026-07-21T11:00:00Z",
                  "validUntil":"2026-07-21T13:00:00Z",
                  "revoked":true,
                  "replacedBy":"replacement-key"
                }]
                """.formatted(publicKey.toString().replace("\\", "\\\\")));
        ValidationResult result = new LocalKeyRegistry(registryPath).validate(
                "legacy-key", "OTESTER", Instant.parse("2026-07-21T11:30:00Z"));
        assertTrue(result.violations().contains("REVOKED_SIGNING_KEY"));
    }

    @Test
    void ledgerAppendsTwoReceiptsAtomicallyAndExposesChainHead() throws Exception {
        Path receipt1 = receipt("r1.json", 1, RUN_ID);
        Path receipt2 = receipt("r2.json", 2, RUN_ID);
        LocalReceiptLedger ledger = new LocalReceiptLedger(temp.resolve("ledger/ledger.jsonl"));

        assertEquals(Decision.PASS, ledger.appendAllAtomic(List.of(receipt1, receipt2), RUN_ID).decision());
        assertEquals(2, Files.readAllLines(temp.resolve("ledger/ledger.jsonl")).size());
        assertNotEquals("0".repeat(64), ledger.chainHead());
        assertEquals(Decision.PASS, ledger.verifyChain().decision());
        assertTrue(ledger.appendAllAtomic(List.of(receipt1, receipt2), RUN_ID).violations().contains("LOCAL_RECEIPT_REPLAY"));
        assertEquals(2, Files.readAllLines(temp.resolve("ledger/ledger.jsonl")).size());
    }

    @Test
    void ledgerRejectsMissingOrDifferentRunContext() throws Exception {
        Path missing = temp.resolve("missing-run.json");
        Files.writeString(missing, "{\"id\":1}");
        Path other = receipt("other-run.json", 2, "run-context-0002");
        LocalReceiptLedger ledger = new LocalReceiptLedger(temp.resolve("run-check/ledger.jsonl"));

        assertTrue(ledger.append(missing).violations().contains("LOCAL_LEDGER_RUN_ID_INVALID"));
        assertTrue(ledger.appendAllAtomic(List.of(other), RUN_ID).violations()
                .contains("LOCAL_LEDGER_RUN_CONTEXT_MISMATCH"));
    }

    @Test
    void ledgerSnapshotRestoresExactPriorState() throws Exception {
        Path receipt1 = receipt("snapshot-r1.json", 1, RUN_ID);
        Path receipt2 = receipt("snapshot-r2.json", 2, RUN_ID);
        Path ledgerPath = temp.resolve("snapshot-ledger/ledger.jsonl");
        LocalReceiptLedger ledger = new LocalReceiptLedger(ledgerPath);

        assertEquals(Decision.PASS, ledger.append(receipt1).decision());
        byte[] before = Files.readAllBytes(ledgerPath);
        String headBefore = ledger.chainHead();
        LocalReceiptLedger.Snapshot snapshot = ledger.snapshot();
        assertEquals(Decision.PASS, ledger.append(receipt2).decision());
        assertNotEquals(headBefore, ledger.chainHead());

        assertEquals(Decision.PASS, ledger.restore(snapshot).decision());
        assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(ledgerPath)));
        assertEquals(headBefore, ledger.chainHead());
        assertEquals(Decision.PASS, ledger.verifyChain().decision());
    }

    @Test
    void ledgerDetectsMiddleEntryTampering() throws Exception {
        Path receipt1 = receipt("r1.json", 1, RUN_ID);
        Path receipt2 = receipt("r2.json", 2, RUN_ID);
        Path ledgerPath = temp.resolve("ledger/ledger.jsonl");
        LocalReceiptLedger ledger = new LocalReceiptLedger(ledgerPath);
        assertEquals(Decision.PASS, ledger.appendAllAtomic(List.of(receipt1, receipt2), RUN_ID).decision());
        String content = Files.readString(ledgerPath).replace("r1.json", "evil.json");
        Files.writeString(ledgerPath, content);
        ValidationResult result = ledger.verifyChain();
        assertTrue(result.violations().contains("LOCAL_LEDGER_ENTRY_TAMPERED"));
    }

    private Path receipt(String filename, int id, String runId) throws Exception {
        Path receipt = temp.resolve(filename);
        Files.writeString(receipt, "{\"id\":" + id + ",\"assurance_run_id\":\"" + runId + "\"}");
        return receipt;
    }
}
