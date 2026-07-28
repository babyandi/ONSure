package io.onsure.assurance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Verifies and consumes human/external approval receipts against a trusted key registry. */
public final class ApprovalReceiptVerifier {
    public static final String AUTHORITY = "APPROVAL";
    public static final String AUTHORITY_CLASS = "HUMAN_OR_EXTERNAL_APPROVER";
    public static final String REPLAY_CONTRACT = "ONSURE_APPROVAL_REPLAY_LEDGER_V1";
    private static final String GENESIS = "0".repeat(64);
    private static final Duration MAX_APPROVAL_AGE = Duration.ofDays(7);

    private enum ConsumptionState { NONE, EXACT_RECEIPT, APPROVAL_ID_COLLISION }

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final Path registryFile;
    private final Path replayLedger;
    private final Path replayLock;

    public ApprovalReceiptVerifier(Path registryFile, Path replayLedger) {
        this.registryFile = Objects.requireNonNull(registryFile, "registryFile")
                .toAbsolutePath().normalize();
        this.replayLedger = Objects.requireNonNull(replayLedger, "replayLedger")
                .toAbsolutePath().normalize();
        this.replayLock = this.replayLedger.resolveSibling(this.replayLedger.getFileName() + ".lock");
    }

    public ValidationResult verify(
            Path receiptFile,
            String expectedContract,
            String expectedPurpose,
            Instant now) {
        List<String> violations = new ArrayList<>();
        try {
            Path receipt = receiptFile.toAbsolutePath().normalize();
            if (!Files.isRegularFile(receipt, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(receipt)) {
                return ValidationResult.fail(List.of("APPROVAL_RECEIPT_FILE_INVALID"));
            }
            Map<String, Object> value = readObject(receipt);
            if (!Objects.equals(expectedContract, value.get("contract"))) {
                violations.add("APPROVAL_RECEIPT_CONTRACT_MISMATCH");
            }
            if (!AUTHORITY_CLASS.equals(value.get("authority_class"))) {
                violations.add("APPROVAL_AUTHORITY_CLASS_INVALID");
            }
            if (!Objects.equals(expectedPurpose, value.get("approval_purpose"))) {
                violations.add("APPROVAL_PURPOSE_MISMATCH");
            }
            String approvalId = string(value, "approval_id");
            String actor = string(value, "actor");
            String keyId = string(value, "key_id");
            String nonce = string(value, "nonce");
            if (!approvalId.matches("[A-Za-z0-9._:-]{3,160}")) violations.add("APPROVAL_ID_INVALID");
            if (actor.isBlank() || "ONSURE_AUTOMATION".equals(actor)) violations.add("APPROVAL_ACTOR_INVALID");
            if (!nonce.matches("[A-Za-z0-9._:-]{16,160}")) violations.add("APPROVAL_NONCE_INVALID");
            if (!"Ed25519".equals(value.get("signature_algorithm"))) {
                violations.add("APPROVAL_SIGNATURE_ALGORITHM_INVALID");
            }
            if (string(value, "signature").isBlank()) violations.add("APPROVAL_SIGNATURE_MISSING");

            Instant approvedAt = parseInstant(value, "approved_at", "APPROVAL_TIMESTAMP_INVALID", violations);
            Instant expiresAt = parseInstant(value, "expires_at", "APPROVAL_EXPIRY_INVALID", violations);
            Instant effectiveNow = now == null ? Instant.now() : now;
            if (approvedAt != null && approvedAt.isAfter(effectiveNow.plusSeconds(300))) {
                violations.add("APPROVAL_TIMESTAMP_IN_FUTURE");
            }
            if (approvedAt != null && effectiveNow.isAfter(approvedAt.plus(MAX_APPROVAL_AGE))) {
                violations.add("APPROVAL_TOO_OLD");
            }
            if (expiresAt != null && !effectiveNow.isBefore(expiresAt)) {
                violations.add("APPROVAL_EXPIRED");
            }
            if (approvedAt != null && expiresAt != null
                    && (!expiresAt.isAfter(approvedAt) || expiresAt.isAfter(approvedAt.plus(MAX_APPROVAL_AGE)))) {
                violations.add("APPROVAL_EXPIRY_WINDOW_INVALID");
            }

            LocalKeyRegistry registry = new LocalKeyRegistry(registryFile);
            if (approvedAt != null) {
                ValidationResult keyValidation = registry.validate(keyId, AUTHORITY, approvedAt);
                violations.addAll(keyValidation.violations());
            }
            LocalKeyRegistry.KeyRecord record = registry.load().stream()
                    .filter(item -> Objects.equals(item.keyId(), keyId))
                    .findFirst().orElse(null);
            if (record == null) {
                violations.add("APPROVAL_TRUSTED_KEY_MISSING");
            } else {
                Path publicKey = Path.of(record.publicKeyFile()).toAbsolutePath().normalize();
                if (!Files.isRegularFile(publicKey, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(publicKey)) {
                    violations.add("APPROVAL_PUBLIC_KEY_INVALID");
                } else {
                    try {
                        if (!LocalReceiptCrypto.verify(value, LocalReceiptCrypto.readPublicKey(publicKey))) {
                            violations.add("APPROVAL_SIGNATURE_INVALID");
                        }
                    } catch (Exception verificationFailure) {
                        violations.add("APPROVAL_SIGNATURE_UNREADABLE");
                    }
                }
            }
            String receiptSha = sha256(Files.readAllBytes(receipt));
            ConsumptionState consumption = consumptionState(
                    approvalId, expectedContract, expectedPurpose, receiptSha);
            if (consumption == ConsumptionState.EXACT_RECEIPT) {
                violations.add("APPROVAL_RECEIPT_REPLAY");
            } else if (consumption == ConsumptionState.APPROVAL_ID_COLLISION) {
                violations.add("APPROVAL_RECEIPT_ID_COLLISION");
            }
        } catch (Exception unreadable) {
            violations.add("APPROVAL_RECEIPT_UNREADABLE");
        }
        return violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations);
    }

    public Map<String, Object> requireValidAndConsume(
            Path receiptFile,
            String expectedContract,
            String expectedPurpose,
            Instant now) throws Exception {
        ValidationResult result = verify(receiptFile, expectedContract, expectedPurpose, now);
        if (result.decision() != Decision.PASS) {
            throw new IllegalStateException(
                    "APPROVAL_RECEIPT_INVALID:" + String.join(",", result.violations()));
        }
        Map<String, Object> receipt = readObject(receiptFile);
        ExclusiveFileLock.run(replayLock, () -> appendConsumption(receiptFile, receipt,
                expectedContract, expectedPurpose));
        return Map.copyOf(receipt);
    }

    private void appendConsumption(
            Path receiptFile,
            Map<String, Object> receipt,
            String contract,
            String purpose) throws Exception {
        String approvalId = string(receipt, "approval_id");
        String receiptSha = sha256(Files.readAllBytes(receiptFile));
        ConsumptionState state = consumptionStateUnlocked(
                approvalId, contract, purpose, receiptSha);
        if (state != ConsumptionState.NONE) {
            throw new IllegalStateException(state == ConsumptionState.EXACT_RECEIPT
                    ? "APPROVAL_RECEIPT_REPLAY" : "APPROVAL_RECEIPT_ID_COLLISION");
        }
        List<String> lines = Files.exists(replayLedger)
                ? new ArrayList<>(Files.readAllLines(replayLedger, StandardCharsets.UTF_8))
                : new ArrayList<>();
        String previous = lines.isEmpty() ? GENESIS
                : unwrapJson(mapper.readTree(lines.get(lines.size() - 1))).path("entry_hash").asText();
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("contract", REPLAY_CONTRACT);
        entry.put("sequence", lines.size() + 1L);
        entry.put("approval_id", approvalId);
        entry.put("approval_contract", contract);
        entry.put("approval_purpose", purpose);
        entry.put("actor", receipt.get("actor"));
        entry.put("key_id", receipt.get("key_id"));
        entry.put("nonce", receipt.get("nonce"));
        entry.put("receipt_sha256", receiptSha);
        entry.put("consumed_at", Instant.now().toString());
        entry.put("previous_hash", previous);
        entry.put("entry_hash", canonicalEntryHash(entry));
        lines.add(mapper.writeValueAsString(entry));
        writeLinesAtomic(replayLedger, lines);
    }

    private ConsumptionState consumptionState(
            String approvalId, String contract, String purpose, String receiptSha) throws Exception {
        return ExclusiveFileLock.call(replayLock,
                () -> consumptionStateUnlocked(approvalId, contract, purpose, receiptSha));
    }

    private ConsumptionState consumptionStateUnlocked(
            String approvalId, String contract, String purpose, String receiptSha) throws Exception {
        if (!Files.exists(replayLedger)) return ConsumptionState.NONE;
        String previous = GENESIS;
        long sequence = 1L;
        for (String line : Files.readAllLines(replayLedger, StandardCharsets.UTF_8)) {
            if (line.isBlank()) throw new IllegalStateException("APPROVAL_REPLAY_LEDGER_BLANK");
            Map<String, Object> entry = objectMap(unwrapJson(mapper.readTree(line)));
            if (!REPLAY_CONTRACT.equals(entry.get("contract"))) {
                throw new IllegalStateException("APPROVAL_REPLAY_LEDGER_CONTRACT_INVALID");
            }
            if (number(entry.get("sequence")) != sequence++) {
                throw new IllegalStateException("APPROVAL_REPLAY_LEDGER_SEQUENCE_BROKEN");
            }
            if (!previous.equals(entry.get("previous_hash"))) {
                throw new IllegalStateException("APPROVAL_REPLAY_LEDGER_CHAIN_BROKEN");
            }
            Map<String, Object> unsigned = new TreeMap<>(entry);
            String declared = String.valueOf(unsigned.remove("entry_hash"));
            String calculated = sha256(mapper.writeValueAsBytes(unsigned));
            if (!declared.equals(calculated)) {
                throw new IllegalStateException("APPROVAL_REPLAY_LEDGER_TAMPERED");
            }
            previous = declared;
            if (approvalId.equals(entry.get("approval_id"))
                    && contract.equals(entry.get("approval_contract"))
                    && purpose.equals(entry.get("approval_purpose"))) {
                return receiptSha.equals(entry.get("receipt_sha256"))
                        ? ConsumptionState.EXACT_RECEIPT
                        : ConsumptionState.APPROVAL_ID_COLLISION;
            }
        }
        return ConsumptionState.NONE;
    }

    private String canonicalEntryHash(Map<String, Object> entry) throws Exception {
        Map<String, Object> canonical = new TreeMap<>(entry);
        canonical.remove("entry_hash");
        return sha256(mapper.writeValueAsBytes(canonical));
    }

    private Map<String, Object> readObject(Path file) throws Exception {
        return objectMap(unwrapJson(mapper.readTree(file.toFile())));
    }

    private JsonNode unwrapJson(JsonNode node) throws Exception {
        if (node != null && node.isTextual()) {
            JsonNode decoded = mapper.readTree(node.textValue());
            if (decoded != null && decoded.isTextual()) {
                throw new IllegalArgumentException("MULTIPLE_JSON_STRING_LAYERS_PROHIBITED");
            }
            return decoded;
        }
        return node;
    }

    private Map<String, Object> objectMap(JsonNode node) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException("JSON_OBJECT_REQUIRED");
        Map<String, Object> result = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            result.put(field.getKey(), jsonValue(field.getValue()));
        }
        return result;
    }

    private Object jsonValue(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isObject()) return objectMap(node);
        if (node.isArray()) {
            List<Object> values = new ArrayList<>();
            node.forEach(item -> values.add(jsonValue(item)));
            return values;
        }
        if (node.isTextual()) return node.textValue();
        if (node.isBoolean()) return node.booleanValue();
        if (node.isIntegralNumber()) return node.canConvertToInt() ? node.intValue() : node.longValue();
        if (node.isFloatingPointNumber()) return node.decimalValue();
        throw new IllegalArgumentException("JSON_VALUE_TYPE_UNSUPPORTED:" + node.getNodeType());
    }

    private static Instant parseInstant(
            Map<String, Object> value,
            String key,
            String error,
            List<String> violations) {
        try { return Instant.parse(string(value, key)); }
        catch (Exception invalid) {
            violations.add(error);
            return null;
        }
    }

    private static void writeLinesAtomic(Path file, List<String> lines) throws Exception {
        Files.createDirectories(file.toAbsolutePath().normalize().getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temporary, lines, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String string(Map<String, Object> value, String key) {
        Object item = value.get(key);
        return item instanceof String text ? text : "";
    }

    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(String.valueOf(value));
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
