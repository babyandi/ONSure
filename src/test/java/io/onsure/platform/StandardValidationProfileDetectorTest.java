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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandardValidationProfileDetectorTest {
    @TempDir Path temp;

    @Test
    void detectsNeutralMultiLanguageProjectWithoutTargetManifest() throws Exception {
        Files.writeString(temp.resolve("pom.xml"), """
                <project><build><plugins><plugin><artifactId>maven-failsafe-plugin</artifactId></plugin></plugins></build></project>
                """);
        Files.writeString(temp.resolve("pyproject.toml"), "[tool.pytest.ini_options]\n");
        Files.createDirectories(temp.resolve("tests/integration"));
        Files.writeString(temp.resolve("package.json"), """
                {"scripts":{"test":"node --test","test:integration":"node --test integration","build":"tsc"}}
                """);
        Files.writeString(temp.resolve("openapi.yaml"), "openapi: 3.1.0\n");
        Files.createDirectories(temp.resolve("db/migration"));

        var profile = new StandardValidationProfileDetector().detect("neutral-product", temp);

        assertFalse(Files.exists(temp.resolve("onsure-target.json")));
        assertTrue(profile.technologies().containsAll(
                java.util.Set.of("JAVA", "MAVEN", "PYTHON", "NODE", "OPENAPI", "DATABASE_MIGRATIONS")));
        assertTrue(profile.steps().stream().anyMatch(step -> step.stepId().equals("maven.clean-verify")));
        assertTrue(profile.steps().stream().anyMatch(step -> step.stepId().equals("python.integration")));
        assertTrue(profile.steps().stream().anyMatch(step -> step.stepId().equals("node.integration")));
        assertEquals("DATABASE_RUNTIME_AND_APPROVED_SYNTHETIC_CONNECTION_NOT_CONFIGURED",
                profile.notRunReasons().get(OPERATIONAL_RESILIENCE));
    }

    @Test
    void neverPromotesMissingEndToEndAndOperationsToPass() throws Exception {
        Files.writeString(temp.resolve("pom.xml"), "<project/>");
        var profile = new StandardValidationProfileDetector().detect("maven-only", temp);

        var nothingExecuted = profile.phaseOutcomes(Map.of());
        assertEquals(NOT_RUN, nothingExecuted.get(STRUCTURE_STATIC));
        assertEquals(NOT_RUN, nothingExecuted.get(COMPONENT_AND_NEGATIVE));
        assertEquals(NOT_RUN, nothingExecuted.get(END_TO_END_LINEAGE));
        assertEquals(NOT_RUN, nothingExecuted.get(OPERATIONAL_RESILIENCE));

        var componentPassed = profile.phaseOutcomes(Map.of(
                "environment.preflight", PASS_NONFINAL,
                "structure.inventory", PASS_NONFINAL,
                "validator.meta-check", PASS_NONFINAL,
                "maven.clean-verify", PASS_NONFINAL));
        assertEquals(PASS_NONFINAL, componentPassed.get(STRUCTURE_STATIC));
        assertEquals(NOT_RUN, componentPassed.get(COMPONENT_AND_NEGATIVE));
        assertEquals(NOT_RUN, componentPassed.get(END_TO_END_LINEAGE));
        assertEquals(NOT_RUN, componentPassed.get(OPERATIONAL_RESILIENCE));
    }

    @Test
    void failureDominatesOtherResultsWithinTheSamePhase() throws Exception {
        Files.writeString(temp.resolve("package.json"),
                "{\"scripts\":{\"test\":\"node --test\",\"build\":\"tsc\"}}");
        var profile = new StandardValidationProfileDetector().detect("node", temp);

        var outcomes = profile.phaseOutcomes(Map.of(
                "structure.inventory", PASS_NONFINAL,
                "node.tests", PASS_NONFINAL,
                "node.build", FAIL));
        assertEquals(FAIL, outcomes.get(COMPONENT_AND_NEGATIVE));
    }

    @Test
    void rejectsSymlinkSourceRoot() throws Exception {
        Path real = Files.createDirectory(temp.resolve("real"));
        Path link = temp.resolve("link");
        Files.createSymbolicLink(link, real);
        var detector = new StandardValidationProfileDetector();
        assertThrows(IllegalArgumentException.class, () -> detector.detect("unsafe", link));
    }

    @Test
    void rejectsOversizedDetectionConfigurationBeforeParsing() throws Exception {
        Path packageJson = temp.resolve("package.json");
        try (var channel = Files.newByteChannel(packageJson,
                java.nio.file.StandardOpenOption.CREATE_NEW,
                java.nio.file.StandardOpenOption.WRITE)) {
            channel.position(5L * 1024 * 1024);
            channel.write(java.nio.ByteBuffer.wrap(new byte[] {'}'}));
        }

        var error = assertThrows(IllegalArgumentException.class,
                () -> new StandardValidationProfileDetector().detect("oversized", temp));
        assertTrue(error.getMessage().contains("VALIDATION_CONFIG_INVALID_OR_TOO_LARGE"));
    }

    @Test
    void trustedPackAddsFrameworkE2eAndRecoveryWithoutReplacingCoreGates() throws Exception {
        Files.writeString(temp.resolve("package.json"), "{\"scripts\":{\"test\":\"node --test\"}}");
        ValidationPack pack = new ValidationPack() {
            @Override public String id() { return "renderer"; }

            @Override
            public Contribution detect(Path sourceRoot) {
                var render = new UniversalValidationProfile.Step(
                        "renderer.render", COMPONENT_AND_NEGATIVE,
                        UniversalValidationProfile.StepKind.BUILD, true,
                        List.of("npm", "--offline", "run", "render"), Path.of(""),
                        Duration.ofMinutes(2), List.of("validator.meta-check"));
                var e2e = new UniversalValidationProfile.Step(
                        "renderer.connected-audit", END_TO_END_LINEAGE,
                        UniversalValidationProfile.StepKind.WORKFLOW_LINEAGE, true,
                        List.of("npm", "--offline", "run", "test:e2e"), Path.of(""),
                        Duration.ofMinutes(2), List.of("renderer.render"));
                var recovery = new UniversalValidationProfile.Step(
                        "renderer.recovery", OPERATIONAL_RESILIENCE,
                        UniversalValidationProfile.StepKind.RECOVERY, true,
                        List.of("npm", "--offline", "run", "test:recovery"), Path.of(""),
                        Duration.ofMinutes(2), List.of("evidence.verify"));
                return new Contribution(Set.of("RENDERER"), List.of(
                        new UniversalValidationProfile.EnvironmentRequirement(
                                "renderer.font", UniversalValidationProfile.RequirementKind.FONT_FAMILY,
                                "DejaVu Sans", true),
                        new UniversalValidationProfile.EnvironmentRequirement(
                                "renderer.signing-fixture", UniversalValidationProfile.RequirementKind.SOURCE_FILE,
                                "signing/fixture.json", false)), List.of(render, e2e, recovery));
            }
        };

        var profile = new StandardValidationProfileDetector(List.of(pack)).detect("packed", temp);

        assertTrue(profile.technologies().contains("RENDERER"));
        assertEquals(2, profile.environmentRequirements().size());
        assertEquals(List.of("environment.preflight", "structure.inventory", "validator.meta-check"),
                profile.steps().subList(0, 3).stream().map(UniversalValidationProfile.Step::stepId).toList());
        assertTrue(profile.steps().stream().anyMatch(step -> step.stepId().equals("renderer.connected-audit")));
        var lineage = profile.steps().stream()
                .filter(step -> step.stepId().equals("renderer.connected-audit")).findFirst().orElseThrow();
        assertTrue(lineage.dependsOn().containsAll(List.of(
                "e2e.request-flow", "e2e.render-or-produce", "e2e.artifact-readback",
                "e2e.tester-check", "e2e.audit-check", "e2e.exposure-decision")));
        int evidenceIndex = profile.steps().stream().map(UniversalValidationProfile.Step::stepId).toList()
                .indexOf("evidence.verify");
        int recoveryIndex = profile.steps().stream().map(UniversalValidationProfile.Step::stepId).toList()
                .indexOf("renderer.recovery");
        assertTrue(evidenceIndex > 0 && recoveryIndex > evidenceIndex);
    }

    @Test
    void packCannotReplaceCoreEnvironmentGate() throws Exception {
        ValidationPack pack = new ValidationPack() {
            @Override public String id() { return "unsafe"; }
            @Override public Contribution detect(Path sourceRoot) {
                return new Contribution(Set.of(), List.of(new UniversalValidationProfile.Step(
                        "unsafe.preflight", STRUCTURE_STATIC,
                        UniversalValidationProfile.StepKind.ENVIRONMENT_PREFLIGHT, true,
                        List.of(), Path.of(""), Duration.ofSeconds(1), List.of())));
            }
        };

        assertThrows(IllegalArgumentException.class,
                () -> new StandardValidationProfileDetector(List.of(pack)).detect("unsafe", temp));
    }

    @Test
    void nodeDependenciesRequireLockAndOfflinePreparationBeforeTests() throws Exception {
        Files.writeString(temp.resolve("package.json"), """
                {"scripts":{"test":"vitest"},"devDependencies":{"vitest":"1.0.0"}}
                """);
        var profile = new StandardValidationProfileDetector().detect("node-dependencies", temp);

        assertTrue(profile.environmentRequirements().stream()
                .anyMatch(value -> value.requirementId().equals("node.lockfile") && value.required()));
        var preparation = profile.steps().stream().filter(step -> step.stepId().equals("node.dependencies"))
                .findFirst().orElseThrow();
        assertEquals(List.of("npm", "--offline", "ci", "--ignore-scripts"), preparation.command());
        assertEquals(List.of("node.dependencies"), profile.steps().stream()
                .filter(step -> step.stepId().equals("node.tests")).findFirst().orElseThrow().dependsOn());
    }

    @Test
    void installedNodeScriptPackFillsEveryFunctionalE2eAndRecoveryFacet() throws Exception {
        Files.writeString(temp.resolve("package.json"), """
                {"scripts":{
                  "test":"node --test","test:negative":"node --test","test:retry":"node --test",
                  "test:blocking":"node --test","test:e2e-request":"node --test","render":"node --test",
                  "test:readback":"node --test","test:tester":"node --test","test:audit":"node --test",
                  "test:exposure":"node --test","test:lineage":"node generate-lineage.js",
                  "test:interruption":"node --test","test:resume":"node --test",
                  "test:rollback":"node --test","test:rerun":"node --test"
                }}
                """);

        var profile = new StandardValidationProfileDetector().detect("node-full-pack", temp);

        assertTrue(profile.technologies().contains("NODE_VALIDATION_SCRIPTS"));
        assertFalse(profile.steps().stream().map(UniversalValidationProfile.Step::stepId)
                .anyMatch(id -> id.startsWith("functional.") || id.startsWith("e2e.")
                        || id.startsWith("operations.")));
        for (var kind : List.of(
                UniversalValidationProfile.StepKind.NEGATIVE_TEST,
                UniversalValidationProfile.StepKind.RETRY_TEST,
                UniversalValidationProfile.StepKind.BLOCKING_TEST,
                UniversalValidationProfile.StepKind.E2E_ARTIFACT_READBACK,
                UniversalValidationProfile.StepKind.E2E_AUDIT_CHECK,
                UniversalValidationProfile.StepKind.WORKFLOW_LINEAGE,
                UniversalValidationProfile.StepKind.RERUN_TEST)) {
            assertTrue(profile.steps().stream().anyMatch(step -> step.kind() == kind));
        }
    }

    @Test
    void standardCapabilitiesAreImplementedByIndependentValidationPacks() {
        List<ValidationPack> packs = List.of(
                new MavenValidationPack(), new GradleValidationPack(), new PythonValidationPack(),
                new NodeValidationPack(), new OpenApiValidationPack(), new PostgresqlValidationPack());

        assertEquals(List.of("maven", "gradle", "python", "node", "openapi", "postgresql"),
                packs.stream().map(ValidationPack::id).toList());
    }

    @Test
    void mavenAndPythonNamedTestsFillFunctionalPathFacetsWithFixedOfflineCommands() throws Exception {
        Path maven = Files.createDirectories(temp.resolve("maven/src/test/java/example"));
        Files.writeString(temp.resolve("maven/pom.xml"), "<project/>");
        Files.writeString(maven.resolve("SecurityAdversarialTest.java"), "class SecurityAdversarialTest {}");
        Files.writeString(maven.resolve("ResumeReplayTest.java"), "class ResumeReplayTest {}");
        Files.writeString(maven.resolve("ApprovalBoundaryTest.java"), "class ApprovalBoundaryTest {}");
        var mavenProfile = new StandardValidationProfileDetector().detect("maven-facets", temp.resolve("maven"));
        for (var kind : List.of(
                UniversalValidationProfile.StepKind.NEGATIVE_TEST,
                UniversalValidationProfile.StepKind.RETRY_TEST,
                UniversalValidationProfile.StepKind.BLOCKING_TEST)) {
            var step = mavenProfile.steps().stream().filter(value -> value.kind() == kind)
                    .findFirst().orElseThrow();
            assertEquals("mvn", step.command().get(0));
            assertTrue(step.command().contains("-o"));
            assertEquals(List.of("maven.clean-verify"), step.dependsOn());
        }
        assertFalse(mavenProfile.steps().stream().map(UniversalValidationProfile.Step::stepId)
                .anyMatch(id -> id.startsWith("functional.")));

        Path python = Files.createDirectories(temp.resolve("python/tests"));
        Files.writeString(python.resolve("test_security.py"), "def test_tamper_is_blocked(): pass\n");
        Files.writeString(python.resolve("test_recovery.py"), "def test_retry_and_resume(): pass\n");
        var pythonProfile = new StandardValidationProfileDetector().detect("python-facets", temp.resolve("python"));
        for (var kind : List.of(
                UniversalValidationProfile.StepKind.NEGATIVE_TEST,
                UniversalValidationProfile.StepKind.RETRY_TEST,
                UniversalValidationProfile.StepKind.BLOCKING_TEST)) {
            var step = pythonProfile.steps().stream().filter(value -> value.kind() == kind)
                    .findFirst().orElseThrow();
            assertEquals(List.of("python3", "-m", "unittest", "discover", "-v", "-s", "tests"),
                    step.command());
            assertEquals(List.of("python.tests"), step.dependsOn());
        }
    }

    @Test
    void mavenValidationConventionsFillConnectedAndOperationalFacets() throws Exception {
        Path tests = Files.createDirectories(temp.resolve("src/test/java/example"));
        Files.writeString(temp.resolve("pom.xml"), "<project/>");
        Files.writeString(tests.resolve("SecurityAdversarialTest.java"),
                "class SecurityAdversarialTest { void retryResumeApprovalBoundary() {} }");
        Files.writeString(tests.resolve("ConnectedWorkflowValidationTest.java"),
                "class ConnectedWorkflowValidationTest {}");
        Files.writeString(tests.resolve("OperationalResilienceValidationTest.java"),
                "class OperationalResilienceValidationTest {}");

        var profile = new StandardValidationProfileDetector().detect("maven-complete", temp);

        assertFalse(profile.steps().stream().map(UniversalValidationProfile.Step::stepId)
                .anyMatch(id -> id.startsWith("functional.") || id.startsWith("e2e.")
                        || id.startsWith("operations.")));
        for (var kind : List.of(
                UniversalValidationProfile.StepKind.E2E_REQUEST_FLOW,
                UniversalValidationProfile.StepKind.E2E_RENDER_OR_PRODUCE,
                UniversalValidationProfile.StepKind.E2E_ARTIFACT_READBACK,
                UniversalValidationProfile.StepKind.E2E_TESTER_CHECK,
                UniversalValidationProfile.StepKind.E2E_AUDIT_CHECK,
                UniversalValidationProfile.StepKind.E2E_EXPOSURE_DECISION,
                UniversalValidationProfile.StepKind.WORKFLOW_LINEAGE,
                UniversalValidationProfile.StepKind.INTERRUPTION_TEST,
                UniversalValidationProfile.StepKind.RESUME_TEST,
                UniversalValidationProfile.StepKind.ROLLBACK_TEST,
                UniversalValidationProfile.StepKind.RERUN_TEST)) {
            assertTrue(profile.steps().stream().anyMatch(step -> step.kind() == kind), kind.toString());
        }
        var request = profile.steps().stream()
                .filter(step -> step.kind() == UniversalValidationProfile.StepKind.E2E_REQUEST_FLOW)
                .findFirst().orElseThrow();
        assertTrue(request.dependsOn().containsAll(List.of(
                "maven.clean-verify", "maven.negative-paths",
                "maven.retry-paths", "maven.blocking-paths")));
    }

    @Test
    void detectsNestedPostgresqlFlywayMigrationAndInventoriesIt() throws Exception {
        Path source = Files.createDirectory(temp.resolve("source"));
        Files.writeString(source.resolve("pom-modular.xml"), """
                <project><dependencies><dependency><artifactId>postgresql</artifactId></dependency></dependencies></project>
                """);
        Path migration = Files.createDirectories(source.resolve(
                "modules/database/src/main/resources/db/migration/postgresql"));
        Files.writeString(migration.resolve("V1__create_event.sql"),
                "create table assurance_event(id bigint primary key);\n");

        var profile = new StandardValidationProfileDetector(List.of()).detect("postgresql-nested", source);

        assertTrue(profile.technologies().containsAll(Set.of("DATABASE_MIGRATIONS", "POSTGRESQL")));
        assertTrue(profile.steps().stream().anyMatch(step ->
                step.stepId().equals("postgresql.migration-static")
                        && step.kind() == UniversalValidationProfile.StepKind.DATABASE_MIGRATION));
        var result = new UniversalValidationRunner((step, root) ->
                new UniversalValidationRunner.StepExecution(PASS_NONFINAL, 0, "pass", false, "test"))
                .run(profile, temp.resolve("run"));
        var migrationResult = result.steps().stream()
                .filter(step -> step.stepId().equals("postgresql.migration-static"))
                .findFirst().orElseThrow();
        assertEquals(PASS_NONFINAL, migrationResult.outcome());
        assertTrue(Files.readString(Path.of(migrationResult.logFile())).contains("V1__create_event.sql"));
    }
}
