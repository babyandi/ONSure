package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import kr.co.oruda.onsure.assurance.ExclusiveFileLock;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Durable emergency-override audit ledger, one file per event_id. Every break-glass event is
 * created with review_required permanently true and review_completed false -- there is no
 * constructor path that creates an already-reviewed event -- and can only later be closed by
 * {@link #recordReview}, which requires a reviewer distinct from whoever invoked the override (the
 * same actor cannot grant themselves emergency access and then also sign off on it).
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
            write(file, event);
        });
        return event;
    }

    public Event recordReview(String eventId, String reviewerActorId, String reviewNotes, Instant now) throws Exception {
        Path file = root.resolve(eventId + ".json");
        Path lockFile = root.resolve(".locks").resolve(eventId + ".lock");
        return ExclusiveFileLock.call(lockFile, () -> {
            Event existing = read(file);
            if (existing == null) throw new IllegalArgumentException("BREAK_GLASS_EVENT_NOT_FOUND:" + eventId);
            if (existing.invokerActorId().equals(reviewerActorId)) {
                throw new SecurityException("BREAK_GLASS_REVIEWER_CANNOT_BE_THE_INVOKER");
            }
            if (existing.reviewCompleted()) throw new IllegalArgumentException("BREAK_GLASS_EVENT_ALREADY_REVIEWED:" + eventId);
            Event reviewed = new Event(
                    existing.eventId(), existing.invokerActorId(), existing.justification(), existing.invokedAt(),
                    true, true, reviewerActorId, now.toString());
            write(file, reviewed);
            return reviewed;
        });
    }

    public Event get(String eventId) throws Exception {
        return read(root.resolve(eventId + ".json"));
    }

    private Event read(Path file) throws Exception {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return null;
        if (Files.isSymbolicLink(file)) throw new IllegalArgumentException("BREAK_GLASS_RECORD_SYMLINK_PROHIBITED");
        @SuppressWarnings("unchecked")
        Map<String, Object> row = mapper.readValue(file.toFile(), Map.class);
        return new Event(
                (String) row.get("event_id"), (String) row.get("invoker_actor_id"), (String) row.get("justification"),
                (String) row.get("invoked_at"), (boolean) row.get("review_required"), (boolean) row.get("review_completed"),
                (String) row.get("reviewer_actor_id"), (String) row.get("reviewed_at"));
    }

    private void write(Path file, Event event) throws Exception {
        Files.createDirectories(file.getParent());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("event_id", event.eventId());
        row.put("invoker_actor_id", event.invokerActorId());
        row.put("justification", event.justification());
        row.put("invoked_at", event.invokedAt());
        row.put("review_required", event.reviewRequired());
        row.put("review_completed", event.reviewCompleted());
        row.put("reviewer_actor_id", event.reviewerActorId());
        row.put("reviewed_at", event.reviewedAt());
        mapper.writeValue(file.toFile(), row);
    }
}
