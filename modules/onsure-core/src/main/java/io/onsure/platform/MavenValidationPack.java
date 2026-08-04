package io.onsure.platform;

import static io.onsure.platform.StandardValidationPackSupport.BUILD_TIMEOUT;
import static io.onsure.platform.StandardValidationPackSupport.step;

import io.onsure.platform.UniversalValidationProfile.Phase;
import io.onsure.platform.UniversalValidationProfile.Step;
import io.onsure.platform.UniversalValidationProfile.StepKind;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Standard offline Maven build and integration-test profile. */
public final class MavenValidationPack implements ValidationPack {
    @Override public String id() { return "maven"; }

    @Override
    public Contribution detect(Path root) throws Exception {
        if (!StandardValidationPackSupport.file(root, "pom.xml")) return Contribution.none();
        String pom = StandardValidationPackSupport.readConfig(root.resolve("pom.xml"));
        List<Step> steps = new ArrayList<>();
        steps.add(step("maven.clean-verify", Phase.COMPONENT_AND_NEGATIVE, StepKind.BUILD,
                List.of("mvn", "-B", "-ntp", "-o", "clean", "verify"), BUILD_TIMEOUT,
                List.of("validator.meta-check")));
        if (pom.contains("maven-failsafe-plugin")) {
            steps.add(step("maven.integration", Phase.END_TO_END_LINEAGE, StepKind.INTEGRATION_TEST,
                    List.of("mvn", "-B", "-ntp", "-o", "verify"), BUILD_TIMEOUT,
                    List.of("maven.clean-verify")));
        }
        return new Contribution(Set.of("JAVA", "MAVEN"), steps);
    }
}
