package io.onsure.platform;

import static io.onsure.platform.StandardValidationPackSupport.BUILD_TIMEOUT;
import static io.onsure.platform.StandardValidationPackSupport.step;

import io.onsure.platform.UniversalValidationProfile.Phase;
import io.onsure.platform.UniversalValidationProfile.StepKind;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Standard Gradle-wrapper offline build profile. */
public final class GradleValidationPack implements ValidationPack {
    @Override public String id() { return "gradle"; }

    @Override
    public Contribution detect(Path root) {
        if (!StandardValidationPackSupport.file(root, "gradlew")
                || !(StandardValidationPackSupport.file(root, "build.gradle")
                || StandardValidationPackSupport.file(root, "build.gradle.kts"))) {
            return Contribution.none();
        }
        return new Contribution(Set.of("JAVA", "GRADLE"), List.of(step(
                "gradle.clean-test", Phase.COMPONENT_AND_NEGATIVE, StepKind.BUILD,
                List.of("bash", "gradlew", "--offline", "clean", "test"), BUILD_TIMEOUT,
                List.of("validator.meta-check"))));
    }
}
