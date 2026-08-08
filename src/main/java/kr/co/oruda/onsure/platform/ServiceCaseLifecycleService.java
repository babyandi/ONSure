package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import kr.co.oruda.onsure.assurance.ApprovalReceiptVerifier;
import kr.co.oruda.onsure.assurance.Decision;
import kr.co.oruda.onsure.assurance.ExclusiveFileLock;
import kr.co.oruda.onsure.assurance.ValidationResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Durable one-time service case workflow with signed payment, refund and deletion verification. */
public final class ServiceCaseLifecycleService {
    public static final String STATE_CONTRACT = "ONSURE_SERVICE_CASE_STATE_V1";
    public static final String EVENT_CONTRACT = "ONSURE_SERVICE_CASE_EVENT_V1";
    public static final String RECEIPT_INDEX_CONTRACT = "ONSURE_EXTERNAL_RECEIPT_INDEX_V1";
    public static final String VERIFICATION_RECEIPT_CONTRACT = "ONSURE_SERVICE_VERIFICATION_RECEIPT_V1";

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final Path root;
    private final Path receiptIndex;
    private final Path receiptIndexLock;

    public ServiceCaseLifecycleService(Path root) {
        this.root = root.toAbsolutePath().normalize();
        this.receiptIndex = this.root.resolve("external-receipt-index.json");
        this.receiptIndexLock = this.root.resolve("external-receipt-index.lock");
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
        Map<String, Object> state = new LinkedHashMap<>();
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
        state.put("legal_hold", false);
        state.put("legal_hold_reason", null);
        state.put("retention_state", "ACTIVE");
        state.put("created_at", Instant.now().toString());
        state.put("final_claim_allowed", false);
        return state(store(caseId).initialize(state, "CASE_OPENED", actor, Map.of(
                "service_type", serviceType,
                "target_reference", targetReference)));
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
            requireStatus(state, "PREFLIGHT_REQUIRED", "PREFLIGHT_BLOCKED");
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
        if (currency == null || !currency.matches("[A-Z]{3}") || amountMinor <= 0
                || expiresAt == null || !expiresAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException("QUOTE_VALUE_INVALID");
        }
        List<String> normalizedScope = scope == null ? List.of()
                : scope.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
        if (normalizedScope.isEmpty()) throw new IllegalArgumentException("QUOTE_SCOPE_REQUIRED");
        return mutate(caseId, actor, "QUOTE_CREATED", state -> {
            requireStatus(state, "QUOTE_READY", "QUOTED");
            Map<String, Object> quote = Map.of(
                    "quote_id", quoteId,
                    "currency", currency,
                    "amount_minor", amountMinor,
                    "scope", normalizedScope,
                    "expires_at", expiresAt.toString(),
                    "created_at", Instant.now().toString(),
                    "accepted", false);
            state.put("quote", quote);
            state.put("status", "QUOTED");
            return quote;
        });
    }

