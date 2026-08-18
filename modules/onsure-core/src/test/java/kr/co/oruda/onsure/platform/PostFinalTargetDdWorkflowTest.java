package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class PostFinalTargetDdWorkflowTest {
    @TempDir Path temp;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static Stream<String> ddOperations() {
        return new DdAssuranceOperationRuntime().operations().stream().sorted();
    }

    private static ObjectNode validRequest(String operation) {
        DdAssuranceOperationRuntime runtime = new DdAssuranceOperationRuntime();
        ObjectNode request = MAPPER.createObjectNode();
        request.put("dd_id", runtime.ddIdFor(operation));
        request.putArray("evidence_refs");
        return request;
    }

    @ParameterizedTest
    @MethodSource("ddOperations")
    void allDdOperationsReachRealFailClosedWorkflowBoundary(String operation) throws Exception {
        Map<String, Object> envelope = new PostFinalTargetWorkflowDispatcher(
                temp, AuthenticatedWorkflowIdentity.localAdministrator()).dispatch(operation, validRequest(operation));

        assertEquals(PostFinalTargetWorkflowDispatcher.CONTRACT, envelope.get("contract"));
        assertEquals("POST_FINAL_TARGET_DD_FAIL_CLOSED", envelope.get("route"));
        assertEquals(false, envelope.get("final_claim_allowed"));
        assertEquals("NOT_QUALIFIED", envelope.get("semantic_completion"));
        assertFailClosedResult(envelope);
    }

    @ParameterizedTest
    @MethodSource("ddOperations")
    void sharedSemanticBridgeUsedByLocalApiAlsoRoutesAllDdOperationsFailClosed(String operation) throws Exception {
        Map<String, Object> envelope = new SemanticAssuranceV2DispatcherBridge(
                temp, AuthenticatedWorkflowIdentity.localAdministrator()).dispatch(operation, validRequest(operation));

        assertEquals(SemanticAssuranceV2DispatcherBridge.CONTRACT, envelope.get("contract"));
        assertEquals("POST_FINAL_TARGET_DD_FAIL_CLOSED", envelope.get("route"));
        assertEquals(false, envelope.get("final_claim_allowed"));
        assertEquals("NOT_QUALIFIED", envelope.get("semantic_completion"));
        assertFailClosedResult(envelope);
    }

    private static void assertFailClosedResult(Map<String, Object> envelope) {
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) envelope.get("result");
        assertEquals("HOLD", result.get("decision"));
        assertEquals(List.of("SEMANTIC_EVALUATOR_NOT_QUALIFIED"), result.get("blocking_reasons"));
        assertEquals(false, result.get("claim_strengthening_allowed"));
        assertEquals(false, result.get("external_effect_performed"));
        assertEquals(false, result.get("final_claim_allowed"));
    }

    @Test
    void runtimeDenominatorIsExactlyFortyUniqueOperations() {
        var operations = new DdAssuranceOperationRuntime().operations();
        assertEquals(40, operations.size());
        assertEquals(40, operations.stream().distinct().count());
    }

    @Test
    void ddIdIsRequired() {
        ObjectNode request = MAPPER.createObjectNode();
        request.putArray("evidence_refs");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new DdAssuranceOperationRuntime().execute("assurance.visibility-evidence.evaluate", request));
        assertTrue(error.getMessage().contains("DD_ID_REQUIRED"));
    }

    @Test
    void mismatchedDdIdentityIsRejected() {
        ObjectNode request = MAPPER.createObjectNode();
        request.put("dd_id", "DD-040");
        request.putArray("evidence_refs");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new DdAssuranceOperationRuntime().execute("assurance.visibility-evidence.evaluate", request));
        assertTrue(error.getMessage().contains("DD_OPERATION_ID_MISMATCH"));
    }

    @Test
    void evidenceRefsAreRequiredAndMustBeArray() {
        ObjectNode missing = MAPPER.createObjectNode();
        missing.put("dd_id", "DD-001");
        IllegalArgumentException missingError = assertThrows(IllegalArgumentException.class,
                () -> new DdAssuranceOperationRuntime().execute("assurance.visibility-evidence.evaluate", missing));
        assertTrue(missingError.getMessage().contains("DD_EVIDENCE_REFS_ARRAY_REQUIRED"));

        ObjectNode invalid = MAPPER.createObjectNode();
        invalid.put("dd_id", "DD-001");
        invalid.put("evidence_refs", "spoofed");
        IllegalArgumentException invalidError = assertThrows(IllegalArgumentException.class,
                () -> new DdAssuranceOperationRuntime().execute("assurance.visibility-evidence.evaluate", invalid));
        assertTrue(invalidError.getMessage().contains("DD_EVIDENCE_REFS_ARRAY_REQUIRED"));
    }

    @Test
    void duplicateEvidenceRefsAreRejected() {
        ObjectNode request = validRequest("assurance.visibility-evidence.evaluate");
        request.withArray("evidence_refs").add("evidence://same").add("evidence://same");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new DdAssuranceOperationRuntime().execute("assurance.visibility-evidence.evaluate", request));
        assertTrue(error.getMessage().contains("DD_EVIDENCE_REF_DUPLICATE"));
    }

    @Test
    void callerCannotInjectAuthorityOrDecisionFields() {
        for (String field : List.of("final_claim_allowed", "decision_override", "_authorized_target_root")) {
            ObjectNode request = validRequest("assurance.visibility-evidence.evaluate");
            request.put(field, true);
            SecurityException error = assertThrows(SecurityException.class,
                    () -> new DdAssuranceOperationRuntime().execute("assurance.visibility-evidence.evaluate", request));
            assertTrue(error.getMessage().contains("DD_CALLER_AUTHORITY_FIELD_PROHIBITED"));
        }
    }
}
