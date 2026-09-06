package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/** Structural contract validator shared by all post-final-target DD operations. */
public final class DdAssuranceContractValidator {
    private DdAssuranceContractValidator() {}

    public static void validateRequest(String expectedDd, String operation, JsonNode request) {
        if (expectedDd == null || operation == null) throw new IllegalArgumentException("DD_CONTRACT_IDENTITY_REQUIRED");
        if (request == null || !request.isObject()) throw new IllegalArgumentException("DD_REQUEST_OBJECT_REQUIRED");
        String supplied = request.path("dd_id").asText("");
        if (supplied.isBlank()) throw new IllegalArgumentException("DD_ID_REQUIRED");
        if (!expectedDd.equals(supplied)) throw new IllegalArgumentException("DD_OPERATION_ID_MISMATCH:" + operation + ":" + supplied);
        JsonNode evidence = request.path("evidence_refs");
        if (!evidence.isArray()) throw new IllegalArgumentException("DD_EVIDENCE_REFS_ARRAY_REQUIRED");
        java.util.HashSet<String> unique = new java.util.HashSet<>();
        for (JsonNode ref : evidence) {
            if (!ref.isTextual() || ref.asText().isBlank()) throw new IllegalArgumentException("DD_EVIDENCE_REF_INVALID");
            if (!unique.add(ref.asText())) throw new IllegalArgumentException("DD_EVIDENCE_REF_DUPLICATE");
        }
        for (String forbidden : List.of("final_claim_allowed", "decision_override", "_authorized_target_root",
                "_authorized_target_id", "_authorized_project_id", "qualification_current",
                "independent_qualification", "qualification_receipt_digest")) {
            if (request.has(forbidden)) throw new SecurityException("DD_CALLER_AUTHORITY_FIELD_PROHIBITED:" + forbidden);
        }
    }

    public static void validateFailClosedResult(String expectedDd, String expectedOperation, Map<String, Object> result) {
        validateCommonIdentity(expectedDd, expectedOperation, result);
        String decision = String.valueOf(result.get("decision"));
        if (!List.of("HOLD", "BLOCKED", "INCONCLUSIVE", "NOT_RUN", "UNKNOWN", "FAIL").contains(decision)) {
            throw new IllegalStateException("DD_UNQUALIFIED_RUNTIME_POSITIVE_DECISION_PROHIBITED:" + decision);
        }
        Object reasons = result.get("blocking_reasons");
        if (!(reasons instanceof List<?> list) || list.isEmpty()) throw new IllegalStateException("DD_BLOCKING_REASONS_REQUIRED");
        if (!Boolean.FALSE.equals(result.get("claim_strengthening_allowed"))) throw new IllegalStateException("DD_CLAIM_STRENGTHENING_PROHIBITED");
        if (!Boolean.FALSE.equals(result.get("external_effect_performed"))) throw new IllegalStateException("DD_EXTERNAL_EFFECT_PROHIBITED");
        requireFinalFalse(result);
    }

    /**
     * Minimum guard for a result from an independently qualified evaluator. DD-specific positive
     * oracle rules remain the evaluator's responsibility and are separately qualification-bound.
     */
    public static void validateQualifiedResult(String expectedDd, String expectedOperation, Map<String, Object> result) {
        validateCommonIdentity(expectedDd, expectedOperation, result);
        String decision = String.valueOf(result.get("decision"));
        if (!List.of("PASS_NONFINAL", "FAIL", "HOLD", "BLOCKED", "INCONCLUSIVE", "NOT_RUN", "UNKNOWN").contains(decision)) {
            throw new IllegalStateException("DD_QUALIFIED_RESULT_DECISION_INVALID:" + decision);
        }
        Object receipts = result.get("evidence_receipt_refs");
        if (!(receipts instanceof List<?> evidenceReceipts)) throw new IllegalStateException("DD_EVIDENCE_RECEIPT_REFS_REQUIRED");
        if ("PASS_NONFINAL".equals(decision)) {
            if (evidenceReceipts.isEmpty()) throw new IllegalStateException("DD_POSITIVE_RESULT_EVIDENCE_REQUIRED");
            Object reasons = result.get("blocking_reasons");
            if (reasons instanceof List<?> list && !list.isEmpty()) throw new IllegalStateException("DD_POSITIVE_RESULT_HAS_BLOCKERS");
            if (!Boolean.TRUE.equals(result.get("claim_strengthening_allowed"))) throw new IllegalStateException("DD_POSITIVE_RESULT_STRENGTH_FLAG_REQUIRED");
        } else if (!Boolean.FALSE.equals(result.get("claim_strengthening_allowed"))) {
            throw new IllegalStateException("DD_NONPOSITIVE_RESULT_CANNOT_STRENGTHEN");
        }
        // External effects are never authorized by evaluator qualification alone.
        if (!Boolean.FALSE.equals(result.get("external_effect_performed"))) throw new IllegalStateException("DD_EVALUATOR_CANNOT_SELF_AUTHORIZE_EXTERNAL_EFFECT");
        if (result.get("evaluator_id") == null || result.get("evaluator_version") == null
                || result.get("qualification_receipt_digest") == null) {
            throw new IllegalStateException("DD_QUALIFIED_EVALUATOR_PROVENANCE_REQUIRED");
        }
        requireFinalFalse(result);
    }

    private static void validateCommonIdentity(String expectedDd, String expectedOperation, Map<String, Object> result) {
        if (result == null) throw new IllegalArgumentException("DD_RESULT_REQUIRED");
        if (!expectedDd.equals(result.get("dd_id"))) throw new IllegalStateException("DD_RESULT_IDENTITY_MISMATCH");
        if (!expectedOperation.equals(result.get("operation"))) throw new IllegalStateException("DD_RESULT_OPERATION_MISMATCH");
    }

    private static void requireFinalFalse(Map<String, Object> result) {
        if (!Boolean.FALSE.equals(result.get("final_claim_allowed"))) throw new IllegalStateException("DD_FINAL_CLAIM_PROHIBITED");
    }
}
