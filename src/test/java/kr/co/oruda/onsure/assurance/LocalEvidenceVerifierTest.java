package kr.co.oruda.onsure.assurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalEvidenceVerifierTest {
    @TempDir Path temp;
    private static final String RUN_ID = "run-context-0001";
    private Path runRoot;
    private Path fixtureSnapshot;
    private final Instant runStartedAt = Instant.parse("2026-07-21T12:00:00Z");

    @BeforeEach
    void setup() throws Exception {
        runRoot = temp.resolve(RUN_ID);
        Files.createDirectories(runRoot);
        LocalRunContext.write(runRoot.resolve("run-context.json"), RUN_ID, runStartedAt);
        fixtureSnapshot = runRoot.resolve("adversarial-transition-fixtures.snapshot.json");
        Files.copy(Path.of("fixtures/design/adversarial-transition-fixtures.v1.json"), fixtureSnapshot);
    }

    @Test
    void acceptsBoundTwoRunAndSignedInternalAgentChain() throws Exception {
        writeSameRegressionEvidence();
        Path otester = writeSignedReceipt("otester/receipt.json", "OTESTER",
                regressionEvidenceDigest(), "otester-key-1", "otester");
        writeSignedReceipt("oaudit/receipt.json", "OAUDIT", sha256(otester), "oaudit-key-1", "oaudit");
        assertEquals(Decision.PASS, new LocalEvidenceVerifier().verify(runRoot).decision());
    }

    @Test
    void rejectsFalseIndependentAuthorityClaim() throws Exception {
        writeSameRegressionEvidence();
        Path otester = writeSignedReceipt("otester/receipt.json", "OTESTER",
                regressionEvidenceDigest(), "otester-false-independent", "otester");
        Map<String, Object> changed = new ObjectMapper().readValue(otester.toFile(), Map.class);
        changed.put("independent_authority", true);
        new ObjectMapper().writeValue(otester.toFile(), changed);
        writeSignedReceipt("oaudit/receipt.json", "OAUDIT", sha256(otester), "oaudit-false-independent", "oaudit");
        ValidationResult result = new LocalEvidenceVerifier().verify(runRoot);
        assertTrue(result.violations().contains("OTESTER_FALSE_INDEPENDENCE_CLAIM"));
        assertTrue(result.violations().contains("OTESTER_SIGNATURE_INVALID"));
    }

    @Test
    void rejectsOtesterNotBoundToRegressionEvidence() throws Exception {
        writeSameRegressionEvidence();
        Path otester = writeSignedReceipt("otester/receipt.json", "OTESTER",
                "a".repeat(64), "otester-wrong-input", "otester");
        writeSignedReceipt("oaudit/receipt.json", "OAUDIT", sha256(otester), "oaudit-wrong-input", "oaudit");
        assertTrue(new LocalEvidenceVerifier().verify(runRoot).violations()
                .contains("OTESTER_INPUT_EVIDENCE_MISMATCH"));
    }

    @Test
    void rejectsTamperedEvidenceManifestOrFixtureReport() throws Exception {
        writeSameRegressionEvidence();
        Path otester = writeSignedReceipt("otester/receipt.json", "OTESTER",
                regressionEvidenceDigest(), "otester-manifest", "otester");
        writeSignedReceipt("oaudit/receipt.json", "OAUDIT", sha256(otester), "oaudit-manifest", "oaudit");
        Files.writeString(runRoot.resolve("regression-1/adversarial-fixtures.tsv"), "tampered\n");
        ValidationResult result = new LocalEvidenceVerifier().verify(runRoot);
        assertTrue(result.violations().contains("ADVERSARIAL_FIXTURE_RESULT_DIVERGENCE"));
        assertTrue(result.violations().contains("REGRESSION_EVIDENCE_DIGEST_MISMATCH"));
    }

    @Test
    void rejectsMissingFixtureSnapshot() throws Exception {
        writeSameRegressionEvidence();
        Files.delete(fixtureSnapshot);
        ValidationResult result = new LocalEvidenceVerifier().verify(runRoot);
        assertTrue(result.violations().contains("ADVERSARIAL_FIXTURE_SNAPSHOT_MISSING"));
    }

    @Test
    void rejectsMissingAgentReceiptContract() throws Exception {
        writeSameRegressionEvidence();
        Path otester = writeSignedReceipt("otester/receipt.json", "OTESTER",
                regressionEvidenceDigest(), "otester-contract", "otester");
        Map<String, Object> changed = new ObjectMapper().readValue(otester.toFile(), Map.class);
        changed.remove("contract");
        new ObjectMapper().writeValue(otester.toFile(), changed);
        writeSignedReceipt("oaudit/receipt.json", "OAUDIT", sha256(otester), "oaudit-contract", "oaudit");
        ValidationResult result = new LocalEvidenceVerifier().verify(runRoot);
        assertTrue(result.violations().contains("OTESTER_RECEIPT_CONTRACT_MISMATCH"));
        assertTrue(result.violations().contains("OTESTER_SIGNATURE_INVALID"));
    }

    @Test
    void rejectsReceiptFromDifferentAssuranceRun() throws Exception {
        writeSameRegressionEvidence();
        Path otester = writeSignedReceipt("otester/receipt.json", "OTESTER",
                regressionEvidenceDigest(), "otester-mixed", "otester");
        Map<String, Object> changed = new ObjectMapper().readValue(otester.toFile(), Map.class);
        changed.put("assurance_run_id", "another-run-0001");
        new ObjectMapper().writeValue(otester.toFile(), changed);
        writeSignedReceipt("oaudit/receipt.json", "OAUDIT", sha256(otester), "oaudit-mixed", "oaudit");
        ValidationResult result = new LocalEvidenceVerifier().verify(runRoot);
        assertTrue(result.violations().contains("OTESTER_RUN_CONTEXT_ID_MISMATCH"));
        assertTrue(result.violations().contains("OTESTER_SIGNATURE_INVALID"));
    }

    @Test
    void acceptsHistoricalReceiptWhenKeyWasValidAtCreationTime() throws Exception {
        writeSameRegressionEvidence();
        Instant createdAt = runStartedAt.plusSeconds(10);
        Path otester = writeSignedReceipt("otester/receipt.json", "OTESTER", regressionEvidenceDigest(),
                "otester-historical", "otester", createdAt, createdAt.minusSeconds(60), createdAt.plusSeconds(60));
        writeSignedReceipt("oaudit/receipt.json", "OAUDIT", sha256(otester),
                "oaudit-historical", "oaudit", createdAt.plusSeconds(10), createdAt.minusSeconds(60), createdAt.plusSeconds(60));
        assertEquals(Decision.PASS, new LocalEvidenceVerifier().verify(runRoot).decision());
    }

    @Test
    void rejectsAuditNotBoundToOtester() throws Exception {
        writeSameRegressionEvidence();
        writeSignedReceipt("otester/receipt.json", "OTESTER", regressionEvidenceDigest(),
                "otester-key-2", "otester");
        writeSignedReceipt("oaudit/receipt.json", "OAUDIT", "b".repeat(64), "oaudit-key-2", "oaudit");
        ValidationResult result = new LocalEvidenceVerifier().verify(runRoot);
        assertTrue(result.violations().contains("OAUDIT_INPUT_EVIDENCE_MISMATCH"));
    }

    @Test
    void rejectsTamperedSignedReceipt() throws Exception {
        writeSameRegressionEvidence();
        Path otester = writeSignedReceipt("otester/receipt.json", "OTESTER", regressionEvidenceDigest(),
                "otester-key-3", "otester");
        Map<String, Object> changed = new ObjectMapper().readValue(otester.toFile(), Map.class);
        changed.put("decision", "FAIL");
        new ObjectMapper().writeValue(otester.toFile(), changed);
        writeSignedReceipt("oaudit/receipt.json", "OAUDIT", sha256(otester), "oaudit-key-3", "oaudit");
        assertTrue(new LocalEvidenceVerifier().verify(runRoot).violations().contains("OTESTER_SIGNATURE_INVALID"));
    }

    @Test
    void rejectsOtesterPolicyInjectedIntoAuditReceipt() throws Exception {
        writeSameRegressionEvidence();
        Path otester = writeSignedReceipt("otester/receipt.json", "OTESTER", regressionEvidenceDigest(),
                "otester-key-5", "otester");
        Path oaudit = writeSignedReceipt("oaudit/receipt.json", "OAUDIT", sha256(otester),
                "oaudit-key-5", "oaudit");
        Map<String, Object> changed = new ObjectMapper().readValue(oaudit.toFile(), Map.class);
        changed.put("role_policy", LocalRolePolicy.OTESTER_POLICY);
        changed.put("evidence_scope", LocalRolePolicy.OTESTER_SCOPE);
        new ObjectMapper().writeValue(oaudit.toFile(), changed);
        ValidationResult result = new LocalEvidenceVerifier().verify(runRoot);
        assertTrue(result.violations().contains("OAUDIT_ROLE_POLICY_MISMATCH"));
        assertTrue(result.violations().contains("OAUDIT_EVIDENCE_SCOPE_MISMATCH"));
        assertTrue(result.violations().contains("OAUDIT_SIGNATURE_INVALID"));
    }

    @Test
    void rejectsMissingRegistrySnapshot() throws Exception {
        writeSameRegressionEvidence();
        ValidationResult result = new LocalEvidenceVerifier().verify(runRoot);
        assertTrue(result.violations().contains("KEY_REGISTRY_SNAPSHOT_MISSING"));
    }

    @Test
    void ledgerRejectsSameReceiptTwice() throws Exception {
        Path receipt = writeSignedReceipt("otester/receipt.json", "OTESTER",
                "a".repeat(64), "otester-key-4", "otester");
        LocalReceiptLedger ledger = new LocalReceiptLedger(temp.resolve("ledger.jsonl"));
        assertEquals(Decision.PASS, ledger.append(receipt).decision());
        assertTrue(ledger.append(receipt).violations().contains("LOCAL_RECEIPT_REPLAY"));
    }

    private void writeSameRegressionEvidence() throws Exception {
        for (String run : java.util.List.of("regression-1", "regression-2")) {
            Path dir = runRoot.resolve(run);
            Files.createDirectories(dir);
            Path summary = dir.resolve("test-summary.txt");
            Path classes = dir.resolve("classes.sha256");
            Path fixtures = dir.resolve("adversarial-fixtures.tsv");
            Files.writeString(summary, "Tests run: 20, Failures: 0, Errors: 0\n");
            Files.writeString(classes, "abc  ./A.class\n");
            assertEquals(Decision.PASS, AdversarialFixtureReportMain.writeReport(
                    fixtureSnapshot, fixtures).decision());
            Files.writeString(dir.resolve("evidence.sha256"),
                    manifestLine(summary) + manifestLine(classes) + manifestLine(fixtures));
        }
    }

    private String regressionEvidenceDigest() throws Exception {
        return sha256(runRoot.resolve("regression-2/evidence.sha256"));
    }

    private static String manifestLine(Path file) throws Exception {
        return sha256(file) + "  " + file.toAbsolutePath().normalize() + "\n";
    }

    private Path writeSignedReceipt(String relative, String authority, String inputDigest,
            String keyId, String keyPrefix) throws Exception {
        Instant createdAt = runStartedAt.plusSeconds(authority.equals("OTESTER") ? 10 : 20);
        return writeSignedReceipt(relative, authority, inputDigest, keyId, keyPrefix,
                createdAt, createdAt.minus(1, ChronoUnit.MINUTES), createdAt.plus(1, ChronoUnit.DAYS));
    }

    private Path writeSignedReceipt(String relative, String authority, String inputDigest,
            String keyId, String keyPrefix, Instant createdAt, Instant validFrom, Instant validUntil) throws Exception {
        var pair = LocalReceiptCrypto.generate();
        Path publicKey = runRoot.resolve("keys/" + keyPrefix + "-public.key");
        LocalReceiptCrypto.writePublicKey(publicKey, pair.getPublic());
        ValidationResult registered = new LocalKeyRegistry(runRoot.resolve("key-registry.snapshot.json")).register(
                new LocalKeyRegistry.KeyRecord(keyId, authority, publicKey.toString(), validFrom, validUntil, false, null));
        assertEquals(Decision.PASS, registered.decision());
        Path file = runRoot.resolve(relative);
        Files.createDirectories(file.getParent());
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("contract", LocalAgentMain.CONTRACT);
        value.put("authority", authority);
        value.put("authority_class", LocalAgentMain.AUTHORITY_CLASS);
        value.put("assurance_class", LocalAgentMain.ASSURANCE_CLASS);
        value.put("independent_authority", false);
        value.put("run_id", authority.toLowerCase() + "-agent-0001");
        value.put("assurance_run_id", RUN_ID);
        value.put("run_started_at", runStartedAt.toString());
        value.put("input_digest", inputDigest);
        value.put("decision", "PASS");
        value.put("created_at", createdAt.toString());
        value.put("execution_mode", "LOCAL_SEPARATE_JVM_SAME_ENVIRONMENT");
        value.put("role_policy", LocalRolePolicy.expectedPolicy(authority));
        value.put("evidence_scope", LocalRolePolicy.expectedScope(authority));
        value.put("key_id", keyId);
        value.put("signature_algorithm", "Ed25519");
        value.put("signature", LocalReceiptCrypto.sign(value, pair.getPrivate()));
        new ObjectMapper().writeValue(file.toFile(), value);
        return file;
    }

    private static String sha256(Path file) throws Exception {
        byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
        return java.util.HexFormat.of().formatHex(hash);
    }
}
