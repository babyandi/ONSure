package io.onsure.assurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ExecutionEnvironmentContractTest {
    @Test
    void devcontainerPinsJdk17MavenAndPreparationCommand() throws Exception {
        Path file = Path.of(".devcontainer/devcontainer.json");
        JsonNode root = new ObjectMapper().readTree(file.toFile());

        assertTrue(root.path("image").asText().contains("17"));
        JsonNode javaFeature = root.path("features")
                .path("ghcr.io/devcontainers/features/java:1");
        assertFalse(javaFeature.isMissingNode());
        assertEquals("none", javaFeature.path("version").asText());
        assertTrue(javaFeature.path("installMaven").asBoolean(false));
        assertFalse(javaFeature.path("installGradle").asBoolean(true));
        assertEquals("bash scripts/prepare-assurance-environment.sh",
                root.path("postCreateCommand").asText());
    }

    @Test
    void finalExecutionScriptsAreVersioned() {
        assertTrue(Files.isRegularFile(Path.of("scripts/prepare-assurance-environment.sh")));
        assertTrue(Files.isRegularFile(Path.of("scripts/execute-issue-4-final-gate.sh")));
        assertTrue(Files.isRegularFile(Path.of("scripts/run-local-assurance-twice.sh")));
    }
}
