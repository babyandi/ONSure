package kr.co.oruda.onsure.assurance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LocalReceiptLedger {
    private static final String GENESIS = "0".repeat(64);
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path ledgerFile;

    public record Snapshot(boolean existed, byte[] content) {
        public Snapshot {
            content = content == null ? new byte[0] : content.clone();
        }
        @Override public byte[] content() { return content.clone(); }
    }

    public LocalReceiptLedger(Path ledgerFile) { this.ledgerFile = ledgerFile; }

    public synchronized Snapshot snapshot() throws Exception {
        return Files.exists(ledgerFile)
                ? new Snapshot(true, Files.readAllBytes(ledgerFile))
                : new Snapshot(false, new byte[0]);
    }

    public synchronized ValidationResult restore(Snapshot snapshot) {
        Path temp = ledgerFile.resolveSibling(ledgerFile.getFileName() + ".restore.tmp");
        try {
            if (!snapshot.existed()) {
                Files.deleteIfExists(ledgerFile);
                Files.deleteIfExists(temp);
                return ValidationResult.pass();
            }
            Files.createDirectories(ledgerFile.getParent());
            Files.write(temp, snapshot.content());
            moveReplacing(temp, ledgerFile);
            return verifyChain();
        } catch (Exception e) {
            try { Files.deleteIfExists(temp); } catch (Exception ignored) {}
            return ValidationResult.fail(List.of("LOCAL_LEDGER_RESTORE_FAILED"));
        }
    }

    public synchronized ValidationResult append(Path receiptFile) {
        return appendAllAtomic(List.of(receiptFile), null);
    }

    public synchronized ValidationResult appendAllAtomic(List<Path> receiptFiles) {
        return appendAllAtomic(receiptFiles, null);
    }

    public synchronized ValidationResult appendAllAtomic(List<Path> receiptFiles, String assuranceRunId) {
        if (assuranceRunId != null && !validRunId(assuranceRunId)) {
            return ValidationResult.fail(List.of("LOCAL_LEDGER_RUN_ID_INVALID"));
        }
        ValidationResult chain = verifyChain();
        if (chain.decision() != Decision.PASS) return chain;
        Path temp = ledgerFile.resolveSibling(ledgerFile.getFileName() + ".tmp");
        try {
            List<String> lines = Files.exists(ledgerFile)
                    ? new ArrayList<>(Files.readAllLines(ledgerFile, StandardCharsets.UTF_8))
                    : new ArrayList<>();
            List<String> seen = new ArrayList<>();
            for (String line : lines) seen.add(mapper.readTree(line).path("receipt_digest").asText());
            String previous = lines.isEmpty() ? GENESIS
                    : mapper.readTree(lines.get(lines.size() - 1)).path("entry_hash").asText();

            for (Path receiptFile : receiptFiles) {
                if (!Files.isRegularFile(receiptFile)) return ValidationResult.fail(List.of("LOCAL_RECEIPT_MISSING"));
                JsonNode receipt = mapper.readTree(receiptFile.toFile());
                String receiptRunId = receipt.path("assurance_run_id").asText();
                if (!validRunId(receiptRunId)) {
                    return ValidationResult.fail(List.of("LOCAL_LEDGER_RUN_ID_INVALID"));
                }
                if (assuranceRunId != null && !assuranceRunId.equals(receiptRunId)) {
                    return ValidationResult.fail(List.of("LOCAL_LEDGER_RUN_CONTEXT_MISMATCH"));
                }
                String receiptDigest = sha256(Files.readAllBytes(receiptFile));
                if (seen.contains(receiptDigest)) return ValidationResult.fail(List.of("LOCAL_RECEIPT_REPLAY"));
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("assurance_run_id", assuranceRunId == null ? receiptRunId : assuranceRunId);
                body.put("receipt_digest", receiptDigest);
                body.put("receipt_file", receiptFile.toAbsolutePath().normalize().toString());
                body.put("recorded_at", Instant.now().toString());
                body.put("previous_hash", previous);
                String entryHash = sha256(mapper.writeValueAsBytes(body));
                body.put("entry_hash", entryHash);
                lines.add(mapper.writeValueAsString(body));
                seen.add(receiptDigest);
                previous = entryHash;
            }

            Files.createDirectories(ledgerFile.getParent());
            Files.write(temp, lines, StandardCharsets.UTF_8);
            moveReplacing(temp, ledgerFile);
            return verifyChain();
        } catch (Exception e) {
            try { Files.deleteIfExists(temp); } catch (Exception ignored) {}
            return ValidationResult.fail(List.of("LOCAL_LEDGER_WRITE_FAILED"));
        }
    }

    public synchronized ValidationResult verifyChain() {
        try {
            if (!Files.exists(ledgerFile)) return ValidationResult.pass();
            String expectedPrevious = GENESIS;
            List<String> seen = new ArrayList<>();
            for (String line : Files.readAllLines(ledgerFile, StandardCharsets.UTF_8)) {
                if (line.isBlank()) return ValidationResult.fail(List.of("LOCAL_LEDGER_CHAIN_BROKEN"));
                JsonNode node = mapper.readTree(line);
                String runId = node.path("assurance_run_id").asText();
                if (!validRunId(runId)) return ValidationResult.fail(List.of("LOCAL_LEDGER_RUN_ID_INVALID"));
                String receiptDigest = node.path("receipt_digest").asText();
                if (!receiptDigest.matches("[0-9a-f]{64}") || seen.contains(receiptDigest))
                    return ValidationResult.fail(List.of("LOCAL_LEDGER_DUPLICATE_OR_INVALID_RECEIPT"));
                if (!expectedPrevious.equals(node.path("previous_hash").asText()))
                    return ValidationResult.fail(List.of("LOCAL_LEDGER_CHAIN_BROKEN"));
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("assurance_run_id", runId);
                body.put("receipt_digest", receiptDigest);
                body.put("receipt_file", node.path("receipt_file").asText());
                body.put("recorded_at", node.path("recorded_at").asText());
                body.put("previous_hash", node.path("previous_hash").asText());
                String calculated = sha256(mapper.writeValueAsBytes(body));
                if (!calculated.equals(node.path("entry_hash").asText()))
                    return ValidationResult.fail(List.of("LOCAL_LEDGER_ENTRY_TAMPERED"));
                expectedPrevious = calculated;
                seen.add(receiptDigest);
            }
            return ValidationResult.pass();
        } catch (Exception e) {
            return ValidationResult.fail(List.of("LOCAL_LEDGER_READ_FAILED"));
        }
    }

    public synchronized ValidationResult verifyRunBinding(
            String assuranceRunId, List<Path> receiptFiles, String expectedRunHead) {
        if (!validRunId(assuranceRunId) || expectedRunHead == null
                || !expectedRunHead.matches("[0-9a-f]{64}")) {
            return ValidationResult.fail(List.of("LOCAL_LEDGER_RUN_BINDING_INVALID"));
        }
        ValidationResult chain = verifyChain();
        if (chain.decision() != Decision.PASS) return chain;
        List<String> violations = new ArrayList<>();
        try {
            List<JsonNode> entries = new ArrayList<>();
            if (Files.exists(ledgerFile)) {
                for (String line : Files.readAllLines(ledgerFile, StandardCharsets.UTF_8)) {
                    JsonNode node = mapper.readTree(line);
                    if (assuranceRunId.equals(node.path("assurance_run_id").asText())) entries.add(node);
                }
            }
            if (entries.size() != receiptFiles.size()) {
                violations.add("LOCAL_LEDGER_RUN_ENTRY_COUNT_MISMATCH");
            }
            int checked = Math.min(entries.size(), receiptFiles.size());
            for (int i = 0; i < checked; i++) {
                Path file = receiptFiles.get(i).toAbsolutePath().normalize();
                JsonNode entry = entries.get(i);
                if (!Files.isRegularFile(file)) {
                    violations.add("LOCAL_RECEIPT_MISSING");
                    continue;
                }
                if (!file.toString().equals(entry.path("receipt_file").asText())) {
                    violations.add("LOCAL_LEDGER_RECEIPT_PATH_MISMATCH");
                }
                String digest = sha256(Files.readAllBytes(file));
                if (!digest.equals(entry.path("receipt_digest").asText())) {
                    violations.add("LOCAL_LEDGER_RECEIPT_DIGEST_MISMATCH");
                }
            }
            if (entries.isEmpty()
                    || !expectedRunHead.equals(entries.get(entries.size() - 1).path("entry_hash").asText())) {
                violations.add("LOCAL_LEDGER_RUN_HEAD_MISMATCH");
            }
        } catch (Exception e) {
            violations.add("LOCAL_LEDGER_RUN_BINDING_UNREADABLE");
        }
        return violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations);
    }

    public synchronized String chainHead() {
        ValidationResult result = verifyChain();
        if (result.decision() != Decision.PASS) throw new IllegalStateException("invalid ledger chain: " + result.violations());
        try {
            if (!Files.exists(ledgerFile)) return GENESIS;
            List<String> lines = Files.readAllLines(ledgerFile, StandardCharsets.UTF_8);
            return lines.isEmpty() ? GENESIS : mapper.readTree(lines.get(lines.size() - 1)).path("entry_hash").asText();
        } catch (Exception e) {
            throw new IllegalStateException("cannot read ledger chain head", e);
        }
    }

    private static boolean validRunId(String value) {
        return value != null && value.matches("[A-Za-z0-9._:-]{8,128}");
    }

    private static void moveReplacing(Path source, Path target) throws Exception {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
