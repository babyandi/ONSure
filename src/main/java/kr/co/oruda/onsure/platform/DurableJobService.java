package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import kr.co.oruda.onsure.assurance.ConsumedApprovalReceiptVerifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Restart-safe long-running job queue with CAS transitions and approval-bound checkpoints. */
public final class DurableJobService {
    public static final String STATE_CONTRACT = "ONSURE_DURABLE_JOB_STATE_V1";
    public static final String EVENT_CONTRACT = "ONSURE_DURABLE_JOB_EVENT_V1";
    public static final String RESTORE_CONTRACT = "ONSURE_RESTART_RESTORE_RECEIPT_V1";

    public enum State { QUEUED, RUNNING, PAUSED, RECOVERY_HOLD, COMPLETED, CANCELLED }

    public record ApprovalBinding(Path receiptFile, String expectedContract, String expectedPurpose) {
        public ApprovalBinding {
            receiptFile = Objects.requireNonNull(receiptFile, "receiptFile").toAbsolutePath().normalize();
            requireText(expectedContract, "expectedContract");
            requireText(expectedPurpose, "expectedPurpose");
        }
    }

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final Path jobsRoot;

    public DurableJobService(Path jobsRoot) {
        this.jobsRoot = Objects.requireNonNull(jobsRoot, "jobsRoot").toAbsolutePath().normalize();
        requireNoSymlink(this.jobsRoot, "DURABLE_JOB_ROOT_SYMLINK_PROHIBITED");
    }

    public Map<String, Object> create(
            String jobId, String operation, String requestSha256, String actor) throws Exception {
        requireId(jobId, "JOB_ID_INVALID");
        requireId(operation, "JOB_OPERATION_INVALID");
        requireDigest(requestSha256, "JOB_REQUEST_DIGEST_INVALID");
        Map<String, Object> initial = new LinkedHashMap<>();
        initial.put("operation", operation);
        initial.put("request_sha256", requestSha256);
        initial.put("status", State.QUEUED.name());
        initial.put("checkpoint", Map.of());
        initial.put("approval_history", List.of());
        initial.put("approval_history_restored", false);
        initial.put("restart_count", 0);
        initial.put("created_at", Instant.now().toString());
        initial.put("completed_at", null);
        initial.put("cancellation_reason", null);
        initial.put("final_claim_allowed", false);
        return ledger(jobId).initialize(initial, "JOB_QUEUED", actor,
                Map.of("request_sha256", requestSha256));
    }

    public Map<String, Object> start(String jobId, long expectedRevision, String actor) throws Exception {
        return transition(jobId, expectedRevision, actor, "JOB_STARTED", State.QUEUED, State.RUNNING,
                state -> Map.of());
    }

    public Map<String, Object> checkpoint(
            String jobId,
            long expectedRevision,
            String stageId,
            String cursor,
            List<String> artifactRefs,
            List<ApprovalBinding> approvals,
            Path trustedKeyRegistry,
            Path replayLedger,
            String actor) throws Exception {
        requireText(stageId, "JOB_CHECKPOINT_STAGE_INVALID");
        requireText(cursor, "JOB_CHECKPOINT_CURSOR_INVALID");
        List<String> artifacts = artifactRefs == null ? List.of() : artifactRefs.stream()
                .map(value -> requireText(value, "JOB_CHECKPOINT_ARTIFACT_INVALID")).distinct().sorted().toList();
        List<Map<String, Object>> approvalHistory = verifiedApprovalHistory(
                approvals, trustedKeyRegistry, replayLedger);
        return mutate(jobId, expectedRevision, actor, "JOB_CHECKPOINTED", state -> {
            requireState(state, State.RUNNING);
            Map<String, Object> checkpoint = new LinkedHashMap<>();
            checkpoint.put("stage_id", stageId);
            checkpoint.put("cursor", cursor);
            checkpoint.put("artifact_refs", artifacts);
            checkpoint.put("approval_history_sha256", sha256(mapper.writeValueAsBytes(approvalHistory)));
            checkpoint.put("created_at", Instant.now().toString());
            checkpoint.put("checkpoint_sha256", canonicalCheckpointSha(checkpoint));
            state.put("checkpoint", Map.copyOf(checkpoint));
            state.put("approval_history", List.copyOf(approvalHistory));
            state.put("approval_history_restored", false);
            return Map.of("checkpoint_sha256", checkpoint.get("checkpoint_sha256"),
                    "approval_count", approvalHistory.size());
        });
    }

