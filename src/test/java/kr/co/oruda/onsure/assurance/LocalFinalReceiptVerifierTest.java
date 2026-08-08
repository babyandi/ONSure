package kr.co.oruda.onsure.assurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFinalReceiptVerifierTest {
    @TempDir Path temp;

    @Test
    void acceptsHistoricalNonfinalReceiptAfterLaterRunAndRejectsTampering() throws Exception {
        Path run = temp.resolve("receipts/local/run-20260721").toAbsolutePath().normalize();
        Files.createDirectories(run);
        String runId = "run-20260721";
        Instant started = Instant.parse("2026-07-21T12:00:00Z");

        LocalRunContext.write(run.resolve("run-context.json"), runId, started);
        Files.writeString(run.resolve("source-lock.json"), "{\"commit_sha\":\"" + "a".repeat(40)
                + "\",\"tree_sha256\":\"" + "b".repeat(64)
                + "\",\"policy_sha256\":\"" + "c".repeat(64) + "\"}");
        Path fixtureSnapshot = run.resolve("adversarial-transition-fixtures.snapshot.json");
        Files.writeString(fixtureSnapshot, "{\"fixtures\":[]}");
        Path securitySnapshot = run.resolve("security-findings.snapshot.json");
        Files.writeString(securitySnapshot, "{\"contract\":\"ONSURE_SECURITY_FINDINGS_V1\",\"review_status\":\"COMPLETE\",\"review_method\":\"TEST\",\"findings\":[]}");
        Files.writeString(run.resolve("key-registry.snapshot.json"), "[]");
        Files.writeString(run.resolve("final-lock.sha256"), "locked-evidence\n");

        Path otester = run.resolve("otester/receipt.json");
        Path oaudit = run.resolve("oaudit/receipt.json");
        Files.createDirectories(otester.getParent());
        Files.createDirectories(oaudit.getParent());
        Files.writeString(otester, "{\"assurance_run_id\":\"" + runId
                + "\",\"authority\":\"OTESTER\"}");
        Files.writeString(oaudit, "{\"assurance_run_id\":\"" + runId
                + "\",\"authority\":\"OAUDIT\"}");
        Path ledgerPath = run.getParent().resolve("receipt-ledger.jsonl").toAbsolutePath().normalize();
        LocalReceiptLedger ledger = new LocalReceiptLedger(ledgerPath);
        assertEquals(Decision.PASS, ledger.appendAllAtomic(java.util.List.of(otester, oaudit), runId).decision());
        String runHead = ledger.chainHead();

        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("contract", LocalFinalReceiptVerifier.CONTRACT);
        receipt.put("decision", "PASS");
        receipt.put("assurance_class", "SELF_VALIDATION_NONFINAL");
        receipt.put("independent_otester", "NOT_RUN");
        receipt.put("independent_oaudit", "NOT_RUN");
        receipt.put("final_lock_allowed", false);
        receipt.put("production_go", false);
        receipt.put("commercial_go", false);
        receipt.put("execution_mode", "LOCAL_STANDALONE_SELF_VALIDATION");
        receipt.put("assurance_run_id", runId);
        receipt.put("run_started_at", started.toString());
        receipt.put("verified_at", started.plusSeconds(10).toString());
        receipt.put("receipt_dir", run.toString());
        receipt.put("source_commit_sha", "a".repeat(40));
        receipt.put("source_tree_sha256", "b".repeat(64));
        receipt.put("policy_sha256", "c".repeat(64));
        receipt.put("fixture_contract_snapshot", fixtureSnapshot.toString());
        receipt.put("fixture_contract_snapshot_sha256", sha256(fixtureSnapshot));
        receipt.put("security_findings_snapshot", securitySnapshot.toString());
        receipt.put("security_findings_snapshot_sha256", sha256(securitySnapshot));
        receipt.put("key_registry_snapshot", run.resolve("key-registry.snapshot.json").toString());
        receipt.put("key_registry_snapshot_sha256", sha256(run.resolve("key-registry.snapshot.json")));
        receipt.put("ledger", ledgerPath.toString());
        receipt.put("ledger_chain_head", runHead);
        receipt.put("evidence_lock_sha256", sha256(run.resolve("final-lock.sha256")));
        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(run.resolve("final-receipt.json").toFile(), receipt);

        LocalFinalReceiptVerifier verifier = new LocalFinalReceiptVerifier();
        assertEquals(Decision.PASS, verifier.verify(run).decision());

        Path later = temp.resolve("receipts/local/run-20260722").toAbsolutePath().normalize();
        Path laterOtester = later.resolve("otester/receipt.json");
        Path laterOaudit = later.resolve("oaudit/receipt.json");
        Files.createDirectories(laterOtester.getParent());
        Files.createDirectories(laterOaudit.getParent());
        Files.writeString(laterOtester,
                "{\"assurance_run_id\":\"run-20260722\",\"authority\":\"OTESTER\"}");
        Files.writeString(laterOaudit,
                "{\"assurance_run_id\":\"run-20260722\",\"authority\":\"OAUDIT\"}");
        assertEquals(Decision.PASS, ledger.appendAllAtomic(
                java.util.List.of(laterOtester, laterOaudit), "run-20260722").decision());
        assertEquals(Decision.PASS, verifier.verify(run).decision());

        receipt.put("final_lock_allowed", true);
        mapper.writeValue(run.resolve("final-receipt.json").toFile(), receipt);
        assertTrue(verifier.verify(run).violations().contains("FINAL_RECEIPT_FINAL_LOCK_MUST_BE_FALSE"));
        receipt.put("final_lock_allowed", false);

        receipt.put("independent_otester", "PASS");
        mapper.writeValue(run.resolve("final-receipt.json").toFile(), receipt);
        assertTrue(verifier.verify(run).violations().contains("FINAL_RECEIPT_FALSE_OTESTER_CLAIM"));
        receipt.put("independent_otester", "NOT_RUN");

        Files.writeString(fixtureSnapshot, "tampered");
        mapper.writeValue(run.resolve("final-receipt.json").toFile(), receipt);
        assertTrue(verifier.verify(run).violations().contains("FINAL_RECEIPT_FIXTURE_SNAPSHOT_HASH_MISMATCH"));
        Files.writeString(fixtureSnapshot, "{\"fixtures\":[]}");

        receipt.put("ledger", temp.resolve("foreign-ledger.jsonl").toString());
        mapper.writeValue(run.resolve("final-receipt.json").toFile(), receipt);
        assertTrue(verifier.verify(run).violations().contains("FINAL_RECEIPT_LEDGER_PATH_MISMATCH"));
    }

    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
}
