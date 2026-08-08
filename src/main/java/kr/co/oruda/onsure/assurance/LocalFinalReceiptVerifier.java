package kr.co.oruda.onsure.assurance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

public final class LocalFinalReceiptVerifier {
    public static final String CONTRACT = "ONSURE_LOCAL_FINAL_RECEIPT_V1";
    private final ObjectMapper mapper = new ObjectMapper();

    public ValidationResult verify(Path runRoot) {
        List<String> violations = new ArrayList<>();
        Path root = runRoot.toAbsolutePath().normalize();
        Path finalReceipt = root.resolve("final-receipt.json");
        if (!Files.isRegularFile(finalReceipt)) return ValidationResult.fail(List.of("FINAL_RECEIPT_MISSING"));
        try {
            JsonNode receipt = mapper.readTree(finalReceipt.toFile());
            LocalRunContext.Context context = LocalRunContext.read(root.resolve("run-context.json"));
            JsonNode sourceLock = mapper.readTree(root.resolve("source-lock.json").toFile());
            Path fixtureSnapshot = root.resolve("adversarial-transition-fixtures.snapshot.json")
                    .toAbsolutePath().normalize();
            Path securitySnapshot = root.resolve("security-findings.snapshot.json")
                    .toAbsolutePath().normalize();
            Path registrySnapshot = root.resolve("key-registry.snapshot.json").toAbsolutePath().normalize();
            Path evidenceLock = root.resolve("final-lock.sha256").toAbsolutePath().normalize();
            Path ledger = root.getParent().resolve("receipt-ledger.jsonl").toAbsolutePath().normalize();

            if (!CONTRACT.equals(receipt.path("contract").asText())) violations.add("FINAL_RECEIPT_CONTRACT_MISMATCH");
            if (!"PASS".equals(receipt.path("decision").asText())) violations.add("FINAL_RECEIPT_NON_PASS");
            if (!"SELF_VALIDATION_NONFINAL".equals(receipt.path("assurance_class").asText())) violations.add("FINAL_RECEIPT_ASSURANCE_CLASS_INVALID");
            if (!"NOT_RUN".equals(receipt.path("independent_otester").asText())) violations.add("FINAL_RECEIPT_FALSE_OTESTER_CLAIM");
            if (!"NOT_RUN".equals(receipt.path("independent_oaudit").asText())) violations.add("FINAL_RECEIPT_FALSE_OAUDIT_CLAIM");
            if (receipt.path("final_lock_allowed").asBoolean(true)) violations.add("FINAL_RECEIPT_FINAL_LOCK_MUST_BE_FALSE");
            if (receipt.path("production_go").asBoolean(true)) violations.add("FINAL_RECEIPT_PRODUCTION_GO_MUST_BE_FALSE");
            if (receipt.path("commercial_go").asBoolean(true)) violations.add("FINAL_RECEIPT_COMMERCIAL_GO_MUST_BE_FALSE");
            if (!"LOCAL_STANDALONE_SELF_VALIDATION".equals(receipt.path("execution_mode").asText())) violations.add("FINAL_RECEIPT_EXECUTION_MODE_INVALID");
            if (!Objects.equals(context.runId(), receipt.path("assurance_run_id").asText())) violations.add("FINAL_RECEIPT_RUN_ID_MISMATCH");
            if (!Objects.equals(context.startedAt().toString(), receipt.path("run_started_at").asText())) violations.add("FINAL_RECEIPT_RUN_START_MISMATCH");
            try {
                Instant verifiedAt = Instant.parse(receipt.path("verified_at").asText());
                if (verifiedAt.isBefore(context.startedAt())) violations.add("FINAL_RECEIPT_VERIFIED_BEFORE_RUN_START");
            } catch (Exception e) {
                violations.add("FINAL_RECEIPT_VERIFIED_AT_INVALID");
            }
            if (!Objects.equals(root.toString(), receipt.path("receipt_dir").asText())) violations.add("FINAL_RECEIPT_DIRECTORY_MISMATCH");
            if (!Objects.equals(fixtureSnapshot.toString(), receipt.path("fixture_contract_snapshot").asText())) violations.add("FINAL_RECEIPT_FIXTURE_SNAPSHOT_PATH_MISMATCH");
            if (!Objects.equals(securitySnapshot.toString(), receipt.path("security_findings_snapshot").asText())) violations.add("FINAL_RECEIPT_SECURITY_SNAPSHOT_PATH_MISMATCH");
            if (!Objects.equals(registrySnapshot.toString(), receipt.path("key_registry_snapshot").asText())) violations.add("FINAL_RECEIPT_KEY_REGISTRY_PATH_MISMATCH");
            if (!Objects.equals(ledger.toString(), receipt.path("ledger").asText())) violations.add("FINAL_RECEIPT_LEDGER_PATH_MISMATCH");
            if (!Objects.equals(sourceLock.path("commit_sha").asText(), receipt.path("source_commit_sha").asText())) violations.add("FINAL_RECEIPT_SOURCE_COMMIT_MISMATCH");
            if (!Objects.equals(sourceLock.path("tree_sha256").asText(), receipt.path("source_tree_sha256").asText())) violations.add("FINAL_RECEIPT_SOURCE_TREE_MISMATCH");
            if (!Objects.equals(sourceLock.path("policy_sha256").asText(), receipt.path("policy_sha256").asText())) violations.add("FINAL_RECEIPT_POLICY_MISMATCH");
            if (!Objects.equals(sha256(fixtureSnapshot), receipt.path("fixture_contract_snapshot_sha256").asText())) violations.add("FINAL_RECEIPT_FIXTURE_SNAPSHOT_HASH_MISMATCH");
            if (!Objects.equals(sha256(securitySnapshot), receipt.path("security_findings_snapshot_sha256").asText())) violations.add("FINAL_RECEIPT_SECURITY_SNAPSHOT_HASH_MISMATCH");
            if (!Objects.equals(sha256(registrySnapshot), receipt.path("key_registry_snapshot_sha256").asText())) violations.add("FINAL_RECEIPT_KEY_REGISTRY_HASH_MISMATCH");
            if (!Objects.equals(sha256(evidenceLock), receipt.path("evidence_lock_sha256").asText())) violations.add("FINAL_RECEIPT_EVIDENCE_LOCK_HASH_MISMATCH");
            ValidationResult security = new LocalSecurityGateVerifier().verify(securitySnapshot);
            if (security.decision() != Decision.PASS) violations.addAll(security.violations());
            LocalReceiptLedger localLedger = new LocalReceiptLedger(ledger);
            ValidationResult runBinding = localLedger.verifyRunBinding(
                    context.runId(),
                    List.of(root.resolve("otester/receipt.json"), root.resolve("oaudit/receipt.json")),
                    receipt.path("ledger_chain_head").asText());
            if (runBinding.decision() != Decision.PASS) violations.addAll(runBinding.violations());
        } catch (Exception e) {
            violations.add("FINAL_RECEIPT_UNREADABLE");
        }
        return violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations);
    }

    private static String sha256(Path file) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
        return HexFormat.of().formatHex(digest);
    }
}
