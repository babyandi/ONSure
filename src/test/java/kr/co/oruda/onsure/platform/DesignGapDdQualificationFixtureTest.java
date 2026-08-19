package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** Eight successor synthetic mechanics cases; never independent qualification. */
class DesignGapDdQualificationFixtureTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TestFactory
    Stream<DynamicTest> exact8DesignGapQualificationFixtureMechanics() {
        List<DynamicTest> tests = new ArrayList<>();
        for (DdSemanticEvaluator evaluator : DesignGapDdSemanticEvaluators.all()) {
            tests.add(DynamicTest.dynamicTest(evaluator.ddId() + " positive", () -> positive(evaluator)));
            tests.add(DynamicTest.dynamicTest(evaluator.ddId() + " negative", () -> negative(evaluator)));
            tests.add(DynamicTest.dynamicTest(evaluator.ddId() + " recovery", () -> recovery(evaluator)));
            tests.add(DynamicTest.dynamicTest(evaluator.ddId() + " adversarial", () -> adversarial(evaluator)));
        }
        assertEquals(8, tests.size());
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

    private void adversarial(DdSemanticEvaluator evaluator) throws Exception {
        var result = evaluate(evaluator, positiveFacts(evaluator.ddId()), false, true);
        assertEquals("HOLD", result.decision());
        assertTrue(result.blockingReasons().stream().anyMatch(v -> v.startsWith("EVIDENCE_INTEGRITY_NOT_VERIFIED")));
        assertFalse(result.claimStrengtheningAllowed());
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
        return Map.ofEntries(
                Map.entry("claim_category", "SELF_REFERENTIAL_AI_SAFETY"),
                Map.entry("claim_author_lineage", "claim-author"),
                Map.entry("evidence_author_lineage", "evidence-author"),
                Map.entry("evaluator_lineage", "independent-evaluator"),
                Map.entry("oracle_lineage", "independent-oracle"),
                Map.entry("promotion_authority_lineage", "independent-promoter"),
                Map.entry("independent_adversarial_evidence_count", 4),
                Map.entry("required_adversarial_evidence_count", 4),
                Map.entry("authority_oracle_current", true));
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
        return Map.ofEntries(
                Map.entry("claim_category", "SELF_REFERENTIAL_AI_SAFETY"),
                Map.entry("claim_author_lineage", "same-control"),
                Map.entry("evidence_author_lineage", "same-control"),
                Map.entry("evaluator_lineage", "same-control"),
                Map.entry("oracle_lineage", "same-control"),
                Map.entry("promotion_authority_lineage", "same-control"),
                Map.entry("independent_adversarial_evidence_count", 0),
                Map.entry("required_adversarial_evidence_count", 4),
                Map.entry("authority_oracle_current", false));
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
