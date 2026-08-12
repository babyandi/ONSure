package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Null-safe, fail-closed v1 -> v2 semantic reconstruction helper. */
public final class SemanticAssuranceV2Reconstructor {
    public static final String CONTRACT = "ONSURE_SEMANTIC_ASSURANCE_V2_RECONSTRUCTOR_V2";
    private static final String NA = "NOT_AVAILABLE";
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    public enum GapClass {
        DIRECTLY_MAPPABLE,
        DERIVABLE_WITH_PROOF,
        REQUIRES_READBACK,
        REQUIRES_REPERFORMANCE,
        REQUIRES_HUMAN_OR_EXTERNAL_AUTHORITY,
        UNRECOVERABLE_FROM_V1
    }

    public Map<String, Object> reconstructStatus(JsonNode v1, String sourceContract, String targetId) {
        requireObject(v1);
        List<Map<String, Object>> gaps = new ArrayList<>();
        String legacy = firstText(v1, "decision", "verification_status", "quality_decision", "status");
        String normalized = normalizeLegacyDecision(legacy);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("contract", CONTRACT);
        out.put("reconstruction_type", "STATUS_V1_TO_V2");
        out.put("source_contract", safe(sourceContract));
        out.put("target_id", safe(targetId));
        out.put("legacy_decision", safe(legacy));
        out.put("implementation_state", mapImplementation(v1.path("implementation_status").asText(null)));
        out.put("execution_state", mapExecution(normalized, v1));
        out.put("evidence_state", deriveEvidenceState(v1, gaps));
        out.put("independence_state", deriveIndependence(v1, gaps));
        out.put("qualification_state", "NOT_QUALIFIED");
        gaps.add(gap("qualification", GapClass.REQUIRES_REPERFORMANCE));
        out.put("freshness_state", "STATUS_UNKNOWN");
        gaps.add(gap("freshness", GapClass.REQUIRES_READBACK));
        out.put("publication_state", "SELF_VALIDATION_NONFINAL");
        out.put("decision", failClosedDecision(normalized, gaps));
        out.put("gap_requirements", List.copyOf(gaps));
        out.put("reconstructed_at", Instant.now().toString());
        out.put("final_claim_allowed", false);
        out.put("reconstruction_sha256", digest(out));
        return immutable(out);
    }

    public Map<String, Object> reconstructReceipt(JsonNode v1, String sourceContract, String targetId) {
        requireObject(v1);
        List<Map<String, Object>> gaps = new ArrayList<>();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("contract", CONTRACT);
        out.put("reconstruction_type", "RECEIPT_V1_TO_V2");
        out.put("source_contract", safe(sourceContract));
        out.put("target_id", safe(targetId));
        out.put("source_receipt_id", safe(firstText(v1, "receipt_id", "run_receipt_id", "approval_id", "review_id", "proof_id")));
        out.put("source_receipt_sha256", safe(firstDigest(v1, "receipt_sha256", "proof_sha256", "review_sha256", "change_set_sha256")));
        out.put("source_digest", safe(firstDigest(v1, "source_tree_sha256", "source_digest", "source_hash")));
        out.put("policy_digest", safe(firstDigest(v1, "policy_digest", "policy_sha256")));
        out.put("actor", safe(firstText(v1, "actor", "operator_id", "reviewer_id", "approval_actor")));
        out.put("key_id", safe(firstText(v1, "key_id", "approval_key_id")));
        out.put("legacy_decision", safe(firstText(v1, "decision", "status", "quality_decision")));
        requireIfMissing(gaps, "tenant_id", GapClass.UNRECOVERABLE_FROM_V1, v1.hasNonNull("tenant_id"));
        requireIfMissing(gaps, "scope_epoch", GapClass.REQUIRES_READBACK, v1.hasNonNull("scope_epoch"));
        requireIfMissing(gaps, "requirement_epoch", GapClass.REQUIRES_READBACK, v1.hasNonNull("requirement_epoch"));
        requireIfMissing(gaps, "denominator_epoch", GapClass.REQUIRES_READBACK, v1.hasNonNull("denominator_epoch"));
        requireIfMissing(gaps, "authority_profile", GapClass.REQUIRES_HUMAN_OR_EXTERNAL_AUTHORITY,
                v1.hasNonNull("authority_profile_sha256") || v1.hasNonNull("principal_profile_sha256"));
        requireIfMissing(gaps, "independence_profile", GapClass.REQUIRES_REPERFORMANCE, v1.hasNonNull("independence_profile_sha256"));
        requireIfMissing(gaps, "qualification_epoch", GapClass.REQUIRES_REPERFORMANCE, v1.hasNonNull("qualification_epoch"));
        requireIfMissing(gaps, "freshness_epoch", GapClass.REQUIRES_READBACK, v1.hasNonNull("freshness_epoch"));
        requireIfMissing(gaps, "oracle_set_digest", GapClass.REQUIRES_REPERFORMANCE, v1.hasNonNull("oracle_set_digest"));
        requireIfMissing(gaps, "validator_set_digest", GapClass.REQUIRES_REPERFORMANCE, v1.hasNonNull("validator_set_digest"));
        requireIfMissing(gaps, "canonicalization_profile", GapClass.UNRECOVERABLE_FROM_V1, v1.hasNonNull("canonicalization"));
        out.put("gap_requirements", List.copyOf(gaps));
        out.put("decision", gaps.isEmpty() ? "NON_FINAL" : "HOLD");
        out.put("reconstructed_at", Instant.now().toString());
        out.put("final_claim_allowed", false);
        out.put("reconstruction_sha256", digest(out));
        return immutable(out);
    }

