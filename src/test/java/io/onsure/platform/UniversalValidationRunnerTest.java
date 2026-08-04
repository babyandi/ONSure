package io.onsure.platform;

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
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UniversalValidationRunnerTest {
    @TempDir Path temp;

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
}
