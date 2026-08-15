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
 * Durable, append-only Hazard disposition history (127_SAFETY_HAZARD_AND_CONTESTABILITY_
 * GOVERNANCE.md SS1.3/1.6), one file per hazard_id holding every disposition transition ever
 * recorded -- escalation/closure is a real appended event, not an overwrite, so the full history
 * (including who moved it and when) always survives.
 */
public final class HazardLedger {
    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9_.:-]{1,160}$");

    /** 127 SS1.3: only these forward transitions are real -- no disposition may be skipped. */
    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "IDENTIFIED", Set.of("ANALYZED", "STALE"),
            "ANALYZED", Set.of("CONTROL_REQUIRED", "STALE"),
            "CONTROL_REQUIRED", Set.of("CONTROLLED_PENDING_VALIDATION", "STALE"),
            "CONTROLLED_PENDING_VALIDATION", Set.of(
                    "VALIDATED_CONTROLLED", "RESIDUAL_RISK_ACCEPTANCE_REQUIRED", "UNACCEPTABLE", "STALE"),
            "RESIDUAL_RISK_ACCEPTANCE_REQUIRED", Set.of("VALIDATED_CONTROLLED", "UNACCEPTABLE", "STALE"),
            "VALIDATED_CONTROLLED", Set.of("STALE"),
            "UNACCEPTABLE", Set.of("STALE"),
            "STALE", Set.of("ANALYZED"));

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final Path root;

    public HazardLedger(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public record DispositionEvent(String disposition, String actorId, String justification, String recordedAt) {}

    /** First event for a hazard: always IDENTIFIED, recorded by its creator. */
    public DispositionEvent create(String hazardId, String createdBy) throws Exception {
        if (!ID_PATTERN.matcher(hazardId).matches()) throw new IllegalArgumentException("HAZARD_ID_INVALID");
        DispositionEvent event = new DispositionEvent("IDENTIFIED", createdBy, "HAZARD_IDENTIFIED", Instant.now().toString());
        Path file = root.resolve(hazardId + ".json");
        Path lockFile = root.resolve(".locks").resolve(hazardId + ".lock");
        ExclusiveFileLock.run(lockFile, () -> {
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("HAZARD_ID_ALREADY_EXISTS:" + hazardId);
            if (Files.isSymbolicLink(file)) throw new IllegalArgumentException("HAZARD_RECORD_SYMLINK_PROHIBITED");
            write(file, List.of(event));
        });
        return event;
    }

    /**
     * Advances a hazard's disposition. Transitioning INTO VALIDATED_CONTROLLED FROM
     * RESIDUAL_RISK_ACCEPTANCE_REQUIRED (i.e. accepting residual risk) requires
     * {@code safetyAuthorityConfirmed} and an actor distinct from whoever created the hazard --
     * 127 SS1.6: "Residual Safety Risk acceptance는 일반 개발자/Reviewer가 수행하지 못한다."
     */
    private static final Set<String> KNOWN_DISPOSITIONS = Set.of(
            "IDENTIFIED", "ANALYZED", "CONTROL_REQUIRED", "CONTROLLED_PENDING_VALIDATION",
            "VALIDATED_CONTROLLED", "RESIDUAL_RISK_ACCEPTANCE_REQUIRED", "UNACCEPTABLE", "STALE");

    public DispositionEvent advance(
            String hazardId, String toDisposition, String actorId, String justification,
            boolean safetyAuthorityConfirmed) throws Exception {
        if (!KNOWN_DISPOSITIONS.contains(toDisposition)) {
            throw new IllegalArgumentException("HAZARD_DISPOSITION_INVALID");
        }
        Path file = root.resolve(hazardId + ".json");
        Path lockFile = root.resolve(".locks").resolve(hazardId + ".lock");
        return ExclusiveFileLock.call(lockFile, () -> {
            List<DispositionEvent> history = read(file);
            if (history.isEmpty()) throw new IllegalArgumentException("HAZARD_NOT_FOUND:" + hazardId);
            DispositionEvent current = history.get(history.size() - 1);
            Set<String> allowed = ALLOWED_TRANSITIONS.getOrDefault(current.disposition(), Set.of());
            if (!allowed.contains(toDisposition)) {
                throw new IllegalArgumentException(
                        "HAZARD_TRANSITION_NOT_ALLOWED:" + current.disposition() + "->" + toDisposition);
            }
            if ("RESIDUAL_RISK_ACCEPTANCE_REQUIRED".equals(current.disposition())
                    && "VALIDATED_CONTROLLED".equals(toDisposition)) {
                String creatorId = history.get(0).actorId();
                if (actorId.equals(creatorId)) {
                    throw new SecurityException("RESIDUAL_RISK_ACCEPTANCE_REQUIRES_ACTOR_DISTINCT_FROM_CREATOR");
                }
                if (!safetyAuthorityConfirmed) {
                    throw new SecurityException("RESIDUAL_RISK_ACCEPTANCE_REQUIRES_SAFETY_AUTHORITY");
                }
            }
            DispositionEvent event = new DispositionEvent(toDisposition, actorId, justification, Instant.now().toString());
            List<DispositionEvent> updated = new ArrayList<>(history);
            updated.add(event);
            write(file, updated);
            return event;
        });
    }

    public List<DispositionEvent> history(String hazardId) throws Exception {
        return read(root.resolve(hazardId + ".json"));
    }

    public String currentDisposition(String hazardId) throws Exception {
        List<DispositionEvent> history = history(hazardId);
        if (history.isEmpty()) throw new IllegalArgumentException("HAZARD_NOT_FOUND:" + hazardId);
        return history.get(history.size() - 1).disposition();
    }

    private List<DispositionEvent> read(Path file) throws Exception {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return List.of();
        if (Files.isSymbolicLink(file)) throw new IllegalArgumentException("HAZARD_RECORD_SYMLINK_PROHIBITED");
        List<Map<String, String>> raw = mapper.readValue(
                file.toFile(), mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        List<DispositionEvent> events = new ArrayList<>();
        for (Map<String, String> row : raw) {
            events.add(new DispositionEvent(row.get("disposition"), row.get("actor_id"), row.get("justification"), row.get("recorded_at")));
        }
        return events;
    }

    private void write(Path file, List<DispositionEvent> events) throws Exception {
        Files.createDirectories(file.getParent());
        List<Map<String, String>> rows = new ArrayList<>();
        for (DispositionEvent event : events) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("disposition", event.disposition());
            row.put("actor_id", event.actorId());
            row.put("justification", event.justification());
            row.put("recorded_at", event.recordedAt());
            rows.add(row);
        }
        mapper.writeValue(file.toFile(), rows);
    }
}