    public Map<String, Object> reconstructFinalCandidate(
            String targetId, String sourceTreeSha256, String artifactDigest,
            List<Map<String, Object>> evidence, Map<String, String> epochs,
            String otesterReceiptSha256, String oauditReceiptSha256,
            String humanAcceptanceReceiptSha256, int openP0, int openP1) {
        List<String> blockers = new ArrayList<>();
        requireDigest(blockers, "source_tree_sha256", sourceTreeSha256);
        requireDigest(blockers, "artifact_digest", artifactDigest);
        for (String key : List.of("scope", "requirement", "denominator", "policy", "oracle", "validator_qualification", "authority")) {
            if (epochs == null || blank(epochs.get(key))) blockers.add("MISSING_EPOCH:" + key);
        }
        requireDigest(blockers, "otester_receipt_sha256", otesterReceiptSha256);
        requireDigest(blockers, "oaudit_receipt_sha256", oauditReceiptSha256);
        requireDigest(blockers, "human_acceptance_receipt_sha256", humanAcceptanceReceiptSha256);
        if (evidence == null || evidence.isEmpty()) blockers.add("EVIDENCE_BUNDLE_EMPTY");
        if (openP0 > 0) blockers.add("OPEN_P0_FINDINGS:" + openP0);
        if (openP1 > 0) blockers.add("OPEN_P1_FINDINGS:" + openP1);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("contract", CONTRACT);
        out.put("reconstruction_type", "FINAL_CANDIDATE_V2");
        out.put("target_id", safe(targetId));
        out.put("source_tree_sha256", safe(sourceTreeSha256));
        out.put("artifact_digest", safe(artifactDigest));
        out.put("epochs", epochs == null ? Map.of() : Map.copyOf(epochs));
        out.put("evidence_count", evidence == null ? 0 : evidence.size());
        out.put("otester_receipt_sha256", safe(otesterReceiptSha256));
        out.put("oaudit_receipt_sha256", safe(oauditReceiptSha256));
        out.put("human_acceptance_receipt_sha256", safe(humanAcceptanceReceiptSha256));
        out.put("open_p0", openP0);
        out.put("open_p1", openP1);
        out.put("blockers", List.copyOf(blockers));
        out.put("decision", blockers.isEmpty() ? "NON_FINAL" : "HOLD");
        out.put("eligible_for_shadow_comparison", blockers.isEmpty());
        out.put("final_lock_allowed", false);
        out.put("reconstructed_at", Instant.now().toString());
        out.put("reconstruction_sha256", digest(out));
        return immutable(out);
    }

    public Path write(Path output, Map<String, Object> value) throws Exception {
        Path absolute = output.toAbsolutePath().normalize();
        if (absolute.getParent() != null) Files.createDirectories(absolute.getParent());
        mapper.writeValue(absolute.toFile(), value);
        return absolute;
    }

