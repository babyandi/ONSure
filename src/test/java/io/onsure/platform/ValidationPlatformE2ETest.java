package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.onsure.assurance.Decision;
import io.onsure.platform.ProductCatalog.Project;
import io.onsure.platform.ProductCatalog.RegisteredTarget;
import io.onsure.platform.ProductCatalog.Workspace;
import io.onsure.platform.ValidationModel.StageResult;
import io.onsure.platform.ValidationModel.TargetType;
import io.onsure.platform.ValidationModel.ValidationTarget;
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
    void generalProgramRunsFromCatalogThroughRcaReportAndRevalidation() throws Exception {
        ProductCatalog catalog = new ProductCatalog(temp.resolve("catalog"));
        catalog.registerWorkspace(new Workspace("workspace-1", "Demo Workspace", Instant.now()));
        catalog.registerProject(new Project("project-1", "workspace-1", "General Program", Instant.now()));

        ValidationTarget flawed = target(
                "sample-general-program", TargetType.GENERAL_SOFTWARE,
                Path.of("fixtures/e2e/general-program"), GenericManifestTargetAdapter.ID, "a".repeat(40));
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
        assertIndependentPass(baseline);
        assertPersistentRun(baseline.runRoot());

        ValidationTarget fixed = target(
                "sample-general-program", TargetType.GENERAL_SOFTWARE,
                Path.of("fixtures/e2e/general-program-fixed"), GenericManifestTargetAdapter.ID, "b".repeat(40));
        ValidationEngine.RunResult current = engine.run(fixed);
        assertEquals(Decision.PASS, current.report().decision());
        assertTrue(current.report().findings().isEmpty());
        assertTrue(current.report().fixtureResults().stream()
                .allMatch(value -> value.decision() == Decision.PASS));
        assertTrue(current.report().fixtureResults().stream()
                .anyMatch(value -> value.fixtureId().equals("unauthorized-user")
                        && value.observed().equals("DENY")));
        assertIndependentPass(current);
        assertPersistentRun(current.runRoot());

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
    void aiProgramExecutesFixturesAndDetectsPromptToolApprovalAndContextRisks() throws Exception {
        ValidationTarget ai = target(
                "sample-ai-program", TargetType.AI_APPLICATION,
                Path.of("fixtures/e2e/ai-program"), GenericManifestTargetAdapter.ID, "c".repeat(40));
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
        assertIndependentPass(result);
        assertPersistentRun(result.runRoot());
    }

    @Test
    void standaloneDefaultEngineDoesNotRegisterOrudaAdapter() throws Exception {
        ValidationTarget oruda = target(
                "ORUDA", TargetType.AI_AGENTIC_PLATFORM,
                Path.of("fixtures/e2e/oruda-target"), OrudaTargetAdapter.ID, "d".repeat(40));
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> ValidationEngine.defaultEngine(temp.resolve("standalone-runs")).run(oruda));
        assertTrue(failure.getMessage().contains("NO_TARGET_ADAPTER"));
    }

    @Test
    void orudaRunsOnlyThroughExplicitOptionalAdapterProfile() throws Exception {
        ValidationTarget oruda = target(
                "ORUDA", TargetType.AI_AGENTIC_PLATFORM,
                Path.of("fixtures/e2e/oruda-target"), OrudaTargetAdapter.ID, "d".repeat(40));
        ValidationEngine.RunResult result = ValidationEngine.withOrudaAdapter(temp.resolve("oruda-runs")).run(oruda);
        assertEquals(Decision.FAIL, result.report().decision());
        assertEquals(OrudaTargetAdapter.ID, result.report().summary().get("adapter_id"));
        assertTrue(result.report().findings().stream()
                .anyMatch(value -> value.category().equals("AI_SELF_APPROVAL")));
        assertTrue(result.report().fixtureResults().stream()
                .anyMatch(value -> value.fixtureId().equals("agent-self-approval")
                        && value.observed().equals("ALLOW")
                        && value.decision() == Decision.FAIL));
        assertIndependentPass(result);
        assertPersistentRun(result.runRoot());
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
                "ORUDA", TargetType.AI_AGENTIC_PLATFORM, root, OrudaTargetAdapter.ID, "e".repeat(40));
        try {
            ValidationEngine.withOrudaAdapter(temp.resolve("invalid-runs")).run(target);
        } catch (ValidationEngine.ValidationExecutionException e) {
            assertTrue(e.getCause().getMessage().contains("ORUDA_CANNOT_WRITE_ONSURE_FINAL_DECISION"));
            assertNotNull(e.report());
            assertTrue(Files.isRegularFile(e.runRoot().resolve("validation-report.json")));
            return;
        }
        throw new AssertionError("ORUDA authority claim must fail closed");
    }

    private static StageResult stage(ValidationEngine.RunResult result, String id) {
        return result.report().stages().stream().filter(value -> id.equals(value.stageId()))
                .findFirst().orElseThrow();
    }

    private static void assertIndependentPass(ValidationEngine.RunResult result) {
        assertEquals("PASS", result.report().summary().get("internal_verifier"));
        assertEquals("PASS", result.report().summary().get("internal_audit"));
        assertEquals("NOT_RUN", result.report().summary().get("independent_verifier"));
        assertEquals("NOT_RUN", result.report().summary().get("independent_audit"));
    }

    private static ValidationTarget target(String id, TargetType type, Path sourceRoot,
            String adapterId, String ignoredSourceReference) throws Exception {
        return new ValidationTarget(
                id, id, type, sourceRoot, SourceReferenceBinding.treeReference(sourceRoot), adapterId,
                "ONSURE_DEFAULT_POLICY_V1", "LOCAL_E2E");
    }

    private static void assertPersistentRun(Path runRoot) {
        for (String file : Set.of(
                "target.json", "job.json", "target-metadata.json", "evidence.json",
                "findings.json", "failure-modes.json", "rca.json", "remediation-plans.json",
                "fixture-registry.json", "oracle-registry.json", "fixture-results.json",
                "stage-results.json", "regression-lock.json", "internal-verifier-receipt.json",
                "internal-audit-receipt.json", "validation-report.json", "validation-report.md",
                "validation-report.html", "manifest.sha256")) {
            assertTrue(Files.isRegularFile(runRoot.resolve(file)), file);
        }
        assertTrue(Files.isRegularFile(runRoot.getParent().getParent().resolve("failure-mode-registry.json")));
    }
}
