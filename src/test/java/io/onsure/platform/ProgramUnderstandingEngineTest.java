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
                      responses: {'200': {description: ok}}
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
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> questions = (List<Map<String, Object>>) result.get("minimal_questions");
        assertTrue(questions.stream().anyMatch(question -> "RUNTIME_ENDPOINT".equals(question.get("question_id"))));
    }
}
