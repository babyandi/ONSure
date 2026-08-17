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
 * Durable emergency-override audit ledger, one file per event_id. Every break-glass event is
 * created with review_required permanently true and review_completed false -- there is no
 * constructor path that creates an already-reviewed event -- and can only later be closed by
 * {@link #recordReview}, which requires a reviewer distinct from whoever invoked the override (the
 * same actor cannot grant themselves emergency access and then also sign off on it). The invoke
 * and review are two separate, hash-chained records via {@link HashChainRecordStore} (Autonomous
 * Development Mode 2026-08-15 tamper-evidence hardening) -- the original unreviewed INVOKED record
 * is preserved verbatim, not overwritten in place, so review can never silently erase the original
 * emergency-access moment it is attesting to.
 */
public final class BreakGlassLedger {
    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9_.:-]{1,160}$");

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final Path root;

    public BreakGlassLedger(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public record Event(
            String eventId, String invokerActorId, String justification, String invokedAt,
            boolean reviewRequired, boolean reviewCompleted, String reviewerActorId, String reviewedAt) {}

    public Event invoke(String eventId, String invokerActorId, String justification, Instant now) throws Exception {
        if (!ID_PATTERN.matcher(eventId).matches()) throw new IllegalArgumentException("BREAK_GLASS_EVENT_ID_INVALID");
        if (justification == null || justification.isBlank()) {
            throw new IllegalArgumentException("BREAK_GLASS_JUSTIFICATION_REQUIRED");
        }
        Event event = new Event(eventId, invokerActorId, justification, now.toString(), true, false, null, null);
        Path file = root.resolve(eventId + ".json");
        Path lockFile = root.resolve(".locks").resolve(eventId + ".lock");
        ExclusiveFileLock.run(lockFile, () -> {
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("BREAK_GLASS_EVENT_ID_ALREADY_EXISTS:" + eventId);
            }
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("record_type", "INVOKED");
            fields.put("event_id", eventId);
            fields.put("invoker_actor_id", invokerActorId);
            fields.put("justification", justification);
            fields.put("invoked_at", event.invokedAt());
            append(file, List.of(), fields);
        });
        return event;
    }

    public Event recordReview(String eventId, String reviewerActorId, String reviewNotes, Instant now) throws Exception {
        Path file = root.resolve(eventId + ".json");
        Path lockFile = root.resolve(".locks").resolve(eventId + ".lock");
        return ExclusiveFileLock.call(lockFile, () -> {
            List<Map<String, Object>> raw = readRaw(file);
            Event existing = fold(eventId, raw);
            if (existing == null) throw new IllegalArgumentException("BREAK_GLASS_EVENT_NOT_FOUND:" + eventId);
            if (existing.invokerActorId().equals(reviewerActorId)) {
                throw new SecurityException("BREAK_GLASS_REVIEWER_CANNOT_BE_THE_INVOKER");
            }
            if (existing.reviewCompleted()) throw new IllegalArgumentException("BREAK_GLASS_EVENT_ALREADY_REVIEWED:" + eventId);
            String reviewedAt = now.toString();
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("record_type", "REVIEWED");
            fields.put("reviewer_actor_id", reviewerActorId);
            fields.put("review_notes", reviewNotes);
            fields.put("reviewed_at", reviewedAt);
            append(file, raw, fields);
            return new Event(
                    existing.eventId(), existing.invokerActorId(), existing.justification(), existing.invokedAt(),
                    true, true, reviewerActorId, reviewedAt);
        });
    }

    public Event get(String eventId) throws Exception {
        return fold(eventId, readRaw(root.resolve(eventId + ".json")));
    }

    private static Event fold(String eventId, List<Map<String, Object>> raw) {
        if (raw.isEmpty()) return null;
        Map<String, Object> invoked = raw.get(0);
        Map<String, Object> reviewed = raw.size() > 1 ? raw.get(1) : null;
        return new Event(
                (String) invoked.get("event_id"), (String) invoked.get("invoker_actor_id"),
                (String) invoked.get("justification"), (String) invoked.get("invoked_at"),
                true, reviewed != null,
                reviewed != null ? (String) reviewed.get("reviewer_actor_id") : null,
                reviewed != null ? (String) reviewed.get("reviewed_at") : null);
    }

    private List<Map<String, Object>> readRaw(Path file) throws Exception {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return List.of();
        if (Files.isSymbolicLink(file)) throw new IllegalArgumentException("BREAK_GLASS_RECORD_SYMLINK_PROHIBITED");
        List<Map<String, Object>> raw = mapper.readValue(
                file.toFile(), mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        HashChainRecordStore.ChainVerification chain = HashChainRecordStore.verify(mapper, raw);
        if (!chain.valid()) {
            throw new IllegalStateException("BREAK_GLASS_LEDGER_CHAIN_INVALID:" + chain.violations());
        }
        return raw;
    }

    private void append(Path file, List<Map<String, Object>> currentRaw, Map<String, Object> fields) throws Exception {
        Files.createDirectories(file.getParent());
        Map<String, Object> chained = HashChainRecordStore.nextRecord(mapper, currentRaw, fields);
        List<Map<String, Object>> updated = new ArrayList<>(currentRaw);
        updated.add(chained);
        mapper.writeValue(file.toFile(), updated);
    }
}
