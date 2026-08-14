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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Durable dual-approval ledger (policy-profile.v1.schema.json four_eyes_required): a subject is
 * only satisfied once at least {@code REQUIRED_DISTINCT_APPROVERS} genuinely distinct actors have
 * each recorded an approval for it. The same actor recording twice never counts as two of the
 * "four eyes" -- that would defeat the entire point -- so a repeat by the same actor is rejected
 * rather than silently double-counted or silently ignored.
 */
public final class FourEyesLedger {
    static final int REQUIRED_DISTINCT_APPROVERS = 2;
    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9_.:-]{1,160}$");

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final Path root;

    public FourEyesLedger(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public record ApprovalRecord(String actorId, String recordedAt) {}

    public record Result(List<ApprovalRecord> approvals, boolean satisfied) {}

    public Result recordApproval(String subjectId, String actorId) throws Exception {
        if (!ID_PATTERN.matcher(subjectId).matches()) throw new IllegalArgumentException("FOUR_EYES_SUBJECT_ID_INVALID");
        Path file = root.resolve(subjectId + ".json");
        Path lockFile = root.resolve(".locks").resolve(subjectId + ".lock");

        return ExclusiveFileLock.call(lockFile, () -> {
            List<ApprovalRecord> approvals = read(file);
            if (approvals.stream().anyMatch(record -> record.actorId().equals(actorId))) {
                throw new SecurityException("FOUR_EYES_SAME_ACTOR_CANNOT_COUNT_TWICE:" + actorId);
            }
            List<ApprovalRecord> updated = new ArrayList<>(approvals);
            updated.add(new ApprovalRecord(actorId, Instant.now().toString()));
            write(file, updated);
            return new Result(List.copyOf(updated), distinctActorCount(updated) >= REQUIRED_DISTINCT_APPROVERS);
        });
    }

    public Result approvalsFor(String subjectId) throws Exception {
        if (!ID_PATTERN.matcher(subjectId).matches()) throw new IllegalArgumentException("FOUR_EYES_SUBJECT_ID_INVALID");
        List<ApprovalRecord> approvals = read(root.resolve(subjectId + ".json"));
        return new Result(approvals, distinctActorCount(approvals) >= REQUIRED_DISTINCT_APPROVERS);
    }

    private static int distinctActorCount(List<ApprovalRecord> approvals) {
        return new LinkedHashSet<>(approvals.stream().map(ApprovalRecord::actorId).toList()).size();
    }

    private List<ApprovalRecord> read(Path file) throws Exception {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return List.of();
        if (Files.isSymbolicLink(file)) throw new IllegalArgumentException("FOUR_EYES_RECORD_SYMLINK_PROHIBITED");
        List<Map<String, String>> raw = mapper.readValue(
                file.toFile(), mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        List<ApprovalRecord> records = new ArrayList<>();
        for (Map<String, String> row : raw) records.add(new ApprovalRecord(row.get("actor_id"), row.get("recorded_at")));
        return records;
    }

    private void write(Path file, List<ApprovalRecord> approvals) throws Exception {
        Files.createDirectories(file.getParent());
        List<Map<String, String>> rows = new ArrayList<>();
        for (ApprovalRecord record : approvals) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("actor_id", record.actorId());
            row.put("recorded_at", record.recordedAt());
            rows.add(row);
        }
        mapper.writeValue(file.toFile(), rows);
    }
}
