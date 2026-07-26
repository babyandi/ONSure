package io.onsure.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;

/** File-backed OLicense lifecycle with credit reservations, offline grace and rollback detection. */
public final class LicenseLifecycleService {
    public static final String STATE_CONTRACT = "ONSURE_LICENSE_STATE_V1";
    public static final String EVENT_CONTRACT = "ONSURE_LICENSE_EVENT_V1";
    private static final String GENESIS = "0".repeat(64);
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final Path root;

    public LicenseLifecycleService(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Map<String, Object> issue(
            Map<String, Object> tenantContext,
            String licenseId,
            String productId,
            String edition,
            Instant validFrom,
            Instant validUntil,
            long totalCredits,
            long offlineGraceHours,
            long clockToleranceSeconds,
            List<String> entitlements,
            String actor) throws Exception {
        requireTenant(tenantContext);
        requireId(licenseId, "LICENSE_ID_INVALID");
        requireId(productId, "PRODUCT_ID_INVALID");
        requireId(edition, "EDITION_INVALID");
        requireActor(actor);
        if (validFrom == null || validUntil == null || !validUntil.isAfter(validFrom)) {
            throw new IllegalArgumentException("LICENSE_VALIDITY_INVALID");
        }
        if (totalCredits < 0 || offlineGraceHours < 0 || offlineGraceHours > 24 * 90L
                || clockToleranceSeconds < 0 || clockToleranceSeconds > 3600) {
            throw new IllegalArgumentException("LICENSE_LIMIT_INVALID");
        }
        return locked(licenseId, () -> {
            Path stateFile = stateFile(licenseId);
            if (Files.exists(stateFile, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("LICENSE_ALREADY_EXISTS");
            }
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("contract", STATE_CONTRACT);
            state.put("license_id", licenseId);
            state.put("organization_id", tenantContext.get("organization_id"));
            state.put("tenant_id", tenantContext.get("tenant_id"));
            state.put("workspace_id", tenantContext.get("workspace_id"));
            state.put("product_id", productId);
            state.put("edition", edition);
            state.put("status", "ISSUED");
            state.put("valid_from", validFrom.toString());
            state.put("valid_until", validUntil.toString());
            state.put("offline_grace_hours", offlineGraceHours);
            state.put("clock_tolerance_seconds", clockToleranceSeconds);
            state.put("last_observed_at", validFrom.toString());
            state.put("last_online_validation_at", null);
            state.put("entitlements", List.copyOf(entitlements == null ? List.of() : entitlements));
            state.put("credits", credits(totalCredits, totalCredits, 0, 0));
            state.put("reservations", new TreeMap<String, Object>());
            state.put("revision", 1L);
            state.put("created_at", Instant.now().toString());
            state.put("updated_at", Instant.now().toString());
            state.put("final_claim_allowed", false);
            state.put("state_sha256", stateHash(state));
            writeState(stateFile, state);
            appendEvent(licenseId, "ISSUED", actor, Map.of(
                    "product_id", productId, "edition", edition, "total_credits", totalCredits));
            return Map.copyOf(state);
        });
    }

    public Map<String, Object> activate(String licenseId, Instant observedAt, String actor) throws Exception {
        return mutate(licenseId, observedAt, actor, "ACTIVATED", state -> {
            String status = string(state, "status");
            if (!List.of("ISSUED", "SUSPENDED").contains(status)) {
                throw new IllegalStateException("LICENSE_ACTIVATION_STATE_INVALID:" + status);
            }
            state.put("status", "ACTIVE");
            state.put("last_online_validation_at", observedAt.toString());
            return Map.of("previous_status", status, "next_status", "ACTIVE");
        });
    }

    public Map<String, Object> validate(
            String licenseId, Instant observedAt, boolean online, String actor) throws Exception {
        return mutate(licenseId, observedAt, actor, "VALIDATED", state -> {
            expireReservations(state, observedAt);
            String status = string(state, "status");
            Instant validFrom = Instant.parse(string(state, "valid_from"));
            Instant validUntil = Instant.parse(string(state, "valid_until"));
            String decision;
            List<String> reasons = new ArrayList<>();
            if (!List.of("ACTIVE", "ISSUED").contains(status)) {
                decision = "DENY";
                reasons.add("STATUS_" + status);
            } else if (observedAt.isBefore(validFrom)) {
                decision = "DENY";
                reasons.add("NOT_YET_VALID");
            } else if (observedAt.isAfter(validUntil)) {
                decision = "DENY";
                reasons.add("LICENSE_EXPIRED");
            } else if (!online && !offlineAllowed(state, observedAt)) {
                decision = "DENY";
                reasons.add("OFFLINE_GRACE_EXPIRED");
            } else {
                decision = "ALLOW";
                reasons.add(online ? "ONLINE_VALIDATION" : "OFFLINE_GRACE_VALID");
            }
            if (online && "ALLOW".equals(decision)) {
                state.put("last_online_validation_at", observedAt.toString());
            }
            return Map.of(
                    "decision", decision,
                    "reasons", List.copyOf(reasons),
                    "online", online,
                    "available_credits", creditValue(state, "available"));
        });
    }

    public Map<String, Object> reserve(
            String licenseId,
            String reservationId,
            long credits,
            Instant expiresAt,
            Instant observedAt,
            String actor) throws Exception {
        requireId(reservationId, "RESERVATION_ID_INVALID");
        if (credits <= 0 || expiresAt == null || !expiresAt.isAfter(observedAt)) {
            throw new IllegalArgumentException("RESERVATION_INVALID");
        }
        return mutate(licenseId, observedAt, actor, "CREDITS_RESERVED", state -> {
            requireActive(state, observedAt);
            expireReservations(state, observedAt);
            Map<String, Object> reservations = reservations(state);
            if (reservations.containsKey(reservationId)) throw new IllegalStateException("RESERVATION_REPLAY");
            long available = creditValue(state, "available");
            if (available < credits) throw new IllegalStateException("INSUFFICIENT_CREDITS");
            reservations.put(reservationId, Map.of(
                    "reservation_id", reservationId,
                    "credits", credits,
                    "state", "RESERVED",
                    "created_at", observedAt.toString(),
                    "expires_at", expiresAt.toString()));
            updateCredits(state, available - credits,
                    creditValue(state, "reserved") + credits,
                    creditValue(state, "committed"));
            return Map.of("reservation_id", reservationId, "credits", credits, "state", "RESERVED");
        });
    }

    public Map<String, Object> commitReservation(
            String licenseId, String reservationId, Instant observedAt, String actor) throws Exception {
        return mutate(licenseId, observedAt, actor, "CREDITS_COMMITTED", state -> {
            Map<String, Object> reservations = reservations(state);
            Map<String, Object> reservation = reservation(reservations, reservationId);
            if (!"RESERVED".equals(reservation.get("state"))) {
                throw new IllegalStateException("RESERVATION_NOT_RESERVED");
            }
            if (observedAt.isAfter(Instant.parse(reservation.get("expires_at").toString()))) {
                throw new IllegalStateException("RESERVATION_EXPIRED");
            }
            long amount = number(reservation.get("credits"));
            Map<String, Object> changed = new LinkedHashMap<>(reservation);
            changed.put("state", "COMMITTED");
            changed.put("committed_at", observedAt.toString());
            reservations.put(reservationId, Map.copyOf(changed));
            updateCredits(state, creditValue(state, "available"),
                    creditValue(state, "reserved") - amount,
                    creditValue(state, "committed") + amount);
            return Map.of("reservation_id", reservationId, "credits", amount, "state", "COMMITTED");
        });
    }

    public Map<String, Object> releaseReservation(
            String licenseId, String reservationId, Instant observedAt, String actor) throws Exception {
        return mutate(licenseId, observedAt, actor, "CREDITS_RELEASED", state -> {
            Map<String, Object> reservations = reservations(state);
            Map<String, Object> reservation = reservation(reservations, reservationId);
            if (!"RESERVED".equals(reservation.get("state"))) {
                throw new IllegalStateException("RESERVATION_NOT_RESERVED");
            }
            long amount = number(reservation.get("credits"));
            Map<String, Object> changed = new LinkedHashMap<>(reservation);
            changed.put("state", "RELEASED");
            changed.put("released_at", observedAt.toString());
            reservations.put(reservationId, Map.copyOf(changed));
            updateCredits(state,
                    creditValue(state, "available") + amount,
                    creditValue(state, "reserved") - amount,
                    creditValue(state, "committed"));
            return Map.of("reservation_id", reservationId, "credits", amount, "state", "RELEASED");
        });
    }

    public Map<String, Object> revoke(
            String licenseId, String reason, Instant observedAt, String actor) throws Exception {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("REVOCATION_REASON_REQUIRED");
        return mutate(licenseId, observedAt, actor, "REVOKED", state -> {
            state.put("status", "REVOKED");
            state.put("revocation_reason", reason);
            state.put("revoked_at", observedAt.toString());
            return Map.of("status", "REVOKED", "reason", reason);
        });
    }

    public Map<String, Object> read(String licenseId) throws Exception {
        return locked(licenseId, () -> Map.copyOf(readState(stateFile(licenseId))));
    }

    private Map<String, Object> mutate(
            String licenseId,
            Instant observedAt,
            String actor,
            String eventType,
            Mutation mutation) throws Exception {
        requireId(licenseId, "LICENSE_ID_INVALID");
        requireActor(actor);
        if (observedAt == null) throw new IllegalArgumentException("OBSERVED_AT_REQUIRED");
        return locked(licenseId, () -> {
            Path stateFile = stateFile(licenseId);
            Map<String, Object> state = readState(stateFile);
            enforceClock(state, observedAt);
            Map<String, Object> details = mutation.apply(state);
            state.put("last_observed_at", observedAt.toString());
            state.put("updated_at", Instant.now().toString());
            state.put("revision", number(state.get("revision")) + 1);
            state.put("state_sha256", stateHash(state));
            writeState(stateFile, state);
            appendEvent(licenseId, eventType, actor, details);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("state", Map.copyOf(state));
            result.put("event", eventType);
            result.put("details", details);
            result.put("assurance_class", "SELF_VALIDATION_NONFINAL");
            result.put("final_claim_allowed", false);
            return Map.copyOf(result);
        });
    }

    private void enforceClock(Map<String, Object> state, Instant observedAt) {
        Instant last = Instant.parse(string(state, "last_observed_at"));
        long tolerance = number(state.get("clock_tolerance_seconds"));
        if (observedAt.plusSeconds(tolerance).isBefore(last)) {
            state.put("status", "SUSPENDED");
            throw new IllegalStateException("LICENSE_CLOCK_ROLLBACK_DETECTED");
        }
    }

    private static void requireActive(Map<String, Object> state, Instant now) {
        if (!"ACTIVE".equals(state.get("status"))) throw new IllegalStateException("LICENSE_NOT_ACTIVE");
        if (now.isBefore(Instant.parse(state.get("valid_from").toString()))
                || now.isAfter(Instant.parse(state.get("valid_until").toString()))) {
            throw new IllegalStateException("LICENSE_OUTSIDE_VALIDITY");
        }
    }

    private static boolean offlineAllowed(Map<String, Object> state, Instant observedAt) {
        Object lastOnline = state.get("last_online_validation_at");
        if (!(lastOnline instanceof String text) || text.isBlank()) return false;
        long hours = number(state.get("offline_grace_hours"));
        return !observedAt.isAfter(Instant.parse(text).plus(Duration.ofHours(hours)));
    }

    private static void expireReservations(Map<String, Object> state, Instant now) {
        Map<String, Object> reservations = reservations(state);
        long returned = 0;
        for (Map.Entry<String, Object> entry : new ArrayList<>(reservations.entrySet())) {
            if (!(entry.getValue() instanceof Map<?, ?> raw)) continue;
            Map<String, Object> value = cast(raw);
            if ("RESERVED".equals(value.get("state"))
                    && now.isAfter(Instant.parse(value.get("expires_at").toString()))) {
                long amount = number(value.get("credits"));
                returned += amount;
                Map<String, Object> changed = new LinkedHashMap<>(value);
                changed.put("state", "EXPIRED");
                changed.put("expired_at", now.toString());
                reservations.put(entry.getKey(), Map.copyOf(changed));
            }
        }
        if (returned > 0) {
            updateCredits(state,
                    creditValue(state, "available") + returned,
                    creditValue(state, "reserved") - returned,
                    creditValue(state, "committed"));
        }
    }

    private static Map<String, Object> credits(long total, long available, long reserved, long committed) {
        return Map.of("total", total, "available", available, "reserved", reserved, "committed", committed);
    }

    private static void updateCredits(Map<String, Object> state, long available, long reserved, long committed) {
        long total = creditValue(state, "total");
        if (available < 0 || reserved < 0 || committed < 0 || available + reserved + committed != total) {
            throw new IllegalStateException("LICENSE_CREDIT_INVARIANT_BROKEN");
        }
        state.put("credits", credits(total, available, reserved, committed));
    }

    private static long creditValue(Map<String, Object> state, String key) {
        Object value = state.get("credits");
        if (!(value instanceof Map<?, ?> credits)) throw new IllegalStateException("LICENSE_CREDITS_MISSING");
        return number(credits.get(key));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> reservations(Map<String, Object> state) {
        Object value = state.get("reservations");
        if (!(value instanceof Map<?, ?>)) throw new IllegalStateException("LICENSE_RESERVATIONS_MISSING");
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> reservation(Map<String, Object> reservations, String id) {
        requireId(id, "RESERVATION_ID_INVALID");
        Object value = reservations.get(id);
        if (!(value instanceof Map<?, ?> map)) throw new IllegalStateException("RESERVATION_NOT_FOUND");
        return cast(map);
    }

    private static Map<String, Object> cast(Map<?, ?> value) {
        Map<String, Object> result = new LinkedHashMap<>();
        value.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private void appendEvent(String licenseId, String type, String actor, Map<String, Object> details) throws Exception {
        Path ledger = ledgerFile(licenseId);
        List<String> lines = Files.exists(ledger)
                ? new ArrayList<>(Files.readAllLines(ledger, StandardCharsets.UTF_8)) : new ArrayList<>();
        String previous = lines.isEmpty() ? GENESIS
                : mapper.readTree(lines.get(lines.size() - 1)).path("entry_hash").asText();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("contract", EVENT_CONTRACT);
        event.put("sequence", lines.size() + 1L);
        event.put("license_id", licenseId);
        event.put("event_type", type);
        event.put("actor", actor);
        event.put("details", new TreeMap<>(details));
        event.put("recorded_at", Instant.now().toString());
        event.put("previous_hash", previous);
        event.put("entry_hash", sha256(mapper.writeValueAsBytes(event)));
        lines.add(mapper.writeValueAsString(event));
        writeLinesAtomic(ledger, lines);
    }

    private <T> T locked(String licenseId, Callable<T> action) throws Exception {
        Path lock = licenseRoot(licenseId).resolve("license.lock");
        Files.createDirectories(lock.getParent());
        try (FileChannel channel = FileChannel.open(lock,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            return action.call();
        }
    }

    private Map<String, Object> readState(Path file) throws Exception {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
            throw new IllegalStateException("LICENSE_STATE_MISSING");
        }
        Map<String, Object> state = mapper.readValue(file.toFile(), new TypeReference<>() {});
        if (!STATE_CONTRACT.equals(state.get("contract"))) throw new IllegalStateException("LICENSE_STATE_CONTRACT_INVALID");
        String expected = string(state, "state_sha256");
        if (!expected.equals(stateHash(state))) throw new IllegalStateException("LICENSE_STATE_TAMPERED");
        return new LinkedHashMap<>(state);
    }

    private String stateHash(Map<String, Object> state) throws Exception {
        Map<String, Object> copy = new TreeMap<>(state);
        copy.remove("state_sha256");
        return sha256(mapper.writeValueAsBytes(copy));
    }

    private void writeState(Path file, Map<String, Object> state) throws Exception {
        writeAtomic(file, state);
    }

    private static void requireTenant(Map<String, Object> tenant) {
        if (tenant == null || !"ONSURE_TENANT_CONTEXT_V1".equals(tenant.get("contract"))) {
            throw new IllegalArgumentException("TENANT_CONTEXT_INVALID");
        }
        for (String key : List.of("organization_id", "tenant_id", "workspace_id", "actor_id")) {
            requireId(String.valueOf(tenant.get(key)), "TENANT_CONTEXT_FIELD_INVALID:" + key);
        }
    }

    private static void requireActor(String actor) {
        if (actor == null || actor.isBlank()) throw new IllegalArgumentException("ACTOR_REQUIRED");
    }

    private static void requireId(String value, String error) {
        if (value == null || !value.matches("[A-Za-z0-9._:-]{1,160}")) throw new IllegalArgumentException(error);
    }

    private Path licenseRoot(String id) {
        requireId(id, "LICENSE_ID_INVALID");
        Path value = root.resolve("licenses").resolve(id).normalize();
        if (!value.startsWith(root)) throw new IllegalArgumentException("LICENSE_PATH_ESCAPE");
        return value;
    }
    private Path stateFile(String id) { return licenseRoot(id).resolve("state.json"); }
    private Path ledgerFile(String id) { return licenseRoot(id).resolve("ledger.jsonl"); }

    private void writeAtomic(Path file, Object value) throws Exception {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        mapper.writeValue(temporary.toFile(), value);
        move(temporary, file);
    }
    private static void writeLinesAtomic(Path file, List<String> lines) throws Exception {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temporary, lines, StandardCharsets.UTF_8);
        move(temporary, file);
    }
    private static void move(Path source, Path target) throws Exception {
        try { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException error) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
    private static String string(Map<String, Object> state, String key) {
        Object value = state.get(key);
        return value instanceof String text ? text : "";
    }
    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(String.valueOf(value));
    }
    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    @FunctionalInterface
    private interface Mutation { Map<String, Object> apply(Map<String, Object> state) throws Exception; }
}