    public Map<String, Object> pause(String jobId, long expectedRevision, String actor) throws Exception {
        return transition(jobId, expectedRevision, actor, "JOB_PAUSED", State.RUNNING, State.PAUSED,
                state -> {
                    requireValidCheckpoint(state);
                    return Map.of("checkpoint_sha256", checkpoint(state).get("checkpoint_sha256"));
                });
    }

    public Map<String, Object> resume(String jobId, long expectedRevision, String actor) throws Exception {
        return transition(jobId, expectedRevision, actor, "JOB_RESUMED", State.PAUSED, State.RUNNING,
                state -> {
                    requireValidCheckpoint(state);
                    if (!Boolean.TRUE.equals(state.get("approval_history_restored"))
                            && number(state.get("restart_count")) > 0) {
                        throw new IllegalStateException("JOB_APPROVAL_HISTORY_NOT_RESTORED");
                    }
                    return Map.of("checkpoint_sha256", checkpoint(state).get("checkpoint_sha256"));
                });
    }

    public Map<String, Object> complete(String jobId, long expectedRevision, String actor) throws Exception {
        return transition(jobId, expectedRevision, actor, "JOB_COMPLETED", State.RUNNING, State.COMPLETED,
                state -> {
                    state.put("completed_at", Instant.now().toString());
                    return Map.of();
                });
    }

    public Map<String, Object> cancel(
            String jobId, long expectedRevision, String reason, String actor) throws Exception {
        requireText(reason, "JOB_CANCELLATION_REASON_INVALID");
        return mutate(jobId, expectedRevision, actor, "JOB_CANCELLED", state -> {
            State current = currentState(state);
            if (current == State.COMPLETED || current == State.CANCELLED) {
                throw new IllegalStateException("JOB_TERMINAL_TRANSITION_PROHIBITED");
            }
            state.put("status", State.CANCELLED.name());
            state.put("cancellation_reason", reason);
            state.put("completed_at", Instant.now().toString());
            return Map.of("reason", reason, "previous_status", current.name());
        });
    }

    public Map<String, Object> read(String jobId) throws Exception {
        requireId(jobId, "JOB_ID_INVALID");
        return ledger(jobId).read();
    }

    public record BacklogSummary(
            int queuedCount, int runningCount, long oldestQueuedAgeSeconds, List<String> staleJobIds) {
        public BacklogSummary { staleJobIds = List.copyOf(staleJobIds); }
    }

    /** Queue lag/backlog operational metric: how many jobs are waiting or running, and for how long. */
    public BacklogSummary backlogSummary(Instant now, java.time.Duration staleThreshold) throws Exception {
        Objects.requireNonNull(now, "now");
        if (staleThreshold == null || staleThreshold.isNegative()) {
            throw new IllegalArgumentException("JOB_STALE_THRESHOLD_INVALID");
        }
        int queued = 0;
        int running = 0;
        long oldestQueuedAgeSeconds = 0;
        List<String> stale = new ArrayList<>();
        if (Files.isDirectory(jobsRoot, LinkOption.NOFOLLOW_LINKS)) {
            try (var entries = Files.list(jobsRoot)) {
                for (Path jobRoot : entries.sorted().toList()) {
                    if (!Files.isDirectory(jobRoot, LinkOption.NOFOLLOW_LINKS)
                            || Files.isSymbolicLink(jobRoot)) continue;
                    String jobId = jobRoot.getFileName().toString();
                    Map<String, Object> state = ledger(jobId).read();
                    State current = currentState(state);
                    if (current != State.QUEUED && current != State.RUNNING) continue;
                    Instant createdAt = Instant.parse(String.valueOf(state.get("created_at")));
                    long ageSeconds = java.time.Duration.between(createdAt, now).getSeconds();
                    if (current == State.QUEUED) {
                        queued++;
                        oldestQueuedAgeSeconds = Math.max(oldestQueuedAgeSeconds, ageSeconds);
                    } else {
                        running++;
                    }
                    if (ageSeconds >= staleThreshold.getSeconds()) stale.add(jobId);
                }
            }
        }
        return new BacklogSummary(queued, running, oldestQueuedAgeSeconds, List.copyOf(stale));
    }

