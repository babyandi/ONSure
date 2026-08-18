package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Synthetic mechanics fixtures for the exact DD-001..040 x four qualification classes.
 *
 * <p>These tests do NOT constitute independent evaluator qualification. They only prove that the
 * built-in implementation can be exercised fail-closed against the planned fixture classes.</p>
 */
class DdSemanticEvaluatorQualificationFixtureTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TestFactory
    Stream<DynamicTest> exact160QualificationFixtureMechanics() {
        List<DynamicTest> tests = new ArrayList<>();
        for (DdSemanticEvaluator evaluator : BuiltInDdSemanticEvaluators.all()) {
            MachineSpec spec = spec(evaluator);
            tests.add(DynamicTest.dynamicTest(evaluator.ddId() + " positive", () -> positive(evaluator, spec)));
            tests.add(DynamicTest.dynamicTest(evaluator.ddId() + " negative", () -> negative(evaluator, spec)));
            tests.add(DynamicTest.dynamicTest(evaluator.ddId() + " recovery", () -> recovery(evaluator, spec)));
            tests.add(DynamicTest.dynamicTest(evaluator.ddId() + " adversarial", () -> adversarial(evaluator, spec)));
        }
        assertEquals(160, tests.size());
        return tests.stream();
    }

    private void positive(DdSemanticEvaluator evaluator, MachineSpec spec) throws Exception {
        var result = evaluate(evaluator, spec, true, true, true);
        assertEquals("PASS_NONFINAL", result.decision());
        assertTrue(result.blockingReasons().isEmpty());
        assertTrue(result.claimStrengtheningAllowed());
        assertFalse(result.externalEffectPerformed());
    }

    private void negative(DdSemanticEvaluator evaluator, MachineSpec spec) throws Exception {
        var result = evaluate(evaluator, spec, false, true, true);
        assertEquals("HOLD", result.decision());
        assertTrue(result.blockingReasons().contains("DD_SAFE_FLOOR_NOT_SATISFIED"));
        assertFalse(result.claimStrengtheningAllowed());
    }

    private void recovery(DdSemanticEvaluator evaluator, MachineSpec spec) throws Exception {
        var failed = evaluate(evaluator, spec, false, true, true);
        assertEquals("HOLD", failed.decision());
        var recovered = evaluate(evaluator, spec, true, true, true);
        assertEquals("PASS_NONFINAL", recovered.decision());
        assertFalse(recovered.externalEffectPerformed());
    }

    private void adversarial(DdSemanticEvaluator evaluator, MachineSpec spec) throws Exception {
        var result = evaluate(evaluator, spec, true, false, true);
        assertEquals("HOLD", result.decision());
        assertTrue(result.blockingReasons().stream().anyMatch(v -> v.startsWith("DD_EVIDENCE_INTEGRITY_UNVERIFIED")));
        assertFalse(result.claimStrengtheningAllowed());
    }

    private DdSemanticEvaluator.Evaluation evaluate(
            DdSemanticEvaluator evaluator,
            MachineSpec spec,
            boolean positive,
            boolean integrityVerified,
            boolean current) throws Exception {
        ObjectNode facts = JSON.createObjectNode();
        for (String key : spec.requiredFacts()) facts.put(key, "present");
        facts.put(spec.passFact(), positive);
        ObjectNode document = JSON.createObjectNode();
        document.set("facts", facts);
        String ref = "fixture:" + evaluator.ddId() + ":" + positive + ":" + integrityVerified + ":" + current;
        var resolver = DdEvidenceResolver.inMemory(Map.of(
                ref, new DdEvidenceResolver.ResolvedEvidence(
                        ref, "sha256:synthetic-mechanics", document, integrityVerified, current, "authority:synthetic")));
        JsonNode request = JSON.readTree("{\"dd_id\":\"" + evaluator.ddId() + "\",\"evidence_refs\":[\"" + ref + "\"]}");
        var context = new DdSemanticEvaluator.EvaluationContext(
                "synthetic-mechanics-" + evaluator.ddId(),
                BuiltInDdSemanticEvaluators.VERSION,
                "SYNTHETIC_NOT_QUALIFICATION",
                "policy:synthetic",
                "authority:synthetic",
                resolver);
        return evaluator.evaluate(request, context);
    }

    /** Read the private immutable evaluator spec only to avoid duplicating the production 40-row denominator in test code. */
    @SuppressWarnings("unchecked")
    private MachineSpec spec(DdSemanticEvaluator evaluator) {
        try {
            Field field = evaluator.getClass().getDeclaredField("spec");
            field.setAccessible(true);
            Object spec = field.get(evaluator);
            Method required = spec.getClass().getDeclaredMethod("requiredFacts");
            Method pass = spec.getClass().getDeclaredMethod("passFact");
            required.setAccessible(true);
            pass.setAccessible(true);
            return new MachineSpec((List<String>) required.invoke(spec), (String) pass.invoke(spec));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("DD_TEST_SPEC_ACCESS_FAILED", e);
        }
    }

    private record MachineSpec(List<String> requiredFacts, String passFact) {}
}