    public Map<String, Object> acceptOrder(String caseId, String quoteId, String actor) throws Exception {
        requireId(quoteId, "QUOTE_ID_INVALID");
        Map<String, Object> result = mutate(caseId, actor, "ORDER_ACCEPTANCE_ATTEMPTED", state -> {
            requireStatus(state, "QUOTED");
            Map<String, Object> quote = map(state.get("quote"), "QUOTE_MISSING");
            if (!quoteId.equals(quote.get("quote_id"))) throw new IllegalStateException("QUOTE_ID_MISMATCH");
            if (!Instant.now().isBefore(Instant.parse(quote.get("expires_at").toString()))) {
                state.put("status", "QUOTE_EXPIRED");
                return Map.of("accepted", false, "expired", true, "quote_id", quoteId);
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
        if (Boolean.TRUE.equals(details(result).get("expired"))) {
            throw new IllegalStateException("QUOTE_EXPIRED");
        }
        return result;
    }

    /** Records a provider receipt but does not claim that payment was independently verified. */
    public Map<String, Object> recordPayment(
            String caseId,
            String provider,
            String providerReceiptId,
            String currency,
            long amountMinor,
            String receiptDigest,
            String actor) throws Exception {
        return recordPaymentReceipt(caseId, provider, providerReceiptId, currency,
                amountMinor, receiptDigest, actor);
    }

    public Map<String, Object> recordPaymentReceipt(
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
        registerExternalReceipt("PAYMENT", provider, providerReceiptId, caseId, receiptDigest);
        try {
            return mutate(caseId, actor, "PAYMENT_RECEIPT_RECORDED", state -> {
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
                        "provider_verification", "NOT_RUN",
                        "recorded_at", Instant.now().toString());
                state.put("payment", payment);
                state.put("status", "PAYMENT_RECEIPT_RECORDED");
                return payment;
            });
        } catch (Exception failure) {
            unregisterExternalReceipt("PAYMENT", provider, providerReceiptId, caseId, receiptDigest);
            throw failure;
        }
    }

    @Deprecated
    public Map<String, Object> verifyPayment(
            String caseId,
            String verifierIdentity,
            String verificationReceiptDigest,
            boolean valid,
            String actor) {
        throw new IllegalStateException("SIGNED_PAYMENT_VERIFICATION_RECEIPT_REQUIRED");
    }

    public Map<String, Object> verifyPayment(
            String caseId,
            Path signedVerificationReceipt,
            Path trustedKeyRegistry,
            Path verificationReplayLedger,
            String actor) throws Exception {
        Map<String, Object> current = read(caseId);
        Map<String, Object> payment = map(current.get("payment"), "PAYMENT_MISSING");
        Map<String, Object> verification = validateVerificationReceipt(
                signedVerificationReceipt,
                trustedKeyRegistry,
                verificationReplayLedger,
                caseId,
                number(current.get("revision")),
                "PAYMENT_VERIFICATION",
                "PAYMENT",
                String.valueOf(payment.get("provider")),
                String.valueOf(payment.get("provider_receipt_id")),
                String.valueOf(payment.get("receipt_digest")));
        return mutate(caseId, actor, "PASS".equals(verification.get("decision"))
                ? "PAYMENT_CONFIRMED" : "PAYMENT_REJECTED", state -> {
            requireRevision(state, number(verification.get("case_revision")));
            requireStatus(state, "PAYMENT_RECEIPT_RECORDED");
            Map<String, Object> existing = map(state.get("payment"), "PAYMENT_MISSING");
            Map<String, Object> verified = new LinkedHashMap<>(existing);
            verified.put("provider_verification", verification.get("decision"));
            verified.put("verifier_identity", verification.get("actor"));
            verified.put("verification_approval_id", verification.get("approval_id"));
            verified.put("verification_key_id", verification.get("key_id"));
            verified.put("verification_receipt_sha256", fileSha(signedVerificationReceipt));
            verified.put("verified_at", Instant.now().toString());
            state.put("payment", Map.copyOf(verified));
            state.put("status", "PASS".equals(verification.get("decision"))
                    ? "PAYMENT_CONFIRMED" : "PAYMENT_REJECTED");
            return Map.copyOf(verified);
        });
    }

    public Map<String, Object> startWork(String caseId, String actor) throws Exception {
        return mutate(caseId, actor, "WORK_STARTED", state -> {
            requireStatus(state, "PAYMENT_CONFIRMED");
            Map<String, Object> payment = map(state.get("payment"), "PAYMENT_MISSING");
            if (!"PASS".equals(payment.get("provider_verification"))) {
                throw new IllegalStateException("PAYMENT_NOT_EXTERNALLY_VERIFIED");
            }
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

    public Map<String, Object> acceptDelivery(
            String caseId,
            String acceptanceReceiptDigest,
            String actor) throws Exception {
        requireDigest(acceptanceReceiptDigest, "ACCEPTANCE_RECEIPT_DIGEST_INVALID");
        return mutate(caseId, actor, "DELIVERY_ACCEPTED", state -> {
            requireStatus(state, "DELIVERED_AWAITING_ACCEPTANCE");
            Map<String, Object> delivery = map(state.get("delivery"), "DELIVERY_MISSING");
            Map<String, Object> accepted = new LinkedHashMap<>(delivery);
            accepted.put("customer_acceptance", "PASS");
            accepted.put("acceptance_receipt_digest", acceptanceReceiptDigest);
            accepted.put("accepted_by", actor);
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
            if (!List.of("PAYMENT_CONFIRMED", "IN_PROGRESS", "DELIVERED_AWAITING_ACCEPTANCE",
                    "DELIVERY_ACCEPTED").contains(state.get("status"))) {
                throw new IllegalStateException("REFUND_STATE_INVALID:" + state.get("status"));
            }
            Map<String, Object> payment = map(state.get("payment"), "PAYMENT_MISSING");
            if (amountMinor <= 0 || amountMinor > number(payment.get("amount_minor"))) {
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

    /** Records the provider refund receipt. External verification remains NOT_RUN. */
    public Map<String, Object> completeRefund(
            String caseId, String providerReceiptId, String receiptDigest, String actor) throws Exception {
        requireText(providerReceiptId, "REFUND_RECEIPT_ID_REQUIRED");
        requireDigest(receiptDigest, "REFUND_RECEIPT_DIGEST_INVALID");
        Map<String, Object> current = read(caseId);
        Map<String, Object> payment = map(current.get("payment"), "PAYMENT_MISSING");
        String provider = String.valueOf(payment.get("provider"));
        registerExternalReceipt("REFUND", provider, providerReceiptId, caseId, receiptDigest);
        try {
            return mutate(caseId, actor, "REFUND_RECEIPT_RECORDED", state -> {
                requireStatus(state, "REFUND_PENDING");
                Map<String, Object> refund = map(state.get("refund"), "REFUND_MISSING");
                Map<String, Object> recorded = new LinkedHashMap<>(refund);
                recorded.put("state", "RECEIPT_RECORDED");
                recorded.put("provider", provider);
                recorded.put("provider_receipt_id", providerReceiptId);
                recorded.put("receipt_digest", receiptDigest);
                recorded.put("provider_verification", "NOT_RUN");
                recorded.put("recorded_at", Instant.now().toString());
                state.put("refund", Map.copyOf(recorded));
                state.put("status", "REFUND_RECEIPT_RECORDED");
                return Map.copyOf(recorded);
            });
        } catch (Exception failure) {
            unregisterExternalReceipt("REFUND", provider, providerReceiptId, caseId, receiptDigest);
            throw failure;
        }
    }

    @Deprecated
    public Map<String, Object> verifyRefund(
            String caseId,
            String verifierIdentity,
            String verificationReceiptDigest,
            boolean valid,
            String actor) {
        throw new IllegalStateException("SIGNED_REFUND_VERIFICATION_RECEIPT_REQUIRED");
    }

    public Map<String, Object> verifyRefund(
            String caseId,
            Path signedVerificationReceipt,
            Path trustedKeyRegistry,
            Path verificationReplayLedger,
            String actor) throws Exception {
        Map<String, Object> current = read(caseId);
        Map<String, Object> refund = map(current.get("refund"), "REFUND_MISSING");
        Map<String, Object> verification = validateVerificationReceipt(
                signedVerificationReceipt,
                trustedKeyRegistry,
                verificationReplayLedger,
                caseId,
                number(current.get("revision")),
                "REFUND_VERIFICATION",
                "REFUND",
                String.valueOf(refund.get("provider")),
                String.valueOf(refund.get("provider_receipt_id")),
                String.valueOf(refund.get("receipt_digest")));
        return mutate(caseId, actor, "PASS".equals(verification.get("decision"))
                ? "REFUND_COMPLETED" : "REFUND_REJECTED", state -> {
            requireRevision(state, number(verification.get("case_revision")));
            requireStatus(state, "REFUND_RECEIPT_RECORDED");
            Map<String, Object> existing = map(state.get("refund"), "REFUND_MISSING");
            Map<String, Object> verified = new LinkedHashMap<>(existing);
            verified.put("provider_verification", verification.get("decision"));
            verified.put("verifier_identity", verification.get("actor"));
            verified.put("verification_approval_id", verification.get("approval_id"));
            verified.put("verification_key_id", verification.get("key_id"));
            verified.put("verification_receipt_sha256", fileSha(signedVerificationReceipt));
            verified.put("verified_at", Instant.now().toString());
            verified.put("state", "PASS".equals(verification.get("decision"))
                    ? "COMPLETED" : "REJECTED");
            state.put("refund", Map.copyOf(verified));
            state.put("status", "PASS".equals(verification.get("decision"))
                    ? "REFUNDED" : "REFUND_REJECTED");
            return Map.copyOf(verified);
        });
    }

    public Map<String, Object> setLegalHold(
            String caseId, boolean active, String reason, String actor) throws Exception {
        if (active) requireText(reason, "LEGAL_HOLD_REASON_REQUIRED");
        return mutate(caseId, actor, active ? "LEGAL_HOLD_APPLIED" : "LEGAL_HOLD_RELEASED", state -> {
            state.put("legal_hold", active);
            state.put("legal_hold_reason", active ? reason : null);
            state.put("legal_hold_updated_at", Instant.now().toString());
            return Map.of("legal_hold", active, "reason", active ? reason : "RELEASED");
        });
    }

    @Deprecated
    public Map<String, Object> recordDeletion(
            String caseId, String deletionReceiptDigest, String actor) {
        throw new IllegalStateException("SIGNED_DELETION_VERIFICATION_RECEIPT_REQUIRED");
    }

    public Map<String, Object> recordDeletion(
            String caseId,
            Path signedVerificationReceipt,
            Path trustedKeyRegistry,
            Path verificationReplayLedger,
            String actor) throws Exception {
        Map<String, Object> current = read(caseId);
        Map<String, Object> verification = readVerificationReceipt(signedVerificationReceipt);
        requireVerificationEnvelope(verification, caseId, number(current.get("revision")),
                "DELETION_VERIFICATION", "DELETION");
        String provider = string(verification, "provider");
        String receiptId = string(verification, "provider_receipt_id");
        String receiptDigest = string(verification, "provider_receipt_digest");
        registerExternalReceipt("DELETION", provider, receiptId, caseId, receiptDigest);
        try {
            verifyAndConsume(signedVerificationReceipt, trustedKeyRegistry,
                    verificationReplayLedger, "DELETION_VERIFICATION");
            if (!"PASS".equals(verification.get("decision"))) {
                throw new IllegalStateException("DELETION_VERIFICATION_NON_PASS");
            }
            return mutate(caseId, actor, "DATA_DELETION_RECORDED", state -> {
                requireRevision(state, number(verification.get("case_revision")));
                if (!List.of("DELIVERY_ACCEPTED", "REFUNDED").contains(state.get("status"))) {
                    throw new IllegalStateException("DELETION_STATE_INVALID:" + state.get("status"));
                }
                if (Boolean.TRUE.equals(state.get("legal_hold"))) {
                    throw new IllegalStateException("DELETION_BLOCKED_BY_LEGAL_HOLD");
                }
                Map<String, Object> deletion = Map.of(
                        "provider", provider,
                        "provider_receipt_id", receiptId,
                        "receipt_digest", receiptDigest,
                        "verification_approval_id", verification.get("approval_id"),
                        "verification_key_id", verification.get("key_id"),
                        "verification_receipt_sha256", fileSha(signedVerificationReceipt),
                        "recorded_at", Instant.now().toString(),
                        "complete_deletion", "SIGNED_EXTERNAL_VERIFICATION_PASS");
                state.put("deletion_receipt", deletion);
                state.put("retention_state", "DELETED_SIGNED_EXTERNAL_VERIFICATION");
                state.put("status", "CLOSED");
                return deletion;
            });
        } catch (Exception failure) {
            unregisterExternalReceipt("DELETION", provider, receiptId, caseId, receiptDigest);
            throw failure;
        }
    }

    public Map<String, Object> cancel(String caseId, String reason, String actor) throws Exception {
        requireText(reason, "CANCELLATION_REASON_REQUIRED");
        return mutate(caseId, actor, "CASE_CANCELLED", state -> {
            if (!List.of("PREFLIGHT_REQUIRED", "PREFLIGHT_BLOCKED", "QUOTE_READY", "QUOTED",
                    "QUOTE_EXPIRED", "PAYMENT_PENDING", "PAYMENT_REJECTED").contains(state.get("status"))) {
                throw new IllegalStateException("CASE_CANCELLATION_STATE_INVALID:" + state.get("status"));
            }
            state.put("status", "CANCELLED");
            state.put("cancellation_reason", reason);
            state.put("cancelled_at", Instant.now().toString());
            return Map.of("status", "CANCELLED", "reason", reason);
        });
    }

    public Map<String, Object> read(String caseId) throws Exception {
        requireId(caseId, "CASE_ID_INVALID");
        return store(caseId).read();
    }

    public DurableStateLedger.Verification verify(String caseId) throws Exception {
        requireId(caseId, "CASE_ID_INVALID");
        return store(caseId).verify();
    }

    private Map<String, Object> validateVerificationReceipt(
            Path receiptFile,
            Path keyRegistry,
            Path replayLedger,
            String caseId,
            long revision,
            String purpose,
            String type,
            String provider,
            String providerReceiptId,
            String providerReceiptDigest) throws Exception {
        Map<String, Object> verification = readVerificationReceipt(receiptFile);
        requireVerificationEnvelope(verification, caseId, revision, purpose, type);
        if (!provider.equals(verification.get("provider"))
                || !providerReceiptId.equals(verification.get("provider_receipt_id"))
                || !providerReceiptDigest.equals(verification.get("provider_receipt_digest"))) {
            throw new IllegalStateException("SERVICE_VERIFICATION_PROVIDER_RECEIPT_MISMATCH");
        }
        verifyAndConsume(receiptFile, keyRegistry, replayLedger, purpose);
        return verification;
    }

    private Map<String, Object> readVerificationReceipt(Path receiptFile) throws Exception {
        if (!Files.isRegularFile(receiptFile, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(receiptFile)) {
            throw new IllegalArgumentException("SERVICE_VERIFICATION_RECEIPT_FILE_INVALID");
        }
        Map<String, Object> value = mapper.readValue(receiptFile.toFile(), new TypeReference<>() {});
        if (!VERIFICATION_RECEIPT_CONTRACT.equals(value.get("contract"))) {
            throw new IllegalArgumentException("SERVICE_VERIFICATION_RECEIPT_CONTRACT_MISMATCH");
        }
        return value;
    }

    private static void requireVerificationEnvelope(
            Map<String, Object> verification,
            String caseId,
            long revision,
            String purpose,
            String type) {
        if (!caseId.equals(verification.get("case_id"))) {
            throw new IllegalStateException("SERVICE_VERIFICATION_CASE_MISMATCH");
        }
        if (revision != number(verification.get("case_revision"))) {
            throw new IllegalStateException("SERVICE_VERIFICATION_CASE_REVISION_MISMATCH");
        }
        if (!purpose.equals(verification.get("approval_purpose"))) {
            throw new IllegalStateException("SERVICE_VERIFICATION_PURPOSE_MISMATCH");
        }
        if (!type.equals(verification.get("verification_type"))) {
            throw new IllegalStateException("SERVICE_VERIFICATION_TYPE_MISMATCH");
        }
    }

    private static void verifyAndConsume(
            Path receiptFile,
            Path keyRegistry,
            Path replayLedger,
            String purpose) throws Exception {
        ApprovalReceiptVerifier verifier = new ApprovalReceiptVerifier(keyRegistry, replayLedger);
        ValidationResult result = verifier.verify(
                receiptFile, VERIFICATION_RECEIPT_CONTRACT, purpose, Instant.now());
        if (result.decision() != Decision.PASS) {
            throw new IllegalStateException(
                    "SERVICE_VERIFICATION_RECEIPT_INVALID:" + String.join(",", result.violations()));
        }
        verifier.requireValidAndConsume(
                receiptFile, VERIFICATION_RECEIPT_CONTRACT, purpose, Instant.now());
    }

    private Map<String, Object> mutate(
            String caseId, String actor, String eventType, DurableStateLedger.Mutation mutation)
            throws Exception {
        requireId(caseId, "CASE_ID_INVALID");
        requireActor(actor);
        return store(caseId).mutate(eventType, actor, mutation);
    }

    private DurableStateLedger store(String caseId) {
        return new DurableStateLedger(caseRoot(caseId), STATE_CONTRACT, EVENT_CONTRACT,
                "case_id", caseId);
    }

    private void registerExternalReceipt(
            String kind, String provider, String receiptId, String caseId, String digest) throws Exception {
        ExclusiveFileLock.run(receiptIndexLock, () -> {
            Map<String, Object> index = readReceiptIndex();
            String key = receiptKey(kind, provider, receiptId);
            if (index.containsKey(key)) throw new IllegalStateException("EXTERNAL_RECEIPT_REPLAY:" + key);
            index.put(key, Map.of(
                    "kind", kind,
                    "provider", provider,
                    "receipt_id", receiptId,
                    "case_id", caseId,
                    "digest", digest,
                    "registered_at", Instant.now().toString()));
            writeReceiptIndex(index);
        });
    }

    private void unregisterExternalReceipt(
            String kind, String provider, String receiptId, String caseId, String digest) throws Exception {
        ExclusiveFileLock.run(receiptIndexLock, () -> {
            Map<String, Object> index = readReceiptIndex();
            String key = receiptKey(kind, provider, receiptId);
            Object current = index.get(key);
            if (current instanceof Map<?, ?> raw
                    && caseId.equals(String.valueOf(raw.get("case_id")))
                    && digest.equals(String.valueOf(raw.get("digest")))) {
                index.remove(key);
                writeReceiptIndex(index);
            }
        });
    }

    private Map<String, Object> readReceiptIndex() throws Exception {
        if (!Files.exists(receiptIndex, LinkOption.NOFOLLOW_LINKS)) return new TreeMap<>();
        if (!Files.isRegularFile(receiptIndex, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(receiptIndex)) {
            throw new IllegalStateException("EXTERNAL_RECEIPT_INDEX_INVALID");
        }
        Map<String, Object> wrapper = mapper.readValue(receiptIndex.toFile(), new TypeReference<>() {});
        if (!RECEIPT_INDEX_CONTRACT.equals(wrapper.get("contract"))) {
            throw new IllegalStateException("EXTERNAL_RECEIPT_INDEX_CONTRACT_INVALID");
        }
        Object entries = wrapper.get("entries");
        if (!(entries instanceof Map<?, ?> raw)) throw new IllegalStateException("EXTERNAL_RECEIPT_ENTRIES_INVALID");
        Map<String, Object> canonical = new TreeMap<>();
        raw.forEach((key, value) -> canonical.put(String.valueOf(key), value));
        String declared = string(wrapper, "index_sha256");
        if (!declared.equals(indexHash(canonical))) throw new IllegalStateException("EXTERNAL_RECEIPT_INDEX_TAMPERED");
        return canonical;
    }

    private void writeReceiptIndex(Map<String, Object> entries) throws Exception {
        Map<String, Object> canonical = new TreeMap<>(entries);
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("contract", RECEIPT_INDEX_CONTRACT);
        wrapper.put("entries", canonical);
        wrapper.put("updated_at", Instant.now().toString());
        wrapper.put("index_sha256", indexHash(canonical));
        Files.createDirectories(receiptIndex.getParent());
        Path temporary = receiptIndex.resolveSibling(receiptIndex.getFileName() + ".tmp");
        mapper.writeValue(temporary.toFile(), wrapper);
        move(temporary, receiptIndex);
    }

    private String indexHash(Map<String, Object> entries) throws Exception {
        return sha256(mapper.writeValueAsBytes(new TreeMap<>(entries)));
    }

    private static String receiptKey(String kind, String provider, String receiptId) {
        requireId(kind, "RECEIPT_KIND_INVALID");
        requireId(provider, "RECEIPT_PROVIDER_INVALID");
        requireText(receiptId, "RECEIPT_ID_INVALID");
        return kind + ":" + provider + ":" + receiptId;
    }

    private Path caseRoot(String id) {
        requireId(id, "CASE_ID_INVALID");
        Path value = root.resolve("cases").resolve(id).normalize();
        if (!value.startsWith(root)) throw new IllegalArgumentException("CASE_PATH_ESCAPE");
        return value;
    }

    private static void requireStatus(Map<String, Object> state, String... allowed) {
        if (!List.of(allowed).contains(state.get("status"))) {
            throw new IllegalStateException("CASE_STATUS_INVALID:" + state.get("status"));
        }
    }

    private static void requireRevision(Map<String, Object> state, long expected) {
        if (number(state.get("revision")) != expected) {
            throw new IllegalStateException("CASE_REVISION_DRIFT");
        }
    }

    private static Map<String, Object> map(Object value, String error) {
        if (!(value instanceof Map<?, ?> raw)) throw new IllegalStateException(error);
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> state(Map<String, Object> envelope) {
        Object value = envelope.get("state");
        if (!(value instanceof Map<?, ?> raw)) throw new IllegalStateException("CASE_STATE_RESULT_MISSING");
        return (Map<String, Object>) raw;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> details(Map<String, Object> envelope) {
        Object value = envelope.get("details");
        if (!(value instanceof Map<?, ?> raw)) throw new IllegalStateException("CASE_DETAILS_RESULT_MISSING");
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

    private static void requireActor(String actor) { requireText(actor, "ACTOR_REQUIRED"); }

    private static void requireId(String value, String error) {
        if (value == null || !value.matches("[A-Za-z0-9._:-]{1,160}")) {
            throw new IllegalArgumentException(error);
        }
    }

    private static void requireText(String value, String error) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(error);
    }

    private static void requireDigest(String value, String error) {
        if (value == null || !value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(error);
    }

    private static String string(Map<String, Object> value, String key) {
        Object item = value.get(key);
        return item instanceof String text ? text : "";
    }

    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(String.valueOf(value));
    }

    private static String fileSha(Path file) throws Exception {
        return sha256(Files.readAllBytes(file));
    }

    private static String sha256(byte[] value) throws Exception {
        return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static void move(Path source, Path destination) throws Exception {
        try {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}