package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import kr.co.oruda.onsure.assurance.ExclusiveFileLock;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Durable session lifecycle ledger (NFR-SESSION: "만료된 세션으로의 요청은 거부되어야 하고, 동일
 * 사용자의 동시 활성 세션 수가 상한을 넘으면 가장 오래된 세션이 무효화되어야 한다"), one file per
 * user_id holding every session ever issued for that user. Expiry is a real Instant comparison at
 * read time, never a caller-declared "still valid" flag. Creating a session that would push the
 * user's active (non-expired, non-evicted) session count over the ceiling evicts the single oldest
 * active session -- real, not a soft warning -- so the ceiling is a genuine invariant, not
 * advisory. Every record is hash-chained via {@link HashChainRecordStore} from the start (built
 * after, and applying the lesson of, the 2026-08-15 Autonomous Development Mode retrofit of the
 * other nine append-only ledgers in this package).
 */
public final class SessionLedger {
    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9_.:-]{1,160}$");

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final Path root;

    public SessionLedger(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public record Session(
            String sessionId, String userId, String issuedAt, String expiresAt, String status) {}

    public record CreateResult(Session session, String evictedSessionId) {}

    /** status is always ACTIVE at creation; EXPIRED/EVICTED are only ever derived, never chosen by a caller. */
    public CreateResult create(
            String sessionId, String userId, Instant expiresAt, int sessionCeiling, Instant now) throws Exception {
        if (!ID_PATTERN.matcher(sessionId).matches()) throw new IllegalArgumentException("SESSION_ID_INVALID");
        if (!ID_PATTERN.matcher(userId).matches()) throw new IllegalArgumentException("SESSION_USER_ID_INVALID");
        if (!expiresAt.isAfter(now)) throw new IllegalArgumentException("SESSION_EXPIRY_MUST_BE_IN_THE_FUTURE");
        if (sessionCeiling < 1) throw new IllegalArgumentException("SESSION_CEILING_MUST_BE_POSITIVE");

        Path file = root.resolve(userId + ".json");
        Path lockFile = root.resolve(".locks").resolve(userId + ".lock");
        return ExclusiveFileLock.call(lockFile, () -> {
            List<Map<String, Object>> raw = readRaw(file);
            if (raw.stream().anyMatch(row -> sessionId.equals(row.get("session_id")))) {
                throw new IllegalArgumentException("SESSION_ID_ALREADY_EXISTS:" + sessionId);
            }
            List<Map<String, Object>> active = raw.stream()
                    .filter(row -> isActive(row, now))
                    .sorted(Comparator.comparing(row -> (String) row.get("issued_at")))
                    .toList();

            String evictedSessionId = null;
            List<Map<String, Object>> updated = new ArrayList<>(raw);
            if (active.size() >= sessionCeiling) {
                Map<String, Object> oldest = active.get(0);
                evictedSessionId = (String) oldest.get("session_id");
                Map<String, Object> evictionFields = new LinkedHashMap<>();
                evictionFields.put("session_id", evictedSessionId);
                evictionFields.put("user_id", userId);
                evictionFields.put("issued_at", oldest.get("issued_at"));
                evictionFields.put("expires_at", oldest.get("expires_at"));
                evictionFields.put("status", "EVICTED");
                evictionFields.put("evicted_reason", "SESSION_CEILING_EXCEEDED");
                Map<String, Object> chainedEviction = HashChainRecordStore.nextRecord(mapper, updated, evictionFields);
                updated.add(chainedEviction);
            }

            Session session = new Session(sessionId, userId, now.toString(), expiresAt.toString(), "ACTIVE");
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("session_id", sessionId);
            fields.put("user_id", userId);
            fields.put("issued_at", session.issuedAt());
            fields.put("expires_at", session.expiresAt());
            fields.put("status", "ACTIVE");
            fields.put("evicted_reason", null);
            Map<String, Object> chained = HashChainRecordStore.nextRecord(mapper, updated, fields);
            updated.add(chained);

            Files.createDirectories(file.getParent());
            mapper.writeValue(file.toFile(), updated);
            return new CreateResult(session, evictedSessionId);
        });
    }

    /** Real expiry/eviction check at read time -- never trusts a caller's "still valid" claim. */
    public boolean isValid(String sessionId, String userId, Instant now) throws Exception {
        Path file = root.resolve(userId + ".json");
        List<Map<String, Object>> raw = readRaw(file);
        return raw.stream()
                .filter(row -> sessionId.equals(row.get("session_id")))
                .reduce((first, second) -> second) // latest record for this session_id wins (eviction is appended after creation)
                .map(row -> isActive(row, now))
                .orElse(false);
    }

    public List<Session> activeSessionsFor(String userId, Instant now) throws Exception {
        Path file = root.resolve(userId + ".json");
        List<Map<String, Object>> raw = readRaw(file);
        Map<String, Map<String, Object>> latestBySessionId = new LinkedHashMap<>();
        for (Map<String, Object> row : raw) {
            latestBySessionId.put((String) row.get("session_id"), row);
        }
        List<Session> active = new ArrayList<>();
        for (Map<String, Object> row : latestBySessionId.values()) {
            if (isActive(row, now)) {
                active.add(new Session(
                        (String) row.get("session_id"), (String) row.get("user_id"),
                        (String) row.get("issued_at"), (String) row.get("expires_at"), "ACTIVE"));
            }
        }
        return List.copyOf(active);
    }

    private static boolean isActive(Map<String, Object> row, Instant now) {
        if (!"ACTIVE".equals(row.get("status"))) return false;
        Instant expiresAt = Instant.parse((String) row.get("expires_at"));
        return expiresAt.isAfter(now);
    }

    private List<Map<String, Object>> readRaw(Path file) throws Exception {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return List.of();
        if (Files.isSymbolicLink(file)) throw new IllegalArgumentException("SESSION_RECORD_SYMLINK_PROHIBITED");
        List<Map<String, Object>> raw = mapper.readValue(
                file.toFile(), mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        HashChainRecordStore.ChainVerification chain = HashChainRecordStore.verify(mapper, raw);
        if (!chain.valid()) {
            throw new IllegalStateException("SESSION_LEDGER_CHAIN_INVALID:" + chain.violations());
        }
        return raw;
    }
}
