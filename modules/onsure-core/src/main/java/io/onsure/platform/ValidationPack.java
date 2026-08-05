package io.onsure.platform;

import io.onsure.platform.UniversalValidationProfile.Step;
import io.onsure.platform.UniversalValidationProfile.EnvironmentRequirement;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Trusted, installed extension point for framework-specific validation steps.
 *
 * <p>A pack may contribute functional, connected E2E, or operational steps. It
 * cannot replace ONSure's environment, structure, meta-validation, or evidence
 * gates. Target source is inspected read-only during detection.
 */
public interface ValidationPack {
    String id();

    Contribution detect(Path sourceRoot) throws Exception;

    record Contribution(
            Set<String> technologies,
            List<EnvironmentRequirement> environmentRequirements,
            List<Step> steps) {
        public Contribution {
            technologies = technologies == null
                    ? Set.of() : Set.copyOf(new LinkedHashSet<>(technologies));
            environmentRequirements = environmentRequirements == null
                    ? List.of() : List.copyOf(environmentRequirements);
            steps = steps == null ? List.of() : List.copyOf(steps);
        }

        public Contribution(Set<String> technologies, List<Step> steps) {
            this(technologies, List.of(), steps);
        }

        public static Contribution none() {
            return new Contribution(Set.of(), List.of(), List.of());
        }
    }
}
