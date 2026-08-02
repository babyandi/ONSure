package io.onsure.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Package-private, digest-chained stage-boundary checkpoint for one validation run. */
final class ValidationStageCheckpointJournal {
    static final String CONTRACT = "ONSURE_VALIDATION_STAGE_CHECKPOINT_V1";
    static final String FILE_NAME = "stage-checkpoint.json";
    private static final String GENESIS = "0".repeat(64);
    private static final TypeReference<LinkedHashMap<String, Object>> MAP = new TypeReference<>() {};

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final Path runRoot;
    private final Path file;
    private final String jobId;
    private final String targetId;

    ValidationStageCheckpointJournal(
            Path runRoot, String jobId, String targetId, List<String> plannedStages) throws Exception {
        this.runRoot = runRoot.toAbsolutePath().normalize();
        this.file = this.runRoot.resolve(FILE_NAME);
        this.jobId = requireId(jobId, "job_id");
        this.targetId = requireId(targetId, "target_id");
        if (!Files.isDirectory(this.runRoot, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(this.runRoot)) {
            throw new IllegalArgumentException("VALIDATION_CHECKPOINT_RUN_ROOT_INVALID");
        }
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("VALIDATION_CHECKPOINT_ALREADY_EXISTS");
        }
        List<String> stages = new ArrayList<>();
        for (String stage : plannedStages) stages.add(requireId(stage, "stage_id"));
        if (stages.isEmpty() || stages.stream().distinct().count() != stages.size()) {
            throw new IllegalArgumentException("VALIDATION_CHECKPOINT_STAGE_PLAN_INVALID");
        }
        Instant now = Instant.now();
        Map<String, Object> initial = base(0L, GENESIS, now);
        initial.put("state", "INITIALIZED");
        initial.put("planned_stage_ids", List.copyOf(stages));
        initial.put("completed_stage_ids", List.of());
        initial.put("current_stage_id", null);
        initial.put("current_stage_index", null);
        initial.put("current_stage_decision", null);
        initial.put("failure", null);
        initial.put("started_at", now.toString());
        initial.put("history", List.of());
        sealAndWrite(initial);
    }

    void stageStarted(String stageId, int index) throws Exception {
        Map<String, Object> next = next();
        List<String> planned = strings(next.get("planned_stage_ids"));
        List<String> completed = strings(next.get("completed_stage_ids"));
        String stage = requireId(stageId, "stage_id");
        if (index < 0 || index >= planned.size() || !stage.equals(planned.get(index))
                || index != completed.size()) {
            throw new IllegalStateException("VALIDATION_CHECKPOINT_STAGE_ORDER_INVALID");
        }
        next.put("state", "STAGE_RUNNING");
        next.put("current_stage_id", stage);
        next.put("current_stage_index", index);
        next.put("current_stage_decision", null);
        next.put("failure", null);
        sealAndWrite(next);
    }

    void stageCompleted(String stageId, String decision) throws Exception {
        Map<String, Object> next = next();
        requireCurrent(next, stageId, "STAGE_RUNNING");
        List<String> completed = new ArrayList<>(strings(next.get("completed_stage_ids")));
        completed.add(stageId);
        next.put("state", "STAGE_COMPLETED");
        next.put("completed_stage_ids", List.copyOf(completed));
        next.put("current_stage_decision", requireId(decision, "stage_decision"));
        sealAndWrite(next);
    }

    void stageFailed(String stageId, Exception failure) throws Exception {
        Map<String, Object> next = next();
        requireCurrent(next, stageId, "STAGE_RUNNING");
        next.put("state", "STAGE_FAILED");
        next.put("current_stage_decision", "FAIL");
        next.put("failure", Map.of(
                "exception", failure.getClass().getName(),
                "message", safeMessage(failure)));
        sealAndWrite(next);
    }

    void stagesFinished(String decision) throws Exception {
        Map<String, Object> next = next();
        String state = String.valueOf(next.get("state"));
        if (!List.of("STAGE_COMPLETED", "STAGE_FAILED").contains(state)) {
            throw new IllegalStateException("VALIDATION_CHECKPOINT_NOT_AT_STAGE_BOUNDARY");
        }
        next.put("state", "STAGES_FINISHED");
        next.put("validation_decision", requireId(decision, "validation_decision"));
        next.put("finished_at", Instant.now().toString());
        sealAndWrite(next);
    }

