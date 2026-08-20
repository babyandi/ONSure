package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** Thirteen successor synthetic mechanics cases; never independent qualification. */
class DesignGapDdQualificationFixtureTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TestFactory
    Stream<DynamicTest> exact13DesignGapQualificationFixtureMechanics() {
        List<DynamicTest> tests = new ArrayList<>();
        for (DdSemanticEvaluator evaluator : DesignGapDdSemanticEvaluators.all()) {
            tests.add(DynamicTest.dynamicTest(evaluator.ddId() + " positive", () -> positive(evaluator)));
            tests.add(DynamicTest.dynamicTest(evaluator.ddId() + " negative", () -> negative(evaluator)));
            tests.add(DynamicTest.dynamicTest(evaluator.ddId() + " recovery", () -> recovery(evaluator)));
            if ("DD-041".equals(evaluator.ddId())) {
                tests.add(DynamicTest.dynamicTest("DD-041 adversarial integrity", () -> dd041Adversarial(evaluator)));
            } else {
                tests.add(DynamicTest.dynamicTest("DD-042 adversarial claim/evidence self-sourcing", () ->
                        dd042Adversarial(evaluator, dd042ClaimEvidenceSelfSourcing(), "CLAIM_EVIDENCE_SELF_SOURCING")));
                tests.add(DynamicTest.dynamicTest("DD-042 adversarial evaluator/oracle circularity", () ->
                        dd042Adversarial(evaluator, dd042EvaluatorOracleCircularity(), "EVALUATOR_ORACLE_CIRCULARITY")));
                tests.add(DynamicTest.dynamicTest("DD-042 adversarial self-promotion", () ->
                        dd042Adversarial(evaluator, dd042SelfPromotion(), "CLAIM_OR_EVALUATOR_SELF_PROMOTION")));
                tests.add(DynamicTest.dynamicTest("DD-042 adversarial unverified independence provenance", () ->
                        dd042Adversarial(evaluator, dd042UnverifiedIndependenceProvenance(), "INDEPENDENCE_PROVENANCE_NOT_VERIFIED")));
                tests.add(DynamicTest.dynamicTest("DD-042 adversarial insufficient independent evidence", () ->
                        dd042Adversarial(evaluator, dd042InsufficientIndependentEvidence(), "INDEPENDENT_ADVERSARIAL_EVIDENCE_INSUFFICIENT")));
                tests.add(DynamicTest.dynamicTest("DD-042 adversarial shared-control loop", () ->
                        dd042Adversarial(evaluator, dd042MaterialSharedControlLoop(), "MATERIAL_SHARED_CONTROL_LOOP")));
            }
        }
        assertEquals(13, tests.size());
        return tests.stream();
    }

    private void positive(DdSemanticEvaluator evaluator) throws Exception {
        var result = evaluate(evaluator, positiveFacts(evaluator.ddId()), true, true);
        assertEquals("PASS_NONFINAL", result.decision());
        assertTrue(result.blockingReasons().isEmpty());
        assertTrue(result.claimStrengtheningAllowed());
        assertFalse(result.externalEffectPerformed());
    }

    private void negative(DdSemanticEvaluator evaluator) throws Exception {
        var result = evaluate(evaluator, negativeFacts(evaluator.ddId()), true, true);
        assertEquals("HOLD", result.decision());
        assertFalse(result.blockingReasons().isEmpty());
        assertFalse(result.claimStrengtheningAllowed());
    }

    private void recovery(DdSemanticEvaluator evaluator) throws Exception {
        assertEquals("HOLD", evaluate(evaluator, negativeFacts(evaluator.ddId()), true, true).decision());
        var recovered = evaluate(evaluator, positiveFacts(evaluator.ddId()), true, true);
        assertEquals("PASS_NONFINAL", recovered.decision());
        assertFalse(recovered.externalEffectPerformed());
    }

    private void dd041Adversarial(DdSemanticEvaluator evaluator) throws Exception {
        var result = evaluate(evaluator, positiveFacts(evaluator.ddId()), false, true);
        assertEquals("HOLD", result.decision());
        assertTrue(result.blockingReasons().stream().anyMatch(v -> v.startsWith("EVIDENCE_INTEGRITY_NOT_VERIFIED")));
        assertFalse(result.claimStrengtheningAllowed());
    }

    private void dd042Adversarial(DdSemanticEvaluator evaluator, Map<String,Object> facts, String requiredReason) throws Exception {
        var result = evaluate(evaluator, facts, true, true);
        assertEquals("HOLD", result.decision());
        assertTrue(result.blockingReasons().contains(requiredReason), () -> "missing reason " + requiredReason + ": " + result.blockingReasons());
        assertFalse(result.claimStrengtheningAllowed());
        assertFalse(result.externalEffectPerformed());
    }

    private Map<String,Object> positiveFacts(String dd) {
        if ("DD-041".equals(dd)) {
            return Map.of(
                    "payload_scope_policy", "policy:crypto-erasure:v1",
                    "expected_payload_copy_count", 5,
                    "accounted_payload_copy_count", 5,
                    "active_key_binding_count", 0,
                    "unresolved_retention_conflict_count", 0,
                    "unverified_erasure_count", 0);
        }
        Map<String,Object> facts = new LinkedHashMap<>();
        facts.put("claim_category", "SELF_REFERENTIAL_AI_SAFETY");
        facts.put("claim_author_lineage", "claim-author");
        facts.put("evidence_author_lineage", "evidence-author");
        facts.put("evaluator_lineage", "independent-evaluator");
        facts.put("oracle_lineage", "independent-oracle");
        facts.put("promotion_authority_lineage", "independent-promoter");
        facts.put("independence_provenance_verified", true);
        facts.put("material_shared_control_loop_detected", false);
        facts.put("independent_adversarial_evidence_count", 6);
        facts.put("required_adversarial_evidence_count", 6);
        facts.put("authority_oracle_current", true);
        return facts;
    }

    private Map<String,Object> negativeFacts(String dd) {
        if ("DD-041".equals(dd)) {
            return Map.of(
                    "payload_scope_policy", "policy:crypto-erasure:v1",
                    "expected_payload_copy_count", 5,
                    "accounted_payload_copy_count", 4,
                    "active_key_binding_count", 1,
                    "unresolved_retention_conflict_count", 1,
                    "unverified_erasure_count", 1);
        }
        Map<String,Object> facts = new LinkedHashMap<>(positiveFacts(dd));
        facts.put("claim_author_lineage", "same-control");
        facts.put("evidence_author_lineage", "same-control");
        facts.put("evaluator_lineage", "same-control");
        facts.put("oracle_lineage", "same-control");
        facts.put("promotion_authority_lineage", "same-control");
        facts.put("independence_provenance_verified", false);
        facts.put("material_shared_control_loop_detected", true);
        facts.put("independent_adversarial_evidence_count", 0);
        facts.put("authority_oracle_current", false);
        return facts;
    }

    private Map<String,Object> dd042ClaimEvidenceSelfSourcing() {
        Map<String,Object> facts = new LinkedHashMap<>(positiveFacts("DD-042"));
        facts.put("evidence_author_lineage", facts.get("claim_author_lineage"));
        return facts;
    }

    private Map<String,Object> dd042EvaluatorOracleCircularity() {
        Map<String,Object> facts = new LinkedHashMap<>(positiveFacts("DD-042"));
        facts.put("oracle_lineage", facts.get("evaluator_lineage"));
        return facts;
    }

    private Map<String,Object> dd042SelfPromotion() {
        Map<String,Object> facts = new LinkedHashMap<>(positiveFacts("DD-042"));
        facts.put("promotion_authority_lineage", facts.get("evaluator_lineage"));
        return facts;
    }

    private Map<String,Object> dd042UnverifiedIndependenceProvenance() {
        Map<String,Object> facts = new LinkedHashMap<>(positiveFacts("DD-042"));
        facts.put("independence_provenance_verified", false);
        return facts;
    }

    private Map<String,Object> dd042InsufficientIndependentEvidence() {
        Map<String,Object> facts = new LinkedHashMap<>(positiveFacts("DD-042"));
        facts.put("independent_adversarial_evidence_count", 5);
        return facts;
    }

    private Map<String,Object> dd042MaterialSharedControlLoop() {
        Map<String,Object> facts = new LinkedHashMap<>(positiveFacts("DD-042"));
        facts.put("material_shared_control_loop_detected", true);
        return facts;
    }

    private DdSemanticEvaluator.Evaluation evaluate(
            DdSemanticEvaluator evaluator, Map<String,Object> facts, boolean integrityVerified, boolean current) throws Exception {
        var document = JSON.valueToTree(facts);
        String ref = "fixture:" + evaluator.ddId() + ":" + integrityVerified + ":" + current + ":" + facts.hashCode();
        var resolver = DdEvidenceResolver.inMemory(Map.of(
                ref, new DdEvidenceResolver.ResolvedEvidence(
                        ref, "sha256:synthetic-successor-mechanics", document, integrityVerified, current, "authority:synthetic")));
        JsonNode request = JSON.readTree("{\"dd_id\":\"" + evaluator.ddId() + "\",\"evidence_refs\":[\"" + ref + "\"]}");
        var context = new DdSemanticEvaluator.EvaluationContext(
                "synthetic-successor-" + evaluator.ddId(), DesignGapDdSemanticEvaluators.VERSION,
                "SYNTHETIC_NOT_QUALIFICATION", "policy:synthetic", "authority:synthetic", resolver);
        return evaluator.evaluate(request, context);
    }
}