    private String deriveEvidenceState(JsonNode v1, List<Map<String, Object>> gaps) {
        if (!hasDigest(v1, "receipt_sha256", "evidence_sha256", "proof_sha256", "review_sha256")) {
            gaps.add(gap("evidence_binding", GapClass.REQUIRES_READBACK));
            return "UNKNOWN";
        }
        gaps.add(gap("evidence_binding_strength", GapClass.REQUIRES_REPERFORMANCE));
        return "PARTIALLY_BOUND";
    }

    private String deriveIndependence(JsonNode v1, List<Map<String, Object>> gaps) {
        if (v1.has("independent_authority") && !v1.path("independent_authority").asBoolean(false)) return "SELF_VALIDATION";
        gaps.add(gap("independence_profile", GapClass.REQUIRES_REPERFORMANCE));
        return "NOT_ASSESSED";
    }

    private String failClosedDecision(String legacy, List<Map<String, Object>> gaps) {
        return switch (legacy) {
            case "FAIL", "BLOCKED", "NOT_RUN", "HOLD", "INCONCLUSIVE" -> legacy;
            default -> gaps.isEmpty() ? "NON_FINAL" : "HOLD";
        };
    }

    private String mapExecution(String decision, JsonNode v1) {
        return switch (decision) {
            case "NOT_RUN" -> "NOT_RUN";
            case "BLOCKED" -> "BLOCKED";
            case "HOLD" -> "HOLD";
            case "INCONCLUSIVE" -> "INCONCLUSIVE";
            default -> hasDigest(v1, "receipt_sha256", "evidence_sha256", "proof_sha256", "review_sha256") ? "EXECUTED" : "PLANNED";
        };
    }

    private String mapImplementation(String value) {
        if (value == null) return "DESIGN_ONLY";
        return switch (value) {
            case "IMPLEMENTED" -> "IMPLEMENTED";
            case "PARTIAL" -> "PARTIAL";
            case "CONFLICT" -> "CONFLICT";
            case "DEPRECATED" -> "DEPRECATED";
            default -> "DESIGN_ONLY";
        };
    }

    private String normalizeLegacyDecision(String value) {
        if (value == null) return "NOT_RUN";
        return switch (value) {
            case "PASS", "FAIL", "BLOCKED", "HOLD", "NOT_RUN", "INCONCLUSIVE" -> value;
            default -> "INCONCLUSIVE";
        };
    }

    private Map<String, Object> gap(String field, GapClass gapClass) {
        return Map.of("field", field, "gap_class", gapClass.name());
    }

    private void requireIfMissing(List<Map<String, Object>> gaps, String field, GapClass gapClass, boolean present) {
        if (!present) gaps.add(gap(field, gapClass));
    }

    private void requireDigest(List<String> blockers, String field, String digest) {
        if (digest == null || !digest.matches("[0-9a-f]{64}")) blockers.add("MISSING_OR_INVALID_DIGEST:" + field);
    }

    private boolean hasDigest(JsonNode node, String... names) { return firstDigest(node, names) != null; }

    private String firstDigest(JsonNode node, String... names) {
        for (String name : names) {
            String value = node.path(name).asText(null);
            if (value != null && value.matches("[0-9a-f]{64}")) return value;
        }
        return null;
    }

    private String firstText(JsonNode node, String... names) {
        for (String name : names) {
            String value = node.path(name).asText(null);
            if (!blank(value)) return value;
        }
        return null;
    }

    private void requireObject(JsonNode node) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException("V1_OBJECT_REQUIRED");
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String safe(String value) { return blank(value) ? NA : value; }

    private Map<String, Object> immutable(Map<String, Object> value) {
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }

    private String digest(Map<String, Object> value) {
        try {
            TreeMap<String, Object> copy = new TreeMap<>(value);
            copy.remove("reconstruction_sha256");
            return Hashing.sha256(mapper.writeValueAsBytes(copy));
        } catch (Exception e) {
            throw new IllegalStateException("V2_RECONSTRUCTION_DIGEST_FAILED", e);
        }
    }
}
