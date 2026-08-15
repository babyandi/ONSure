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
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Durable, append-only AppealCase lifecycle history (127_SAFETY_HAZARD_AND_CONTESTABILITY_
 * GOVERNANCE.md SS2.3/2.4/2.6/2.7), one file per appeal_case_id. Every transition, evidence
 * submission, and decision is appended, never overwritten -- "원 Decision은 immutable historical
 * record로 유지한다": a reversal is a new event on top of the same history, the original FILED/
 * DECIDED entries always remain readable exactly as recorded. Every record is hash-chained via
 * {@link HashChainRecordStore} (Autonomous Development Mode 2026-08-15 tamper-evidence hardening).
 */
public final class AppealLedger {
    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9_.:-]{1,160}$");

    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "FILED", Set.of("ADMISSIBILITY_REVIEW", "WITHDRAWN"),
            "ADMISSIBILITY_REVIEW", Set.of("EVIDENCE_LOCKED", "REJECTED_OUT_OF_SCOPE", "HOLD_INPUT_REQUIRED", "WITHDRAWN"),
            "HOLD_INPUT_REQUIRED", Set.of("EVIDENCE_LOCKED", "WITHDRAWN"),
            "EVIDENCE_LOCKED", Set.of("INDEPENDENT_REVIEW"),
            "INDEPENDENT_REVIEW", Set.of("DECIDED", "INCONCLUSIVE"),
            "DECIDED", Set.of("IMPACT_APPLIED"),
            "IMPACT_APPLIED", Set.of("CLOSED"));

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final Path root;

    public AppealLedger(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public record Event(
            String eventType, String status, String actorId, String detail, String recordedAt) {}

    public record FileResult(String appealCaseId, List<Event> history) {}

    public FileResult file(
            String appealCaseId, String appellantPrincipalId, String challengedDecisionPrincipalId,
            String reasonCode) throws Exception {
        if (!ID_PATTERN.matcher(appealCaseId).matches()) throw new IllegalArgumentException("APPEAL_CASE_ID_INVALID");
        if (appellantPrincipalId.equals(challengedDecisionPrincipalId)) {
            throw new SecurityException("APPELLANT_CANNOT_BE_THE_ORIGINAL_DECISION_PRINCIPAL");
        }
        Event event = new Event(
                "FILED", "FILED", appellantPrincipalId,
                "challenged_decision_principal_id=" + challengedDecisionPrincipalId + ";reason_code=" + reasonCode,
                Instant.now().toString());
        Path file = root.resolve(appealCaseId + ".json");
        Path lockFile = root.resolve(".locks").resolve(appealCaseId + ".lock");
        ExclusiveFileLock.run(lockFile, () -> {
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("APPEAL_CASE_ID_ALREADY_EXISTS:" + appealCaseId);
            if (Files.isSymbolicLink(file)) throw new IllegalArgumentException("APPEAL_RECORD_SYMLINK_PROHIBITED");
            append(file, List.of(), event);
        });
        return new FileResult(appealCaseId, List.of(event));
    }

    /**
     * Assigns the independent reviewer for the appeal, transitioning EVIDENCE_LOCKED ->
     * INDEPENDENT_REVIEW. 127 SS2.6: the reviewer can never be the challenged decision's original
     * principal ("원 판정의 principal... appeal final reviewer가 될 수 없다") -- checked against
     * the challenged_decision_principal_id recorded at filing time, not a caller-declared claim.
     */
    public Event assignReviewer(String appealCaseId, String reviewerPrincipalId) throws Exception {
        Path file = root.resolve(appealCaseId + ".json");
        Path lockFile = root.resolve(".locks").resolve(appealCaseId + ".lock");
        return ExclusiveFileLock.call(lockFile, () -> {
            List<Map<String, Object>> raw = readRaw(file);
            List<Event> history = toEvents(raw);
            requireCurrentStatus(history, appealCaseId, "EVIDENCE_LOCKED");
            String challengedDecisionPrincipalId = challengedDecisionPrincipalId(history);
            if (reviewerPrincipalId.equals(challengedDecisionPrincipalId)) {
                throw new SecurityException("ORIGINAL_DECISION_PRINCIPAL_CANNOT_REVIEW_OWN_APPEAL");
            }
            Event event = new Event(
                    "REVIEWER_ASSIGNED", "INDEPENDENT_REVIEW", reviewerPrincipalId,
                    "reviewer_principal_id=" + reviewerPrincipalId, Instant.now().toString());
            append(file, raw, event);
            return event;
        });
    }

    /** Records new evidence. Never replaces prior evidence entries -- every submission is its own event. */
    public Event submitEvidence(String appealCaseId, String actorId, String evidenceRefSha256) throws Exception {
        Path file = root.resolve(appealCaseId + ".json");
        Path lockFile = root.resolve(".locks").resolve(appealCaseId + ".lock");
        return ExclusiveFileLock.call(lockFile, () -> {
            List<Map<String, Object>> raw = readRaw(file);
            List<Event> history = toEvents(raw);
            if (history.isEmpty()) throw new IllegalArgumentException("APPEAL_CASE_NOT_FOUND:" + appealCaseId);
            String currentStatus = history.get(history.size() - 1).status();
            if ("CLOSED".equals(currentStatus) || "DECIDED".equals(currentStatus) || "IMPACT_APPLIED".equals(currentStatus)) {
                throw new IllegalArgumentException("APPEAL_EVIDENCE_WINDOW_CLOSED:" + currentStatus);
            }
            Event event = new Event("EVIDENCE_SUBMITTED", currentStatus, actorId, evidenceRefSha256, Instant.now().toString());
            append(file, raw, event);
            return event;
        });
    }

    public Event transition(String appealCaseId, String toStatus, String actorId, String detail) throws Exception {
        Path file = root.resolve(appealCaseId + ".json");
        Path lockFile = root.resolve(".locks").resolve(appealCaseId + ".lock");
        return ExclusiveFileLock.call(lockFile, () -> {
            List<Map<String, Object>> raw = readRaw(file);
            List<Event> history = toEvents(raw);
            if (history.isEmpty()) throw new IllegalArgumentException("APPEAL_CASE_NOT_FOUND:" + appealCaseId);
            String currentStatus = history.get(history.size() - 1).status();
            Set<String> allowed = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of());
            if (!allowed.contains(toStatus)) {
                throw new IllegalArgumentException("APPEAL_TRANSITION_NOT_ALLOWED:" + currentStatus + "->" + toStatus);
            }
            Event event = new Event("TRANSITION", toStatus, actorId, detail, Instant.now().toString());
            append(file, raw, event);
            return event;
        });
    }

    /**
     * Records the appeal decision. The reviewer must be the one assigned via assignReviewer --
     * a decision from anyone else, including the challenged decision's original principal, is
     * rejected.
     */
    public Event decide(String appealCaseId, String decision, String reviewerPrincipalId, String rationale) throws Exception {
        Path file = root.resolve(appealCaseId + ".json");
        Path lockFile = root.resolve(".locks").resolve(appealCaseId + ".lock");
        return ExclusiveFileLock.call(lockFile, () -> {
            List<Map<String, Object>> raw = readRaw(file);
            List<Event> history = toEvents(raw);
            requireCurrentStatus(history, appealCaseId, "INDEPENDENT_REVIEW");
            String assignedReviewer = assignedReviewer(history);
            if (assignedReviewer == null || !assignedReviewer.equals(reviewerPrincipalId)) {
                throw new SecurityException("APPEAL_DECISION_MUST_COME_FROM_THE_ASSIGNED_REVIEWER");
            }
            Event event = new Event("DECISION", "DECIDED", reviewerPrincipalId, decision + ";" + rationale, Instant.now().toString());
            append(file, raw, event);
            return event;
        });
    }

    public List<Event> history(String appealCaseId) throws Exception {
        return read(root.resolve(appealCaseId + ".json"));
    }

    public String currentStatus(String appealCaseId) throws Exception {
        List<Event> history = history(appealCaseId);
        if (history.isEmpty()) throw new IllegalArgumentException("APPEAL_CASE_NOT_FOUND:" + appealCaseId);
        return history.get(history.size() - 1).status();
    }

    private static void requireCurrentStatus(List<Event> history, String appealCaseId, String expected) {
        if (history.isEmpty()) throw new IllegalArgumentException("APPEAL_CASE_NOT_FOUND:" + appealCaseId);
        String currentStatus = history.get(history.size() - 1).status();
        if (!expected.equals(currentStatus)) {
            throw new IllegalArgumentException("APPEAL_TRANSITION_NOT_ALLOWED:" + currentStatus + "->" + expected);
        }
    }

    private static String challengedDecisionPrincipalId(List<Event> history) {
        String detail = history.get(0).detail();
        for (String part : detail.split(";")) {
            if (part.startsWith("challenged_decision_principal_id=")) {
                return part.substring("challenged_decision_principal_id=".length());
            }
        }
        return null;
    }

    private static String assignedReviewer(List<Event> history) {
        for (int index = history.size() - 1; index >= 0; index--) {
            Event event = history.get(index);
            if ("REVIEWER_ASSIGNED".equals(event.eventType())) return event.actorId();
        }
        return null;
    }

    private void append(Path file, List<Map<String, Object>> currentRaw, Event event) throws Exception {
        Files.createDirectories(file.getParent());
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("event_type", event.eventType());
        fields.put("status", event.status());
        fields.put("actor_id", event.actorId());
        fields.put("detail", event.detail());
        fields.put("recorded_at", event.recordedAt());
        Map<String, Object> chained = HashChainRecordStore.nextRecord(mapper, currentRaw, fields);
        List<Map<String, Object>> updated = new ArrayList<>(currentRaw);
        updated.add(chained);
        mapper.writeValue(file.toFile(), updated);
    }

    private List<Event> read(Path file) throws Exception {
        return toEvents(readRaw(file));
    }

    private List<Map<String, Object>> readRaw(Path file) throws Exception {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return List.of();
        if (Files.isSymbolicLink(file)) throw new IllegalArgumentException("APPEAL_RECORD_SYMLINK_PROHIBITED");
        List<Map<String, Object>> raw = mapper.readValue(
                file.toFile(), mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        HashChainRecordStore.ChainVerification chain = HashChainRecordStore.verify(mapper, raw);
        if (!chain.valid()) {
            throw new IllegalStateException("APPEAL_LEDGER_CHAIN_INVALID:" + chain.violations());
        }
        return raw;
    }

    private static List<Event> toEvents(List<Map<String, Object>> raw) {
        List<Event> events = new ArrayList<>();
        for (Map<String, Object> row : raw) {
            events.add(new Event(
                    (String) row.get("event_type"), (String) row.get("status"),
                    (String) row.get("actor_id"), (String) row.get("detail"), (String) row.get("recorded_at")));
        }
        return events;
    }
}
