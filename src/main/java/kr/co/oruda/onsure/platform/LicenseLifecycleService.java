package kr.co.oruda.onsure.platform;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Durable OLicense lifecycle with credit reservations, offline grace and clock rollback detection. */
public final class LicenseLifecycleService {
    public static final String STATE_CONTRACT = "ONSURE_LICENSE_STATE_V1";
    public static final String EVENT_CONTRACT = "ONSURE_LICENSE_EVENT_V1";
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
        List<String> normalizedEntitlements = entitlements == null ? List.of() : entitlements.stream()
                .peek(value -> requireId(value, "ENTITLEMENT_INVALID"))
                .distinct().sorted().toList();
        Map<String, Object> state = new LinkedHashMap<>();
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
        state.put("entitlements", normalizedEntitlements);
        state.put("credits", credits(totalCredits, totalCredits, 0, 0));
        state.put("reservations", new TreeMap<String, Object>());
        state.put("created_at", Instant.now().toString());
        state.put("final_claim_allowed", false);
        Map<String, Object> envelope = store(licenseId).initialize(state, "ISSUED", actor, Map.of(
                "product_id", productId,
                "edition", edition,
                "total_credits", totalCredits,
                "entitlements", normalizedEntitlements));
        return state(envelope);
    }

    public Map<String, Object> activate(String licenseId, Instant observedAt, String actor) throws Exception {
        return mutateStrict(licenseId, observedAt, actor, "ACTIVATED", state -> {
            String status = string(state, "status");
            if (!List.of("ISSUED", "SUSPENDED").contains(status)) {
                throw new IllegalStateException("LICENSE_ACTIVATION_STATE_INVALID:" + status);
            }
            requireWithinValidity(state, observedAt);
            state.put("status", "ACTIVE");
            state.put("last_online_validation_at", observedAt.toString());
            return Map.of("previous_status", status, "next_status", "ACTIVE");
        });
    }

    public Map<String, Object> validate(
            String licenseId, Instant observedAt, boolean online, String actor) throws Exception {
        requireObservedAt(observedAt);
        return store(licenseId).mutate("VALIDATED", actor, state -> {
            Map<String, Object> rollback = suspendOnClockRollback(state, observedAt);
            if (rollback != null) return rollback;
            expireReservations(state, observedAt);
            String status = string(state, "status");
            Instant validFrom = Instant.parse(string(state, "valid_from"));
            Instant validUntil = Instant.parse(string(state, "valid_until"));
            String decision;
            List<String> reasons = new ArrayList<>();
            if (!"ACTIVE".equals(status)) {
                decision = "DENY";
                reasons.add("STATUS_" + status);
            } else if (observedAt.isBefore(validFrom)) {
                decision = "DENY";
                reasons.add("NOT_YET_VALID");
            } else if (!observedAt.isBefore(validUntil)) {
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
            state.put("last_observed_at", observedAt.toString());
            return Map.of(
                    "decision", decision,
                    "reasons", List.copyOf(reasons),
                    "online", online,
                    "available_credits", creditValue(state, "available"));
        });
    }

    public Map<String, Object> authorize(
            String licenseId,
            String entitlement,
            Instant observedAt,
            boolean online,
            String actor) throws Exception {
        requireId(entitlement, "ENTITLEMENT_INVALID");
        Map<String, Object> result = validate(licenseId, observedAt, online, actor);
        Map<String, Object> current = state(result);
        Map<String, Object> details = details(result);
        boolean validationAllowed = "ALLOW".equals(details.get("decision"));
        boolean entitled = entitlements(current).contains(entitlement);
        Map<String, Object> decision = new LinkedHashMap<>();
        decision.put("decision", validationAllowed && entitled ? "ALLOW" : "DENY");
        decision.put("entitlement", entitlement);
        decision.put("license_validation", details.get("decision"));
        decision.put("entitlement_present", entitled);
        decision.put("available_credits", creditValue(current, "available"));
        decision.put("assurance_class", "SELF_VALIDATION_NONFINAL");
        decision.put("final_claim_allowed", false);
        return Map.copyOf(decision);
    }

    public Map<String, Object> reserve(
            String licenseId,
            String reservationId,
            long amount,
            Instant expiresAt,
            Instant observedAt,
            String actor) throws Exception {
        requireId(reservationId, "RESERVATION_ID_INVALID");
        requireObservedAt(observedAt);
        if (amount <= 0 || expiresAt == null || !expiresAt.isAfter(observedAt)) {
            throw new IllegalArgumentException("RESERVATION_INVALID");
        }
        return mutateStrict(licenseId, observedAt, actor, "CREDITS_RESERVED", state -> {
            requireActive(state, observedAt);
            expireReservations(state, observedAt);
            Map<String, Object> reservations = reservations(state);
            if (reservations.containsKey(reservationId)) throw new IllegalStateException("RESERVATION_REPLAY");
            long available = creditValue(state, "available");
            if (available < amount) throw new IllegalStateException("INSUFFICIENT_CREDITS");
            reservations.put(reservationId, Map.of(
                    "reservation_id", reservationId,
                    "credits", amount,
                    "state", "RESERVED",
                    "created_at", observedAt.toString(),
                    "expires_at", expiresAt.toString()));
            updateCredits(state, available - amount,
                    creditValue(state, "reserved") + amount,
                    creditValue(state, "committed"));
            return Map.of("reservation_id", reservationId, "credits", amount, "state", "RESERVED");
        });
    }

    public Map<String, Object> commitReservation(
            String licenseId, String reservationId, Instant observedAt, String actor) throws Exception {
        return mutateStrict(licenseId, observedAt, actor, "CREDITS_COMMITTED", state -> {
            requireActive(state, observedAt);
            expireReservations(state, observedAt);
            Map<String, Object> reservations = reservations(state);
            Map<String, Object> reservation = reservation(reservations, reservationId);
            if (!"RESERVED".equals(reservation.get("state"))) {
                throw new IllegalStateException("RESERVATION_NOT_RESERVED");
            }
            long amount = number(reservation.get("credits"));
            Map<String, Object> changed = new LinkedHashMap<>(reservation);
            changed.put("state", "COMMITTED");
            changed.put("committed_at", observedAt.toString());
            reservations.put(reservationId, Map.copyOf(changed));
            updateCredits(state,
                    creditValue(state, "available"),
                    creditValue(state, "reserved") - amount,
                    creditValue(state, "committed") + amount);
            return Map.of("reservation_id", reservationId, "credits", amount, "state", "COMMITTED");
        });
    }

    public Map<String, Object> releaseReservation(
            String licenseId, String reservationId, Instant observedAt, String actor) throws Exception {
        return mutateStrict(licenseId, observedAt, actor, "CREDITS_RELEASED", state -> {
            expireReservations(state, observedAt);
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

    public Map<String, Object> suspend(
            String licenseId, String reason, Instant observedAt, String actor) throws Exception {
        requireText(reason, "SUSPENSION_REASON_REQUIRED");
        return mutateStrict(licenseId, observedAt, actor, "SUSPENDED", state -> {
            state.put("status", "SUSPENDED");
            state.put("suspension_reason", reason);
            state.put("suspended_at", observedAt.toString());
            return Map.of("status", "SUSPENDED", "reason", reason);
        });
    }

    public Map<String, Object> revoke(
            String licenseId, String reason, Instant observedAt, String actor) throws Exception {
        requireText(reason, "REVOCATION_REASON_REQUIRED");
        return mutateStrict(licenseId, observedAt, actor, "REVOKED", state -> {
            if ("REVOKED".equals(state.get("status"))) throw new IllegalStateException("LICENSE_ALREADY_REVOKED");
            state.put("status", "REVOKED");
            state.put("revocation_reason", reason);
            state.put("revoked_at", observedAt.toString());
            return Map.of("status", "REVOKED", "reason", reason);
        });
    }

    public Map<String, Object> read(String licenseId) throws Exception {
        requireId(licenseId, "LICENSE_ID_INVALID");
        return store(licenseId).read();
    }

    public DurableStateLedger.Verification verify(String licenseId) throws Exception {
        requireId(licenseId, "LICENSE_ID_INVALID");
        return store(licenseId).verify();
    }

    private Map<String, Object> mutateStrict(
            String licenseId,
            Instant observedAt,
            String actor,
            String eventType,
            DurableStateLedger.Mutation mutation) throws Exception {
        requireId(licenseId, "LICENSE_ID_INVALID");
        requireActor(actor);
        requireObservedAt(observedAt);
        Map<String, Object> result = store(licenseId).mutate(eventType, actor, state -> {
            Map<String, Object> rollback = suspendOnClockRollback(state, observedAt);
            if (rollback != null) return rollback;
            Map<String, Object> details = mutation.apply(state);
            state.put("last_observed_at", observedAt.toString());
            return details;
        });
        if (Boolean.TRUE.equals(details(result).get("clock_rollback"))) {
            throw new IllegalStateException("LICENSE_CLOCK_ROLLBACK_DETECTED");
        }
        return result;
    }

    private static Map<String, Object> suspendOnClockRollback(
            Map<String, Object> state, Instant observedAt) {
        Instant last = Instant.parse(string(state, "last_observed_at"));
        long tolerance = number(state.get("clock_tolerance_seconds"));
        if (observedAt.plusSeconds(tolerance).isBefore(last)) {
            state.put("status", "SUSPENDED");
            state.put("clock_rollback_detected_at", observedAt.toString());
            state.put("last_observed_at", last.toString());
            return Map.of(
                    "decision", "DENY",
                    "reason", "CLOCK_ROLLBACK_DETECTED",
                    "clock_rollback", true,
                    "previous_observed_at", last.toString(),
                    "rejected_observed_at", observedAt.toString());
        }
        return null;
    }

    private static void requireActive(Map<String, Object> state, Instant now) {
        if (!"ACTIVE".equals(state.get("status"))) throw new IllegalStateException("LICENSE_NOT_ACTIVE");
        requireWithinValidity(state, now);
    }

    private static void requireWithinValidity(Map<String, Object> state, Instant now) {
        if (now.isBefore(Instant.parse(state.get("valid_from").toString()))
                || !now.isBefore(Instant.parse(state.get("valid_until").toString()))) {
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
                    && !now.isBefore(Instant.parse(value.get("expires_at").toString()))) {
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

    private DurableStateLedger store(String licenseId) {
        return new DurableStateLedger(licenseRoot(licenseId), STATE_CONTRACT, EVENT_CONTRACT,
                "license_id", licenseId);
    }

    private Path licenseRoot(String id) {
        requireId(id, "LICENSE_ID_INVALID");
        Path value = root.resolve("licenses").resolve(id).normalize();
        if (!value.startsWith(root)) throw new IllegalArgumentException("LICENSE_PATH_ESCAPE");
        return value;
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

    private static List<String> entitlements(Map<String, Object> state) {
        Object value = state.get("entitlements");
        if (!(value instanceof List<?> values)) return List.of();
        return values.stream().map(String::valueOf).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> state(Map<String, Object> envelope) {
        Object value = envelope.get("state");
        if (!(value instanceof Map<?, ?> raw)) throw new IllegalStateException("LICENSE_STATE_RESULT_MISSING");
        return (Map<String, Object>) raw;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> details(Map<String, Object> envelope) {
        Object value = envelope.get("details");
        if (!(value instanceof Map<?, ?> raw)) throw new IllegalStateException("LICENSE_DETAILS_RESULT_MISSING");
        return (Map<String, Object>) raw;
    }

    private static void requireTenant(Map<String, Object> tenant) {
        if (tenant == null || !"ONSURE_TENANT_CONTEXT_V1".equals(tenant.get("contract"))) {
            throw new IllegalArgumentException("TENANT_CONTEXT_INVALID");
        }
        for (String key : List.of("organization_id", "tenant_id", "workspace_id", "actor_id")) {
            Object value = tenant.get(key);
            if (!(value instanceof String text)) {
                throw new IllegalArgumentException("TENANT_CONTEXT_FIELD_INVALID:" + key);
            }
            requireId(text, "TENANT_CONTEXT_FIELD_INVALID:" + key);
        }
    }

    private static void requireObservedAt(Instant observedAt) {
        if (observedAt == null) throw new IllegalArgumentException("OBSERVED_AT_REQUIRED");
    }

    private static void requireActor(String actor) { requireText(actor, "ACTOR_REQUIRED"); }

    private static void requireId(String value, String error) {
        if (value == null || !value.matches("[A-Za-z0-9._:-]{1,160}")) {
            throw new IllegalArgumentException(error);
        }
    }

    private static void requireText(String value, String error) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(error);
    }

    private static String string(Map<String, Object> state, String key) {
        Object value = state.get(key);
        return value instanceof String text ? text : "";
    }

    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(String.valueOf(value));
    }
}