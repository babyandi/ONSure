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
        if (StandardValidationPackSupport.directory(root, "tests/integration")) {
            List<String> integration = pytest
                    ? List.of("python3", "-m", "pytest", "-q", "tests/integration")
                    : List.of("python3", "-m", "unittest", "discover", "-s", "tests/integration");
            steps.add(step("python.integration", Phase.END_TO_END_LINEAGE, StepKind.INTEGRATION_TEST,
                    integration, TEST_TIMEOUT, List.of("python.tests")));
        }
        return new Contribution(Set.of("PYTHON"), steps);
    }
}
