package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Successor evaluators added after Design Discovery reopened the denominator. */
public final class DesignGapDdSemanticEvaluators {
    public static final String VERSION = "design-gap-dd-evaluators-v2";
    public static final int DD042_MINIMUM_ADVERSARIAL_EVIDENCE_COUNT = 6;

    private DesignGapDdSemanticEvaluators() {}

    public static List<DdSemanticEvaluator> all() {
        return List.of(new CryptoErasureCompletenessEvaluator(), new SelfReferentialSafetyClaimEvaluator());
    }

    private record EvidenceFacts(Map<String, JsonNode> facts, List<String> refs, List<String> reasons) {}

    private static EvidenceFacts resolve(JsonNode request, DdSemanticEvaluator.EvaluationContext context) {
        Map<String, JsonNode> facts = new LinkedHashMap<>();
        List<String> refs = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        JsonNode evidenceRefs = request.path("evidence_refs");
        if (!evidenceRefs.isArray() || evidenceRefs.isEmpty()) {
            reasons.add("REQUIRED_EVIDENCE_MISSING");
            return new EvidenceFacts(facts, refs, reasons);
        }
        for (JsonNode refNode : evidenceRefs) {
            String ref = refNode.asText("");
            if (ref.isBlank()) {
                reasons.add("EVIDENCE_REF_INVALID");
                continue;
            }
            var resolved = context.evidenceResolver().resolve(ref);
            if (resolved.isEmpty()) {
                reasons.add("EVIDENCE_NOT_RESOLVED:" + ref);
                continue;
            }
            var evidence = resolved.get();
            if (!evidence.integrityVerified()) reasons.add("EVIDENCE_INTEGRITY_NOT_VERIFIED:" + ref);
            if (!evidence.current()) reasons.add("EVIDENCE_NOT_CURRENT:" + ref);
            if (!evidence.integrityVerified() || !evidence.current()) continue;
            refs.add(ref);
            Iterator<Map.Entry<String, JsonNode>> fields = evidence.document().fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                facts.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        if (refs.isEmpty() && reasons.isEmpty()) reasons.add("NO_CURRENT_INTEGRITY_VERIFIED_EVIDENCE");
        return new EvidenceFacts(facts, refs, reasons);
    }

    private static int integer(Map<String, JsonNode> facts, String key, List<String> reasons) {
        JsonNode v = facts.get(key);
        if (v == null || !v.canConvertToInt()) {
            reasons.add("FACT_MISSING_OR_INVALID:" + key);
            return Integer.MIN_VALUE;
        }
        return v.asInt();
    }

    private static boolean bool(Map<String, JsonNode> facts, String key, List<String> reasons) {
        JsonNode v = facts.get(key);
        if (v == null || !v.isBoolean()) {
            reasons.add("FACT_MISSING_OR_INVALID:" + key);
            return false;
        }
        return v.asBoolean();
    }

    private static String text(Map<String, JsonNode> facts, String key, List<String> reasons) {
        JsonNode v = facts.get(key);
        if (v == null || !v.isTextual() || v.asText().isBlank()) {
            reasons.add("FACT_MISSING_OR_INVALID:" + key);
            return "";
        }
        return v.asText();
    }

    private static DdSemanticEvaluator.Evaluation hold(List<String> reasons, List<String> refs, Map<String,Object> details) {
        return new DdSemanticEvaluator.Evaluation("HOLD", reasons, refs, false, false, details);
    }

    private static DdSemanticEvaluator.Evaluation pass(List<String> refs, Map<String,Object> details) {
        return new DdSemanticEvaluator.Evaluation("PASS_NONFINAL", List.of(), refs, true, false, details);
    }

    private static final class CryptoErasureCompletenessEvaluator implements DdSemanticEvaluator {
        @Override public String ddId() { return "DD-041"; }

        @Override
        public Evaluation evaluate(JsonNode request, EvaluationContext context) {
            EvidenceFacts e = resolve(request, context);
            List<String> reasons = new ArrayList<>(e.reasons());
            String payloadPolicy = text(e.facts(), "payload_scope_policy", reasons);
            int expected = integer(e.facts(), "expected_payload_copy_count", reasons);
            int accounted = integer(e.facts(), "accounted_payload_copy_count", reasons);
            int activeKeys = integer(e.facts(), "active_key_binding_count", reasons);
            int retentionConflicts = integer(e.facts(), "unresolved_retention_conflict_count", reasons);
            int unverified = integer(e.facts(), "unverified_erasure_count", reasons);
            if (!payloadPolicy.isBlank() && "UNRESOLVED".equals(payloadPolicy)) reasons.add("PAYLOAD_SCOPE_POLICY_UNRESOLVED");
            if (expected != Integer.MIN_VALUE && accounted != Integer.MIN_VALUE && expected != accounted) reasons.add("PAYLOAD_COPY_DENOMINATOR_MISMATCH");
            if (activeKeys > 0) reasons.add("ACTIVE_KEY_BINDINGS_REMAIN");
            if (retentionConflicts > 0) reasons.add("RETENTION_CONFLICT_UNRESOLVED");
            if (unverified > 0) reasons.add("ERASURE_EVIDENCE_UNVERIFIED");
            Map<String,Object> details = Map.of(
                    "payload_scope_policy", payloadPolicy,
                    "expected_payload_copy_count", expected,
                    "accounted_payload_copy_count", accounted,
                    "active_key_binding_count", activeKeys,
                    "unresolved_retention_conflict_count", retentionConflicts,
                    "unverified_erasure_count", unverified);
            return reasons.isEmpty() ? pass(e.refs(), details) : hold(List.copyOf(reasons), e.refs(), details);
        }
    }

    private static final class SelfReferentialSafetyClaimEvaluator implements DdSemanticEvaluator {
        @Override public String ddId() { return "DD-042"; }

        @Override
        public Evaluation evaluate(JsonNode request, EvaluationContext context) {
            EvidenceFacts e = resolve(request, context);
            List<String> reasons = new ArrayList<>(e.reasons());
            String category = text(e.facts(), "claim_category", reasons);
            String claimAuthor = text(e.facts(), "claim_author_lineage", reasons);
            String evidenceAuthor = text(e.facts(), "evidence_author_lineage", reasons);
            String evaluator = text(e.facts(), "evaluator_lineage", reasons);
            String oracle = text(e.facts(), "oracle_lineage", reasons);
            String promotion = text(e.facts(), "promotion_authority_lineage", reasons);
            boolean independenceProvenanceVerified = bool(e.facts(), "independence_provenance_verified", reasons);
            boolean materialSharedControlLoopDetected = bool(e.facts(), "material_shared_control_loop_detected", reasons);
            int independent = integer(e.facts(), "independent_adversarial_evidence_count", reasons);
            int required = integer(e.facts(), "required_adversarial_evidence_count", reasons);
            boolean oracleCurrent = bool(e.facts(), "authority_oracle_current", reasons);

            if (!"SELF_REFERENTIAL_AI_SAFETY".equals(category)) reasons.add("SELF_REFERENTIAL_CLAIM_CATEGORY_NOT_BOUND");
            if (!claimAuthor.isBlank() && claimAuthor.equals(evidenceAuthor)) reasons.add("CLAIM_EVIDENCE_SELF_SOURCING");
            if (!evaluator.isBlank() && evaluator.equals(oracle)) reasons.add("EVALUATOR_ORACLE_CIRCULARITY");
            if ((!claimAuthor.isBlank() && claimAuthor.equals(promotion)) || (!evaluator.isBlank() && evaluator.equals(promotion))) {
                reasons.add("CLAIM_OR_EVALUATOR_SELF_PROMOTION");
            }
            if (!claimAuthor.isBlank() && (claimAuthor.equals(evaluator) || claimAuthor.equals(oracle) || claimAuthor.equals(promotion))) reasons.add("CLAIM_AUTHOR_SELF_APPROVAL_LOOP");
            if (!evidenceAuthor.isBlank() && (evidenceAuthor.equals(evaluator) || evidenceAuthor.equals(oracle) || evidenceAuthor.equals(promotion))) reasons.add("EVIDENCE_AUTHOR_SELF_APPROVAL_LOOP");
            if (!independenceProvenanceVerified) reasons.add("INDEPENDENCE_PROVENANCE_NOT_VERIFIED");
            if (materialSharedControlLoopDetected) reasons.add("MATERIAL_SHARED_CONTROL_LOOP");
            if (required < DD042_MINIMUM_ADVERSARIAL_EVIDENCE_COUNT) reasons.add("ADVERSARIAL_FIXTURE_MINIMUM_BELOW_AUTHORITY_FLOOR");
            if (independent != Integer.MIN_VALUE && required != Integer.MIN_VALUE && independent < required) reasons.add("INDEPENDENT_ADVERSARIAL_EVIDENCE_INSUFFICIENT");
            if (!oracleCurrent) reasons.add("AUTHORITY_ORACLE_NOT_CURRENT");

            Map<String,Object> details = new LinkedHashMap<>();
            details.put("claim_category", category);
            details.put("independence_provenance_verified", independenceProvenanceVerified);
            details.put("material_shared_control_loop_detected", materialSharedControlLoopDetected);
            details.put("independent_adversarial_evidence_count", independent);
            details.put("required_adversarial_evidence_count", required);
            details.put("authority_oracle_current", oracleCurrent);
            details.put("authority_minimum_adversarial_evidence_count", DD042_MINIMUM_ADVERSARIAL_EVIDENCE_COUNT);
            return reasons.isEmpty() ? pass(e.refs(), Map.copyOf(details)) : hold(List.copyOf(reasons), e.refs(), Map.copyOf(details));
        }
    }
}
