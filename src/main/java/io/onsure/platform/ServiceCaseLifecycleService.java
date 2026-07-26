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
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;

/** File-backed one-time service case workflow. Payment is accepted only as an external receipt. */
public final class ServiceCaseLifecycleService {
    public static final String STATE_CONTRACT = "ONSURE_SERVICE_CASE_STATE_V1";
    public static final String EVENT_CONTRACT = "ONSURE_SERVICE_CASE_EVENT_V1";
    private static final String GENESIS = "0".repeat(64);
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final Path root;

    public ServiceCaseLifecycleService(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Map<String, Object> open(
            Map<String, Object> tenantContext,
            String caseId,
            String serviceType,
            String targetReference,
            String actor) throws Exception {
        requireTenant(tenantContext);
        requireId(caseId, "CASE_ID_INVALID");
        requireId(serviceType, "SERVICE_TYPE_INVALID");
        requireText(targetReference, "TARGET_REFERENCE_REQUIRED");
        requireActor(actor);
        return locked(caseId, () -> {
            if (Files.exists(stateFile(caseId), LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("CASE_ALREADY_EXISTS");
            }
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("contract", STATE_CONTRACT);
            state.put("case_id", caseId);
            state.put("organization_id", tenantContext.get("organization_id"));
            state.put("tenant_id", tenantContext.get("tenant_id"));
            state.put("workspace_id", tenantContext.get("workspace_id"));
            state.put("service_type", serviceType);
            state.put("target_reference", targetReference);
            state.put("status", "PREFLIGHT_REQUIRED");
            state.put("preflight", null);
            state.put("quote", null);
            state.put("order", null);
            state.put("payment", null);
            state.put("delivery", null);
            state.put("refund", null);
            state.put("deletion_receipt", null);
            state.put("revision", 1L);
            state.put("created_at", Instant.now().toString());
            state.put("updated_at", Instant.now().toString());
            state.put("final_claim_allowed", false);
            state.put("state_sha256", stateHash(state));
            writeAtomic(stateFile(caseId), state);
            appendEvent(caseId, "CASE_OPENED", actor, Map.of(
                    "service_type", serviceType, "target_reference", targetReference));
            return Map.copyOf(state);
        });
    }

    public Map<String, Object> recordPreflight(
            String caseId,
            String decision,
            String sourceDigest,
            List<String> findings,
            String actor) throws Exception {
        if (!List.of("PASS", "HOLD", "FAIL").contains(decision)) {
            throw new IllegalArgumentException("PREFLIGHT_DECISION_INVALID");
        }
        requireDigest(sourceDigest, "PREFLIGHT_SOURCE_DIGEST_INVALID");
        return mutate(caseId, actor, "PREFLIGHT_RECORDED", state -> {
            requireStatus(state, "PREFLIGHT_REQUIRED");
            Map<String, Object> preflight = Map.of(
                    "decision", decision,
                    "source_digest", sourceDigest,
                    "findings", List.copyOf(findings == null ? List.of() : findings),
                    "recorded_at", Instant.now().toString());
            state.put("preflight", preflight);
            state.put("status", "PASS".equals(decision) ? "QUOTE_READY" : "PREFLIGHT_BLOCKED");
            return preflight;
        });
    }

    public Map<String, Object> createQuote(
            String caseId,
            String quoteId,
            String currency,
            long amountMinor,
            Instant expiresAt,
            List<String> scope,
            String actor) throws Exception {
        requireId(quoteId, "QUOTE_ID_INVALID");
        if (!currency.matches("[A-Z]{3}") || amountMinor < 0 || expiresAt == null
                || !expiresAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException("QUOTE_VALUE_INVALID");
        }
        return mutate(caseId, actor, "QUOTE_CREATED", state -> {
            requireStatus(state, "QUOTE_READY", "QUOTED");
            Map<String, Object> quote = Map.of(
                    "quote_id", quoteId,
                    "currency", currency,
                    "amount_minor", amountMinor,
                    "scope", List.copyOf(scope == null ? List.of() : scope),
                    "expires_at", expiresAt.toString(),
                    "created_at", Instant.now().toString(),
                    "accepted", false);
            state.put("quote", quote);
            state.put("status", "QUOTED");
            return quote;
        });
    }

    public Map<String, Object> acceptOrder(String caseId, String quoteId, String actor) throws Exception {
        return mutate(caseId, actor, "ORDER_ACCEPTED", state -> {
            requireStatus(state, "QUOTED");
            Map<String, Object> quote = map(state.get("quote"), "QUOTE_MISSING");
            if (!quoteId.equals(quote.get("quote_id"))) throw new IllegalStateException("QUOTE_ID_MISMATCH");
            if (Instant.now().isAfter(Instant.parse(quote.get("expires_at").toString()))) {
                throw new IllegalStateException("QUOTE_EXPIRED");
            }
            Map<String, Object> acceptedQuote = new LinkedHashMap<>(quote);
            acceptedQuote.put("accepted", true);
            acceptedQuote.put("accepted_at", Instant.now().toString());
            state.put("quote", Map.copyOf(acceptedQuote));
            Map<String, Object> order = Map.of(
                    "order_id", "ORDER-" + caseId,
                    "quote_id", quoteId,
                    "state", "PAYMENT_PENDING",
                    "created_at", Instant.now().toString());
            state.put("order", order);
            state.put("status", "PAYMENT_PENDING");
            return order;
        });
    }

    public Map<String, Object> recordPayment(
            String caseId,
            String provider,
            String providerReceiptId,
            String currency,
            long amountMinor,
            String receiptDigest,
            String actor) throws Exception {
        requireId(provider, "PAYMENT_PROVIDER_INVALID");
        requireText(providerReceiptId, "PAYMENT_RECEIPT_ID_REQUIRED");
        requireDigest(receiptDigest, "PAYMENT_RECEIPT_DIGEST_INVALID");
        return mutate(caseId, actor, "PAYMENT_CONFIRMED", state -> {
            requireStatus(state, "PAYMENT_PENDING");
            Map<String, Object> quote = map(state.get("quote"), "QUOTE_MISSING");
            if (!currency.equals(quote.get("currency"))
                    || amountMinor != number(quote.get("amount_minor"))) {
                throw new IllegalStateException("PAYMENT_QUOTE_MISMATCH");
            }
            Map<String, Object> payment = Map.of(
                    "provider", provider,
                    "provider_receipt_id", providerReceiptId,
                    "currency", currency,
                    "amount_minor", amountMinor,
                    "receipt_digest", receiptDigest,
                    "provider_verification", "EXTERNAL_RECEIPT_BOUND_NOT_RECALCULATED",
                    "confirmed_at", Instant.now().toString());
            state.put("payment", payment);
            state.put("status", "PAYMENT_CONFIRMED");
            return payment;
        });
    }

    public Map<String, Object> startWork(String caseId, String actor) throws Exception {
        return mutate(caseId, actor, "WORK_STARTED", state -> {
            requireStatus(state, "PAYMENT_CONFIRMED");
            state.put("status", "IN_PROGRESS");
            return Map.of("state", "IN_PROGRESS", "started_at", Instant.now().toString());
        });
    }

    public Map<String, Object> deliver(
            String caseId,
            String evidenceBundleDigest,
            String deliveryPointer,
            String actor) throws Exception {
        requireDigest(evidenceBundleDigest, "DELIVERY_EVIDENCE_DIGEST_INVALID");
        requireText(deliveryPointer, "DELIVERY_POINTER_REQUIRED");
        return mutate(caseId, actor, "DELIVERED", state -> {
            requireStatus(state, "IN_PROGRESS");
            Map<String, Object> delivery = Map.of(
                    "evidence_bundle_digest", evidenceBundleDigest,
                    "delivery_pointer", deliveryPointer,
                    "customer_acceptance", "NOT_RUN",
                    "delivered_at", Instant.now().toString(),
                    "final_claim_allowed", false);
            state.put("delivery", delivery);
            state.put("status", "DELIVERED_AWAITING_ACCEPTANCE");
            return delivery;
        });
    }

    public Map<String, Object> acceptDelivery(String caseId, String acceptanceReceiptDigest, String actor)
            throws Exception {
        requireDigest(acceptanceReceiptDigest, "ACCEPTANCE_RECEIPT_DIGEST_INVALID");
        return mutate(caseId, actor, "DELIVERY_ACCEPTED", state -> {
            requireStatus(state, "DELIVERED_AWAITING_ACCEPTANCE");
            Map<String, Object> delivery = map(state.get("delivery"), "DELIVERY_MISSING");
            Map<String, Object> accepted = new LinkedHashMap<>(delivery);
            accepted.put("customer_acceptance", "PASS");
            accepted.put("acceptance_receipt_digest", acceptanceReceiptDigest);
            accepted.put("accepted_at", Instant.now().toString());
            state.put("delivery", Map.copyOf(accepted));
            state.put("status", "DELIVERY_ACCEPTED");
            return Map.copyOf(accepted);
        });
    }

    public Map<String, Object> requestRefund(
            String caseId, String reason, long amountMinor, String actor) throws Exception {
        requireText(reason, "REFUND_REASON_REQUIRED");
        return mutate(caseId, actor, "REFUND_REQUESTED", state -> {
            if (!List.of("PAYMENT_CONFIRMED", "IN_PROGRESS", "DELIVERED_AWAITING_ACCEPTANCE")
                    .contains(state.get("status"))) {
                throw new IllegalStateException("REFUND_STATE_INVALID:" + state.get("status"));
            }
            Map<String, Object> payment = map(state.get("payment"), "PAYMENT_MISSING");
            if (amountMinor < 0 || amountMinor > number(payment.get("amount_minor"))) {
                throw new IllegalArgumentException("REFUND_AMOUNT_INVALID");
            }
            Map<String, Object> refund = Map.of(
                    "reason", reason,
                    "currency", payment.get("currency"),
                    "amount_minor", amountMinor,
                    "state", "REQUESTED",
                    "requested_at", Instant.now().toString());
            state.put("refund", refund);
            state.put("status", "REFUND_PENDING");
            return refund;
        });
    }

    public Map<String, Object> completeRefund(
            String caseId, String providerReceiptId, String receiptDigest, String actor) throws Exception {
        requireText(providerReceiptId, "REFUND_RECEIPT_ID_REQUIRED");
        requireDigest(receiptDigest, "REFUND_RECEIPT_DIGEST_INVALID");
        return mutate(caseId, actor, "REFUND_COMPLETED", state -> {
            requireStatus(state, "REFUND_PENDING");
            Map<String, Object> refund = map(state.get("refund"), "REFUND_MISSING");
            Map<String, Object> complete = new LinkedHashMap<>(refund);
            complete.put("state", "COMPLETED");
            complete.put("provider_receipt_id", providerReceiptId);
            complete.put("receipt_digest", receiptDigest);
            complete.put("completed_at", Instant.now().toString());
            state.put("refund", Map.copyOf(complete));
            state.put("status", "REFUNDED");
            return Map.copyOf(complete);
        });
    }

    public Map<String, Object> recordDeletion(
            String caseId, String deletionReceiptDigest, String actor) throws Exception {
        requireDigest(deletionReceiptDigest, "DELETION_RECEIPT_DIGEST_INVALID");
        return mutate(caseId, actor, "DATA_DELETION_RECORDED", state -> {
            if (!List.of("DELIVERY_ACCEPTED", "REFUNDED").contains(state.get("status"))) {
                throw new IllegalStateException("DELETION_STATE_INVALID:" + state.get("status"));
            }
            Map<String, Object> deletion = Map.of(
                    "receipt_digest", deletionReceiptDigest,
                    "recorded_at", Instant.now().toString(),
                    "complete_deletion", "EXTERNAL_RECEIPT_BOUND");
            state.put("deletion_receipt", deletion);
            state.put("status", "CLOSED");
            return deletion;
        });
    }

    public Map<String, Object> read(String caseId) throws Exception {
        return locked(caseId, () -> Map.copyOf(readState(stateFile(caseId))));
    }

    private Map<String, Object> mutate(
            String caseId, String actor, String eventType, Mutation mutation) throws Exception {
        requireId(caseId, "CASE_ID_INVALID");
        requireActor(actor);
        return locked(caseId, () -> {
            Map<String, Object> state = readState(stateFile(caseId));
            Map<String, Object> details = mutation.apply(state);
            state.put("revision", number(state.get("revision")) + 1);
            state.put("updated_at", Instant.now().toString());
            state.put("state_sha256", stateHash(state));
            writeAtomic(stateFile(caseId), state);
            appendEvent(caseId, eventType, actor, details);
            return Map.of(
                    "state", Map.copyOf(state),
                    "event", eventType,
                    "details", details,
                    "assurance_class", "SELF_VALIDATION_NONFINAL",
                    "final_claim_allowed", false);
        });
    }

    private void appendEvent(String caseId, String type, String actor, Map<String, Object> details)
            throws Exception {
        Path ledger = ledgerFile(caseId);
        List<String> lines = Files.exists(ledger)
                ? new ArrayList<>(Files.readAllLines(ledger, StandardCharsets.UTF_8)) : new ArrayList<>();
        String previous = lines.isEmpty() ? GENESIS
                : mapper.readTree(lines.get(lines.size() - 1)).path("entry_hash").asText();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("contract", EVENT_CONTRACT);
        event.put("sequence", lines.size() + 1L);
        event.put("case_id", caseId);
        event.put("event_type", type);
        event.put("actor", actor);
        event.put("details", new TreeMap<>(details));
        event.put("recorded_at", Instant.now().toString());
        event.put("previous_hash", previous);
        event.put("entry_hash", sha256(mapper.writeValueAsBytes(event)));
        lines.add(mapper.writeValueAsString(event));
        writeLinesAtomic(ledger, lines);
    }

    private Map<String, Object> readState(Path file) throws Exception {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
            throw new IllegalStateException("CASE_STATE_MISSING");
        }
        Map<String, Object> state = mapper.readValue(file.toFile(), new TypeReference<>() {});
        if (!STATE_CONTRACT.equals(state.get("contract"))) throw new IllegalStateException("CASE_STATE_CONTRACT_INVALID");
        if (!string(state, "state_sha256").equals(stateHash(state))) {
            throw new IllegalStateException("CASE_STATE_TAMPERED");
        }
        return new LinkedHashMap<>(state);
    }

    private String stateHash(Map<String, Object> state) throws Exception {
        Map<String, Object> copy = new TreeMap<>(state);
        copy.remove("state_sha256");
        return sha256(mapper.writeValueAsBytes(copy));
    }

    private <T> T locked(String caseId, Callable<T> action) throws Exception {
        Path lock = caseRoot(caseId).resolve("case.lock");
        Files.createDirectories(lock.getParent());
        try (FileChannel channel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            return action.call();
        }
    }

    private static void requireStatus(Map<String, Object> state, String... allowed) {
        if (!List.of(allowed).contains(state.get("status"))) {
            throw new IllegalStateException("CASE_STATUS_INVALID:" + state.get("status"));
        }
    }
    private static Map<String, Object> map(Object value, String error) {
        if (!(value instanceof Map<?, ?> raw)) throw new IllegalStateException(error);
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }
    private static void requireTenant(Map<String, Object> tenant) {
        if (tenant == null || !"ONSURE_TENANT_CONTEXT_V1".equals(tenant.get("contract"))) {
            throw new IllegalArgumentException("TENANT_CONTEXT_INVALID");
        }
        for (String key : List.of("organization_id", "tenant_id", "workspace_id", "actor_id")) {
            requireId(String.valueOf(tenant.get(key)), "TENANT_CONTEXT_FIELD_INVALID:" + key);
        }
    }
    private static void requireActor(String actor) { requireText(actor, "ACTOR_REQUIRED"); }
    private static void requireId(String value, String error) {
        if (value == null || !value.matches("[A-Za-z0-9._:-]{1,160}")) throw new IllegalArgumentException(error);
    }
    private static void requireText(String value, String error) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(error);
    }
    private static void requireDigest(String value, String error) {
        if (value == null || !value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(error);
    }
    private Path caseRoot(String id) {
        requireId(id, "CASE_ID_INVALID");
        Path value = root.resolve("cases").resolve(id).normalize();
        if (!value.startsWith(root)) throw new IllegalArgumentException("CASE_PATH_ESCAPE");
        return value;
    }
    private Path stateFile(String id) { return caseRoot(id).resolve("state.json"); }
    private Path ledgerFile(String id) { return caseRoot(id).resolve("ledger.jsonl"); }
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
        Object value = state.get(key); return value instanceof String text ? text : "";
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
