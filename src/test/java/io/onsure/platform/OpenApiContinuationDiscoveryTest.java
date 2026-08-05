package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenApiContinuationDiscoveryTest {
    @TempDir Path temp;

    @Test
    void discoversFailClosedPaginationWithStableDocumentBoundary() throws Exception {
        Files.createDirectories(temp.resolve("contracts/openapi"));
        Path first = temp.resolve("contracts/openapi/items.yaml");
        Files.writeString(first, paginationApi("Items"));
        Map<String, Object> firstRun = operation(StaticWorkflowInventory.detect(temp), "/items");
        Map<String, Object> secondRun = operation(StaticWorkflowInventory.detect(temp), "/items");

        assertEquals(firstRun.get("openapi_document_id"), secondRun.get("openapi_document_id"));
        assertEquals(firstRun.get("service_boundary_id"), secondRun.get("service_boundary_id"));
        assertEquals("DOCUMENT_SCOPED_REVIEW_REQUIRED", firstRun.get("service_boundary_state"));

        @SuppressWarnings("unchecked") List<Map<String, Object>> inputs =
                (List<Map<String, Object>>) firstRun.get("request_input_candidates");
        Map<String, Object> cursor = inputs.stream()
                .filter(input -> "cursor".equals(input.get("consumer_parameter_name"))).findFirst().orElseThrow();
        assertEquals(false, cursor.get("required"));
        assertEquals("CURSOR", cursor.get("continuation_role"));
        assertEquals(true, cursor.get("discovery_only"));
        assertEquals(false, cursor.get("auto_bind"));
        assertFalse(inputs.stream().anyMatch(input -> "filter".equals(input.get("consumer_parameter_name"))));

        @SuppressWarnings("unchecked") List<Map<String, Object>> headers =
                (List<Map<String, Object>>) firstRun.get("response_header_candidates");
        assertEquals(List.of("Link", "X-Next-Cursor"), headers.stream()
                .map(header -> header.get("header_name").toString()).toList());
        assertTrue(headers.stream().allMatch(header -> "NOT_RUN".equals(header.get("runtime_state"))
                && Boolean.FALSE.equals(header.get("value_storage_allowed"))));

        Map<String, Object> continuation = continuation(firstRun, "PAGINATION");
        assertEquals("BODY_POINTER", continuation.get("producer_location"));
        assertEquals("/nextCursor", continuation.get("producer_reference"));
        assertEquals("QUERY", continuation.get("consumer_location"));
        assertEquals("cursor", continuation.get("consumer_reference"));
        assertEquals("listItems", continuation.get("source_operation_reference"));
        assertEquals(100, continuation.get("max_iterations"));
        assertEquals(60, continuation.get("max_duration_seconds"));
        assertEquals("STOP_ON_ABSENT_NULL_EMPTY_OR_REPEATED_TOKEN", continuation.get("termination_policy"));
        assertFailClosed(continuation);

        Path second = temp.resolve("contracts/openapi/items-admin.yaml");
        Files.writeString(second, paginationApi("Items Admin"));
        @SuppressWarnings("unchecked") List<Map<String, Object>> operations =
                (List<Map<String, Object>>) StaticWorkflowInventory.detect(temp).get("candidates");
        List<Map<String, Object>> itemOperations = operations.stream()
                .filter(value -> "OPENAPI_OPERATION".equals(value.get("kind"))).toList();
        assertEquals(2, itemOperations.size());
        assertNotEquals(itemOperations.get(0).get("openapi_document_id"),
                itemOperations.get(1).get("openapi_document_id"));
        assertNotEquals(itemOperations.get(0).get("service_boundary_id"),
                itemOperations.get(1).get("service_boundary_id"));
        itemOperations.forEach(value -> ((List<Map<String, Object>>) value.get("continuation_candidates"))
                .forEach(candidate -> assertEquals(value.get("service_boundary_id"),
                        candidate.get("service_boundary_id"))));
    }

    @Test
    void discoversAsyncPollingEvidenceWithoutResolvingOrExecutingTarget() throws Exception {
        Files.writeString(temp.resolve("openapi.yaml"), """
                openapi: 3.1.0
                paths:
                  /jobs:
                    post:
                      operationId: startJob
                      responses:
                        '202':
                          description: accepted
                          headers:
                            Location: {schema: {type: string}}
                            Retry-After: {schema: {type: integer}}
                            Set-Cookie: {schema: {type: string}}
                          content:
                            application/json:
                              schema:
                                type: object
                                properties:
                                  jobId: {type: string}
                """);
        Map<String, Object> operation = operation(StaticWorkflowInventory.detect(temp), "/jobs");
        @SuppressWarnings("unchecked") List<Map<String, Object>> headers =
                (List<Map<String, Object>>) operation.get("response_header_candidates");
        assertEquals(List.of("Location", "Retry-After"), headers.stream()
                .map(header -> header.get("header_name").toString()).toList());
        assertFalse(headers.toString().contains("Set-Cookie"));

        Map<String, Object> continuation = continuation(operation, "ASYNC_POLL");
        assertEquals("RESPONSE_HEADER", continuation.get("producer_location"));
        assertEquals("Location", continuation.get("producer_reference"));
        assertEquals("UNRESOLVED", continuation.get("consumer_location"));
        assertEquals("NOT_RESOLVED", continuation.get("consumer_reference"));
        assertEquals("startJob", continuation.get("source_operation_reference"));
        assertEquals(60, continuation.get("max_iterations"));
        assertEquals(300, continuation.get("max_duration_seconds"));
        assertEquals(1_000, continuation.get("initial_delay_millis"));
        assertEquals(10_000, continuation.get("max_delay_millis"));
        assertEquals("REVIEWED_TERMINAL_STATUS_SET_REQUIRED", continuation.get("termination_policy"));
        assertEquals("NOT_RUN_REVIEW_REQUIRED", continuation.get("target_resolution_state"));
        assertFailClosed(continuation);
    }

    @Test
    void neverCreatesLifecycleBindingsAcrossOpenApiDocumentBoundaries() throws Exception {
        Files.createDirectories(temp.resolve("contracts/openapi"));
        Files.writeString(temp.resolve("contracts/openapi/producer.yaml"), """
                openapi: 3.1.0
                info: {title: Producer, version: '1'}
                paths:
                  /orders:
                    post:
                      operationId: createOrder
                      tags: [Orders]
                      responses:
                        '201':
                          description: created
                          content:
                            application/json:
                              schema: {type: object, properties: {orderId: {type: string}}}
                """);
        Files.writeString(temp.resolve("contracts/openapi/consumer.yaml"), """
                openapi: 3.1.0
                info: {title: Consumer, version: '1'}
                paths:
                  /orders/{orderId}:
                    parameters:
                      - {in: path, name: orderId, required: true, schema: {type: string}}
                    get:
                      operationId: getOrder
                      tags: [Orders]
                      responses: {'200': {description: ok}}
                """);
        Map<String, Object> inventory = StaticWorkflowInventory.detect(temp);
        Map<String, Object> understanding = ProgramUnderstandingEngine.infer(inventory, Hashing.tree(temp));
        @SuppressWarnings("unchecked") List<Map<String, Object>> lifecycles =
                (List<Map<String, Object>>) understanding.get("api_lifecycle_candidates");

        assertEquals(2, lifecycles.size());
        assertTrue(lifecycles.stream().allMatch(lifecycle ->
                ((Number) lifecycle.get("binding_count")).intValue() == 0));
        assertEquals(2, lifecycles.stream().map(lifecycle -> lifecycle.get("service_boundary_id"))
                .distinct().count());
        assertTrue(lifecycles.stream().allMatch(lifecycle ->
                Boolean.TRUE.equals(lifecycle.get("same_service_only"))
                        && Boolean.FALSE.equals(lifecycle.get("cross_service_auto_binding_allowed"))));
    }

    private static void assertFailClosed(Map<String, Object> continuation) {
        assertEquals(true, continuation.get("same_service_only"));
        assertEquals(false, continuation.get("cross_service_auto_binding_allowed"));
        assertEquals(true, continuation.get("review_required"));
        assertEquals("NOT_RUN", continuation.get("runtime_state"));
        assertEquals(false, continuation.get("auto_execute"));
        assertEquals(false, continuation.get("score_eligible"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> operation(Map<String, Object> inventory, String path) {
        return ((List<Map<String, Object>>) inventory.get("candidates")).stream()
                .filter(value -> path.equals(value.get("http_path"))).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> continuation(Map<String, Object> operation, String kind) {
        return ((List<Map<String, Object>>) operation.get("continuation_candidates")).stream()
                .filter(value -> kind.equals(value.get("kind"))).findFirst().orElseThrow();
    }

    private static String paginationApi(String title) {
        return """
                openapi: 3.1.0
                info: {title: %s, version: '1'}
                paths:
                  /items:
                    get:
                      operationId: listItems
                      parameters:
                        - {in: query, name: cursor, required: false, schema: {type: string}}
                        - {in: query, name: filter, required: false, schema: {type: string}}
                      responses:
                        '200':
                          description: ok
                          headers:
                            Link: {schema: {type: string}}
                            X-Next-Cursor: {schema: {type: string}}
                          content:
                            application/json:
                              schema:
                                type: object
                                properties:
                                  nextCursor: {type: string}
                                  items: {type: array, items: {type: string}}
                """.formatted(title);
    }
}
