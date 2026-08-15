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
 * Durable, append-only tenant offboarding closure sequence (126_FINAL_FRESH_PRODUCT_DESIGN_
 * REVIEW_AND_SCOPE_CLOSURE.md FR-FRESH-003), one file per tenant_id: TERMINATION_REQUESTED ->
 * NEW_EFFECT_BLOCKED -> EXPORT_WINDOW -> CREDENTIAL_REVOCATION -> PENDING_JOB_SETTLEMENT ->
 * RETENTION_CLASSIFICATION -> CUSTOMER_EXPORT -> DELETION_OR_LEGAL_HOLD_PROCESSING ->
 * OFFBOARDING_RECEIPT_ISSUED. Legal hold is checked at every deletion attempt, not just recorded
 * as a flag -- "legal hold는 deletion보다 우선하지만 access authority를 자동 연장하지 않음."
 */
public final class OffboardingLedger {
    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_.:-]{1,160}$");

    private static final List<String> STAGE_ORDER = List.of(
            "TERMINATION_REQUESTED", "NEW_EFFECT_BLOCKED", "EXPORT_WINDOW", "CREDENTIAL_REVOCATION",
            "PENDING_JOB_SETTLEMENT", "RETENTION_CLASSIFICATION", "CUSTOMER_EXPORT",
            "DELETION_OR_LEGAL_HOLD_PROCESSING", "OFFBOARDING_RECEIPT_ISSUED");

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final Path root;

    public OffboardingLedger(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public record StageEvent(String stage, String actorId, String detail, String recordedAt) {}

    public StageEvent request(String tenantId, String actorId) throws Exception {
        if (!TENANT_ID_PATTERN.matcher(tenantId).matches()) throw new IllegalArgumentException("OFFBOARDING_TENANT_ID_INVALID");
        StageEvent event = new StageEvent("TERMINATION_REQUESTED", actorId, "termination requested", Instant.now().toString());
        Path file = root.resolve(tenantId + ".json");
        Path lockFile = root.resolve(".locks").resolve(tenantId + ".lock");
        ExclusiveFileLock.run(lockFile, () -> {
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("OFFBOARDING_ALREADY_IN_PROGRESS_OR_COMPLETE:" + tenantId);
            }
            if (Files.isSymbolicLink(file)) throw new IllegalArgumentException("OFFBOARDING_RECORD_SYMLINK_PROHIBITED");
            write(file, List.of(event));
        });
        return event;
    }

    /**
     * Advances to the next stage in strict sequence -- no stage may be skipped, matching the FR-
     * FRESH-003 minimum sequence exactly. Deletion is rejected outright when legalHold is true; the
     * caller must instead record the retained-under-legal-hold outcome via a non-DELETED detail.
     */
    public StageEvent advance(String tenantId, String toStage, String actorId, boolean legalHold, String detail) throws Exception {
        int targetIndex = STAGE_ORDER.indexOf(toStage);
        if (targetIndex < 0) throw new IllegalArgumentException("OFFBOARDING_STAGE_INVALID:" + toStage);
        Path file = root.resolve(tenantId + ".json");
        Path lockFile = root.resolve(".locks").resolve(tenantId + ".lock");
        return ExclusiveFileLock.call(lockFile, () -> {
            List<StageEvent> history = read(file);
            if (history.isEmpty()) throw new IllegalArgumentException("OFFBOARDING_NOT_FOUND:" + tenantId);
            String currentStage = history.get(history.size() - 1).stage();
            int currentIndex = STAGE_ORDER.indexOf(currentStage);
            if (targetIndex != currentIndex + 1) {
                throw new IllegalArgumentException("OFFBOARDING_STAGE_CANNOT_BE_SKIPPED:" + currentStage + "->" + toStage);
            }
            if ("DELETION_OR_LEGAL_HOLD_PROCESSING".equals(toStage) && legalHold && "DELETED".equals(detail)) {
                throw new SecurityException("LEGAL_HOLD_FORBIDS_DELETION");
            }
            StageEvent event = new StageEvent(toStage, actorId, detail, Instant.now().toString());
            List<StageEvent> updated = new ArrayList<>(history);
            updated.add(event);
            write(file, updated);
            return event;
        });
    }

    public List<StageEvent> history(String tenantId) throws Exception {
        return read(root.resolve(tenantId + ".json"));
    }

    public String currentStage(String tenantId) throws Exception {
        List<StageEvent> history = history(tenantId);
        if (history.isEmpty()) throw new IllegalArgumentException("OFFBOARDING_NOT_FOUND:" + tenantId);
        return history.get(history.size() - 1).stage();
    }

    /** "offboarding 완료 전 tenant identifier 재사용 금지": a new request for a still-in-progress or already-complete tenant is rejected by request()'s own file-exists check; this predicate is offered for callers that want to check first without attempting the mutation. */
    public boolean isTerminal(String tenantId) throws Exception {
        List<StageEvent> history = history(tenantId);
        return !history.isEmpty() && "OFFBOARDING_RECEIPT_ISSUED".equals(history.get(history.size() - 1).stage());
    }

    private List<StageEvent> read(Path file) throws Exception {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return List.of();
        if (Files.isSymbolicLink(file)) throw new IllegalArgumentException("OFFBOARDING_RECORD_SYMLINK_PROHIBITED");
        List<Map<String, String>> raw = mapper.readValue(
                file.toFile(), mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        List<StageEvent> events = new ArrayList<>();
        for (Map<String, String> row : raw) {
            events.add(new StageEvent(row.get("stage"), row.get("actor_id"), row.get("detail"), row.get("recorded_at")));
        }
        return events;
    }

    private void write(Path file, List<StageEvent> events) throws Exception {
        Files.createDirectories(file.getParent());
        List<Map<String, String>> rows = new ArrayList<>();
        for (StageEvent event : events) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("stage", event.stage());
            row.put("actor_id", event.actorId());
            row.put("detail", event.detail());
            row.put("recorded_at", event.recordedAt());
            rows.add(row);
        }
        mapper.writeValue(file.toFile(), rows);
    }
}
