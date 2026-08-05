package io.onsure.platform;

import static io.onsure.platform.StandardValidationPackSupport.TEST_TIMEOUT;
import static io.onsure.platform.StandardValidationPackSupport.step;

import io.onsure.platform.UniversalValidationProfile.Phase;
import io.onsure.platform.UniversalValidationProfile.Step;
import io.onsure.platform.UniversalValidationProfile.StepKind;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Standard pytest/unittest offline source profile. */
public final class PythonValidationPack implements ValidationPack {
    @Override public String id() { return "python"; }

    @Override
    public Contribution detect(Path root) {
        boolean detected = StandardValidationPackSupport.file(root, "pyproject.toml")
                || StandardValidationPackSupport.file(root, "pytest.ini")
                || StandardValidationPackSupport.file(root, "requirements.txt")
                || StandardValidationPackSupport.directory(root, "tests");
        if (!detected) return Contribution.none();
        boolean pytest = StandardValidationPackSupport.file(root, "pytest.ini")
                || StandardValidationPackSupport.contains(root.resolve("pyproject.toml"), "pytest")
                || StandardValidationPackSupport.contains(root.resolve("requirements.txt"), "pytest");
        List<String> command = pytest ? List.of("python3", "-m", "pytest", "-q")
                : List.of("python3", "-m", "unittest", "discover", "-s", "tests");
        List<Step> steps = new ArrayList<>();
        steps.add(step("python.tests", Phase.COMPONENT_AND_NEGATIVE, StepKind.UNIT_TEST,
                command, TEST_TIMEOUT, List.of("validator.meta-check")));
        addFunctionalFacet(root, steps, "negative-paths", StepKind.NEGATIVE_TEST,
                List.of("negative", "failure", "adversarial", "tamper", "invalid"));
        addFunctionalFacet(root, steps, "retry-paths", StepKind.RETRY_TEST,
                List.of("retry", "resume", "replay", "idempotent"));
        addFunctionalFacet(root, steps, "blocking-paths", StepKind.BLOCKING_TEST,
                List.of("blocking", "blocked", "boundary", "gate", "approval", "authorization", "permission"));
        addConnectedWorkflowFacets(root, steps);
        addOperationalFacets(root, steps);
        if (StandardValidationPackSupport.directory(root, "tests/integration")) {
            List<String> integration = pytest
                    ? List.of("python3", "-m", "pytest", "-q", "tests/integration")
                    : List.of("python3", "-m", "unittest", "discover", "-s", "tests/integration");
            steps.add(step("python.integration", Phase.END_TO_END_LINEAGE, StepKind.INTEGRATION_TEST,
                    integration, TEST_TIMEOUT, List.of("python.tests")));
        }
        return new Contribution(Set.of("PYTHON"), steps);
    }

    private static void addFunctionalFacet(Path root, List<Step> steps, String id, StepKind kind,
            List<String> signals) {
        try {
            if (!StandardValidationPackSupport.testSignal(
                    root, "tests", Set.of(".py"), signals)) return;
        } catch (Exception error) {
            throw new IllegalArgumentException("PYTHON_TEST_SIGNAL_DETECTION_FAILED:" + id, error);
        }
        steps.add(step("python." + id, Phase.COMPONENT_AND_NEGATIVE, kind,
                List.of("python3", "-m", "unittest", "discover", "-v", "-s", "tests"),
                TEST_TIMEOUT, List.of("python.tests")));
    }

    private static void addConnectedWorkflowFacets(Path root, List<Step> steps) {
        if (!hasTestSignal(root, "class connectedworkflowvalidationtest")) return;
        List<StepKind> kinds = List.of(
                StepKind.E2E_REQUEST_FLOW, StepKind.E2E_RENDER_OR_PRODUCE,
                StepKind.E2E_ARTIFACT_READBACK, StepKind.E2E_TESTER_CHECK,
                StepKind.E2E_AUDIT_CHECK, StepKind.E2E_EXPOSURE_DECISION,
                StepKind.WORKFLOW_LINEAGE);
        List<String> methods = List.of(
                "test_request_flow", "test_render_or_produce", "test_artifact_readback",
                "test_tester_check", "test_audit_check", "test_exposure_decision",
                "test_workflow_lineage");
        String previous = null;
        for (int index = 0; index < kinds.size(); index++) {
            String id = "python.connected-" + methods.get(index).substring("test_".length())
                    .replace('_', '-');
            List<String> dependencies = previous == null ? List.of("python.tests") : List.of(previous);
            steps.add(step(id, Phase.END_TO_END_LINEAGE, kinds.get(index),
                    unittestMethod("tests.test_connected_workflow_validation."
                            + "ConnectedWorkflowValidationTest." + methods.get(index)),
                    TEST_TIMEOUT, dependencies));
            previous = id;
        }
    }

    private static void addOperationalFacets(Path root, List<Step> steps) {
        if (!hasTestSignal(root, "class operationalresiliencevalidationtest")) return;
        List<StepKind> kinds = List.of(
                StepKind.INTERRUPTION_TEST, StepKind.RESUME_TEST,
                StepKind.ROLLBACK_TEST, StepKind.RERUN_TEST);
        List<String> methods = List.of("test_interruption", "test_resume", "test_rollback", "test_rerun");
        for (int index = 0; index < kinds.size(); index++) {
            steps.add(step("python.operations-" + methods.get(index).substring("test_".length()),
                    Phase.OPERATIONAL_RESILIENCE, kinds.get(index),
                    unittestMethod("tests.test_operational_resilience_validation."
                            + "OperationalResilienceValidationTest." + methods.get(index)),
                    TEST_TIMEOUT, List.of("evidence.verify")));
        }
    }

    private static boolean hasTestSignal(Path root, String signal) {
        try {
            return StandardValidationPackSupport.testSignal(
                    root, "tests", Set.of(".py"), List.of(signal));
        } catch (Exception error) {
            throw new IllegalArgumentException("PYTHON_TEST_SIGNAL_DETECTION_FAILED:" + signal, error);
        }
    }

    private static List<String> unittestMethod(String method) {
        return List.of("python3", "-m", "unittest", "-v", method);
    }
}
