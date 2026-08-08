package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import kr.co.oruda.onsure.assurance.Decision;
import kr.co.oruda.onsure.platform.ProductCatalog.Project;
import kr.co.oruda.onsure.platform.ProductCatalog.RegisteredTarget;
import kr.co.oruda.onsure.platform.ProductCatalog.Workspace;
import kr.co.oruda.onsure.platform.ValidationModel.StageResult;
import kr.co.oruda.onsure.platform.ValidationModel.TargetType;
import kr.co.oruda.onsure.platform.ValidationModel.ValidationTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ValidationPlatformE2ETest {
    @TempDir Path temp;

    @Test
    void generalProgramRunsFromLearningThroughReviewRcaPatchPlanAndRevalidation() throws Exception {
        ProductCatalog catalog = new ProductCatalog(temp.resolve("catalog"));
        catalog.registerWorkspace(new Workspace("workspace-1", "Demo Workspace", Instant.now()));
        catalog.registerProject(new Project("project-1", "workspace-1", "General Program", Instant.now()));

        ValidationTarget flawed = target(
                "sample-general-program", TargetType.GENERAL_SOFTWARE,
                Path.of("fixtures/e2e/general-program"), GenericManifestTargetAdapter.ID);
        catalog.registerTarget(new RegisteredTarget("project-1", flawed, Instant.now()));
        assertEquals(flawed.targetId(), catalog.requireTarget(flawed.targetId()).targetId());

        ValidationEngine engine = ValidationEngine.defaultEngine(temp.resolve("runs"));
        ValidationEngine.RunResult baseline = engine.run(flawed);
        assertEquals(Decision.FAIL, baseline.report().decision());
        assertTrue(baseline.report().findings().size() >= 3);
        assertEquals(baseline.report().findings().size(), baseline.report().rcaRecords().size());
        assertEquals(baseline.report().findings().size(), baseline.report().failureModes().size());
        assertEquals(baseline.report().findings().size(),
                ((Number) baseline.report().summary().get("remediation_plan_count")).intValue());
        assertTrue(baseline.report().fixtureResults().stream()
                .anyMatch(value -> value.fixtureId().equals("unauthorized-user")
                        && value.observed().equals("ALLOW")
                        && value.decision() == Decision.FAIL));
        assertEquals(2, ((Number) stage(baseline, "FIXTURE_HARNESS_ORACLE")
                .metrics().get("commands_executed")).intValue());
        assertProductWorkflowStages(baseline, false, Decision.FAIL);
        assertInternalNonfinalPass(baseline);
        assertPersistentRun(baseline.runRoot(), false);

        ValidationTarget fixed = target(
                "sample-general-program", TargetType.GENERAL_SOFTWARE,
                Path.of("fixtures/e2e/general-program-fixed"), GenericManifestTargetAdapter.ID);
        ValidationEngine.RunResult current = engine.run(fixed);
        assertEquals(Decision.HOLD, current.report().decision());
        assertTrue(current.report().findings().isEmpty());
        assertTrue(current.report().fixtureResults().stream()
                .allMatch(value -> value.decision() == Decision.PASS));
        assertTrue(current.report().fixtureResults().stream()
                .anyMatch(value -> value.fixtureId().equals("unauthorized-user")
                        && value.observed().equals("DENY")));
        assertProductWorkflowStages(current, false, Decision.HOLD);
        assertEquals("HOLD", current.report().summary().get("review_quality_decision"));
        assertInternalNonfinalPass(current);
        assertPersistentRun(current.runRoot(), false);

        Path deltaFile = temp.resolve("revalidation/general-program-delta.json");
        var delta = new RevalidationService().compareAndWrite(
                baseline.report(), current.report(), deltaFile);
        assertTrue(delta.sourceChanged());
        assertTrue(delta.regressionResultChanged());
        assertFalse(delta.resolvedFindingFingerprints().isEmpty());
        assertTrue(delta.newFindingFingerprints().isEmpty());
        assertTrue(Files.isRegularFile(deltaFile));
    }

    @Test
    void aiProgramProducesRepeatedBehaviorProfileAndDetectsRisks() throws Exception {
        ValidationTarget ai = target(
                "sample-ai-program", TargetType.AI_APPLICATION,
                Path.of("fixtures/e2e/ai-program"), GenericManifestTargetAdapter.ID);
        ValidationEngine.RunResult result = ValidationEngine.defaultEngine(temp.resolve("ai-runs")).run(ai);
        assertEquals(Decision.FAIL, result.report().decision());
        Set<String> categories = result.report().findings().stream()
                .map(value -> value.category()).collect(Collectors.toSet());
        assertTrue(categories.contains("AI_TOOL_AUTHORIZATION"));
        assertTrue(categories.contains("AI_SELF_APPROVAL"));
        assertTrue(categories.contains("PROMPT_INJECTION"));
        assertTrue(categories.contains("AI_DATA_EXFILTRATION"));
        assertTrue(categories.contains("RUNTIME_BEHAVIOR"));
        assertTrue(result.report().fixtureResults().stream()
                .anyMatch(value -> value.fixtureId().equals("prompt-injection-tool-call")
                        && value.observed().equals("ALLOW_TOOL")
                        && value.decision() == Decision.FAIL));
        assertTrue(((Number) result.report().summary().get("approval_required_count")).longValue() >= 3);
        assertProductWorkflowStages(result, true, Decision.FAIL);
        assertInternalNonfinalPass(result);
        assertPersistentRun(result.runRoot(), true);
        assertTrue(Files.readString(result.runRoot().resolve("behavior-profile.json"))
                .contains("REPEATED_EXECUTABLE_FIXTURE_OBSERVATION_V1"));
        assertEquals(BehaviorLearningService.COVERAGE_PROXY,
                result.report().summary().get("behavior_profile_coverage_class"));
    }

    @Test
    void standaloneDefaultEngineDoesNotRegisterOrudaAdapter() throws Exception {
        ValidationTarget oruda = target(
                "ORUDA", TargetType.AI_AGENTIC_PLATFORM,
                Path.of("fixtures/e2e/oruda-target"), OrudaTargetAdapter.ID);
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> ValidationEngine.defaultEngine(temp.resolve("standalone-runs")).run(oruda));
        assertTrue(failure.getMessage().contains("NO_TARGET_ADAPTER"));
    }

    @Test
    void orudaRunsOnlyThroughExplicitOptionalAdapterProfile() throws Exception {
        ValidationTarget oruda = target(
                "ORUDA", TargetType.AI_AGENTIC_PLATFORM,
                Path.of("fixtures/e2e/oruda-target"), OrudaTargetAdapter.ID);
        ValidationEngine.RunResult result = ValidationEngine.withOptionalAdapters(
                temp.resolve("oruda-runs"), java.util.List.of(new OrudaTargetAdapter())).run(oruda);
        assertEquals(Decision.FAIL, result.report().decision());
        assertEquals(OrudaTargetAdapter.ID, result.report().summary().get("adapter_id"));
        assertTrue(result.report().findings().stream()
                .anyMatch(value -> value.category().equals("AI_SELF_APPROVAL")));
        assertEquals(Decision.FAIL, stage(result, "OREVIEW").decision());
        assertInternalNonfinalPass(result);
        assertPersistentRun(result.runRoot(), true);
    }

    @Test
    void orudaRegistrationFailsClosedWhenItClaimsONSureAuthority() throws Exception {
        Path root = temp.resolve("oruda-invalid");
        Files.createDirectories(root);
        Files.writeString(root.resolve("oruda-target.json"), """
                {
                  "contract":"ONSURE_ORUDA_TARGET_PROFILE_V1",
                  "target_id":"ORUDA",
                  "relationship":"EXTERNAL_VALIDATION_TARGET",
                  "onsure_runtime_dependency_on_oruda":false,
                  "oruda_can_write_onsure_final_decision":true,
                  "fixtures":[]
                }
                """);
        ValidationTarget target = target(
                "ORUDA", TargetType.AI_AGENTIC_PLATFORM, root, OrudaTargetAdapter.ID);
        try {
            ValidationEngine.withOptionalAdapters(
                    temp.resolve("invalid-runs"), java.util.List.of(new OrudaTargetAdapter())).run(target);
        } catch (ValidationEngine.ValidationExecutionException e) {
            assertTrue(e.getCause().getMessage().contains("ORUDA_CANNOT_WRITE_ONSURE_FINAL_DECISION"));
            assertNotNull(e.report());
            assertTrue(Files.isRegularFile(e.runRoot().resolve("validation-report.json")));
            return;
        }
        throw new AssertionError("ORUDA authority claim must fail closed");
    }

    private static void assertProductWorkflowStages(
            ValidationEngine.RunResult result, boolean ai, Decision expectedReview) {
        for (String stageId : Set.of(
                "PROGRAM_LEARNING", "RISK_BASED_EXECUTION_PLANNING",
                "EVIDENCE_BASED_RCA", "PATCH_PLANNING")) {
            assertEquals(Decision.PASS, stage(result, stageId).decision(), stageId);
        }
        assertEquals(expectedReview, stage(result, "OREVIEW").decision());
        if (ai) assertEquals(Decision.PASS, stage(result, "BEHAVIOR_LEARNING").decision());
        assertEquals("PROFILE_CANDIDATE", result.report().summary().get("program_profile_state"));
        assertEquals("AUTO_APPROVED_DEVELOPMENT_NONFINAL",
                result.report().summary().get("execution_plan_approval"));
        assertTrue(String.valueOf(result.report().summary().get("execution_plan_approval_sha256"))
                .matches("[0-9a-f]{64}"));
        assertFalse(result.report().summary().get("review_id").equals("NOT_RUN"));
        assertFalse(result.report().summary().get("patch_plan_id").equals("NOT_RUN"));
    }

    private static StageResult stage(ValidationEngine.RunResult result, String id) {
        return result.report().stages().stream().filter(value -> id.equals(value.stageId()))
                .findFirst().orElseThrow();
    }

    private static void assertInternalNonfinalPass(ValidationEngine.RunResult result) {
        assertEquals("PASS", result.report().summary().get("internal_verifier"));
        assertEquals("PASS", result.report().summary().get("internal_audit"));
        assertEquals("NOT_RUN", result.report().summary().get("independent_verifier"));
        assertEquals("NOT_RUN", result.report().summary().get("independent_audit"));
        assertEquals("SELF_VALIDATION_NONFINAL", result.report().summary().get("assurance_class"));
    }

    private static ValidationTarget target(
            String id, TargetType type, Path sourceRoot, String adapterId) throws Exception {
        return new ValidationTarget(
                id, id, type, sourceRoot, SourceReferenceBinding.treeReference(sourceRoot), adapterId,
                "ONSURE_DEFAULT_POLICY_V1", FixtureRegistryStage.TRUSTED_LOCAL_PROFILE);
    }

    private static void assertPersistentRun(Path runRoot, boolean behaviorExpected) {
        Set<String> files = new java.util.HashSet<>(Set.of(
                "storage-context.json", "target.json", "job.json", "target-metadata.json",
                "evidence.json", "findings.json", "failure-modes.json", "rca.json",
                "remediation-plans.json", "fixture-registry.json", "oracle-registry.json",
                "fixture-results.json", "stage-results.json", "regression-lock.json",
                "internal-verifier-receipt.json", "internal-audit-receipt.json",
                "validation-report.json", "validation-report.md", "validation-report.html",
                "program-profile.json", "execution-plan.json", "execution-plan-approval.json",
                "review-result.json", "evidence-based-rca.json", "patch-plan.json", "manifest.sha256"));
        if (behaviorExpected) files.add("behavior-profile.json");
        for (String file : files) assertTrue(Files.isRegularFile(runRoot.resolve(file)), file);
        assertTrue(Files.isRegularFile(runRoot.getParent().getParent().resolve("failure-mode-registry.json")));
    }
}
