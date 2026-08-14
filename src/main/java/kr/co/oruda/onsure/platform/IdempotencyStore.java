package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import kr.co.oruda.onsure.assurance.ExclusiveFileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Idempotency-Key store for Write operations (04_ARCHITECTURE_DATA_API_OLICENSE.md SS6:
 * "모든 Write API는 Idempotency-Key를 지원한다").
 *
 * <p>A key is scoped to (tenant_id, operation, key) so the same literal key string from two
 * different tenants or two different operations never collides. Replaying the same key with the
 * same request body returns the cached response and marks it a replay. Replaying the same key
 * with a DIFFERENT request body is a conflict, fail-closed -- it never silently returns a
 * response for the wrong request.
 */
public final class IdempotencyStore {
    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_.:-]{8,200}$");

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final Path storeRoot;

    public IdempotencyStore(Path storeRoot) {
        this.storeRoot = storeRoot.toAbsolutePath().normalize();
    }

    public enum Outcome { FIRST_SEEN, REPLAY, CONFLICT }

    public record Lookup(Outcome outcome, Map<String, Object> cachedResponse) {}

    /**
     * Records the first response for a key, or returns the cached response on replay. The
     * {@code responseSupplier} is only invoked when this is genuinely the first time this
     * (tenant, operation, key) triple has been seen.
     */
    public Lookup resolve(
            String tenantId, String operation, String idempotencyKey, JsonNode request,
            CheckedSupplier<Map<String, Object>> responseSupplier) throws Exception {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("IDEMPOTENCY_TENANT_ID_REQUIRED");
        if (operation == null || operation.isBlank()) throw new IllegalArgumentException("IDEMPOTENCY_OPERATION_REQUIRED");
        if (idempotencyKey == null || !KEY_PATTERN.matcher(idempotencyKey).matches()) {
            throw new IllegalArgumentException("IDEMPOTENCY_KEY_INVALID");
        }
        String requestDigest = digest(canonicalize(request));
        Path recordFile = recordPath(tenantId, operation, idempotencyKey);

        return ExclusiveFileLock.call(lockFile(tenantId, operation, idempotencyKey), () -> {
            if (Files.exists(recordFile, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(recordFile)) {
                    throw new IllegalArgumentException("IDEMPOTENCY_RECORD_SYMLINK_PROHIBITED");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> existing = mapper.readValue(recordFile.toFile(), Map.class);
                String existingRequestDigest = (String) existing.get("request_digest");
                if (!requestDigest.equals(existingRequestDigest)) {
                    return new Lookup(Outcome.CONFLICT, existing);
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> cachedResponse = (Map<String, Object>) existing.get("response");
                return new Lookup(Outcome.REPLAY, cachedResponse);
            }

            Map<String, Object> response = responseSupplier.get();
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("contract", "ONSURE_IDEMPOTENCY_RECORD_V1");
            record.put("tenant_id", tenantId);
            record.put("operation", operation);
            record.put("idempotency_key", idempotencyKey);
            record.put("request_digest", requestDigest);
            record.put("response", response);
            record.put("first_seen_at", Instant.now().toString());
            Files.createDirectories(recordFile.getParent());
            mapper.writeValue(recordFile.toFile(), record);
            return new Lookup(Outcome.FIRST_SEEN, response);
        });
    }

    private Path recordPath(String tenantId, String operation, String key) {
        return storeRoot.resolve(safeSegment(tenantId))
                .resolve(safeSegment(operation))
                .resolve(safeSegment(key) + ".json");
    }

    private Path lockFile(String tenantId, String operation, String key) {
        return storeRoot.resolve(".locks")
                .resolve(safeSegment(tenantId) + "__" + safeSegment(operation) + "__" + safeSegment(key) + ".lock");
    }

    private static String safeSegment(String value) {
        return value.replaceAll("[^A-Za-z0-9_.:-]", "_");
    }

    private String canonicalize(JsonNode request) throws Exception {
        Object tree = mapper.treeToValue(request, Object.class);
        return mapper.writeValueAsString(tree);
    }

    private static String digest(String value) throws Exception {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(sha256.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
