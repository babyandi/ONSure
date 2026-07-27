package io.onsure.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.onsure.assurance.ExclusiveFileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Crash-recoverable file store that binds one mutable state document to an append-only hash ledger.
 *
 * <p>Every transition is prepared under an exclusive JVM/OS lock, written to transaction files,
 * and then committed. A later reader completes an interrupted commit before exposing state.
 */
public final class DurableStateLedger {
    public static final String TRANSACTION_CONTRACT = "ONSURE_DURABLE_STATE_LEDGER_TX_V1";
    public static final String GENESIS = "0".repeat(64);
    private static final TypeReference<Map<String, Object>> STRING_OBJECT_MAP =
            new TypeReference<Map<String, Object>>() {};

    public record Verification(boolean valid, List<String> violations, long sequence, String head) {
        public Verification { violations = List.copyOf(violations); }
    }

    @FunctionalInterface
    public interface Mutation {
        Map<String, Object> apply(Map<String, Object> state) throws Exception;
    }

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final Path root;
    private final Path stateFile;
    private final Path ledgerFile;
    private final Path lockFile;
    private final Path transactionRoot;
    private final Path transactionManifest;
    private final Path nextStateFile;
    private final Path nextLedgerFile;
    private final String stateContract;
    private final String eventContract;
    private final String entityField;
    private final String entityId;

    public DurableStateLedger(
            Path root,
            String stateContract,
            String eventContract,
            String entityField,
            String entityId) {
        this.root = root.toAbsolutePath().normalize();
        this.stateFile = this.root.resolve("state.json");
        this.ledgerFile = this.root.resolve("ledger.jsonl");
        this.lockFile = this.root.resolve("state-ledger.lock");
        this.transactionRoot = this.root.resolve(".transaction");
        this.transactionManifest = transactionRoot.resolve("manifest.json");
        this.nextStateFile = transactionRoot.resolve("state.next.json");
        this.nextLedgerFile = transactionRoot.resolve("ledger.next.jsonl");
        this.stateContract = requireText(stateContract, "STATE_CONTRACT_INVALID");
        this.eventContract = requireText(eventContract, "EVENT_CONTRACT_INVALID");
        this.entityField = requireText(entityField, "ENTITY_FIELD_INVALID");
        this.entityId = requireText(entityId, "ENTITY_ID_INVALID");
    }

