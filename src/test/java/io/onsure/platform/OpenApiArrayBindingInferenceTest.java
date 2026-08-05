package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenApiArrayBindingInferenceTest {
    @TempDir Path temp;

    @Test
    void distinguishesReviewOnlyArraysFromSchemaGuaranteedSingletonArrays() throws Exception {
        Map<String, Object> general = binding(infer(false));
        assertEquals("/groups/~2/orderId", general.get("producer_json_pointer"));
        assertEquals("OPENAPI_RESPONSE_SCHEMA_EXACT_PROPERTY_SINGLETON_ARRAY",
                general.get("inference_basis"));
        assertEquals(0.80, general.get("inference_confidence"));

        Map<String, Object> singleton = binding(infer(true));
        assertEquals("/groups/~3/orderId", singleton.get("producer_json_pointer"));
        assertEquals("OPENAPI_RESPONSE_SCHEMA_EXACT_PROPERTY_SCHEMA_SINGLETON_ARRAY",
                singleton.get("inference_basis"));
        assertEquals(0.90, singleton.get("inference_confidence"));
    }

    private Map<String, Object> infer(boolean singleton) throws Exception {
        Path source = Files.createDirectory(temp.resolve("source-" + java.util.UUID.randomUUID()));
        Files.writeString(source.resolve("openapi.yaml"), """
                openapi: 3.1.0
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
                              schema:
                                type: object
                                properties:
                                  groups:
                                    type: array
                                    minItems: %d
                                    maxItems: %d
                                    items:
                                      type: object
                                      properties:
                                        orderId: {type: string}
                  /orders/{orderId}:
                    get:
                      operationId: getOrder
                      tags: [Orders]
                      responses: {'200': {description: ok}}
                """.formatted(singleton ? 1 : 0, singleton ? 1 : 100));
        Map<String, Object> inventory = StaticWorkflowInventory.detect(source);
        if (((Number) inventory.get("candidate_count")).intValue() == 0)
            throw new AssertionError(inventory.toString() + " source=" + Files.readString(source.resolve("openapi.yaml")));
        return ProgramUnderstandingEngine.infer(inventory, Hashing.tree(source));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> binding(Map<String, Object> understanding) {
        List<Map<String, Object>> lifecycles =
                (List<Map<String, Object>>) understanding.get("api_lifecycle_candidates");
        List<Map<String, Object>> bindings = lifecycles.stream()
                .flatMap(lifecycle -> ((List<Map<String, Object>>) lifecycle.get("proposed_bindings")).stream())
                .toList();
        if (bindings.isEmpty()) throw new AssertionError(understanding.toString());
        return bindings.get(0);
    }
}
