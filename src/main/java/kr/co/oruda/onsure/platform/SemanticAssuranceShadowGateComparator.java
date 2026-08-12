package kr.co.oruda.onsure.platform;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compares legacy and v2 gate decisions without promoting either side to final authority. */
public final class SemanticAssuranceShadowGateComparator {
    public static final String CONTRACT = "ONSURE_SHADOW_GATE_COMPARISON_V1_CANDIDATE";

    public Map<String, Object> compare(
            String comparisonId,
            String targetId,
            String sourceTreeSha256,
            GateResult legacy,
            GateResult v2,
            List<String> missingV2Evidence) {
        if (legacy == null || v2 == null) throw new IllegalArgumentException("SHADOW_GATE_RESULTS_REQUIRED");
        List<String> reasons = new ArrayList<>();
        if (!legacy.decision().equals(v2.decision())) {
            reasons.add("DECISION_DIFFERENCE:" + legacy.decision() + "->" + v2.decision());
        }
        if (!legacy.assuranceClass().equals(v2.assuranceClass())) {
            reasons.add("ASSURANCE_CLASS_DIFFERENCE:" + legacy.assuranceClass() + "->" + v2.assuranceClass());
        }
        if ("PASS".equals(legacy.decision()) && !"PASS".equals(v2.decision())) {
            reasons.add("LEGACY_PASS_V2_NONPASS_MUST_NOT_BE_DOWNGRADED");
        }
        if (missingV2Evidence != null && !missingV2Evidence.isEmpty()) {
            reasons.add("V2_EVIDENCE_GAPS_PRESENT");
        }
        boolean disagreement = !reasons.isEmpty();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contract", CONTRACT);
        result.put("comparison_id", safeRequired(comparisonId, "comparison_id"));
        result.put("target_id", safeRequired(targetId, "target_id"));
        if (sourceTreeSha256 == null || !sourceTreeSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("SHADOW_GATE_SOURCE_DIGEST_INVALID");
        }
        result.put("source_tree_sha256", sourceTreeSha256);
        result.put("legacy_gate", legacy.asMap());
        result.put("v2_gate", v2.asMap());
        result.put("disagreement", disagreement);
        result.put("disagreement_reasons", List.copyOf(reasons));
        result.put("missing_v2_evidence", missingV2Evidence == null ? List.of() : List.copyOf(missingV2Evidence));
        result.put("decision", disagreement ? "DISAGREEMENT_HOLD" : "AGREE_NONFINAL");
        result.put("compared_at", Instant.now().toString());
        result.put("final_claim_allowed", false);
        return Map.copyOf(result);
    }

    private static String safeRequired(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("SHADOW_GATE_FIELD_REQUIRED:" + field);
        return value;
    }

    public record GateResult(String decision, String receiptSha256, String assuranceClass) {
        public GateResult {
            if (decision == null || decision.isBlank()) throw new IllegalArgumentException("SHADOW_GATE_DECISION_REQUIRED");
            if (receiptSha256 != null && !receiptSha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("SHADOW_GATE_RECEIPT_DIGEST_INVALID");
            }
            if (assuranceClass == null || assuranceClass.isBlank()) throw new IllegalArgumentException("SHADOW_GATE_ASSURANCE_CLASS_REQUIRED");
        }

        Map<String, Object> asMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("decision", decision);
            map.put("receipt_sha256", receiptSha256);
            map.put("assurance_class", assuranceClass);
            return Collections.unmodifiableMap(map);
        }
    }
}