    public Map<String, Object> initialize(
            Map<String, Object> initialState,
            String eventType,
            String actor,
            Map<String, Object> details) throws Exception {
        return ExclusiveFileLock.call(lockFile, () -> {
            recoverLocked();
            if (Files.exists(stateFile, LinkOption.NOFOLLOW_LINKS)
                    || Files.exists(ledgerFile, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("STATE_LEDGER_ALREADY_EXISTS");
            }
            Map<String, Object> state = new LinkedHashMap<>(initialState);
            state.put("contract", stateContract);
            state.put(entityField, entityId);
            state.put("revision", 1L);
            return commitLocked(null, List.of(), state, eventType, actor, details);
        });
    }

    public Map<String, Object> mutate(String eventType, String actor, Mutation mutation) throws Exception {
        return ExclusiveFileLock.call(lockFile, () -> {
            recoverLocked();
            Map<String, Object> state = readAndVerifyLocked();
            List<String> lines = readLedgerLines();
            Map<String, Object> details = mutation.apply(state);
            state.put("revision", number(state.get("revision")) + 1L);
            return commitLocked(string(state, "state_sha256"), lines, state,
                    eventType, actor, details == null ? Map.of() : details);
        });
    }

    public Map<String, Object> read() throws Exception {
        return ExclusiveFileLock.call(lockFile, () -> snapshot(readAndVerifyLocked()));
    }

    public Verification verify() throws Exception {
        return ExclusiveFileLock.call(lockFile, () -> {
            recoverLocked();
            return verifyLocked();
        });
    }

    private Map<String, Object> commitLocked(
            String previousStateSha,
            List<String> existingLines,
            Map<String, Object> state,
            String eventType,
            String actor,
            Map<String, Object> details) throws Exception {
        requireText(eventType, "EVENT_TYPE_INVALID");
        requireText(actor, "EVENT_ACTOR_INVALID");
        long sequence = existingLines.size() + 1L;
        String previousHead = existingLines.isEmpty() ? GENESIS
                : mapper.readTree(existingLines.get(existingLines.size() - 1)).path("entry_hash").asText();

        state.put("ledger_sequence", sequence);
        state.put("updated_at", Instant.now().toString());
        String payloadSha = statePayloadSha(state);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("contract", eventContract);
        event.put("sequence", sequence);
        event.put(entityField, entityId);
        event.put("event_type", eventType);
        event.put("actor", actor);
        event.put("details", new TreeMap<>(details));
        event.put("previous_state_sha256", previousStateSha == null ? GENESIS : previousStateSha);
        event.put("next_state_payload_sha256", payloadSha);
        event.put("recorded_at", Instant.now().toString());
        event.put("previous_hash", previousHead);
        String eventHash = sha256(mapper.writeValueAsBytes(event));
        event.put("entry_hash", eventHash);

        state.put("ledger_head", eventHash);
        state.put("state_sha256", stateSha(state));
        List<String> nextLines = new ArrayList<>(existingLines);
        nextLines.add(mapper.writeValueAsString(event));

        prepareTransaction(state, nextLines);
        commitPreparedTransaction();
        Map<String, Object> verified = readAndVerifyLocked();
        return Map.of(
                "state", snapshot(verified),
                "event", eventType,
                "details", snapshot(details),
                "ledger_sequence", sequence,
                "ledger_head", eventHash,
                "assurance_class", "SELF_VALIDATION_NONFINAL",
                "final_claim_allowed", false);
    }

    private void prepareTransaction(Map<String, Object> state, List<String> ledgerLines) throws Exception {
        Files.createDirectories(transactionRoot);
        writeJsonAtomic(nextStateFile, state);
        writeLinesAtomic(nextLedgerFile, ledgerLines);
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("contract", TRANSACTION_CONTRACT);
        manifest.put("state_file_sha256", fileSha(nextStateFile));
        manifest.put("ledger_file_sha256", fileSha(nextLedgerFile));
        manifest.put("prepared_at", Instant.now().toString());
        writeJsonAtomic(transactionManifest, manifest);
    }

    private void commitPreparedTransaction() throws Exception {
        JsonNode manifest = mapper.readTree(transactionManifest.toFile());
        requirePrepared(nextLedgerFile, ledgerFile, manifest.path("ledger_file_sha256").asText(),
                "LEDGER_TRANSACTION_FILE_INVALID");
        requirePrepared(nextStateFile, stateFile, manifest.path("state_file_sha256").asText(),
                "STATE_TRANSACTION_FILE_INVALID");
        Files.deleteIfExists(transactionManifest);
        Files.deleteIfExists(nextStateFile);
        Files.deleteIfExists(nextLedgerFile);
        try { Files.deleteIfExists(transactionRoot); } catch (Exception ignored) {}
    }

    private void recoverLocked() throws Exception {
        if (!Files.exists(transactionManifest, LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.isSymbolicLink(transactionManifest)) {
            throw new IllegalStateException("STATE_LEDGER_TRANSACTION_SYMLINK");
        }
        JsonNode manifest = mapper.readTree(transactionManifest.toFile());
        if (!TRANSACTION_CONTRACT.equals(manifest.path("contract").asText())) {
            throw new IllegalStateException("STATE_LEDGER_TRANSACTION_CONTRACT_INVALID");
        }
        commitPreparedTransaction();
    }

    private void requirePrepared(Path prepared, Path destination, String expectedSha, String error)
            throws Exception {
        if (Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(destination)
                && expectedSha.equals(fileSha(destination))) {
            return;
        }
        if (!Files.isRegularFile(prepared, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(prepared)
                || !expectedSha.equals(fileSha(prepared))) {
            throw new IllegalStateException(error);
        }
        move(prepared, destination);
    }

    private Map<String, Object> readAndVerifyLocked() throws Exception {
        recoverLocked();
        Verification verification = verifyLocked();
        if (!verification.valid()) {
            throw new IllegalStateException("STATE_LEDGER_INVALID:" + String.join(",", verification.violations()));
        }
        return readStateRaw();
    }

    private Verification verifyLocked() throws Exception {
        List<String> violations = new ArrayList<>();
        if (!Files.isRegularFile(stateFile, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(stateFile)) {
            return new Verification(false, List.of("STATE_FILE_MISSING"), 0, GENESIS);
        }
        if (!Files.isRegularFile(ledgerFile, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(ledgerFile)) {
            return new Verification(false, List.of("LEDGER_FILE_MISSING"), 0, GENESIS);
        }
        Map<String, Object> state = readStateRaw();
        if (!stateContract.equals(state.get("contract"))) violations.add("STATE_CONTRACT_MISMATCH");
        if (!entityId.equals(state.get(entityField))) violations.add("STATE_ENTITY_MISMATCH");
        if (!string(state, "state_sha256").equals(stateSha(state))) violations.add("STATE_HASH_MISMATCH");

        String previous = GENESIS;
        long expectedSequence = 1L;
        JsonNode last = null;
        for (String line : readLedgerLines()) {
            if (line.isBlank()) {
                violations.add("LEDGER_BLANK_LINE");
                continue;
            }
            JsonNode event;
            try { event = mapper.readTree(line); }
            catch (Exception invalid) {
                violations.add("LEDGER_JSON_INVALID");
                continue;
            }
            if (!eventContract.equals(event.path("contract").asText())) violations.add("EVENT_CONTRACT_MISMATCH");
            if (event.path("sequence").asLong(-1) != expectedSequence) violations.add("LEDGER_SEQUENCE_BROKEN");
            if (!entityId.equals(event.path(entityField).asText())) violations.add("LEDGER_ENTITY_MISMATCH");
            if (!previous.equals(event.path("previous_hash").asText())) violations.add("LEDGER_PREVIOUS_HASH_BROKEN");
            Map<String, Object> unsigned = mapper.convertValue(event, STRING_OBJECT_MAP);
            String declared = String.valueOf(unsigned.remove("entry_hash"));
            String calculated = sha256(mapper.writeValueAsBytes(unsigned));
            if (!declared.equals(calculated)) violations.add("LEDGER_EVENT_TAMPERED");
            previous = declared;
            last = event;
            expectedSequence++;
        }
        long sequence = expectedSequence - 1L;
        if (sequence != number(state.get("ledger_sequence"))) violations.add("STATE_LEDGER_SEQUENCE_MISMATCH");
        if (!previous.equals(string(state, "ledger_head"))) violations.add("STATE_LEDGER_HEAD_MISMATCH");
        if (last == null) {
            violations.add("LEDGER_EMPTY");
        } else {
            if (!statePayloadSha(state).equals(last.path("next_state_payload_sha256").asText())) {
                violations.add("STATE_EVENT_PAYLOAD_MISMATCH");
            }
            if (sequence == 1 && !GENESIS.equals(last.path("previous_state_sha256").asText())) {
                violations.add("GENESIS_STATE_BINDING_INVALID");
            }
        }
        return new Verification(violations.isEmpty(), violations, sequence, previous);
    }

    private Map<String, Object> readStateRaw() throws Exception {
        return new LinkedHashMap<>(mapper.readValue(stateFile.toFile(), STRING_OBJECT_MAP));
    }

    private List<String> readLedgerLines() throws Exception {
        return Files.exists(ledgerFile)
                ? new ArrayList<>(Files.readAllLines(ledgerFile, StandardCharsets.UTF_8))
                : new ArrayList<>();
    }

    private String statePayloadSha(Map<String, Object> state) throws Exception {
        Map<String, Object> copy = new TreeMap<>(state);
        copy.remove("state_sha256");
        copy.remove("ledger_head");
        copy.remove("ledger_sequence");
        return sha256(mapper.writeValueAsBytes(copy));
    }

    private String stateSha(Map<String, Object> state) throws Exception {
        Map<String, Object> copy = new TreeMap<>(state);
        copy.remove("state_sha256");
        return sha256(mapper.writeValueAsBytes(copy));
    }

    private void writeJsonAtomic(Path file, Object value) throws Exception {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        mapper.writeValue(temporary.toFile(), value);
        move(temporary, file);
    }

    private static void writeLinesAtomic(Path file, List<String> lines) throws Exception {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temporary, lines, StandardCharsets.UTF_8);
        move(temporary, file);
    }

    private static void move(Path source, Path destination) throws Exception {
        Files.createDirectories(destination.getParent());
        try {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Map<String, Object> snapshot(Map<String, Object> value) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }

    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(String.valueOf(value));
    }

    private static String string(Map<String, Object> value, String key) {
        Object item = value.get(key);
        return item instanceof String text ? text : "";
    }

    private static String requireText(String value, String error) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(error);
        return value;
    }

    private static String fileSha(Path file) throws Exception {
        return sha256(Files.readAllBytes(file));
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
