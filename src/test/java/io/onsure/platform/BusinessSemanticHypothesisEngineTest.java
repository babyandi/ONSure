package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BusinessSemanticHypothesisEngineTest {
    private static final String SOURCE_SHA = "a".repeat(64);
    private static final String EVIDENCE_SHA = "b".repeat(64);

    @Test
    void deterministicallyInfersEvidenceBoundReviewOnlyCapabilityAndWorkflowHypotheses() {
        Map<String, Object> understanding = understanding(List.of(
                flow("FLOW-1111111111111111", "CREATE", "ORDER", "createOrder", "/orders"),
                flow("FLOW-2222222222222222", "READ", "ORDER", "getOrder", "/orders/{orderId}")),
                List.of(lifecycle("LIFECYCLE-1111111111111111", "ORDER",
                        List.of("CREATE", "READ"),
                        List.of("FLOW-1111111111111111", "FLOW-2222222222222222"))));
        List<Map<String, Object>> components = List.of(
                component("COMP-2", "OrderService.java", "src/main/OrderService.java"),
                component("COMP-1", "OrderController.java", "src/main/OrderController.java"));

        Map<String, Object> first = BusinessSemanticHypothesisEngine.infer(understanding, components);
        List<Map<String, Object>> reversed = new ArrayList<>(components);
        java.util.Collections.reverse(reversed);
        Map<String, Object> second = BusinessSemanticHypothesisEngine.infer(understanding, reversed);

        assertEquals(first, second);
        assertEquals(BusinessSemanticHypothesisEngine.CONTRACT, first.get("contract"));
        assertEquals(3, first.get("hypothesis_count"));
        assertEquals(0L, first.get("unknown_count"));
        assertEquals("NOT_RUN_REVIEW_REQUIRED", first.get("automatic_execution"));
        assertEquals("NOT_RUN", first.get("live_provider_invocation"));
        assertFalse((Boolean) first.get("customer_rules_confirmed"));
        assertFalse((Boolean) first.get("score_eligible"));

        Map<String, Object> create = hypotheses(first).stream()
                .filter(item -> "CREATE_ORDER".equals(item.get("semantic_label")))
                .findFirst().orElseThrow();
        assertEquals("CAPABILITY", create.get("hypothesis_kind"));
        assertEquals("INFERRED_REVIEW_REQUIRED", create.get("semantic_state"));
        assertEquals("LOW", create.get("ambiguity"));
        assertTrue(((Number) create.get("confidence")).doubleValue() >= 0.8);
        assertSafetyBoundary(create);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> evidence =
                (List<Map<String, Object>>) create.get("evidence_references");
        assertTrue(evidence.stream().anyMatch(item ->
                "OPENAPI_OPERATION_ID".equals(item.get("evidence_type"))
                        && "createOrder".equals(item.get("observed_value"))));
        assertTrue(evidence.stream().anyMatch(item ->
                "OPENAPI_TAG".equals(item.get("evidence_type"))
                        && "Orders".equals(item.get("observed_value"))));
        assertTrue(evidence.stream().anyMatch(item ->
                "OPENAPI_PATH".equals(item.get("evidence_type"))
                        && "/orders".equals(item.get("observed_value"))));
        assertEquals(2, evidence.stream()
                .filter(item -> "DETECTED_COMPONENT".equals(item.get("evidence_type"))).count());
        assertTrue(evidence.stream().allMatch(item ->
                item.get("evidence_sha256").toString().matches("[0-9a-f]{64}")));

        Map<String, Object> workflow = hypotheses(first).stream()
                .filter(item -> "WORKFLOW".equals(item.get("hypothesis_kind")))
                .findFirst().orElseThrow();
        assertEquals("ORDER_LIFECYCLE", workflow.get("semantic_label"));
        assertEquals("MEDIUM", workflow.get("ambiguity"));
        assertSafetyBoundary(workflow);
    }

    @Test
    void leavesUnsupportedBusinessMeaningUnknownAndIneligible() {
        Map<String, Object> generic = new LinkedHashMap<>();
        generic.put("flow_id", "FLOW-3333333333333333");
        generic.put("inferred_business_object", "UNKNOWN_OBJECT_REVIEW_REQUIRED");
        generic.put("evidence_sha256", EVIDENCE_SHA);
        Map<String, Object> result = BusinessSemanticHypothesisEngine.infer(
                understanding(List.of(Map.copyOf(generic)), List.of()),
                List.of(component("COMP-9", "Main.java", "src/Main.java")));

        Map<String, Object> unknown = hypotheses(result).get(0);
        assertEquals("UNKNOWN", unknown.get("semantic_label"));
        assertEquals("UNKNOWN", unknown.get("semantic_state"));
        assertEquals("UNKNOWN", unknown.get("business_object"));
        assertEquals("UNKNOWN", unknown.get("action"));
        assertEquals("HIGH", unknown.get("ambiguity"));
        assertEquals(0.0, unknown.get("confidence"));
        assertEquals(1L, result.get("unknown_count"));
        assertSafetyBoundary(unknown);
    }

    @Test
    void rejectsUnboundOrMalformedUnderstandingInput() {
        assertThrows(IllegalArgumentException.class,
                () -> BusinessSemanticHypothesisEngine.infer(Map.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> BusinessSemanticHypothesisEngine.infer(
                Map.of("contract", ProgramUnderstandingEngine.CONTRACT, "source_sha256", "not-a-digest"),
                List.of()));
    }

    private static void assertSafetyBoundary(Map<String, Object> hypothesis) {
        assertTrue((Boolean) hypothesis.get("review_required"));
        assertFalse((Boolean) hypothesis.get("auto_execute"));
        assertFalse((Boolean) hypothesis.get("customer_rule_confirmed"));
        assertFalse((Boolean) hypothesis.get("runtime_verified"));
        assertFalse((Boolean) hypothesis.get("score_eligible"));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> hypotheses(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("hypotheses");
    }

    private static Map<String, Object> understanding(
            List<Map<String, Object>> flows, List<Map<String, Object>> lifecycles) {
        return Map.of(
                "contract", ProgramUnderstandingEngine.CONTRACT,
                "source_sha256", SOURCE_SHA,
                "flow_candidates", flows,
                "api_lifecycle_candidates", lifecycles);
    }

    private static Map<String, Object> flow(
            String id, String action, String object, String operationId, String path) {
        return Map.of(
                "flow_id", id,
                "inferred_business_object", object,
                "evidence_sha256", EVIDENCE_SHA,
                "operation", Map.of(
                        "operation_id", operationId,
                        "tags", List.of("Orders"),
                        "http_path", path,
                        "lifecycle_action", action,
                        "request_schema_refs", List.of("#/components/schemas/OrderRequest")));
    }

    private static Map<String, Object> lifecycle(
            String id, String object, List<String> actions, List<String> flowIds) {
        return Map.of(
                "lifecycle_id", id,
                "business_object", object,
                "actions", actions,
                "operations", flowIds.stream().map(flowId -> Map.<String, Object>of(
                        "flow_id", flowId, "state", "PROPOSED_NOT_RUN")).toList());
    }

    private static Map<String, Object> component(String id, String name, String path) {
        return Map.of(
                "id", id,
                "name", name,
                "kind", "SERVICE",
                "source_locations", List.of(path),
                "confidence", 0.75,
                "verified", false);
    }
}
