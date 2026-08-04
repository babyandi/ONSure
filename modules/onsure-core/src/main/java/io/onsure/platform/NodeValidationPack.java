package io.onsure.platform;

import static io.onsure.platform.StandardValidationPackSupport.BUILD_TIMEOUT;
import static io.onsure.platform.StandardValidationPackSupport.TEST_TIMEOUT;
import static io.onsure.platform.StandardValidationPackSupport.step;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.onsure.platform.UniversalValidationProfile.EnvironmentRequirement;
import io.onsure.platform.UniversalValidationProfile.Phase;
import io.onsure.platform.UniversalValidationProfile.RequirementKind;
import io.onsure.platform.UniversalValidationProfile.Step;
import io.onsure.platform.UniversalValidationProfile.StepKind;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Standard npm offline dependency, build, unit and integration profile. */
public final class NodeValidationPack implements ValidationPack {
    private final ObjectMapper mapper = new ObjectMapper();
    @Override public String id() { return "node"; }

    @Override
    public Contribution detect(Path root) throws Exception {
        if (!StandardValidationPackSupport.file(root, "package.json")) return Contribution.none();
        JsonNode body = mapper.readTree(StandardValidationPackSupport.readConfig(root.resolve("package.json")));
        JsonNode scripts = body.path("scripts");
        boolean dependencies = body.path("dependencies").size() > 0
                || body.path("devDependencies").size() > 0 || body.path("optionalDependencies").size() > 0;
        List<EnvironmentRequirement> requirements = new ArrayList<>();
        List<Step> steps = new ArrayList<>();
        String preparation = null;
        if (dependencies) {
            requirements.add(new EnvironmentRequirement(
                    "node.lockfile", RequirementKind.SOURCE_FILE, "package-lock.json", true));
            preparation = "node.dependencies";
            steps.add(step(preparation, Phase.COMPONENT_AND_NEGATIVE, StepKind.BUILD,
                    List.of("npm", "--offline", "ci", "--ignore-scripts"), BUILD_TIMEOUT,
                    List.of("validator.meta-check")));
        }
        if (scripts.hasNonNull("test")) {
            steps.add(step("node.tests", Phase.COMPONENT_AND_NEGATIVE, StepKind.UNIT_TEST,
                    List.of("npm", "--offline", "test"), TEST_TIMEOUT,
                    List.of(preparation == null ? "validator.meta-check" : preparation)));
        }
        if (scripts.hasNonNull("build")) {
            steps.add(step("node.build", Phase.COMPONENT_AND_NEGATIVE, StepKind.BUILD,
                    List.of("npm", "--offline", "run", "build"), BUILD_TIMEOUT,
                    List.of(preparation == null ? "validator.meta-check" : preparation)));
        }
        if (scripts.hasNonNull("test:integration")) {
            steps.add(step("node.integration", Phase.END_TO_END_LINEAGE, StepKind.INTEGRATION_TEST,
                    List.of("npm", "--offline", "run", "test:integration"), TEST_TIMEOUT,
                    List.of(scripts.hasNonNull("test") ? "node.tests" : "validator.meta-check")));
        }
        return new Contribution(Set.of("NODE"), requirements, steps);
    }
}