    /** Scans durable state after process restart and emits a source-local nonfinal restore receipt. */
    public Map<String, Object> recoverAllAfterRestart(String actor) throws Exception {
        requireText(actor, "JOB_RECOVERY_ACTOR_INVALID");
        List<Map<String, Object>> recovered = new ArrayList<>();
        if (Files.isDirectory(jobsRoot, LinkOption.NOFOLLOW_LINKS)) {
            try (var entries = Files.list(jobsRoot)) {
                for (Path jobRoot : entries.sorted().toList()) {
                    if (!Files.isDirectory(jobRoot, LinkOption.NOFOLLOW_LINKS)
                            || Files.isSymbolicLink(jobRoot)) continue;
                    String jobId = jobRoot.getFileName().toString();
                    Map<String, Object> state = ledger(jobId).read();
                    State current = currentState(state);
                    if (current != State.RUNNING) continue;
                    long revision = number(state.get("revision"));
                    Map<String, Object> result = mutate(jobId, revision, actor, "JOB_RESTART_RESTORED",
                            mutable -> {
                                mutable.put("restart_count", number(mutable.get("restart_count")) + 1);
                                try {
                                    requireValidCheckpoint(mutable);
                                    requireApprovalHistoryBinding(mutable);
                                    mutable.put("approval_history_restored", true);
                                    mutable.put("status", State.PAUSED.name());
                                    return Map.of("restored_status", State.PAUSED.name());
                                } catch (Exception invalid) {
                                    mutable.put("approval_history_restored", false);
                                    mutable.put("status", State.RECOVERY_HOLD.name());
                                    return Map.of("restored_status", State.RECOVERY_HOLD.name(),
                                            "reason", safeMessage(invalid));
                                }
                            });
                    Map<String, Object> restoredState = castMap(result.get("state"));
                    recovered.add(Map.of(
                            "job_id", jobId,
                            "status", restoredState.get("status"),
                            "revision", restoredState.get("revision"),
                            "ledger_head", restoredState.get("ledger_head")));
                }
            }
        }
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("contract", RESTORE_CONTRACT);
        receipt.put("recovered_jobs", List.copyOf(recovered));
        receipt.put("recovered_count", recovered.size());
        receipt.put("actor", actor);
        receipt.put("restored_at", Instant.now().toString());
        receipt.put("assurance_class", "SELF_VALIDATION_NONFINAL");
        receipt.put("final_claim_allowed", false);
        receipt.put("receipt_sha256", sha256(mapper.writeValueAsBytes(receipt)));
        writeAtomic(jobsRoot.resolve("restart-restore-receipt.json"), receipt);
        return Map.copyOf(receipt);
    }

    private Map<String, Object> transition(
            String jobId, long expectedRevision, String actor, String event,
            State expected, State next, JobMutation additional) throws Exception {
        return mutate(jobId, expectedRevision, actor, event, state -> {
            requireState(state, expected);
            Map<String, Object> details = additional.apply(state);
            state.put("status", next.name());
            return details;
        });
    }

    private Map<String, Object> mutate(
            String jobId, long expectedRevision, String actor,
            String event, JobMutation mutation) throws Exception {
        requireId(jobId, "JOB_ID_INVALID");
        if (expectedRevision < 1) throw new IllegalArgumentException("JOB_EXPECTED_REVISION_INVALID");
        return ledger(jobId).mutate(event, actor, state -> {
            if (number(state.get("revision")) != expectedRevision) {
                throw new IllegalStateException("JOB_REVISION_CONFLICT");
            }
            return mutation.apply(state);
        });
    }

