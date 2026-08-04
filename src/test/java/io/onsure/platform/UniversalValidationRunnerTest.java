package io.onsure.platform;

import static io.onsure.platform.UniversalValidationProfile.Outcome.BLOCKED;
import static io.onsure.platform.UniversalValidationProfile.Outcome.FAIL;
import static io.onsure.platform.UniversalValidationProfile.Outcome.NOT_RUN;
import static io.onsure.platform.UniversalValidationProfile.Outcome.PASS_NONFINAL;
import static io.onsure.platform.UniversalValidationProfile.Phase.COMPONENT_AND_NEGATIVE;
import static io.onsure.platform.UniversalValidationProfile.Phase.END_TO_END_LINEAGE;
import static io.onsure.platform.UniversalValidationProfile.Phase.OPERATIONAL_RESILIENCE;
import static io.onsure.platform.UniversalValidationProfile.Phase.STRUCTURE_STATIC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UniversalValidationRunnerTest {
    @TempDir Path temp;

    @Test
    void blocksNodeExecutionWhenPackageManifestAndLockDependencySetsDrift() throws Exception {
        Path source = Files.createDirectory(temp.resolve("node-lock-drift"));
        Files.writeString(source.resolve("package.json"), """
                {"scripts":{"test":"node --test"}}
                """);
        Files.writeString(source.resolve("package-lock.json"), """
                {"lockfileVersion":3,"packages":{"":{"dependencies":{"renderer":"1.0.0"}}}}
                """);
        var profile = new StandardValidationProfileDetector().detect("node-lock-drift", source);

        var result = new UniversalValidationRunner((step, root) ->
                new UniversalValidationRunner.StepExecution(PASS_NONFINAL, 0, "pass", false, "test"))
                .run(profile, temp.resolve("node-lock-drift-run"));

        var preflight = result.steps().stream()
                .filter(step -> step.stepId().equals("environment.preflight"))
                .findFirst().orElseThrow();
        assertEquals(BLOCKED, preflight.outcome());
        assertTrue(Files.readString(Path.of(preflight.logFile()))
                .contains("node.manifest-lock-dependency-mismatch:dependencies"));
        assertEquals(BLOCKED, result.overallOutcome());
    }

    @Test
    void performsFixedOfflineNodeInstallInEnvironmentGroupBeforeStructure() throws Exception {
        Path source = Files.createDirectory(temp.resolve("node-offline-preflight"));
        Files.writeString(source.resolve("package.json"), """
                {"dependencies":{"renderer":"1.0.0"},"scripts":{"test":"node --test"}}
                """);
        Files.writeString(source.resolve("package-lock.json"), """
                {"lockfileVersion":3,"packages":{"":{"dependencies":{"renderer":"1.0.0"}}}}
                """);
        var profile = new StandardValidationProfileDetector().detect("node-offline-preflight", source);
        List<String> executed = new ArrayList<>();

        var result = new UniversalValidationRunner((step, root) -> {
            executed.add(step.stepId());
            if (step.stepId().equals("node.dependencies")) {
                assertEquals(List.of("npm", "--offline", "ci", "--ignore-scripts"), step.command());
                return new UniversalValidationRunner.StepExecution(
                        BLOCKED, 1, "ENOTCACHED", false, "OFFLINE_DEPENDENCY_CACHE_INCOMPLETE");
            }
            throw new AssertionError("later command must not execute: " + step.stepId());
        }).run(profile, temp.resolve("node-offline-preflight-run"));

        assertEquals(List.of("node.dependencies"), executed);
        assertEquals(BLOCKED, result.groupOutcomes().get(
                UniversalValidationProfile.VerificationGroup.ENVIRONMENT_DEPENDENCY));
        assertEquals(NOT_RUN, result.groupOutcomes().get(
                UniversalValidationProfile.VerificationGroup.STRUCTURE));
        assertEquals(NOT_RUN, result.steps().stream()
                .filter(step -> step.stepId().equals("structure.inventory")).findFirst().orElseThrow().outcome());
    }

    @Test
    void runsDetectedStepsInSnapshotAndPreservesUnexecutedPhases() throws Exception {
        Path source = Files.createDirectory(temp.resolve("source"));
        Files.writeString(source.resolve("pom.xml"), "<project/>");
        Files.writeString(source.resolve("approval-gate.yaml"), "enabled: true\n");
        Files.writeString(source.resolve("openapi.yaml"), """
                openapi: 3.1.0
                info:
                  title: neutral
                  version: 1
                paths:
                  /health:
                    get:
                      operationId: readHealth
                      responses:
                        '200': {description: healthy}
                """);
        var profile = new StandardValidationProfileDetector().detect("neutral", source);
        var runner = new UniversalValidationRunner((step, root) -> {
            assertTrue(root.startsWith(temp.resolve("run/execution-source")));
            Files.writeString(root.resolve("generated-by-test.txt"), "snapshot-only");
            return new UniversalValidationRunner.StepExecution(PASS_NONFINAL, 0, "PASS", false, "TEST_EXECUTOR");
        });

        var result = runner.run(profile, temp.resolve("run"));

        assertEquals(PASS_NONFINAL, result.phaseOutcomes().get(STRUCTURE_STATIC));
        assertEquals(NOT_RUN, result.phaseOutcomes().get(COMPONENT_AND_NEGATIVE));
        assertEquals(NOT_RUN, result.phaseOutcomes().get(END_TO_END_LINEAGE));
        assertEquals(NOT_RUN, result.phaseOutcomes().get(OPERATIONAL_RESILIENCE));
        assertEquals(NOT_RUN, result.overallOutcome());
        assertFalse(result.sourceMutationDetected());
        assertFalse(Files.exists(source.resolve("generated-by-test.txt")));
        assertTrue(Files.isRegularFile(result.receiptFile()));
        var receipt = new ObjectMapper().readTree(result.receiptFile().toFile());
        assertFalse(receipt.path("final_claim_allowed").asBoolean(true));
        assertEquals("NOT_RUN", receipt.path("overall_outcome").asText());
        assertTrue(receipt.path("environment_evidence").path("description").isTextual());
        assertEquals(result.steps().get(0).environmentSha256(),
                receipt.path("environment_evidence").path("sha256").asText());
        assertEquals("PASS_NONFINAL", receipt.path("final_evidence_integrity").path("outcome").asText());
        Path finalEvidenceLog = Path.of(
                receipt.path("final_evidence_integrity").path("log_file").asText());
        assertTrue(Files.isRegularFile(finalEvidenceLog));
        assertEquals(Hashing.file(finalEvidenceLog),
                receipt.path("final_evidence_integrity").path("output_sha256").asText());
        assertTrue(receipt.path("steps").get(0).path("startedAt").isTextual());
        assertTrue(Files.isRegularFile(Path.of(receipt.path("steps").get(0).path("logFile").asText())));
        var inventory = result.steps().stream().filter(step -> step.stepId().equals("structure.inventory"))
                .findFirst().orElseThrow();
        assertTrue(Files.readString(Path.of(inventory.logFile())).contains("approval-gate.yaml"));
    }

    @Test
    void metaValidatorFailsWhenARequiredE2eFacetIsMissing() throws Exception {
        Path source = Files.createDirectory(temp.resolve("source"));
        Files.writeString(source.resolve("openapi.yaml"), """
                openapi: 3.1.0
                info: {title: neutral, version: 1}
                paths: {/health: {get: {operationId: health, responses: {'200': {description: ok}}}}}
                """);
        var detected = new StandardValidationProfileDetector().detect("incomplete-validator", source);
        var withoutAuditFacet = detected.steps().stream()
                .filter(step -> step.kind() != UniversalValidationProfile.StepKind.E2E_AUDIT_CHECK)
                .map(step -> new UniversalValidationProfile.Step(
                        step.stepId(), step.phase(), step.kind(), step.required(), step.command(),
                        step.workingDirectory(), step.timeout(), step.dependsOn().stream()
                                .filter(id -> !id.equals("e2e.audit-check")).toList()))
                .toList();
        var incomplete = new UniversalValidationProfile.Profile(
                detected.profileId(), detected.sourceRoot(), detected.technologies(),
                detected.environmentRequirements(), withoutAuditFacet, detected.notRunReasons());

        var result = new UniversalValidationRunner((step, root) ->
                new UniversalValidationRunner.StepExecution(PASS_NONFINAL, 0, "pass", false, "test"))
                .run(incomplete, temp.resolve("run"));

        var meta = result.steps().stream().filter(step -> step.stepId().equals("validator.meta-check"))
                .findFirst().orElseThrow();
        assertEquals(FAIL, meta.outcome());
        assertTrue(Files.readString(Path.of(meta.logFile())).contains("E2E_AUDIT_CHECK"));
        assertEquals(FAIL, result.overallOutcome());
    }

    @Test
    void metaValidatorReportsDiscoveredWorkflowRolesWithoutExecutableCoverage() throws Exception {
        Path source = Files.createDirectory(temp.resolve("unmapped-workflow"));
        Files.writeString(source.resolve("audit_runtime.py"), """
                if __name__ == '__main__':
                    print('audit')
                """);
        var profile = new StandardValidationProfileDetector().detect("unmapped-workflow", source);

        var result = new UniversalValidationRunner((step, root) ->
                new UniversalValidationRunner.StepExecution(PASS_NONFINAL, 0, "pass", false, "test"))
                .run(profile, temp.resolve("unmapped-workflow-run"));

        var meta = result.steps().stream().filter(step -> step.stepId().equals("validator.meta-check"))
                .findFirst().orElseThrow();
        String report = Files.readString(Path.of(meta.logFile()));
        assertEquals(PASS_NONFINAL, meta.outcome());
        assertTrue(report.contains("unmapped_discovered_roles=[AUDIT]"));
        assertTrue(report.contains("workflow_execution_readiness=REVIEW_REQUIRED_NOT_EXECUTABLE"));
        assertEquals(NOT_RUN, result.overallOutcome());
    }

    @Test
    void recordsProductFailureWithoutRunningDependentIntegrationStep() throws Exception {
        Path source = Files.createDirectory(temp.resolve("source"));
        Files.writeString(source.resolve("pyproject.toml"), "[tool.pytest.ini_options]\n");
        Files.createDirectories(source.resolve("tests/integration"));
        var profile = new StandardValidationProfileDetector().detect("python", source);
        var runner = new UniversalValidationRunner((step, root) ->
                new UniversalValidationRunner.StepExecution(FAIL, 1, "failed", false, "COMMAND_EXIT_NONZERO"));

        var result = runner.run(profile, temp.resolve("run"));

        assertEquals(FAIL, result.phaseOutcomes().get(COMPONENT_AND_NEGATIVE));
        assertEquals(NOT_RUN, result.phaseOutcomes().get(END_TO_END_LINEAGE));
        assertEquals(FAIL, result.overallOutcome());
        assertTrue(result.steps().stream().anyMatch(step ->
                step.stepId().equals("python.integration") && step.outcome() == NOT_RUN));
    }

    @Test
    void stopsAfterBlockedEnvironmentProbe() throws Exception {
        Path source = Files.createDirectory(temp.resolve("source"));
        Files.writeString(source.resolve("pyproject.toml"), "[project]\nname='blocked'\n");
        var profile = new StandardValidationProfileDetector().detect("blocked", source);
        var runner = new UniversalValidationRunner(new UniversalValidationRunner.StepExecutor() {
            @Override
            public UniversalValidationRunner.StepExecution execute(
                    UniversalValidationProfile.Step step, Path root) {
                throw new AssertionError("command must not run after blocked preflight");
            }

            @Override
            public UniversalValidationRunner.StepExecution probe(Path root) {
                return new UniversalValidationRunner.StepExecution(
                        UniversalValidationProfile.Outcome.BLOCKED, 1, "namespace denied", false,
                        "SANDBOX_UNAVAILABLE_OR_DENIED");
            }
        });

        var result = runner.run(profile, temp.resolve("run"));

        assertEquals(UniversalValidationProfile.Outcome.BLOCKED, result.overallOutcome());
        assertEquals(UniversalValidationProfile.Outcome.BLOCKED,
                result.groupOutcomes().get(UniversalValidationProfile.VerificationGroup.ENVIRONMENT_DEPENDENCY));
        assertTrue(result.steps().stream().filter(step -> !step.stepId().equals("environment.preflight"))
                .allMatch(step -> step.outcome() == NOT_RUN));
    }

    @Test
    void blocksRequiredPackFixtureMissingFromSnapshot() throws Exception {
        Path source = Files.createDirectory(temp.resolve("source"));
        Files.writeString(source.resolve("openapi.yaml"), "openapi: 3.1.0\npaths: {}\n");
        var detected = new StandardValidationProfileDetector().detect("requirements", source);
        var profile = new UniversalValidationProfile.Profile(
                detected.profileId(), detected.sourceRoot(), detected.technologies(),
                java.util.List.of(new UniversalValidationProfile.EnvironmentRequirement(
                        "signer.fixture", UniversalValidationProfile.RequirementKind.SOURCE_FILE,
                        "signing/receipt.json", true)), detected.steps(), detected.notRunReasons());

        var result = new UniversalValidationRunner((step, root) -> {
            throw new AssertionError("command execution not expected");
        }).run(profile, temp.resolve("run"));

        assertEquals(UniversalValidationProfile.Outcome.BLOCKED, result.overallOutcome());
        assertEquals("REQUIRED_ENVIRONMENT_MISSING:signer.fixture", result.steps().get(0).reason());
    }

    @Test
    void verifiesEveryDeclaredEnvironmentRequirementKindAndRecordsSemanticDigest() throws Exception {
        Path source = Files.createDirectory(temp.resolve("all-environment-kinds"));
        Files.writeString(source.resolve("openapi.yaml"), "openapi: 3.1.0\npaths: {}\n");
        Files.createDirectory(source.resolve("renderer"));
        Files.writeString(source.resolve("renderer/runtime.marker"), "renderer\n");
        Files.writeString(source.resolve("signing.json"), "{}\n");
        Files.writeString(source.resolve("render.sh"), "#!/bin/sh\nexit 0\n");
        source.resolve("render.sh").toFile().setExecutable(false, false);
        var requirements = List.of(
                new UniversalValidationProfile.EnvironmentRequirement(
                        "tool.available", UniversalValidationProfile.RequirementKind.EXECUTABLE, "sh", true),
                new UniversalValidationProfile.EnvironmentRequirement(
                        "renderer.directory", UniversalValidationProfile.RequirementKind.SOURCE_DIRECTORY,
                        "renderer", true),
                new UniversalValidationProfile.EnvironmentRequirement(
                        "signing.fixture", UniversalValidationProfile.RequirementKind.SOURCE_FILE,
                        "signing.json", true),
                new UniversalValidationProfile.EnvironmentRequirement(
                        "renderer.launcher", UniversalValidationProfile.RequirementKind.EXECUTABLE_SOURCE_FILE,
                        "render.sh", true),
                new UniversalValidationProfile.EnvironmentRequirement(
                        "font.missing", UniversalValidationProfile.RequirementKind.FONT_FAMILY,
                        "ONSure Definitely Missing Font 7f5c8e", true));
        var detected = new StandardValidationProfileDetector().detect(
                "all-environment-kinds", source, requirements);

        var result = new UniversalValidationRunner((step, root) -> {
            throw new AssertionError("command execution not expected after blocked preflight");
        }).run(detected, temp.resolve("all-environment-kinds-run"));

        var preflight = result.steps().get(0);
        String report = Files.readString(Path.of(preflight.logFile()));
        assertEquals(BLOCKED, preflight.outcome());
        assertTrue(report.contains("tool.available:EXECUTABLE:PASS_NONFINAL"));
        assertTrue(report.contains("renderer.directory:SOURCE_DIRECTORY:PASS_NONFINAL"));
        assertTrue(report.contains("signing.fixture:SOURCE_FILE:PASS_NONFINAL"));
        assertTrue(report.contains("renderer.launcher:EXECUTABLE_SOURCE_FILE:MISSING_REQUIRED"));
        assertTrue(report.contains("font.missing:FONT_FAMILY:MISSING_REQUIRED"));
        assertTrue(report.matches("(?s).*environment_requirements_sha256=[0-9a-f]{64}.*"));
        JsonNode receipt = new ObjectMapper().readTree(result.receiptFile().toFile());
        assertTrue(receipt.path("environment_requirements_sha256").asText().matches("[0-9a-f]{64}"));
    }

    @Test
    void rejectsRunRootInsideSourceBeforeCreatingIt() throws Exception {
        Path source = Files.createDirectory(temp.resolve("source"));
        Files.writeString(source.resolve("openapi.yaml"), "openapi: 3.1.0\npaths: {}\n");
        var profile = new StandardValidationProfileDetector().detect("overlap", source);
        Path prohibited = source.resolve("generated-run");

        assertThrows(IllegalArgumentException.class,
                () -> new UniversalValidationRunner((step, root) ->
                        new UniversalValidationRunner.StepExecution(PASS_NONFINAL, 0, "", false, "test"))
                        .run(profile, prohibited));
        assertFalse(Files.exists(prohibited));
    }

    @Test
    void rejectsNonemptyRunRootWithoutOverwritingEvidence() throws Exception {
        Path source = Files.createDirectory(temp.resolve("source"));
        Files.writeString(source.resolve("openapi.yaml"), "openapi: 3.1.0\npaths: {}\n");
        Path run = Files.createDirectory(temp.resolve("run"));
        Path existing = run.resolve("existing.txt");
        Files.writeString(existing, "preserve");
        var profile = new StandardValidationProfileDetector().detect("nonempty", source);

        assertThrows(IllegalArgumentException.class,
                () -> new UniversalValidationRunner().run(profile, run));
        assertEquals("preserve", Files.readString(existing));
    }

    @Test
    void rejectsDuplicateOpenApiOperationIdsAndMissingLocalReferences() throws Exception {
        Path source = Files.createDirectory(temp.resolve("source"));
        Files.writeString(source.resolve("openapi.yaml"), """
                openapi: 3.1.0
                info: {title: invalid, version: 1}
                paths:
                  /one:
                    get:
                      operationId: duplicate
                      responses: {'200': {description: ok}}
                  /two:
                    post:
                      operationId: duplicate
                      responses:
                        '200': {$ref: '#/components/responses/Missing'}
                """);
        var profile = new StandardValidationProfileDetector().detect("invalid-openapi", source);

        var result = new UniversalValidationRunner((step, root) -> {
            throw new AssertionError("command execution not expected");
        }).run(profile, temp.resolve("run"));

        var openApi = result.steps().stream().filter(step -> step.stepId().equals("openapi.contract"))
                .findFirst().orElseThrow();
        assertEquals(FAIL, openApi.outcome());
        String log = Files.readString(Path.of(openApi.logFile()));
        assertTrue(log.contains("OPENAPI_OPERATION_ID_DUPLICATED:duplicate"));
        assertTrue(log.contains("OPENAPI_LOCAL_REF_MISSING:#/components/responses/Missing"));
    }

    @Test
    void validatesEveryDiscoveredOpenApiContractIndependently() throws Exception {
        Path source = Files.createDirectory(temp.resolve("source"));
        Path contracts = Files.createDirectories(source.resolve("contracts/openapi"));
        Files.writeString(contracts.resolve("a-service.json"), """
                {"openapi":"3.1.0","info":{"title":"a","version":"1"},"paths":{
                  "/health":{"get":{"operationId":"healthA","responses":{"200":{"description":"ok"}}}}
                }}
                """);
        Files.writeString(contracts.resolve("b-service.json"), """
                {"openapi":"3.1.0","info":{"title":"b"},"paths":{}}
                """);
        Path fixture = Files.createDirectories(source.resolve("fixtures/example"));
        Files.writeString(fixture.resolve("openapi.yaml"), "openapi: invalid-fixture\n");
        var profile = new StandardValidationProfileDetector().detect("multi-openapi", source);

        assertTrue(profile.steps().stream().anyMatch(step -> step.stepId().equals("openapi.contract")));
        assertTrue(profile.steps().stream().anyMatch(step -> step.stepId().equals("openapi.contract-2")));
        assertEquals(2, profile.steps().stream().filter(step ->
                step.stepId().startsWith("openapi.contract")).count());
        var result = new UniversalValidationRunner((step, root) ->
                new UniversalValidationRunner.StepExecution(PASS_NONFINAL, 0, "pass", false, "test"))
                .run(profile, temp.resolve("run"));

        assertEquals(PASS_NONFINAL, result.steps().stream()
                .filter(step -> step.stepId().equals("openapi.contract")).findFirst().orElseThrow().outcome());
        var second = result.steps().stream().filter(step -> step.stepId().equals("openapi.contract-2"))
                .findFirst().orElseThrow();
        assertEquals(FAIL, second.outcome());
        String log = Files.readString(Path.of(second.logFile()));
        assertTrue(log.contains("OPENAPI_INFO_REQUIRED_FIELDS_MISSING"));
        assertTrue(log.contains("OPENAPI_PATHS_EMPTY_OR_INVALID"));
    }

    @Test
    void evidenceMetaValidationDetectsTamperedPassLog() throws Exception {
        Path logs = Files.createDirectory(temp.resolve("logs"));
        Path log = logs.resolve("structure.inventory.log");
        Files.writeString(log, "inventory-pass");
        String environmentSha = Hashing.sha256("test-environment");
        Instant started = Instant.now();
        var pass = new UniversalValidationRunner.StepResult(
                "structure.inventory", STRUCTURE_STATIC,
                UniversalValidationProfile.StepKind.INVENTORY, true, PASS_NONFINAL, 0,
                Hashing.file(log), environmentSha, log.toString(), false,
                "INVENTORY_VALID", started, started.plusMillis(1));

        var valid = UniversalValidationRunner.validateEvidence(
                java.util.List.of(pass), logs, environmentSha);
        assertEquals(PASS_NONFINAL, valid.outcome());

        Files.writeString(log, "tampered-after-pass");
        var tampered = UniversalValidationRunner.validateEvidence(
                java.util.List.of(pass), logs, environmentSha);
        assertEquals(FAIL, tampered.outcome());
        assertTrue(tampered.output().contains("LOG_SHA256_MISMATCH"));
    }

    @Test
    void completesAllSevenGroupsOnlyWithVerifiedLineageAndFinalEvidence() throws Exception {
        Path source = Files.createDirectory(temp.resolve("complete-source"));
        Files.writeString(source.resolve("program.txt"), "portable workflow");
        var profile = completeProfile(source);
        var runner = new UniversalValidationRunner((step, root) -> {
            if (step.kind() == UniversalValidationProfile.StepKind.WORKFLOW_LINEAGE) {
                WorkflowLineageTestFixture.write(root, "Complete workflow");
            }
            return new UniversalValidationRunner.StepExecution(
                    PASS_NONFINAL, 0, "pass:" + step.stepId(), false, "SYNTHETIC_EXECUTED");
        });

        var result = runner.run(profile, temp.resolve("complete-run"));

        assertEquals(PASS_NONFINAL, result.overallOutcome());
        assertTrue(result.groupOutcomes().values().stream().allMatch(outcome -> outcome == PASS_NONFINAL));
        var receipt = new ObjectMapper().readTree(result.receiptFile().toFile());
        assertEquals("PASS_NONFINAL", receipt.path("final_evidence_integrity").path("outcome").asText());
        assertEquals(result.steps().stream().filter(step -> step.outcome() == PASS_NONFINAL).count(),
                receipt.path("final_evidence_integrity").path("verified_pass_step_count").asLong());
    }

    @Test
    void finalEvidenceSealIncludesOperationsAndFailsOnPostStepLogMutation() throws Exception {
        Path source = Files.createDirectory(temp.resolve("tampered-source"));
        Files.writeString(source.resolve("program.txt"), "portable workflow");
        var profile = completeProfile(source);
        Path run = temp.resolve("tampered-run");
        var runner = new UniversalValidationRunner((step, root) -> {
            if (step.kind() == UniversalValidationProfile.StepKind.WORKFLOW_LINEAGE) {
                WorkflowLineageTestFixture.write(root, "Tamper workflow");
            }
            if (step.kind() == UniversalValidationProfile.StepKind.RERUN_TEST) {
                Files.writeString(run.resolve("step-logs/environment.preflight.log"), "tampered");
            }
            return new UniversalValidationRunner.StepExecution(
                    PASS_NONFINAL, 0, "pass:" + step.stepId(), false, "SYNTHETIC_EXECUTED");
        });

        var result = runner.run(profile, run);

        assertEquals(FAIL, result.overallOutcome());
        assertEquals(FAIL, result.groupOutcomes().get(
                UniversalValidationProfile.VerificationGroup.EVIDENCE_DECISION));
        var receipt = new ObjectMapper().readTree(result.receiptFile().toFile());
        assertEquals("FAIL", receipt.path("final_evidence_integrity").path("outcome").asText());
        assertTrue(Files.readString(Path.of(
                receipt.path("final_evidence_integrity").path("log_file").asText()))
                .contains("LOG_SHA256_MISMATCH"));
    }

    private static UniversalValidationProfile.Profile completeProfile(Path source) {
        List<UniversalValidationProfile.Step> steps = new ArrayList<>();
        steps.add(internalStep("environment.preflight", STRUCTURE_STATIC,
                UniversalValidationProfile.StepKind.ENVIRONMENT_PREFLIGHT, List.of()));
        steps.add(internalStep("structure.inventory", STRUCTURE_STATIC,
                UniversalValidationProfile.StepKind.INVENTORY, List.of("environment.preflight")));
        steps.add(internalStep("validator.meta-check", STRUCTURE_STATIC,
                UniversalValidationProfile.StepKind.VALIDATOR_META_CHECK, List.of("structure.inventory")));
        List<String> functionalIds = new ArrayList<>();
        for (var kind : List.of(
                UniversalValidationProfile.StepKind.NEGATIVE_TEST,
                UniversalValidationProfile.StepKind.RETRY_TEST,
                UniversalValidationProfile.StepKind.BLOCKING_TEST)) {
            String id = "functional." + kind.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
            steps.add(executableStep(id, COMPONENT_AND_NEGATIVE, kind, List.of("validator.meta-check")));
            functionalIds.add(id);
        }
        List<String> e2eIds = new ArrayList<>();
        for (var kind : List.of(
                UniversalValidationProfile.StepKind.E2E_REQUEST_FLOW,
                UniversalValidationProfile.StepKind.E2E_RENDER_OR_PRODUCE,
                UniversalValidationProfile.StepKind.E2E_ARTIFACT_READBACK,
                UniversalValidationProfile.StepKind.E2E_TESTER_CHECK,
                UniversalValidationProfile.StepKind.E2E_AUDIT_CHECK,
                UniversalValidationProfile.StepKind.E2E_EXPOSURE_DECISION)) {
            String id = "e2e." + kind.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
            steps.add(executableStep(id, END_TO_END_LINEAGE, kind, functionalIds));
            e2eIds.add(id);
        }
        steps.add(executableStep("e2e.workflow-lineage", END_TO_END_LINEAGE,
                UniversalValidationProfile.StepKind.WORKFLOW_LINEAGE, e2eIds));
        steps.add(internalStep("evidence.verify", END_TO_END_LINEAGE,
                UniversalValidationProfile.StepKind.EVIDENCE_VERIFICATION,
                List.of("e2e.workflow-lineage")));
        for (var kind : List.of(
                UniversalValidationProfile.StepKind.INTERRUPTION_TEST,
                UniversalValidationProfile.StepKind.RESUME_TEST,
                UniversalValidationProfile.StepKind.ROLLBACK_TEST,
                UniversalValidationProfile.StepKind.RERUN_TEST)) {
            String id = "operations." + kind.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
            steps.add(executableStep(id, OPERATIONAL_RESILIENCE, kind, List.of("evidence.verify")));
        }
        return new UniversalValidationProfile.Profile(
                "complete-portable", source, Set.of("SYNTHETIC"), List.of(), steps, Map.of());
    }

    private static UniversalValidationProfile.Step internalStep(
            String id, UniversalValidationProfile.Phase phase,
            UniversalValidationProfile.StepKind kind, List<String> dependencies) {
        return new UniversalValidationProfile.Step(
                id, phase, kind, true, List.of(), Path.of(""), Duration.ofMinutes(2), dependencies);
    }

    private static UniversalValidationProfile.Step executableStep(
            String id, UniversalValidationProfile.Phase phase,
            UniversalValidationProfile.StepKind kind, List<String> dependencies) {
        return new UniversalValidationProfile.Step(
                id, phase, kind, true, List.of("npm", "--offline", "run", "synthetic"),
                Path.of(""), Duration.ofMinutes(2), dependencies);
    }
}
