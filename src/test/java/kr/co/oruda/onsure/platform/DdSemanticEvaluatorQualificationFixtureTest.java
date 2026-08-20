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

/** Synthetic mechanics only; never independent qualification. */
class DdSemanticEvaluatorQualificationFixtureTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TestFactory
    Stream<DynamicTest> exact160QualificationFixtureMechanics() {
        List<DynamicTest> tests = new ArrayList<>();
        for (DdSemanticEvaluator evaluator : BuiltInDdSemanticEvaluators.all()) {
            tests.add(DynamicTest.dynamicTest(evaluator.ddId() + " positive", () -> positive(evaluator)));
            tests.add(DynamicTest.dynamicTest(evaluator.ddId() + " negative", () -> negative(evaluator)));
            tests.add(DynamicTest.dynamicTest(evaluator.ddId() + " recovery", () -> recovery(evaluator)));
            tests.add(DynamicTest.dynamicTest(evaluator.ddId() + " adversarial", () -> adversarial(evaluator)));
        }
        assertEquals(160, tests.size());
        return tests.stream();
    }

    private void positive(DdSemanticEvaluator evaluator) throws Exception {
        var result = evaluate(evaluator, true, true, true);
        assertEquals("PASS_NONFINAL", result.decision());
        assertTrue(result.blockingReasons().isEmpty());
        assertTrue(result.claimStrengtheningAllowed());
        assertFalse(result.externalEffectPerformed());
    }

    private void negative(DdSemanticEvaluator evaluator) throws Exception {
        var result = evaluate(evaluator, false, true, true);
        assertEquals("HOLD", result.decision());
        assertTrue(result.blockingReasons().contains("DD_SEMANTIC_RULE_NOT_SATISFIED"));
        assertFalse(result.claimStrengtheningAllowed());
    }

    private void recovery(DdSemanticEvaluator evaluator) throws Exception {
        assertEquals("HOLD", evaluate(evaluator, false, true, true).decision());
        var recovered = evaluate(evaluator, true, true, true);
        assertEquals("PASS_NONFINAL", recovered.decision());
        assertFalse(recovered.externalEffectPerformed());
    }

    private void adversarial(DdSemanticEvaluator evaluator) throws Exception {
        var result = evaluate(evaluator, true, false, true);
        assertEquals("HOLD", result.decision());
        assertTrue(result.blockingReasons().stream().anyMatch(v -> v.startsWith("DD_EVIDENCE_INTEGRITY_UNVERIFIED")));
        assertFalse(result.claimStrengtheningAllowed());
    }

    private DdSemanticEvaluator.Evaluation evaluate(
            DdSemanticEvaluator evaluator, boolean positive, boolean integrityVerified, boolean current) throws Exception {
        var facts = BuiltInDdSemanticEvaluators.syntheticFacts(evaluator.ddId(), positive);
        var document = JSON.createObjectNode();
        document.set("facts", JSON.valueToTree(facts));
        String ref = "fixture:" + evaluator.ddId() + ":" + positive + ":" + integrityVerified + ":" + current;
        var resolver = DdEvidenceResolver.inMemory(Map.of(
                ref, new DdEvidenceResolver.ResolvedEvidence(
                        ref, "sha256:synthetic-mechanics", document, integrityVerified, current, "authority:synthetic")));
        JsonNode request = JSON.readTree("{\"dd_id\":\"" + evaluator.ddId() + "\",\"evidence_refs\":[\"" + ref + "\"]}");
        var context = new DdSemanticEvaluator.EvaluationContext(
                "synthetic-mechanics-" + evaluator.ddId(), BuiltInDdSemanticEvaluators.VERSION,
                "SYNTHETIC_NOT_QUALIFICATION", "policy:synthetic", "authority:synthetic", resolver);
        return evaluator.evaluate(request, context);
    }
}
