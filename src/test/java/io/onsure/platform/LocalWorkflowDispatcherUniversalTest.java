package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalWorkflowDispatcherUniversalTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void registeredNeutralTargetRunsThroughSharedWorkflowSurface() throws Exception {
        Path source = Files.createDirectories(temp.resolve("source"));
        Files.writeString(source.resolve("openapi.yaml"),
                "openapi: 3.1.0\ninfo:\n  title: neutral\n  version: 1\npaths:\n  /health: {}\n");
        var dispatcher = new LocalWorkflowDispatcher(temp);
        dispatcher.dispatch("project.register-workspace", mapper.valueToTree(Map.of(
                "workspace_id", "workspace", "workspace_name", "Workspace")));
        dispatcher.dispatch("project.register", mapper.valueToTree(Map.of(
                "project_id", "project", "workspace_id", "workspace", "project_name", "Project")));
        dispatcher.dispatch("project.register-target", mapper.valueToTree(Map.of(
                "project_id", "project", "target_id", "neutral", "target_name", "Neutral",
                "target_type", "GENERAL_SOFTWARE", "source_root", source.toString())));

        Map<String, Object> envelope = dispatcher.dispatch("validation.run", mapper.valueToTree(Map.of(
                "project_id", "project", "target_id", "neutral",
                "validation_mode", "UNIVERSAL", "run_id", "run-001")));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) envelope.get("result");
        assertEquals("UNIVERSAL", result.get("validation_mode"));
        UniversalValidationRunner.RunResult run = (UniversalValidationRunner.RunResult) result.get("run");
        assertEquals(UniversalValidationProfile.Outcome.NOT_RUN, run.overallOutcome());
        assertFalse(run.finalClaimAllowed());
        assertTrue(Files.isRegularFile(run.receiptFile()));
        assertFalse(Files.exists(source.resolve("onsure-target.json")));
    }
}
