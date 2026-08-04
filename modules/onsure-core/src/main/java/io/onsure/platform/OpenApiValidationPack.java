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
        if (StandardValidationPackSupport.firstFile(root, "openapi.yaml", "openapi.yml", "openapi.json",
                "contracts/openapi/onsure-local-api.v1.json",
                "contracts/openapi/onsure-llm-gateway.v1.json") == null) return Contribution.none();
        return new Contribution(Set.of("OPENAPI"), List.of(step(
                "openapi.contract", Phase.COMPONENT_AND_NEGATIVE, StepKind.API_CONTRACT,
                List.of(), Duration.ofMinutes(2), List.of("validator.meta-check"))));
    }
}
