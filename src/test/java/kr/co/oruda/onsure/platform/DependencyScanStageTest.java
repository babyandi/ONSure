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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers the new DEPENDENCY_SCAN stage (BuiltInStages) that wires
 * {@link VulnerabilityDenylistVerifier} -- previously only run against ONSURE's own pom.xml in
 * {@link BuildLaneReceiptService} -- into the generic validation engine so any Maven target gets
 * the same denylist check.
 *
 * <p>FAIL/PASS scenarios exercise {@link BuiltInStages#runDependencyScan} with a synthetic
 * temp-file denylist so the real, production contracts/dependency-vulnerability-denylist.v1.json
 * is never touched or mutated by tests. The stage-gating ({@code supports()}) and its absence
 * from the finding set for non-Maven targets is exercised through the real {@link
 * BuiltInStages#defaults()} list, which is what {@link ValidationEngine} actually runs.
 */
class DependencyScanStageTest {
    @TempDir Path temp;

    @Test
    void supportsIsTrueOnlyWhenTargetSourceRootHasAPomXml() throws Exception {
        ValidatorStage stage = dependencyScanStage();

        Path withPom = temp.resolve("with-pom");
        Files.createDirectories(withPom);
        Files.writeString(withPom.resolve("pom.xml"), "<project></project>");
        assertTrue(stage.supports(context(withPom)));

        Path withoutPom = temp.resolve("without-pom");
        Files.createDirectories(withoutPom);
        assertFalse(stage.supports(context(withoutPom)));
    }

    @Test
    void engineSkipsTheStageEntirelyWhenTargetHasNoPomXml() throws Exception {
        Path noPom = temp.resolve("non-maven-target");
        Files.createDirectories(noPom);
        ValidationContext context = context(noPom);
        ValidatorStage stage = dependencyScanStage();

        assertFalse(stage.supports(context));
        // Mirrors ValidationEngine.runInternal's stage loop ("if (!stage.supports(context))
        // continue;"): an unsupported stage is skipped outright and contributes no StageResult,
        // findings, or evidence -- a no-op skip, not an auto-PASS and not a forced FAIL.
        if (stage.supports(context)) {
            context.addStageResult(stage.execute(context));
        }

        assertTrue(context.stageResults().isEmpty());
        assertTrue(context.findings().isEmpty());
    }

    @Test
    void failsWithAFindingWhenPomDeclaresADenylistedDependency() throws Exception {
        Path targetRoot = temp.resolve("vulnerable-target");
        Files.createDirectories(targetRoot);
        Files.writeString(targetRoot.resolve("pom.xml"), """
                <project>
                  <dependencies>
                    <dependency><groupId>org.example</groupId><artifactId>vulnerable-lib</artifactId><version>1.2.3</version></dependency>
                  </dependencies>
                </project>
                """);
        Path denylist = temp.resolve("denylist.json");
        Files.writeString(denylist, """
                {"contract":"ONSURE_DEPENDENCY_VULNERABILITY_DENYLIST_V1","denylisted_dependencies":[
                  {"groupId":"org.example","artifactId":"vulnerable-lib","version":"1.2.3","cve":"CVE-2099-0001","severity":"CRITICAL","reason":"remote code execution"}
                ]}
                """);
        ValidationContext context = context(targetRoot);

        StageResult stageResult = BuiltInStages.runDependencyScan(context, denylist);

        assertEquals(Decision.FAIL, stageResult.decision());
        assertEquals(1, context.findings().size());
        Finding finding = context.findings().get(0);
        assertEquals("DEPENDENCY_VULNERABILITY", finding.category());
        assertEquals(Severity.CRITICAL, finding.severity());
        assertTrue(finding.title().contains("org.example:vulnerable-lib"));
        assertTrue(finding.description().contains("CVE-2099-0001"));
        assertFalse(finding.evidenceIds().isEmpty());
        assertTrue(context.evidence().stream()
                .anyMatch(value -> "DEPENDENCY_DENYLIST_MATCH".equals(value.evidenceType())));
    }

    @Test
    void passesWhenPomDependenciesAreClean() throws Exception {
        Path targetRoot = temp.resolve("clean-target");
        Files.createDirectories(targetRoot);
        Files.writeString(targetRoot.resolve("pom.xml"), """
                <project>
                  <dependencies>
                    <dependency><groupId>org.example</groupId><artifactId>safe-lib</artifactId><version>2.0.0</version></dependency>
                  </dependencies>
                </project>
                """);
        Path denylist = temp.resolve("denylist.json");
        Files.writeString(denylist, """
                {"contract":"ONSURE_DEPENDENCY_VULNERABILITY_DENYLIST_V1","denylisted_dependencies":[
                  {"groupId":"org.example","artifactId":"vulnerable-lib","version":"1.2.3","cve":"CVE-2099-0001","severity":"CRITICAL","reason":"remote code execution"}
                ]}
                """);
        ValidationContext context = context(targetRoot);

        StageResult stageResult = BuiltInStages.runDependencyScan(context, denylist);

        assertEquals(Decision.PASS, stageResult.decision());
        assertTrue(context.findings().isEmpty());
    }

    private static ValidatorStage dependencyScanStage() {
        return BuiltInStages.defaults().stream()
                .filter(value -> "DEPENDENCY_SCAN".equals(value.stageId()))
                .findFirst().orElseThrow();
    }

    private ValidationContext context(Path sourceRoot) {
        Instant now = Instant.now();
        ValidationTarget target = new ValidationTarget(
                "target-dependency-scan", "Target", TargetType.GENERAL_SOFTWARE, sourceRoot,
                "sha256:" + "a".repeat(64), GenericManifestTargetAdapter.ID,
                "ONSURE_DEFAULT_POLICY_V1", "LOCAL_DEVELOPMENT");
        ValidationJob job = new ValidationJob(
                "job-dependency-scan", target.targetId(), JobStatus.RUNNING, now, now, null, null);
        return new ValidationContext(target, job, new GenericManifestTargetAdapter(), temp.resolve("run"));
    }
}
