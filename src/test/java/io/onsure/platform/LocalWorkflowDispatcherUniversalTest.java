package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalWorkflowDispatcherUniversalTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

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
        assertEquals(temp.resolve(".onsure/universal-validation/neutral/run-001").toString(),
                result.get("run_root"));
        assertEquals(result.get("run_root"), mapper.valueToTree(envelope)
                .at("/result/run_root").asText());
        assertFalse(run.finalClaimAllowed());
        assertTrue(Files.isRegularFile(run.receiptFile()));
        assertFalse(Files.exists(source.resolve("onsure-target.json")));

        Files.writeString(source.resolve("changed-after-registration.txt"), "drift\n");
        IllegalArgumentException drift = assertThrows(IllegalArgumentException.class,
                () -> dispatcher.dispatch("validation.run", mapper.valueToTree(Map.of(
                        "project_id", "project", "target_id", "neutral",
                        "validation_mode", "UNIVERSAL", "run_id", "run-drift"))));
        assertEquals("PROGRAM_SOURCE_REFERENCE_DRIFT", drift.getMessage());
        assertFalse(Files.exists(temp.resolve(".onsure/universal-validation/neutral/run-drift")));
    }

    @Test
    void sharedWorkflowAppliesWorkspaceBoundExternalEnvironmentProfile() throws Exception {
        Path source = Files.createDirectories(temp.resolve("profile-source"));
        Files.writeString(source.resolve("openapi.yaml"), "openapi: 3.1.0\npaths: {}\n");
        Path profile = temp.resolve("environment-profile.json");
        Files.writeString(profile, """
                {"contract":"ONSURE_ENVIRONMENT_REQUIREMENT_PROFILE_V1","profile_id":"api-preflight",
                 "requirements":[{"requirement_id":"clamav.required","kind":"EXECUTABLE",
                 "value":"onsure-clamscan-definitely-missing","required":true}]}
                """);
        var dispatcher = new LocalWorkflowDispatcher(temp);
        dispatcher.dispatch("project.register-workspace", mapper.valueToTree(Map.of(
                "workspace_id", "workspace", "workspace_name", "Workspace")));
        dispatcher.dispatch("project.register", mapper.valueToTree(Map.of(
                "project_id", "project", "workspace_id", "workspace", "project_name", "Project")));
        dispatcher.dispatch("project.register-target", mapper.valueToTree(Map.of(
                "project_id", "project", "target_id", "profile-target", "target_name", "Profile Target",
                "target_type", "GENERAL_SOFTWARE", "source_root", source.toString())));

        Map<String, Object> envelope = dispatcher.dispatch("validation.run", mapper.valueToTree(Map.of(
                "project_id", "project", "target_id", "profile-target", "validation_mode", "UNIVERSAL",
                "run_id", "run-profile", "environment_profile_file", profile.toString())));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) envelope.get("result");
        UniversalValidationRunner.RunResult run = (UniversalValidationRunner.RunResult) result.get("run");
        assertEquals(UniversalValidationProfile.Outcome.BLOCKED, run.overallOutcome());
        assertEquals("clamav.required", run.environmentRequirements().get(0).requirementId());
        assertTrue(Files.readString(run.receiptFile()).contains("\"external_environment_profile\""));
        assertFalse(Files.exists(source.resolve("environment-profile.json")));
    }
}
