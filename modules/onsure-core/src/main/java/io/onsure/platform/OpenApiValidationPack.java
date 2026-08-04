package io.onsure.platform;

import static io.onsure.platform.StandardValidationPackSupport.step;

import io.onsure.platform.UniversalValidationProfile.Phase;
import io.onsure.platform.UniversalValidationProfile.StepKind;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/** Standard OpenAPI 3.0/3.1 AST contract profile. */
public final class OpenApiValidationPack implements ValidationPack {
    @Override public String id() { return "openapi"; }

    @Override
    public Contribution detect(Path root) {
        try {
            List<Path> contracts = StandardValidationPackSupport.findOpenApiContracts(root);
            if (contracts.isEmpty()) return Contribution.none();
            List<UniversalValidationProfile.Step> steps = new java.util.ArrayList<>();
            for (int index = 0; index < contracts.size(); index++) {
                String id = index == 0 ? "openapi.contract" : "openapi.contract-" + (index + 1);
                steps.add(step(id, Phase.COMPONENT_AND_NEGATIVE, StepKind.API_CONTRACT,
                        List.of(), Duration.ofMinutes(2), List.of("validator.meta-check")));
            }
            return new Contribution(Set.of("OPENAPI"), steps);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("OPENAPI_CONTRACT_DISCOVERY_FAILED", error);
        }
    }
}
