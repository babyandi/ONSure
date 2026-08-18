package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/** Structural contract validator shared by all post-final-target DD operations. */
public final class DdAssuranceContractValidator {
    private DdAssuranceContractValidator() {}

    public static void validateRequest(String expectedDd, String operation, JsonNode request) {
        if (expectedDd == null || operation == null) {
            throw new IllegalArgumentException("DD_CONTRACT_IDENTITY_REQUIRED");
        }
        if (request == null || !request.isObject()) {
            throw new IllegalArgumentException("DD_REQUEST_OBJECT_REQUIRED");
        }
        String supplied = request.path("dd_id").asText("");
        if (supplied.isBlank()) {
            throw new IllegalArgumentException("DD_ID_REQUIRED");
        }
        if (!expectedDd.equals(supplied)) {
            throw new IllegalArgumentException("DD_OPERATION_ID_MISMATCH:" + operation + ":" + supplied);
        }
        JsonNode evidence = request.path("evidence_refs");
        if (!evidence.isArray()) {
            throw new IllegalArgumentException("DD_EVIDENCE_REFS_ARRAY_REQUIRED");
        }
        java.util.HashSet<String> unique = new java.util.HashSet<>();
        for (JsonNode ref : evidence) {
            if (!ref.isTextual() || ref.asText().isBlank()) {
                throw new IllegalArgumentException("DD_EVIDENCE_REF_INVALID");
            }
            if (!unique.add(ref.asText())) {
                throw new IllegalArgumentException("DD_EVIDENCE_REF_DUPLICATE");
            }
        }
        for (String forbidden : List.of("final_claim_allowed", "decision_override", "_authorized_target_root",
                "_authorized_target_id", "_authorized_project_id")) {
            if (request.has(forbidden)) {
                throw new SecurityException("DD_CALLER_AUTHORITY_FIELD_PROHIBITED:" + forbidden);
            }
        }
    }

    public static void validateFailClosedResult(
            String expectedDd, String expectedOperation, Map<String, Object> result) {
        if (result == null) throw new IllegalArgumentException("DD_RESULT_REQUIRED");
        if (!expectedDd.equals(result.get("dd_id"))) {
            throw new IllegalStateException("DD_RESULT_IDENTITY_MISMATCH");
        }
        if (!expectedOperation.equals(result.get("operation"))) {
            throw new IllegalStateException("DD_RESULT_OPERATION_MISMATCH");
        }
        String decision = String.valueOf(result.get("decision"));
        if (!List.of("HOLD", "BLOCKED", "INCONCLUSIVE", "NOT_RUN", "UNKNOWN", "FAIL").contains(decision)) {
            throw new IllegalStateException("DD_UNQUALIFIED_RUNTIME_POSITIVE_DECISION_PROHIBITED:" + decision);
        }
        Object reasons = result.get("blocking_reasons");
        if (!(reasons instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalStateException("DD_BLOCKING_REASONS_REQUIRED");
        }
        if (!Boolean.FALSE.equals(result.get("claim_strengthening_allowed"))) {
            throw new IllegalStateException("DD_CLAIM_STRENGTHENING_PROHIBITED");
        }
        if (!Boolean.FALSE.equals(result.get("external_effect_performed"))) {
            throw new IllegalStateException("DD_EXTERNAL_EFFECT_PROHIBITED");
        }
        if (!Boolean.FALSE.equals(result.get("final_claim_allowed"))) {
            throw new IllegalStateException("DD_FINAL_CLAIM_PROHIBITED");
        }
    }
}
