package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BuiltInDdSemanticEvaluatorsTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void materializesExactlyFortyDistinctLegacyBuiltInDdEvaluators() {
        var evaluators = BuiltInDdSemanticEvaluators.all();
        assertEquals(40, evaluators.size());
        assertEquals(40, evaluators.stream().map(DdSemanticEvaluator::ddId).distinct().count());
        assertEquals("DD-001", evaluators.get(0).ddId());
        assertEquals("DD-040", evaluators.get(39).ddId());
    }

    @Test
    void defaultRuntimeKeepsAllFortyTwoConcreteEvaluatorsUnqualifiedAndFailClosed() throws Exception {
        var runtime = new DdAssuranceOperationRuntime();
        assertEquals(42, runtime.operations().size());
        for (String operation : runtime.operations()) {
            String dd = runtime.ddIdFor(operation);
            var request = JSON.readTree("{\"dd_id\":\"" + dd + "\",\"evidence_refs\":[\"receipt:test\"]}");
            var result = runtime.execute(operation, request);
            assertEquals("HOLD", result.get("decision"));
            assertEquals("IMPLEMENTED_UNQUALIFIED", result.get("semantic_evaluator_state"));
            assertEquals(false, result.get("claim_strengthening_allowed"));
            assertEquals(false, result.get("external_effect_performed"));
            assertEquals(false, result.get("final_claim_allowed"));
        }
    }

    @Test
    void partialQualificationCannotBeRegistered() {
        var evaluator = BuiltInDdSemanticEvaluators.all().get(0);
        assertThrows(IllegalArgumentException.class, () -> new DdSemanticEvaluatorRegistry.Registration(
                evaluator, "test-evaluator", "v1", "sha256:test", true, false));
    }

    @Test
    void qualifiedMechanicsStillRequireTrustedResolvedEvidence() throws Exception {
        var evaluator = BuiltInDdSemanticEvaluators.all().get(0);
        var registration = new DdSemanticEvaluatorRegistry.Registration(
                evaluator, "test-evaluator", "v1", "sha256:test-qualification", true, true);
        var registry = new DdSemanticEvaluatorRegistry(List.of(registration));
        var runtime = new DdAssuranceOperationRuntime(registry, DdEvidenceResolver.rejecting());
        var request = JSON.readTree("{\"dd_id\":\"DD-001\",\"evidence_refs\":[\"receipt:missing\"]}");
        var result = runtime.execute("assurance.visibility-evidence.evaluate", request);
        assertEquals("HOLD", result.get("decision"));
        assertTrue(((List<?>) result.get("blocking_reasons")).stream()
                .anyMatch(v -> String.valueOf(v).startsWith("DD_EVIDENCE_UNRESOLVED")));
        assertFalse((Boolean) result.get("claim_strengthening_allowed"));
    }

    @Test
    void callerContextCannotSubstituteForEvidenceOracle() throws Exception {
        var evaluator = BuiltInDdSemanticEvaluators.all().get(0);
        var registration = new DdSemanticEvaluatorRegistry.Registration(
                evaluator, "test-evaluator", "v1", "sha256:test-qualification", true, true);
        var registry = new DdSemanticEvaluatorRegistry(List.of(registration));
        var runtime = new DdAssuranceOperationRuntime(registry, DdEvidenceResolver.rejecting());
        var request = JSON.readTree("""
                {"dd_id":"DD-001","evidence_refs":["receipt:missing"],
                 "context":{"visibility_profile":"fake","mandatory_dimensions_observable":true}}
                """);
        var result = runtime.execute("assurance.visibility-evidence.evaluate", request);
        assertEquals("HOLD", result.get("decision"));
    }

    @Test
    void integrityVerifiedCurrentEvidenceCanDriveNonfinalPositiveMechanics() throws Exception {
        var evaluator = BuiltInDdSemanticEvaluators.all().get(0);
        var registration = new DdSemanticEvaluatorRegistry.Registration(
                evaluator, "test-evaluator", "v1", "sha256:test-qualification", true, true);
        var registry = new DdSemanticEvaluatorRegistry(List.of(registration));
        var evidenceDocument = JSON.readTree("""
                {"facts":{"visibility_profile":"FULL","mandatory_dimensions":["a"],
                "observed_dimensions":["a"],"mandatory_dimensions_observable":true}}
                """);
        var resolver = DdEvidenceResolver.inMemory(Map.of(
                "receipt:verified", new DdEvidenceResolver.ResolvedEvidence(
                        "receipt:verified", "sha256:evidence", evidenceDocument, true, true, "authority:test")));
        var runtime = new DdAssuranceOperationRuntime(registry, resolver);
        var request = JSON.readTree("{\"dd_id\":\"DD-001\",\"evidence_refs\":[\"receipt:verified\"]}");
        var result = runtime.execute("assurance.visibility-evidence.evaluate", request);
        assertEquals("PASS_NONFINAL", result.get("decision"));
        assertEquals(false, result.get("final_claim_allowed"));
        assertEquals(false, result.get("external_effect_performed"));
    }
}