    Map<String, Object> verifyAndRead() throws Exception {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
            throw new IllegalStateException("VALIDATION_CHECKPOINT_FILE_INVALID");
        }
        Map<String, Object> value = mapper.readValue(file.toFile(), MAP);
        if (!CONTRACT.equals(value.get("contract"))
                || !jobId.equals(value.get("job_id"))
                || !targetId.equals(value.get("target_id"))) {
            throw new IllegalStateException("VALIDATION_CHECKPOINT_BINDING_INVALID");
        }
        String declared = String.valueOf(value.get("checkpoint_sha256"));
        if (!declared.equals(digest(value))) {
            throw new IllegalStateException("VALIDATION_CHECKPOINT_DIGEST_INVALID");
        }
        verifyHistory(value);
        return Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }

    private Map<String, Object> next() throws Exception {
        Map<String, Object> current = new LinkedHashMap<>(verifyAndRead());
        String previous = String.valueOf(current.get("checkpoint_sha256"));
        long sequence = ((Number) current.get("sequence")).longValue() + 1L;
        current.put("sequence", sequence);
        current.put("previous_checkpoint_sha256", previous);
        current.put("updated_at", Instant.now().toString());
        current.remove("checkpoint_sha256");
        return current;
    }

    private Map<String, Object> base(long sequence, String previous, Instant now) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("contract", CONTRACT);
        value.put("version", 1);
        value.put("job_id", jobId);
        value.put("target_id", targetId);
        value.put("sequence", sequence);
        value.put("previous_checkpoint_sha256", previous);
        value.put("updated_at", now.toString());
        value.put("cooperative_stage_boundary", true);
        value.put("completed_stage_reexecution_allowed", false);
        value.put("context_replay_supported", true);
        value.put("context_snapshot_file", ValidationContextSnapshotStore.FILE_NAME);
        value.put("automatic_engine_resume_supported", false);
        value.put("restart_behavior", "RESTORE_VERIFIED_CONTEXT_THEN_EXPLICIT_ENGINE_RESUME_REQUIRED");
        value.put("final_claim_allowed", false);
        return value;
    }

    private void requireCurrent(Map<String, Object> value, String stageId, String expectedState) {
        if (!expectedState.equals(value.get("state"))
                || !stageId.equals(value.get("current_stage_id"))) {
            throw new IllegalStateException("VALIDATION_CHECKPOINT_CURRENT_STAGE_INVALID");
        }
    }

    private void sealAndWrite(Map<String, Object> value) throws Exception {
        value.remove("checkpoint_sha256");
        appendHistory(value);
        value.put("checkpoint_sha256", digest(value));
        Path temporary = file.resolveSibling(file.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            mapper.writeValue(temporary.toFile(), value);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private String digest(Map<String, Object> value) throws Exception {
        Map<String, Object> canonical = new TreeMap<>(value);
        canonical.remove("checkpoint_sha256");
        return Hashing.sha256(mapper.writeValueAsBytes(canonical));
    }

    private void appendHistory(Map<String, Object> value) throws Exception {
        List<Map<String, Object>> history = history(value.get("history"));
        long sequence = ((Number) value.get("sequence")).longValue();
        if (history.size() != sequence) {
            throw new IllegalStateException("VALIDATION_CHECKPOINT_HISTORY_SEQUENCE_INVALID");
        }
        String previous = history.isEmpty() ? GENESIS
                : String.valueOf(history.get(history.size() - 1).get("event_sha256"));
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("sequence", sequence);
        event.put("state", value.get("state"));
        event.put("stage_id", value.get("current_stage_id"));
        event.put("stage_decision", value.get("current_stage_decision"));
        event.put("updated_at", value.get("updated_at"));
        event.put("previous_event_sha256", previous);
        event.put("event_sha256", eventDigest(event));
        List<Map<String, Object>> next = new ArrayList<>(history);
        next.add(Collections.unmodifiableMap(event));
        value.put("history", List.copyOf(next));
    }

    private void verifyHistory(Map<String, Object> value) throws Exception {
        List<Map<String, Object>> history = history(value.get("history"));
        long sequence = ((Number) value.get("sequence")).longValue();
        if (history.size() != sequence + 1L) {
            throw new IllegalStateException("VALIDATION_CHECKPOINT_HISTORY_SIZE_INVALID");
        }
        String previous = GENESIS;
        for (int index = 0; index < history.size(); index++) {
            Map<String, Object> event = history.get(index);
            if (((Number) event.get("sequence")).longValue() != index
                    || !previous.equals(event.get("previous_event_sha256"))) {
                throw new IllegalStateException("VALIDATION_CHECKPOINT_HISTORY_CHAIN_INVALID");
            }
            String declared = String.valueOf(event.get("event_sha256"));
            if (!declared.equals(eventDigest(event))) {
                throw new IllegalStateException("VALIDATION_CHECKPOINT_HISTORY_DIGEST_INVALID");
            }
            previous = declared;
        }
    }

    private String eventDigest(Map<String, Object> event) throws Exception {
        Map<String, Object> canonical = new TreeMap<>(event);
        canonical.remove("event_sha256");
        return Hashing.sha256(mapper.writeValueAsBytes(canonical));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> history(Object value) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalStateException("VALIDATION_CHECKPOINT_HISTORY_INVALID");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                throw new IllegalStateException("VALIDATION_CHECKPOINT_HISTORY_EVENT_INVALID");
            }
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, entry) -> copy.put(String.valueOf(key), entry));
            result.add(copy);
        }
        return List.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalStateException("VALIDATION_CHECKPOINT_LIST_INVALID");
        }
        return list.stream().map(String::valueOf).toList();
    }

    private static String requireId(String value, String label) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,160}")) {
            throw new IllegalArgumentException("VALIDATION_CHECKPOINT_" + label.toUpperCase() + "_INVALID");
        }
        return value;
    }

    private static String safeMessage(Exception value) {
        String message = value.getMessage();
        if (message == null || message.isBlank()) return value.getClass().getSimpleName();
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
