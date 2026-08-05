package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProgramUnderstandingEngineTest {
    @TempDir Path temp;

    @Test
    void infersReviewOnlyFlowAndMinimumQuestionsWithoutInventingPass() throws Exception {
        Files.writeString(temp.resolve("openapi.yaml"), """
                openapi: 3.1.0
                info: {title: Orders, version: '1'}
                paths:
                  /orders:
                    post:
                      operationId: createOrder
                      tags: [Orders]
                      requestBody:
                        content:
                          application/json:
                            schema: {$ref: '#/components/schemas/CreateOrder'}
                      responses:
                        '200':
                          description: ok
                          content:
                            application/json:
                              schema:
                                type: object
                                properties:
                                  data:
                                    type: object
                                    required: [orderId, queryToken, traceId, metadata]
                                    properties:
                                      orderId: {type: string}
                                      queryToken: {type: string}
                                      traceId: {type: string}
                                      metadata:
                                        type: object
                                        required: [revision]
                                        properties:
                                          revision: {type: integer}
                    get:
                      operationId: listOrders
                      tags: [Orders]
                      responses: {'200': {description: ok}}
                  /orders/{orderId}:
                    get:
                      operationId: getOrder
                      tags: [Orders]
                      parameters:
                        - {in: query, name: queryToken, required: true, schema: {type: string}}
                        - {in: query, name: optionalToken, required: false, schema: {type: string}}
                        - {in: header, name: traceId, required: true, schema: {type: string}}
                        - {in: header, name: Authorization, required: true, schema: {type: string}}
                      responses: {'200': {description: found}}
                    patch:
                      operationId: updateOrder
                      tags: [Orders]
                      requestBody:
                        required: true
                        content:
                          application/json:
                            schema:
                              type: object
                              required: [metadata]
                              properties:
                                metadata:
                                  type: object
                                  required: [revision]
                                  properties:
                                    revision: {type: integer}
                                    note: {type: string}
                      responses: {'200': {description: updated}}
                    delete:
                      operationId: deleteOrder
                      tags: [Orders]
                      responses: {'204': {description: deleted}}
                """);
        Files.createDirectories(temp.resolve("tests"));
        Files.writeString(temp.resolve("tests/test_order.py"), "def test_order():\n    assert True\n");

        Map<String, Object> inventory = StaticWorkflowInventory.detect(temp);
        Map<String, Object> result = ProgramUnderstandingEngine.infer(inventory, Hashing.tree(temp));

        assertEquals(ProgramUnderstandingEngine.CONTRACT, result.get("contract"));
        assertTrue(((Number) result.get("flow_candidate_count")).intValue() >= 1);
        assertFalse((Boolean) result.get("inferences_are_pass_evidence"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> flows = (List<Map<String, Object>>) result.get("flow_candidates");
        assertTrue(flows.stream().allMatch(flow -> "INFERRED_REVIEW_REQUIRED".equals(flow.get("semantic_state"))));
        assertTrue(flows.stream().allMatch(flow -> Boolean.FALSE.equals(flow.get("score_eligible"))));
        assertTrue(flows.stream().anyMatch(flow -> "ORDER".equals(flow.get("inferred_business_object"))));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lifecycles = (List<Map<String, Object>>) result.get("api_lifecycle_candidates");
        assertEquals(1, lifecycles.size());
        assertEquals(List.of("CREATE", "READ", "UPDATE", "DELETE"), lifecycles.get(0).get("actions"));
        assertEquals("CRUD_CANDIDATE_COMPLETE", lifecycles.get(0).get("coverage_state"));
        assertEquals(6, lifecycles.get(0).get("binding_count"));
        @SuppressWarnings("unchecked") List<Map<String, Object>> bindings =
                (List<Map<String, Object>>) lifecycles.get(0).get("proposed_bindings");
        Map<String, Object> readBinding = bindings.stream()
                .filter(binding -> binding.get("consumer_flow_id").toString().equals(
                        flows.stream().filter(flow -> "getOrder".equals(flow.get("name")))
                                .findFirst().orElseThrow().get("flow_id")))
                .findFirst().orElseThrow();
        assertEquals("/data/orderId", readBinding.get("producer_json_pointer"));
        assertEquals("OPENAPI_RESPONSE_SCHEMA_EXACT_PROPERTY", readBinding.get("inference_basis"));
        assertEquals(0.93, readBinding.get("inference_confidence"));
        assertEquals("orderId", readBinding.get("consumer_parameter_name"));
        assertEquals("INFERRED_REVIEW_REQUIRED", readBinding.get("semantic_state"));
        assertEquals(false, readBinding.get("auto_execute"));
        assertEquals(false, readBinding.get("value_storage_allowed"));
        assertTrue(bindings.stream().allMatch(binding -> "INFERRED_REVIEW_REQUIRED".equals(
                binding.get("semantic_state"))));
        assertTrue(bindings.stream().allMatch(binding -> Boolean.FALSE.equals(binding.get("runtime_verified"))
                && Boolean.FALSE.equals(binding.get("auto_execute"))
                && Boolean.FALSE.equals(binding.get("value_storage_allowed"))
                && Boolean.FALSE.equals(binding.get("score_eligible"))));
        assertTrue(bindings.stream().anyMatch(binding -> "QUERY".equals(binding.get("consumer_location"))
                && "queryToken".equals(binding.get("consumer_parameter_name"))
                && "/data/queryToken".equals(binding.get("producer_json_pointer"))));
        assertTrue(bindings.stream().anyMatch(binding -> "HEADER".equals(binding.get("consumer_location"))
                && "traceId".equals(binding.get("consumer_parameter_name"))
                && "/data/traceId".equals(binding.get("producer_json_pointer"))));
        assertTrue(bindings.stream().anyMatch(binding -> "BODY".equals(binding.get("consumer_location"))
                && "/metadata/revision".equals(binding.get("consumer_parameter_name"))
                && "/data/metadata/revision".equals(binding.get("producer_json_pointer"))));
        assertFalse(bindings.stream().anyMatch(binding -> "optionalToken".equals(binding.get("consumer_parameter_name"))));
        assertFalse(bindings.stream().anyMatch(binding -> "Authorization".equals(binding.get("consumer_parameter_name"))));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> questions = (List<Map<String, Object>>) result.get("minimal_questions");
        assertTrue(questions.stream().anyMatch(question -> "RUNTIME_ENDPOINT".equals(question.get("question_id"))));
        assertTrue(questions.stream().anyMatch(question ->
                "UNAUTHENTICATED_API_BOUNDARY".equals(question.get("question_id"))));
        assertTrue(questions.stream().anyMatch(question -> "DESTRUCTIVE_TEST_BOUNDARY".equals(question.get("question_id"))));
        assertTrue(questions.stream().anyMatch(question ->
                "LIFECYCLE_BINDING_REVIEW".equals(question.get("question_id"))));
        assertEquals(List.of("OPENAPI_SECURITY_UNDECLARED", "DESTRUCTIVE_API_DISCOVERED"),
                result.get("risk_flags"));
    }
}
