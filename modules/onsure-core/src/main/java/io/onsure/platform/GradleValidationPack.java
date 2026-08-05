package io.onsure.platform;

import static io.onsure.platform.StandardValidationPackSupport.BUILD_TIMEOUT;
import static io.onsure.platform.StandardValidationPackSupport.TEST_TIMEOUT;
import static io.onsure.platform.StandardValidationPackSupport.step;

import io.onsure.platform.UniversalValidationProfile.Phase;
import io.onsure.platform.UniversalValidationProfile.Step;
import io.onsure.platform.UniversalValidationProfile.StepKind;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Standard Gradle-wrapper offline build profile. */
public final class GradleValidationPack implements ValidationPack {
    @Override public String id() { return "gradle"; }

    @Override
    public Contribution detect(Path root) {
        try {
            return detectInternal(root);
        } catch (Exception error) {
            throw new IllegalArgumentException("GRADLE_VALIDATION_PACK_DETECTION_FAILED", error);
        }
    }

    private Contribution detectInternal(Path root) throws Exception {
        if (!StandardValidationPackSupport.file(root, "gradlew")
                || !(StandardValidationPackSupport.file(root, "build.gradle")
                || StandardValidationPackSupport.file(root, "build.gradle.kts"))) {
            return Contribution.none();
        }
        Path buildFile = StandardValidationPackSupport.file(root, "build.gradle")
                ? root.resolve("build.gradle") : root.resolve("build.gradle.kts");
        String build = StandardValidationPackSupport.readConfig(buildFile);
        List<Step> steps = new ArrayList<>();
        steps.add(step("gradle.clean-test", Phase.COMPONENT_AND_NEGATIVE, StepKind.BUILD,
                List.of("bash", "gradlew", "--offline", "clean", "test"), BUILD_TIMEOUT,
                List.of("validator.meta-check")));
        addFunctionalFacet(root, steps, "negative-paths", StepKind.NEGATIVE_TEST,
                List.of("negative", "failure", "adversarial", "tamper", "invalid"),
                "*Negative*", "*Failure*", "*Adversarial*", "*Tamper*", "*Invalid*");
        addFunctionalFacet(root, steps, "retry-paths", StepKind.RETRY_TEST,
                List.of("retry", "resume", "replay", "idempotent"),
                "*Retry*", "*Resume*", "*Replay*", "*Idempotent*");
        addFunctionalFacet(root, steps, "blocking-paths", StepKind.BLOCKING_TEST,
                List.of("blocking", "blocked", "boundary", "gate", "approval", "authorization", "permission"),
                "*Blocking*", "*Blocked*", "*Boundary*", "*Gate*", "*Approval*",
                "*Authorization*", "*Permission*");
        addConnectedWorkflowFacets(root, steps);
        addOperationalFacets(root, steps);
        if (build.contains("integrationTest")) {
            steps.add(step("gradle.integration", Phase.END_TO_END_LINEAGE, StepKind.INTEGRATION_TEST,
                    List.of("bash", "gradlew", "--offline", "integrationTest"), BUILD_TIMEOUT,
                    List.of("gradle.clean-test")));
        }
        return new Contribution(Set.of("JAVA", "GRADLE"), steps);
    }

    private static void addFunctionalFacet(Path root, List<Step> steps, String id, StepKind kind,
            List<String> signals, String... testPatterns) throws Exception {
        if (!StandardValidationPackSupport.testSignal(
                root, "src/test/java", Set.of(".java"), signals)) return;
        List<String> command = new ArrayList<>(List.of(
                "bash", "gradlew", "--offline", "test"));
        for (String pattern : testPatterns) {
            command.add("--tests");
            command.add(pattern);
        }
        steps.add(step("gradle." + id, Phase.COMPONENT_AND_NEGATIVE, kind,
                command, TEST_TIMEOUT, List.of("gradle.clean-test")));
    }

    private static void addConnectedWorkflowFacets(Path root, List<Step> steps) throws Exception {
        if (!StandardValidationPackSupport.testSignal(root, "src/test/java", Set.of(".java"),
                List.of("class connectedworkflowvalidationtest"))) return;
        String previous = null;
        List<StepKind> kinds = List.of(
                StepKind.E2E_REQUEST_FLOW, StepKind.E2E_RENDER_OR_PRODUCE,
                StepKind.E2E_ARTIFACT_READBACK, StepKind.E2E_TESTER_CHECK,
                StepKind.E2E_AUDIT_CHECK, StepKind.E2E_EXPOSURE_DECISION,
                StepKind.WORKFLOW_LINEAGE);
        List<String> methods = List.of(
                "requestFlow", "renderOrProduce", "artifactReadback", "testerCheck",
                "auditCheck", "exposureDecision", "workflowLineage");
        for (int index = 0; index < kinds.size(); index++) {
            String id = "gradle.connected-" + kebab(methods.get(index));
            List<String> dependencies = previous == null
                    ? List.of("gradle.clean-test") : List.of(previous);
            steps.add(step(id, Phase.END_TO_END_LINEAGE, kinds.get(index),
                    List.of("bash", "gradlew", "--offline", "-Donsure.validation.connected=true",
                            "test", "--tests", "ConnectedWorkflowValidationTest." + methods.get(index)),
                    TEST_TIMEOUT, dependencies));
            previous = id;
        }
    }

    private static void addOperationalFacets(Path root, List<Step> steps) throws Exception {
        if (!StandardValidationPackSupport.testSignal(root, "src/test/java", Set.of(".java"),
                List.of("class operationalresiliencevalidationtest"))) return;
        List<StepKind> kinds = List.of(
                StepKind.INTERRUPTION_TEST, StepKind.RESUME_TEST,
                StepKind.ROLLBACK_TEST, StepKind.RERUN_TEST);
        List<String> methods = List.of("interruption", "resume", "rollback", "rerun");
        for (int index = 0; index < kinds.size(); index++) {
            steps.add(step("gradle.operations-" + methods.get(index),
                    Phase.OPERATIONAL_RESILIENCE, kinds.get(index),
                    List.of("bash", "gradlew", "--offline", "-Donsure.validation.operations=true",
                            "test", "--tests", "OperationalResilienceValidationTest." + methods.get(index)),
                    TEST_TIMEOUT, List.of("evidence.verify")));
        }
    }

    private static String kebab(String value) {
        return value.replaceAll("([a-z])([A-Z])", "$1-$2")
                .toLowerCase(java.util.Locale.ROOT);
    }
}
