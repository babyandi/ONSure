package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
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

    @ParameterizedTest
    @MethodSource("ddOperations")
    void allDdOperationsReachRealFailClosedWorkflowBoundary(String operation) throws Exception {
        ObjectNode request = MAPPER.createObjectNode();
        request.putArray("evidence_refs");
        Map<String, Object> envelope = new PostFinalTargetWorkflowDispatcher(
                temp, AuthenticatedWorkflowIdentity.localAdministrator()).dispatch(operation, request);

        assertEquals(PostFinalTargetWorkflowDispatcher.CONTRACT, envelope.get("contract"));
        assertEquals("POST_FINAL_TARGET_DD_FAIL_CLOSED", envelope.get("route"));
        assertEquals(false, envelope.get("final_claim_allowed"));
        assertEquals("NOT_QUALIFIED", envelope.get("semantic_completion"));
        assertFailClosedResult(envelope);
    }

    @ParameterizedTest
    @MethodSource("ddOperations")
    void sharedSemanticBridgeUsedByLocalApiAlsoRoutesAllDdOperationsFailClosed(String operation) throws Exception {
        ObjectNode request = MAPPER.createObjectNode();
        request.putArray("evidence_refs");
        Map<String, Object> envelope = new SemanticAssuranceV2DispatcherBridge(
                temp, AuthenticatedWorkflowIdentity.localAdministrator()).dispatch(operation, request);

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
        assertEquals("SEMANTIC_EVALUATOR_NOT_QUALIFIED", result.get("blocking_reason"));
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
    void mismatchedDdIdentityIsRejected() {
        ObjectNode request = MAPPER.createObjectNode();
        request.put("dd_id", "DD-040");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new DdAssuranceOperationRuntime()
                        .execute("assurance.visibility-evidence.evaluate", request));
        assertTrue(error.getMessage().contains("DD_OPERATION_ID_MISMATCH"));
    }

    @Test
    void evidenceRefsMustBeArray() {
        ObjectNode request = MAPPER.createObjectNode();
        request.put("evidence_refs", "spoofed");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new DdAssuranceOperationRuntime()
                        .execute("assurance.visibility-evidence.evaluate", request));
        assertTrue(error.getMessage().contains("DD_EVIDENCE_REFS_ARRAY_REQUIRED"));
    }
}
