package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.oruda.onsure.platform.ExecutionBudget.DataTransferScope;
import kr.co.oruda.onsure.platform.ExecutionBudgetGuard.BudgetCheckResult;
import kr.co.oruda.onsure.platform.ExecutionBudgetGuard.ProjectedUsage;
import kr.co.oruda.onsure.platform.ValidationModel.TargetType;
import kr.co.oruda.onsure.platform.ValidationModel.ValidationTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Proves ExecutionBudget/ExecutionBudgetGuard (NFR-06, FR-04-B) are actually wired into ExecutionPlanService's output. */
class ExecutionPlanExecutionBudgetTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void planIncludesAnExecutionBudgetDerivedFromRealPlanInputs() throws Exception {
        Path source = temp.resolve("target");
        Files.createDirectories(source);
        Files.writeString(source.resolve("sample.txt"), "sample\n");
        Path profileFile = temp.resolve("program-profile.json");
        mapper.writeValue(profileFile.toFile(), Map.of(
                "contract", ProgramLearningService.CONTRACT,
                "profile_id", "profile-001",
                "source_baseline", Map.of("source_tree_sha256", "a".repeat(64)),
                "components", List.of(Map.of("id", "c1"), Map.of("id", "c2")),
                "dependencies", List.of(),
                "data_flows", List.of(),
                "conflicts", List.of()));

        ExecutionPlanService service = new ExecutionPlanService();
        Map<String, Object> plan = service.plan(
                new ValidationTarget(
                        "target-001", "Target", TargetType.GENERAL_SOFTWARE, source,
                        "sha256:" + "a".repeat(64), GenericManifestTargetAdapter.ID,
                        "ONSURE_DEFAULT_POLICY_V1", "LOCAL_REVIEWED"),
                profileFile, 3, temp.resolve("plan.json"));

        @SuppressWarnings("unchecked")
        Map<String, Object> executionBudget = (Map<String, Object>) plan.get("execution_budget");
        assertTrue(((String) executionBudget.get("expected_result")).contains("target-001"));
        assertTrue((Integer) executionBudget.get("token_estimate") > 0);
        assertEquals(0, ((Number) executionBudget.get("cost_ceiling_micros")).longValue());
        assertEquals("LOCAL_ONLY", executionBudget.get("data_transfer_scope"));

        ExecutionBudget budget = new ExecutionBudget(
                (String) executionBudget.get("expected_result"),
                (Integer) executionBudget.get("token_estimate"),
                ((Number) executionBudget.get("cost_ceiling_micros")).longValue(),
                DataTransferScope.valueOf((String) executionBudget.get("data_transfer_scope")));

        BudgetCheckResult withinBudget = ExecutionBudgetGuard.check(
                budget, new ProjectedUsage(budget.tokenEstimate() - 1, 0, DataTransferScope.LOCAL_ONLY));
        assertTrue(withinBudget.withinBudget());

        BudgetCheckResult overBudget = ExecutionBudgetGuard.check(
                budget, new ProjectedUsage(budget.tokenEstimate() + 1000, 0, DataTransferScope.LOCAL_ONLY));
        assertFalse(overBudget.withinBudget());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scenarioExpectations =
                (List<Map<String, Object>>) plan.get("scenario_expectations");
        @SuppressWarnings("unchecked")
        List<String> scenarioClasses = (List<String>) plan.get("scenario_classes");
        assertEquals(scenarioClasses.size(), scenarioExpectations.size());
        for (Map<String, Object> expectation : scenarioExpectations) {
            assertTrue(scenarioClasses.contains(expectation.get("scenario_class")));
            assertTrue(((String) expectation.get("expected_result")).length() > 10);
        }
    }

    @Test
    void aiTargetScenarioExpectationsCoverThePromptAndToolScenarioClasses() throws Exception {
        Path source = temp.resolve("ai-target");
        Files.createDirectories(source);
        Files.writeString(source.resolve("sample.txt"), "sample\n");
        Path profileFile = temp.resolve("ai-program-profile.json");
        mapper.writeValue(profileFile.toFile(), Map.of(
                "contract", ProgramLearningService.CONTRACT,
                "profile_id", "profile-ai-001",
                "source_baseline", Map.of("source_tree_sha256", "b".repeat(64)),
                "components", List.of(),
                "dependencies", List.of(),
                "data_flows", List.of(),
                "conflicts", List.of()));

        ExecutionPlanService service = new ExecutionPlanService();
        Map<String, Object> plan = service.plan(
                new ValidationTarget(
                        "ai-target-001", "AI Target", TargetType.AI_APPLICATION, source,
                        "sha256:" + "b".repeat(64), GenericManifestTargetAdapter.ID,
                        "ONSURE_DEFAULT_POLICY_V1", "LOCAL_REVIEWED"),
                profileFile, 1, temp.resolve("ai-plan.json"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scenarioExpectations =
                (List<Map<String, Object>>) plan.get("scenario_expectations");
        List<String> classes = scenarioExpectations.stream()
                .map(e -> (String) e.get("scenario_class")).toList();
        assertTrue(classes.contains("PROMPT_INJECTION"));
        assertTrue(classes.contains("TOOL_AUTHORIZATION"));
        assertTrue(classes.contains("CONTEXT_EXFILTRATION"));
        assertTrue(classes.contains("REPEATED_BEHAVIOR_VARIABILITY"));
    }
}
