package io.onsure.assurance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LocalFinalizerMain {
    private LocalFinalizerMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("usage: LocalFinalizerMain <run-root>");
            System.exit(64);
        }
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        Path repositoryRoot = Path.of(".").toAbsolutePath().normalize();
        ValidationResult source = new LocalSourceLockVerifier().verifyAgainstRepository(
                root.resolve("source-lock.json"), repositoryRoot);
        if (source.decision() != Decision.PASS) {
            System.err.println("LOCAL_SOURCE_LOCK_FAIL " + source.violations());
            System.exit(79);
        }
        ValidationResult snapshots = new LocalPolicySnapshotVerifier().verify(root, repositoryRoot);
        if (snapshots.decision() != Decision.PASS) {
            System.err.println("LOCAL_POLICY_SNAPSHOT_FAIL " + snapshots.violations());
            System.exit(77);
        }
        ValidationResult security = new LocalSecurityGateVerifier().verify(
                root.resolve("security-findings.snapshot.json"));
        if (security.decision() != Decision.PASS) {
            System.err.println("LOCAL_SECURITY_GATE_FAIL " + security.violations());
            System.exit(78);
        }
        ValidationResult evidence = new LocalEvidenceVerifier().verify(root);
        if (evidence.decision() != Decision.PASS) {
            System.err.println("LOCAL_EVIDENCE_FAIL " + evidence.violations());
            System.exit(80);
        }
        ValidationResult evidenceLock = new LocalFinalLockVerifier().verify(root.resolve("final-lock.sha256"), root);
        if (evidenceLock.decision() != Decision.PASS) {
            System.err.println("LOCAL_EVIDENCE_LOCK_FAIL " + evidenceLock.violations());
            System.exit(81);
        }

        LocalRunContext.Context runContext = LocalRunContext.read(root.resolve("run-context.json"));
        Path ledger = root.getParent().resolve("receipt-ledger.jsonl");
        LocalReceiptLedger localLedger = new LocalReceiptLedger(ledger);
        ValidationResult existing = localLedger.verifyChain();
        if (existing.decision() != Decision.PASS) {
            System.err.println("LOCAL_LEDGER_CHAIN_FAIL " + existing.violations());
            System.exit(82);
        }
        LocalReceiptLedger.Snapshot ledgerSnapshot = localLedger.snapshot();
        ValidationResult append = localLedger.appendAllAtomic(java.util.List.of(
                root.resolve("otester/receipt.json"), root.resolve("oaudit/receipt.json")), runContext.runId());
        if (append.decision() != Decision.PASS) {
            System.err.println("LOCAL_LEDGER_FAIL " + append.violations());
            System.exit(83);
        }

        Path output = root.resolve("final-receipt.json");
        Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode sourceLock = mapper.readTree(root.resolve("source-lock.json").toFile());
            String chainHead = localLedger.chainHead();
            Path registrySnapshot = root.resolve("key-registry.snapshot.json").toAbsolutePath().normalize();
            Path fixtureSnapshot = root.resolve("adversarial-transition-fixtures.snapshot.json")
                    .toAbsolutePath().normalize();
            Path securitySnapshot = root.resolve("security-findings.snapshot.json")
                    .toAbsolutePath().normalize();
            Map<String, Object> finalReceipt = new LinkedHashMap<>();
            finalReceipt.put("contract", LocalFinalReceiptVerifier.CONTRACT);
            finalReceipt.put("decision", "PASS");
            finalReceipt.put("assurance_class", "SELF_VALIDATION_NONFINAL");
            finalReceipt.put("independent_otester", "NOT_RUN");
            finalReceipt.put("independent_oaudit", "NOT_RUN");
            finalReceipt.put("final_lock_allowed", false);
            finalReceipt.put("production_go", false);
            finalReceipt.put("commercial_go", false);
            finalReceipt.put("execution_mode", "LOCAL_STANDALONE_SELF_VALIDATION");
            finalReceipt.put("assurance_run_id", runContext.runId());
            finalReceipt.put("run_started_at", runContext.startedAt().toString());
            finalReceipt.put("verified_at", Instant.now().toString());
            finalReceipt.put("receipt_dir", root.toString());
            finalReceipt.put("source_commit_sha", sourceLock.path("commit_sha").asText());
            finalReceipt.put("source_tree_sha256", sourceLock.path("tree_sha256").asText());
            finalReceipt.put("policy_sha256", sourceLock.path("policy_sha256").asText());
            finalReceipt.put("fixture_contract_snapshot", fixtureSnapshot.toString());
            finalReceipt.put("fixture_contract_snapshot_sha256", sha256(fixtureSnapshot));
            finalReceipt.put("security_findings_snapshot", securitySnapshot.toString());
            finalReceipt.put("security_findings_snapshot_sha256", sha256(securitySnapshot));
            finalReceipt.put("key_registry_snapshot", registrySnapshot.toString());
            finalReceipt.put("key_registry_snapshot_sha256", sha256(registrySnapshot));
            finalReceipt.put("ledger", ledger.toAbsolutePath().normalize().toString());
            finalReceipt.put("ledger_chain_head", chainHead);
            finalReceipt.put("evidence_lock_sha256", sha256(root.resolve("final-lock.sha256")));
            Files.createDirectories(output.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), finalReceipt);
            moveReplacing(temporary, output);
            ValidationResult published = new LocalFinalReceiptVerifier().verify(root);
            if (published.decision() != Decision.PASS) {
                throw new IllegalStateException("local nonfinal receipt verification failed: " + published.violations());
            }
        } catch (Exception publicationFailure) {
            try { Files.deleteIfExists(temporary); } catch (Exception ignored) {}
            try { Files.deleteIfExists(output); } catch (Exception ignored) {}
            ValidationResult rollback = localLedger.restore(ledgerSnapshot);
            if (rollback.decision() != Decision.PASS) {
                System.err.println("LOCAL_NONFINAL_RECEIPT_FAIL_AND_LEDGER_ROLLBACK_FAIL " + rollback.violations());
                System.exit(85);
            }
            System.err.println("LOCAL_NONFINAL_RECEIPT_FAIL " + publicationFailure.getMessage());
            System.exit(84);
        }
        System.out.println("LOCAL_ASSURANCE_NONFINAL_PASS " + root);
    }

    private static void moveReplacing(Path source, Path target) throws Exception {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String sha256(Path file) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
        return HexFormat.of().formatHex(digest);
    }
}