    private List<Map<String, Object>> verifiedApprovalHistory(
            List<ApprovalBinding> approvals, Path keyRegistry, Path replayLedger) throws Exception {
        if (approvals == null || approvals.isEmpty()) return List.of();
        Objects.requireNonNull(keyRegistry, "keyRegistry");
        Objects.requireNonNull(replayLedger, "replayLedger");
        List<Map<String, Object>> result = new ArrayList<>();
        for (ApprovalBinding approval : approvals) {
            ConsumedApprovalReceiptVerifier.requireTrustedConsumed(
                    approval.receiptFile(), keyRegistry, replayLedger,
                    approval.expectedContract(), approval.expectedPurpose(), Instant.now(),
                    "JOB_CHECKPOINT_APPROVAL_INVALID");
            JsonNode value = mapper.readTree(approval.receiptFile().toFile());
            result.add(Map.of(
                    "approval_id", value.path("approval_id").asText(),
                    "approval_contract", approval.expectedContract(),
                    "approval_purpose", approval.expectedPurpose(),
                    "actor", value.path("actor").asText(),
                    "key_id", value.path("key_id").asText(),
                    "receipt_sha256", Hashing.file(approval.receiptFile())));
        }
        return List.copyOf(result);
    }

    private void requireApprovalHistoryBinding(Map<String, Object> state) throws Exception {
        List<?> history = state.get("approval_history") instanceof List<?> values ? values : List.of();
        String expected = String.valueOf(checkpoint(state).get("approval_history_sha256"));
        if (!expected.equals(sha256(mapper.writeValueAsBytes(history)))) {
            throw new IllegalStateException("JOB_APPROVAL_HISTORY_CHECKPOINT_MISMATCH");
        }
    }

    private void requireValidCheckpoint(Map<String, Object> state) throws Exception {
        Map<String, Object> checkpoint = checkpoint(state);
        if (checkpoint.isEmpty()) throw new IllegalStateException("JOB_CHECKPOINT_MISSING");
        String declared = String.valueOf(checkpoint.get("checkpoint_sha256"));
        if (!declared.matches("[0-9a-f]{64}") || !declared.equals(canonicalCheckpointSha(checkpoint))) {
            throw new IllegalStateException("JOB_CHECKPOINT_INVALID");
        }
        requireApprovalHistoryBinding(state);
    }

    private String canonicalCheckpointSha(Map<String, Object> checkpoint) throws Exception {
        Map<String, Object> canonical = new TreeMap<>(checkpoint);
        canonical.remove("checkpoint_sha256");
        return sha256(mapper.writeValueAsBytes(canonical));
    }

    private Map<String, Object> checkpoint(Map<String, Object> state) {
        return state.get("checkpoint") instanceof Map<?, ?> value ? castMap(value) : Map.of();
    }

    private DurableStateLedger ledger(String jobId) {
        requireId(jobId, "JOB_ID_INVALID");
        return new DurableStateLedger(jobsRoot.resolve(jobId),
                STATE_CONTRACT, EVENT_CONTRACT, "job_id", jobId);
    }

    private static State currentState(Map<String, Object> state) {
        try { return State.valueOf(String.valueOf(state.get("status"))); }
        catch (Exception invalid) { throw new IllegalStateException("JOB_STATE_INVALID"); }
    }

    private static void requireState(Map<String, Object> state, State expected) {
        State current = currentState(state);
        if (current != expected) {
            throw new IllegalStateException("JOB_TRANSITION_INVALID:" + current + ":" + expected);
        }
    }

    private static Map<String, Object> castMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("JOB_MAP_REQUIRED");
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private void writeAtomic(Path file, Object value) throws Exception {
        requireNoSymlink(file, "DURABLE_JOB_RECEIPT_SYMLINK_PROHIBITED");
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        mapper.writeValue(temporary.toFile(), value);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(String.valueOf(value));
    }

    private static String requireText(String value, String code) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(code);
        return value;
    }

    private static void requireId(String value, String code) {
        if (value == null || !value.matches("[A-Za-z0-9._:-]{1,160}")) {
            throw new IllegalArgumentException(code);
        }
    }

    private static void requireDigest(String value, String code) {
        if (value == null || !value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(code);
    }

    private static void requireNoSymlink(Path path, String code) {
        for (Path current = path; current != null; current = current.getParent()) {
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException(code);
            }
        }
    }

    private static String safeMessage(Exception failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception failure) {
            throw new IllegalStateException("JOB_HASH_FAILED", failure);
        }
    }

    @FunctionalInterface
    private interface JobMutation {
        Map<String, Object> apply(Map<String, Object> state) throws Exception;
    }
}
