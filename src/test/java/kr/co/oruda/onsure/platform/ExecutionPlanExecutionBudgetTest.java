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
import java.util.ArrayList;
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

    /** FR-04-A: scenario proposal must match the target's actual Program Profile structure/risk. */
    @Test
    void profileWithDataFlowsAndConflictsProposesMatchingScenarioClasses() throws Exception {
        Path source = temp.resolve("df-target");
        Files.createDirectories(source);
        Files.writeString(source.resolve("sample.txt"), "sample\n");
        Path profileFile = temp.resolve("df-program-profile.json");
        mapper.writeValue(profileFile.toFile(), Map.of(
                "contract", ProgramLearningService.CONTRACT,
                "profile_id", "profile-df-001",
                "source_baseline", Map.of("source_tree_sha256", "c".repeat(64)),
                "components", List.of(),
                "dependencies", List.of(),
                "data_flows", List.of(Map.of("from", "api", "to", "db")),
                "conflicts", List.of("MULTIPLE_PRIMARY_BUILD_DESCRIPTORS")));

        ExecutionPlanService service = new ExecutionPlanService();
        Map<String, Object> plan = service.plan(
                new ValidationTarget(
                        "df-target-001", "Target", TargetType.GENERAL_SOFTWARE, source,
                        "sha256:" + "c".repeat(64), GenericManifestTargetAdapter.ID,
                        "ONSURE_DEFAULT_POLICY_V1", "LOCAL_REVIEWED"),
                profileFile, 1, temp.resolve("df-plan.json"));

        assertScenarioExpectationsCoverAllClasses(plan);
        @SuppressWarnings("unchecked")
        List<String> classes = (List<String>) plan.get("scenario_classes");
        assertTrue(classes.contains("DATA_FLOW_BOUNDARY"));
        assertTrue(classes.contains("PROFILE_CONFLICT_RESOLUTION"));
        assertFalse(classes.contains("DEPENDENCY_SUPPLY_CHAIN"));
        assertFalse(classes.contains("AI_COMPONENT_SPECIFIC_ADVERSARIAL"));
    }

    /** Empty data_flows/conflicts must NOT propose DATA_FLOW_BOUNDARY/PROFILE_CONFLICT_RESOLUTION. */
    @Test
    void profileWithoutDataFlowsOrConflictsDoesNotProposeThoseScenarioClasses() throws Exception {
        Path source = temp.resolve("empty-target");
        Files.createDirectories(source);
        Files.writeString(source.resolve("sample.txt"), "sample\n");
        Path profileFile = temp.resolve("empty-program-profile.json");
        mapper.writeValue(profileFile.toFile(), Map.of(
                "contract", ProgramLearningService.CONTRACT,
                "profile_id", "profile-empty-001",
                "source_baseline", Map.of("source_tree_sha256", "d".repeat(64)),
                "components", List.of(),
                "dependencies", List.of(),
                "data_flows", List.of(),
                "conflicts", List.of()));

        ExecutionPlanService service = new ExecutionPlanService();
        Map<String, Object> plan = service.plan(
                new ValidationTarget(
                        "empty-target-001", "Target", TargetType.GENERAL_SOFTWARE, source,
                        "sha256:" + "d".repeat(64), GenericManifestTargetAdapter.ID,
                        "ONSURE_DEFAULT_POLICY_V1", "LOCAL_REVIEWED"),
                profileFile, 1, temp.resolve("empty-plan.json"));

        assertScenarioExpectationsCoverAllClasses(plan);
        @SuppressWarnings("unchecked")
        List<String> classes = (List<String>) plan.get("scenario_classes");
        assertFalse(classes.contains("DATA_FLOW_BOUNDARY"));
        assertFalse(classes.contains("PROFILE_CONFLICT_RESOLUTION"));
    }

    /**
     * DEPENDENCY_SUPPLY_CHAIN threshold reuses riskScore()'s own dependency-count saturation
     * cap of 10 (see ExecutionPlanService.DEPENDENCY_COUNT_RISK_CAP): at or below it, no
     * scenario is proposed; above it, DEPENDENCY_SUPPLY_CHAIN is proposed.
     */
    @Test
    void dependencyCountAboveTheRiskScoreCapProposesSupplyChainScenarioBelowDoesNot() throws Exception {
        Path source = temp.resolve("dep-target");
        Files.createDirectories(source);
        Files.writeString(source.resolve("sample.txt"), "sample\n");

        List<Map<String, Object>> tenDependencies = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tenDependencies.add(Map.of("coordinate", "group:artifact" + i));
        }
        Path belowThresholdProfile = temp.resolve("dep-below-program-profile.json");
        mapper.writeValue(belowThresholdProfile.toFile(), Map.of(
                "contract", ProgramLearningService.CONTRACT,
                "profile_id", "profile-dep-below-001",
                "source_baseline", Map.of("source_tree_sha256", "e".repeat(64)),
                "components", List.of(),
                "dependencies", tenDependencies,
                "data_flows", List.of(),
                "conflicts", List.of()));

        ExecutionPlanService service = new ExecutionPlanService();
        Map<String, Object> belowPlan = service.plan(
                new ValidationTarget(
                        "dep-below-target-001", "Target", TargetType.GENERAL_SOFTWARE, source,
                        "sha256:" + "e".repeat(64), GenericManifestTargetAdapter.ID,
                        "ONSURE_DEFAULT_POLICY_V1", "LOCAL_REVIEWED"),
                belowThresholdProfile, 1, temp.resolve("dep-below-plan.json"));
        assertScenarioExpectationsCoverAllClasses(belowPlan);
        @SuppressWarnings("unchecked")
        List<String> belowClasses = (List<String>) belowPlan.get("scenario_classes");
        assertFalse(belowClasses.contains("DEPENDENCY_SUPPLY_CHAIN"));

        List<Map<String, Object>> elevenDependencies = new ArrayList<>(tenDependencies);
        elevenDependencies.add(Map.of("coordinate", "group:artifact10"));
        Path aboveThresholdProfile = temp.resolve("dep-above-program-profile.json");
        mapper.writeValue(aboveThresholdProfile.toFile(), Map.of(
                "contract", ProgramLearningService.CONTRACT,
                "profile_id", "profile-dep-above-001",
                "source_baseline", Map.of("source_tree_sha256", "f".repeat(64)),
                "components", List.of(),
                "dependencies", elevenDependencies,
                "data_flows", List.of(),
                "conflicts", List.of()));

        Map<String, Object> abovePlan = service.plan(
                new ValidationTarget(
                        "dep-above-target-001", "Target", TargetType.GENERAL_SOFTWARE, source,
                        "sha256:" + "f".repeat(64), GenericManifestTargetAdapter.ID,
                        "ONSURE_DEFAULT_POLICY_V1", "LOCAL_REVIEWED"),
                aboveThresholdProfile, 1, temp.resolve("dep-above-plan.json"));
        assertScenarioExpectationsCoverAllClasses(abovePlan);
        @SuppressWarnings("unchecked")
        List<String> aboveClasses = (List<String>) abovePlan.get("scenario_classes");
        assertTrue(aboveClasses.contains("DEPENDENCY_SUPPLY_CHAIN"));
    }

    /**
     * AI_COMPONENT_SPECIFIC_ADVERSARIAL is gated on BOTH a non-GENERAL_SOFTWARE target type AND
     * non-empty ai_components -- an AI target proposes it, but a GENERAL_SOFTWARE target must
     * never propose it even when ai_components is (implausibly) present, proving the type gate
     * actually holds rather than only being satisfied by coincidence.
     */
    @Test
    void aiComponentScenarioIsGatedOnBothAiTargetTypeAndNonEmptyAiComponents() throws Exception {
        Path source = temp.resolve("ai-comp-target");
        Files.createDirectories(source);
        Files.writeString(source.resolve("sample.txt"), "sample\n");
        Path profileFile = temp.resolve("ai-comp-program-profile.json");
        mapper.writeValue(profileFile.toFile(), Map.of(
                "contract", ProgramLearningService.CONTRACT,
                "profile_id", "profile-ai-comp-001",
                "source_baseline", Map.of("source_tree_sha256", "0".repeat(64)),
                "components", List.of(),
                "dependencies", List.of(),
                "data_flows", List.of(),
                "conflicts", List.of(),
                "ai_components", List.of(Map.of("id", "llm-1"), Map.of("id", "llm-2"))));

        ExecutionPlanService service = new ExecutionPlanService();
        Map<String, Object> aiPlan = service.plan(
                new ValidationTarget(
                        "ai-comp-target-001", "Target", TargetType.AI_APPLICATION, source,
                        "sha256:" + "0".repeat(64), GenericManifestTargetAdapter.ID,
                        "ONSURE_DEFAULT_POLICY_V1", "LOCAL_REVIEWED"),
                profileFile, 1, temp.resolve("ai-comp-plan.json"));
        assertScenarioExpectationsCoverAllClasses(aiPlan);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> aiExpectations =
                (List<Map<String, Object>>) aiPlan.get("scenario_expectations");
        @SuppressWarnings("unchecked")
        List<String> aiClasses = (List<String>) aiPlan.get("scenario_classes");
        assertTrue(aiClasses.contains("AI_COMPONENT_SPECIFIC_ADVERSARIAL"));
        String aiExpectedResult = aiExpectations.stream()
                .filter(e -> "AI_COMPONENT_SPECIFIC_ADVERSARIAL".equals(e.get("scenario_class")))
                .findFirst().orElseThrow()
                .get("expected_result").toString();
        assertTrue(aiExpectedResult.contains("2"));

        Path generalProfileFile = temp.resolve("general-with-ai-components-program-profile.json");
        mapper.writeValue(generalProfileFile.toFile(), Map.of(
                "contract", ProgramLearningService.CONTRACT,
                "profile_id", "profile-general-ai-comp-001",
                "source_baseline", Map.of("source_tree_sha256", "1".repeat(64)),
                "components", List.of(),
                "dependencies", List.of(),
                "data_flows", List.of(),
                "conflicts", List.of(),
                "ai_components", List.of(Map.of("id", "llm-1"))));
        Map<String, Object> generalPlan = service.plan(
                new ValidationTarget(
                        "general-ai-comp-target-001", "Target", TargetType.GENERAL_SOFTWARE, source,
                        "sha256:" + "1".repeat(64), GenericManifestTargetAdapter.ID,
                        "ONSURE_DEFAULT_POLICY_V1", "LOCAL_REVIEWED"),
                generalProfileFile, 1, temp.resolve("general-ai-comp-plan.json"));
        assertScenarioExpectationsCoverAllClasses(generalPlan);
        @SuppressWarnings("unchecked")
        List<String> generalClasses = (List<String>) generalPlan.get("scenario_classes");
        assertFalse(generalClasses.contains("AI_COMPONENT_SPECIFIC_ADVERSARIAL"));
    }

    @SuppressWarnings("unchecked")
    private static void assertScenarioExpectationsCoverAllClasses(Map<String, Object> plan) {
        List<String> scenarioClasses = (List<String>) plan.get("scenario_classes");
        List<Map<String, Object>> scenarioExpectations =
                (List<Map<String, Object>>) plan.get("scenario_expectations");
        assertEquals(scenarioClasses.size(), scenarioExpectations.size());
        List<String> expectationClasses = scenarioExpectations.stream()
                .map(e -> (String) e.get("scenario_class")).toList();
        for (String scenarioClass : scenarioClasses) {
            assertTrue(expectationClasses.contains(scenarioClass),
                    "missing scenario_expectations entry for " + scenarioClass);
        }
        for (Map<String, Object> expectation : scenarioExpectations) {
            assertTrue(scenarioClasses.contains(expectation.get("scenario_class")));
            assertTrue(((String) expectation.get("expected_result")).length() > 10);
        }
    }
}
