package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import kr.co.oruda.onsure.assurance.ExclusiveFileLock;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Durable ledger backing FR-COM-013 (Separation of Duties): the same actor must not both develop
 * (DEVELOP) and verify/accept (VERIFY / ACCEPT) the same ImprovementRequest under a regulated
 * industry policy profile. One JSON file per improvement_request_id, appended to under an
 * ExclusiveFileLock so a concurrent record-stage call can't race past the conflict check.
 */
public final class SeparationOfDutiesLedger {
    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9_.:-]{1,160}$");
    static final List<String> STAGES = List.of("DEVELOP", "VERIFY", "ACCEPT");

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final Path root;

    public SeparationOfDutiesLedger(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public record StageRecord(String stage, String actorId, String recordedAt) {}

    public enum Outcome { RECORDED, ADVISORY_VIOLATION }

    public record Result(Outcome outcome, List<StageRecord> stages, String conflictingStage) {}

    /**
     * Records that {@code actorId} performed {@code stage} for {@code improvementRequestId}. Under
     * ENFORCED policy, an actor who already recorded a *different* stage for the same request is
     * rejected outright (SecurityException) before anything is written -- the request never reaches
     * a state where the conflicting record exists. Under ADVISORY, the record is still written, but
     * the conflict is reported back rather than silently accepted as a distinct, unrelated stage.
     */
    public Result recordStage(
            String improvementRequestId, String stage, String actorId, boolean enforced) throws Exception {
        if (!ID_PATTERN.matcher(improvementRequestId).matches()) {
            throw new IllegalArgumentException("SOD_IMPROVEMENT_REQUEST_ID_INVALID");
        }
        if (!STAGES.contains(stage)) throw new IllegalArgumentException("SOD_STAGE_INVALID");
        Path file = root.resolve(improvementRequestId + ".json");
        Path lockFile = root.resolve(".locks").resolve(improvementRequestId + ".lock");

        return ExclusiveFileLock.call(lockFile, () -> {
            List<StageRecord> stages = read(file);
            String conflictingStage = stages.stream()
                    .filter(record -> record.actorId().equals(actorId) && !record.stage().equals(stage))
                    .map(StageRecord::stage)
                    .findFirst().orElse(null);

            if (conflictingStage != null && enforced) {
                throw new SecurityException(
                        "SOD_VIOLATION:actor_already_performed:" + conflictingStage + ":cannot_also_perform:" + stage);
            }

            List<StageRecord> updated = new ArrayList<>(stages);
            updated.add(new StageRecord(stage, actorId, Instant.now().toString()));
            write(file, updated);
            return new Result(
                    conflictingStage != null ? Outcome.ADVISORY_VIOLATION : Outcome.RECORDED,
                    List.copyOf(updated), conflictingStage);
        });
    }

    public List<StageRecord> stagesFor(String improvementRequestId) throws Exception {
        if (!ID_PATTERN.matcher(improvementRequestId).matches()) {
            throw new IllegalArgumentException("SOD_IMPROVEMENT_REQUEST_ID_INVALID");
        }
        return read(root.resolve(improvementRequestId + ".json"));
    }

    private List<StageRecord> read(Path file) throws Exception {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return List.of();
        if (Files.isSymbolicLink(file)) throw new IllegalArgumentException("SOD_RECORD_SYMLINK_PROHIBITED");
        List<Map<String, String>> raw = mapper.readValue(
                file.toFile(), mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        List<StageRecord> records = new ArrayList<>();
        for (Map<String, String> row : raw) {
            records.add(new StageRecord(row.get("stage"), row.get("actor_id"), row.get("recorded_at")));
        }
        return records;
    }

    private void write(Path file, List<StageRecord> stages) throws Exception {
        Files.createDirectories(file.getParent());
        List<Map<String, String>> rows = new ArrayList<>();
        for (StageRecord record : stages) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("stage", record.stage());
            row.put("actor_id", record.actorId());
            row.put("recorded_at", record.recordedAt());
            rows.add(row);
        }
        mapper.writeValue(file.toFile(), rows);
    }
}
