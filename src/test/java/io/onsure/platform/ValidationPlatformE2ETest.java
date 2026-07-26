package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ValidationPlatformE2ETest {
    @TempDir Path temp;

    @Test
    void generalProgramRunsFromCatalogThroughRcaCandidateReportAndRevalidation()
            throws Exception {
        ProductCatalog catalog = new ProductCatalog(temp.resolve("catalog"));
        catalog.registerWorkspace(new Workspace("workspace-1", "Demo Workspace", Instant.now()));
        catalog.registerProject(new Project(
                "project-1", "workspace-1", "General Program", Instant.now()));

        ValidationTarget flawed = target(
                "sample-general-program", TargetType.GENERAL_SOFTWARE,
                Path.of("fixtures/e2e/general-program"));
        catalog.registerTarget(new RegisteredTarget("project-1", flawed, Instant.now()));
        assertEquals(flawed.targetId(), catalog.requireTarget(flawed.targetId()).targetId());

        ValidationEngine engine = ValidationEngine.defaultEngine(temp.resolve("runs"));
        ValidationEngine.RunResult baseline = engine.run(flawed);
        assertCoreOnly(baseline);
        assertEquals(Decision.FAIL, baseline.report().decision());
        assertTrue(baseline.report().findings().size() >= 3);
        assertEquals(baseline.report().findings().size(), baseline.report().rcaRecords().size());
        assertEquals(baseline.report().findings().size(), baseline.report().failureModes().size());
        assertEquals("RCA_CANDIDATE_TEMPLATE_NONFINAL",
                baseline.report().summary().get("rca_assurance"));
        assertEquals(baseline.report().findings().size(),
                ((Number) baseline.report().summary().get("remediation_plan_count")).intValue());
        assertTrue(baseline.report().fixtureResults().stream()
                .anyMatch(value -> value.fixtureId().equals("unauthorized-user")
                        && value.observed().equals("ALLOW")
                        && value.decision() == Decision.FAIL));
        assertEquals(2, ((Number) stage(baseline, "FIXTURE_HARNESS_ORACLE")
                .metrics().get("commands_executed")).intValue());
        assertInternalNonfinal(baseline);
        assertPersistentRun(baseline.runRoot());

        ValidationTarget fixed = target(
                "sample-general-program", TargetType.GENERAL_SOFTWARE,
                Path.of("fixtures/e2e/general-program-fixed"));
        ValidationEngine.RunResult current = engine.run(fixed);
        assertCoreOnly(current);
        assertEquals(Decision.PASS, current.report().decision());
        assertTrue(current.report().findings().isEmpty());
        assertTrue(current.report().fixtureResults().stream()
                .allMatch(value -> value.decision() == Decision.PASS));
        assertTrue(current.report().fixtureResults().stream()
                .anyMatch(value -> value.fixtureId().equals("unauthorized-user")
                        && value.observed().equals("DENY")));
        assertInternalNonfinal(current);
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
    void aiProgramExecutesFixturesAndDetectsPromptToolApprovalAndContextRisks()
            throws Exception {
        ValidationTarget ai = target(
                "sample-ai-program", TargetType.AI_APPLICATION,
                Path.of("fixtures/e2e/ai-program"));
        ValidationEngine.RunResult result = ValidationEngine.defaultEngine(
                temp.resolve("ai-runs")).run(ai);
        assertCoreOnly(result);
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
        assertTrue(((Number) result.report().summary().get("approval_required_count"))
                .longValue() >= 3);
        assertInternalNonfinal(result);
        assertPersistentRun(result.runRoot());
    }

    private static void assertCoreOnly(ValidationEngine.RunResult result) {
        assertEquals(GenericManifestTargetAdapter.ID,
                result.report().summary().get("adapter_id"));
        assertEquals(List.of(GenericManifestTargetAdapter.ID),
                result.report().summary().get("registered_adapter_ids"));
    }

    private static StageResult stage(ValidationEngine.RunResult result, String id) {
        return result.report().stages().stream().filter(value -> id.equals(value.stageId()))
                .findFirst().orElseThrow();
    }

    private static void assertInternalNonfinal(ValidationEngine.RunResult result) {
        assertEquals("PASS", result.report().summary().get("internal_verifier"));
        assertEquals("PASS", result.report().summary().get("internal_audit"));
        assertEquals("NOT_RUN", result.report().summary().get("independent_verifier"));
        assertEquals("NOT_RUN", result.report().summary().get("independent_audit"));
        assertEquals("SELF_VALIDATION_NONFINAL",
                result.report().summary().get("assurance_class"));
        assertEquals(false, result.report().summary().get("final_lock_allowed"));
    }

    private static ValidationTarget target(String id, TargetType type, Path sourceRoot)
            throws Exception {
        return new ValidationTarget(
                id, id, type, sourceRoot, SourceReferenceBinding.treeReference(sourceRoot),
                GenericManifestTargetAdapter.ID, "ONSURE_DEFAULT_POLICY_V1", "LOCAL_E2E");
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
        assertTrue(Files.isRegularFile(
                runRoot.getParent().getParent().resolve("failure-mode-registry.json")));
    }
}
