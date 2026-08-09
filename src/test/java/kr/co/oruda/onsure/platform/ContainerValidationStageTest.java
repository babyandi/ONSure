package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import kr.co.oruda.onsure.assurance.Decision;
import kr.co.oruda.onsure.platform.ValidationModel.Finding;
import kr.co.oruda.onsure.platform.ValidationModel.JobStatus;
import kr.co.oruda.onsure.platform.ValidationModel.Severity;
import kr.co.oruda.onsure.platform.ValidationModel.StageResult;
import kr.co.oruda.onsure.platform.ValidationModel.TargetType;
import kr.co.oruda.onsure.platform.ValidationModel.ValidationJob;
import kr.co.oruda.onsure.platform.ValidationModel.ValidationTarget;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers the new CONTAINER_VALIDATION stage (BuiltInStages) that closes the "container validation"
 * execution-type gap in FR-05-A: when a target's source root has its own Dockerfile, the stage
 * builds it and runs a bounded smoke-test container, following the same PASS/FAIL-oracle
 * philosophy as {@code RuntimeFixtureStage}.
 *
 * <p>Guarded exactly like {@code DatabaseSnapshotServiceTest} guards on Postgres reachability: a
 * {@code @BeforeAll} check for {@code docker info} succeeding, with {@link Assumptions#assumeTrue}
 * skipping the whole class otherwise. This must never become a hard dependency for {@code mvn
 * test} in environments without Docker.
 *
 * <p>Uses the already locally-cached {@code alpine:3.20} base image throughout, so no test needs
 * outbound network access to pull an image.
 */
class ContainerValidationStageTest {
    private static boolean dockerAvailable;

    @TempDir Path temp;

    @BeforeAll
    static void checkDockerAvailability() {
        dockerAvailable = runsSuccessfully(List.of("docker", "info"), 15);
    }

    @Test
    void supportsIsTrueOnlyWhenTargetSourceRootHasADockerfile() throws Exception {
        Assumptions.assumeTrue(dockerAvailable, "Docker not reachable; skipping container validation tests");
        ValidatorStage stage = containerValidationStage();

        Path withDockerfile = temp.resolve("with-dockerfile");
        Files.createDirectories(withDockerfile);
        Files.writeString(withDockerfile.resolve("Dockerfile"), "FROM alpine:3.20\nCMD [\"true\"]\n");
        assertTrue(stage.supports(context(withDockerfile)));

        Path withoutDockerfile = temp.resolve("without-dockerfile");
        Files.createDirectories(withoutDockerfile);
        assertFalse(stage.supports(context(withoutDockerfile)));
    }

    @Test
    void engineSkipsTheStageEntirelyWhenTargetHasNoDockerfile() throws Exception {
        Assumptions.assumeTrue(dockerAvailable, "Docker not reachable; skipping container validation tests");
        Path noDockerfile = temp.resolve("non-container-target");
        Files.createDirectories(noDockerfile);
        ValidationContext context = context(noDockerfile);
        ValidatorStage stage = containerValidationStage();

        assertFalse(stage.supports(context));
        // Mirrors ValidationEngine.runInternal's stage loop: an unsupported stage is skipped
        // outright and contributes no StageResult, findings, or evidence -- a no-op skip, not an
        // auto-PASS and not a forced FAIL.
        if (stage.supports(context)) {
            context.addStageResult(stage.execute(context));
        }

        assertTrue(context.stageResults().isEmpty());
        assertTrue(context.findings().isEmpty());
    }

    @Test
    void passesWhenDockerfileBuildsAndTheSmokeTestContainerExitsZero() throws Exception {
        Assumptions.assumeTrue(dockerAvailable, "Docker not reachable; skipping container validation tests");
        Path targetRoot = temp.resolve("passing-target");
        Files.createDirectories(targetRoot);
        Files.writeString(targetRoot.resolve("Dockerfile"), "FROM alpine:3.20\nCMD [\"true\"]\n");
        ValidationContext context = context(targetRoot);
        ValidatorStage stage = containerValidationStage();

        StageResult stageResult = stage.execute(context);

        assertEquals(Decision.PASS, stageResult.decision());
        assertTrue(context.findings().isEmpty());
        assertTrue(context.evidence().stream()
                .anyMatch(value -> "CONTAINER_BUILD_RESULT".equals(value.evidenceType())
                        && value.evidenceId().startsWith("EV-CONTAINER-")));
        assertTrue(context.evidence().stream()
                .anyMatch(value -> "CONTAINER_RUN_RESULT".equals(value.evidenceType())
                        && value.evidenceId().startsWith("EV-CONTAINER-")));
    }

    @Test
    void failsWithAFindingWhenTheSmokeTestContainerExitsNonZero() throws Exception {
        Assumptions.assumeTrue(dockerAvailable, "Docker not reachable; skipping container validation tests");
        Path targetRoot = temp.resolve("failing-run-target");
        Files.createDirectories(targetRoot);
        Files.writeString(targetRoot.resolve("Dockerfile"), "FROM alpine:3.20\nCMD [\"false\"]\n");
        ValidationContext context = context(targetRoot);
        ValidatorStage stage = containerValidationStage();

        StageResult stageResult = stage.execute(context);

        assertEquals(Decision.FAIL, stageResult.decision());
        assertEquals(1, context.findings().size());
        Finding finding = context.findings().get(0);
        assertEquals("CONTAINER_BUILD_OR_RUNTIME_FAILURE", finding.category());
        assertEquals(Severity.HIGH, finding.severity());
        assertFalse(finding.evidenceIds().isEmpty());
    }

    @Test
    void failsWithAFindingWhenTheDockerfileFailsToBuild() throws Exception {
        Assumptions.assumeTrue(dockerAvailable, "Docker not reachable; skipping container validation tests");
        Path targetRoot = temp.resolve("failing-build-target");
        Files.createDirectories(targetRoot);
        // "RUN" against a command that does not exist fails the build itself, entirely locally
        // (no image pull, no network) -- distinct from the run-step failure covered above.
        Files.writeString(targetRoot.resolve("Dockerfile"),
                "FROM alpine:3.20\nRUN this-command-does-not-exist-anywhere-xyz\n");
        ValidationContext context = context(targetRoot);
        ValidatorStage stage = containerValidationStage();

        StageResult stageResult = stage.execute(context);

        assertEquals(Decision.FAIL, stageResult.decision());
        assertEquals(1, context.findings().size());
        Finding finding = context.findings().get(0);
        assertEquals("CONTAINER_BUILD_OR_RUNTIME_FAILURE", finding.category());
        assertTrue(finding.title().toLowerCase().contains("build"));
    }

    @Test
    void builtImageIsAlwaysRemovedAfterTheStageRunsSuccessOrFailure() throws Exception {
        Assumptions.assumeTrue(dockerAvailable, "Docker not reachable; skipping container validation tests");
        ValidatorStage stage = containerValidationStage();

        assertTrue(onsureContainerValidationImages().isEmpty(),
                "test environment already has leftover onsure-container-validation images before this test ran");

        Path passTarget = temp.resolve("cleanup-pass-target");
        Files.createDirectories(passTarget);
        Files.writeString(passTarget.resolve("Dockerfile"), "FROM alpine:3.20\nCMD [\"true\"]\n");
        stage.execute(context(passTarget));
        assertTrue(onsureContainerValidationImages().isEmpty(),
                "image built for the PASSing target was not cleaned up: " + onsureContainerValidationImages());

        Path failTarget = temp.resolve("cleanup-fail-target");
        Files.createDirectories(failTarget);
        Files.writeString(failTarget.resolve("Dockerfile"), "FROM alpine:3.20\nCMD [\"false\"]\n");
        stage.execute(context(failTarget));
        assertTrue(onsureContainerValidationImages().isEmpty(),
                "image built for the FAILing target was not cleaned up: " + onsureContainerValidationImages());
    }

    /**
     * Proves {@code --network none} is really enforced on the smoke-test container, not merely
     * intended: the container's CMD attempts an outbound HTTP request and reports the wget exit
     * code as its own exit code. Manually verified during development (outside this test) that
     * the very same Dockerfile run WITHOUT {@code --network none} in this sandbox DOES reach the
     * network and exits 0 -- so a FAIL here is caused by the stage's network isolation, not by the
     * sandbox lacking general internet access.
     */
    @Test
    void networkNoneOnTheSmokeTestContainerReallyBlocksNetworkAccess() throws Exception {
        Assumptions.assumeTrue(dockerAvailable, "Docker not reachable; skipping container validation tests");
        Path targetRoot = temp.resolve("network-isolation-target");
        Files.createDirectories(targetRoot);
        Files.writeString(targetRoot.resolve("Dockerfile"), """
                FROM alpine:3.20
                CMD ["sh", "-c", "wget -T 5 -q -O- http://example.com >/dev/null 2>&1; exit $?"]
                """);
        ValidationContext context = context(targetRoot);
        ValidatorStage stage = containerValidationStage();

        StageResult stageResult = stage.execute(context);

        assertEquals(Decision.FAIL, stageResult.decision());
        assertEquals(1, context.findings().size());
        assertTrue(context.findings().get(0).description().toLowerCase().contains("exited with code"));
    }

    private static ValidatorStage containerValidationStage() {
        return BuiltInStages.defaults().stream()
                .filter(value -> "CONTAINER_VALIDATION".equals(value.stageId()))
                .findFirst().orElseThrow();
    }

    private ValidationContext context(Path sourceRoot) {
        Instant now = Instant.now();
        ValidationTarget target = new ValidationTarget(
                "target-container-validation-" + sourceRoot.getFileName(), "Target",
                TargetType.GENERAL_SOFTWARE, sourceRoot,
                "sha256:" + "a".repeat(64), GenericManifestTargetAdapter.ID,
                "ONSURE_DEFAULT_POLICY_V1", "LOCAL_DEVELOPMENT");
        ValidationJob job = new ValidationJob(
                "job-container-validation", target.targetId(), JobStatus.RUNNING, now, now, null, null);
        return new ValidationContext(target, job, new GenericManifestTargetAdapter(), temp.resolve("run"));
    }

    /** Direct {@code docker images} process call, deliberately not going through the production stage. */
    private static List<String> onsureContainerValidationImages() throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                "docker", "images", "--format", "{{.Repository}}:{{.Tag}}")
                .redirectErrorStream(true)
                .start();
        List<String> lines = new ArrayList<>();
        try (var reader = process.inputReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("onsure-container-validation:")) lines.add(line);
            }
        }
        process.waitFor(15, TimeUnit.SECONDS);
        return lines;
    }

    private static boolean runsSuccessfully(List<String> command, int timeoutSeconds) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception unavailable) {
            return false;
        }
    }
}
