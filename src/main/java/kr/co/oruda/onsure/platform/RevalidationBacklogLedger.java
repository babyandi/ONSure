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
 * Durable revalidation backlog (doc 158 contradiction class 11 "Ground-truth Drift vs Historical
 * Immutability", LC-P0-011's RevalidationBacklog half), one file per reevaluation_ref. A decision
 * marked STALE/REVIEW_REQUIRED by decision-time-knowledge-snapshot.v1.schema.json's evaluation is
 * not just annotated and forgotten -- it is queued here as a real, trackable backlog item so
 * "every stale decision eventually gets a real reevaluation" is a checkable property (via
 * {@link #pending}), not an unverifiable claim. A reevaluation_ref can only be marked COMPLETED
 * once, by a real completion event distinct from creation -- it never silently starts COMPLETED.
 */
public final class RevalidationBacklogLedger {
    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9_.:-]{1,160}$");

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final Path root;

    public RevalidationBacklogLedger(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public record Entry(String reevaluationRef, String decisionRef, String status, String createdAt, String completedAt) {}

    public Entry enqueue(String reevaluationRef, String decisionRef) throws Exception {
        if (!ID_PATTERN.matcher(reevaluationRef).matches()) throw new IllegalArgumentException("REVALIDATION_REF_INVALID");
        Path file = root.resolve(reevaluationRef + ".json");
        Path lockFile = root.resolve(".locks").resolve(reevaluationRef + ".lock");
        return ExclusiveFileLock.call(lockFile, () -> {
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("REVALIDATION_REF_ALREADY_QUEUED:" + reevaluationRef);
            }
            Entry entry = new Entry(reevaluationRef, decisionRef, "PENDING", Instant.now().toString(), null);
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("reevaluation_ref", reevaluationRef);
            fields.put("decision_ref", decisionRef);
            fields.put("status", "PENDING");
            fields.put("created_at", entry.createdAt());
            fields.put("completed_at", null);
            Map<String, Object> chained = HashChainRecordStore.nextRecord(mapper, List.of(), fields);
            Files.createDirectories(file.getParent());
            mapper.writeValue(file.toFile(), List.of(chained));
            return entry;
        });
    }

    public Entry complete(String reevaluationRef) throws Exception {
        Path file = root.resolve(reevaluationRef + ".json");
        Path lockFile = root.resolve(".locks").resolve(reevaluationRef + ".lock");
        return ExclusiveFileLock.call(lockFile, () -> {
            List<Map<String, Object>> raw = readRaw(file);
            if (raw.isEmpty()) throw new IllegalArgumentException("REVALIDATION_REF_NOT_FOUND:" + reevaluationRef);
            Map<String, Object> current = raw.get(raw.size() - 1);
            if ("COMPLETED".equals(current.get("status"))) {
                throw new IllegalArgumentException("REVALIDATION_REF_ALREADY_COMPLETED:" + reevaluationRef);
            }
            String decisionRef = (String) current.get("decision_ref");
            String completedAt = Instant.now().toString();
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("reevaluation_ref", reevaluationRef);
            fields.put("decision_ref", decisionRef);
            fields.put("status", "COMPLETED");
            fields.put("created_at", current.get("created_at"));
            fields.put("completed_at", completedAt);
            Map<String, Object> chained = HashChainRecordStore.nextRecord(mapper, raw, fields);
            List<Map<String, Object>> updated = new ArrayList<>(raw);
            updated.add(chained);
            mapper.writeValue(file.toFile(), updated);
            return new Entry(reevaluationRef, decisionRef, "COMPLETED", (String) current.get("created_at"), completedAt);
        });
    }

    public Entry find(String reevaluationRef) throws Exception {
        List<Map<String, Object>> raw = readRaw(root.resolve(reevaluationRef + ".json"));
        if (raw.isEmpty()) return null;
        Map<String, Object> latest = raw.get(raw.size() - 1);
        return new Entry(
                (String) latest.get("reevaluation_ref"), (String) latest.get("decision_ref"),
                (String) latest.get("status"), (String) latest.get("created_at"), (String) latest.get("completed_at"));
    }

    /** Real enumeration of every still-open backlog item -- not a caller-supplied count. */
    public List<Entry> pending() throws Exception {
        List<Entry> pending = new ArrayList<>();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return pending;
        try (var stream = Files.list(root)) {
            for (Path file : stream.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                if (Files.isSymbolicLink(file)) continue;
                List<Map<String, Object>> raw = readRaw(file);
                if (raw.isEmpty()) continue;
                Map<String, Object> latest = raw.get(raw.size() - 1);
                if ("PENDING".equals(latest.get("status"))) {
                    pending.add(new Entry(
                            (String) latest.get("reevaluation_ref"), (String) latest.get("decision_ref"),
                            "PENDING", (String) latest.get("created_at"), null));
                }
            }
        }
        return List.copyOf(pending);
    }

    private List<Map<String, Object>> readRaw(Path file) throws Exception {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return List.of();
        if (Files.isSymbolicLink(file)) throw new IllegalArgumentException("REVALIDATION_RECORD_SYMLINK_PROHIBITED");
        List<Map<String, Object>> raw = mapper.readValue(
                file.toFile(), mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        HashChainRecordStore.ChainVerification chain = HashChainRecordStore.verify(mapper, raw);
        if (!chain.valid()) {
            throw new IllegalStateException("REVALIDATION_BACKLOG_CHAIN_INVALID:" + chain.violations());
        }
        return raw;
    }
}
