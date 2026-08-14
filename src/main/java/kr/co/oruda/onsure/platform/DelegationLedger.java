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
 * Durable, bounded-time role delegation ledger, one file per delegation_id (like
 * RevocationLedger/BreakGlassLedger) so delegation_id uniqueness is a genuine global invariant
 * rather than scoped per-delegate. A grant is real expiry-checked (Instant comparison at read
 * time), not a caller-declared "still active" flag -- an expired grant is simply not returned by
 * {@link #activeGrantsFor}, so a caller can never rely on stale delegation state without
 * re-checking.
 */
public final class DelegationLedger {
    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9_.:-]{1,160}$");

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final Path root;

    public DelegationLedger(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public record Grant(
            String delegationId, String delegatorActorId, String delegateActorId,
            String role, String grantedAt, String expiresAt, String justification) {}

    public Grant grant(
            String delegationId, String delegatorActorId, String delegateActorId,
            String role, Instant expiresAt, String justification, Instant now) throws Exception {
        if (!ID_PATTERN.matcher(delegationId).matches()) throw new IllegalArgumentException("DELEGATION_ID_INVALID");
        if (!expiresAt.isAfter(now)) throw new IllegalArgumentException("DELEGATION_EXPIRY_MUST_BE_IN_THE_FUTURE");
        if (delegatorActorId.equals(delegateActorId)) throw new IllegalArgumentException("DELEGATION_CANNOT_SELF_DELEGATE");

        Grant record = new Grant(delegationId, delegatorActorId, delegateActorId, role, now.toString(), expiresAt.toString(), justification);
        Path file = root.resolve(delegationId + ".json");
        Path lockFile = root.resolve(".locks").resolve(delegationId + ".lock");
        ExclusiveFileLock.run(lockFile, () -> {
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("DELEGATION_ID_ALREADY_EXISTS:" + delegationId);
            }
            if (Files.isSymbolicLink(file)) throw new IllegalArgumentException("DELEGATION_RECORD_SYMLINK_PROHIBITED");
            write(file, record);
        });
        return record;
    }

    /** Grants for {@code delegateActorId} that are for {@code role} and not yet expired as of {@code now}. */
    public List<Grant> activeGrantsFor(String delegateActorId, String role, Instant now) throws Exception {
        List<Grant> active = new ArrayList<>();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return active;
        try (var stream = Files.list(root)) {
            for (Path file : stream.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                if (Files.isSymbolicLink(file)) continue;
                Grant grant = read(file);
                if (grant.delegateActorId().equals(delegateActorId) && grant.role().equals(role)
                        && Instant.parse(grant.expiresAt()).isAfter(now)) {
                    active.add(grant);
                }
            }
        }
        return List.copyOf(active);
    }

    private Grant read(Path file) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, String> row = mapper.readValue(file.toFile(), Map.class);
        return new Grant(
                row.get("delegation_id"), row.get("delegator_actor_id"), row.get("delegate_actor_id"),
                row.get("role"), row.get("granted_at"), row.get("expires_at"), row.get("justification"));
    }

    private void write(Path file, Grant grant) throws Exception {
        Files.createDirectories(file.getParent());
        Map<String, String> row = new LinkedHashMap<>();
        row.put("delegation_id", grant.delegationId());
        row.put("delegator_actor_id", grant.delegatorActorId());
        row.put("delegate_actor_id", grant.delegateActorId());
        row.put("role", grant.role());
        row.put("granted_at", grant.grantedAt());
        row.put("expires_at", grant.expiresAt());
        row.put("justification", grant.justification());
        mapper.writeValue(file.toFile(), row);
    }
}
