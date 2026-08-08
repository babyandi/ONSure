package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProductRegistrationWorkflowTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void workspaceProjectTargetLearningAndPlanUseOneRegisteredIdentity() throws Exception {
        LocalWorkflowDispatcher dispatcher = new LocalWorkflowDispatcher(temp);
        Map<String, Object> workspace = result(dispatcher.dispatch(
                "project.register-workspace", request(Map.of(
                        "workspace_id", "workspace-001", "workspace_name", "Workspace"))));
        assertEquals(1L, ((Number) workspace.get("catalog_revision")).longValue());
        Map<String, Object> project = result(dispatcher.dispatch(
                "project.register", request(Map.of(
                        "workspace_id", "workspace-001", "project_id", "project-001",
                        "project_name", "Project"))));
        assertEquals(2L, ((Number) project.get("catalog_revision")).longValue());

        Path source = temp.resolve("source");
        Files.createDirectories(source);
        Files.writeString(source.resolve("README.md"), "registered target\n");
        Files.writeString(source.resolve("onsure-target.json"), """
                {
                  "contract":"ONSURE_TARGET_MANIFEST_V1",
                  "target_id":"target-001",
                  "target_type":"GENERAL_SOFTWARE",
                  "self_reported_final_decision":false,
                  "capabilities":[],
                  "fixtures":[]
                }
                """);
        Map<String, Object> registered = result(dispatcher.dispatch(
                "project.register-target", request(Map.of(
                        "project_id", "project-001", "target_id", "target-001",
                        "target_name", "Target", "target_type", "GENERAL_SOFTWARE",
                        "source_root", source.toString()))));
        assertEquals(3L, ((Number) registered.get("catalog_revision")).longValue());

        Map<String, Object> read = result(dispatcher.dispatch(
                "project.read-target", request(Map.of(
                        "project_id", "project-001", "target_id", "target-001"))));
        ProductCatalog.RegisteredTarget readRegistered =
                (ProductCatalog.RegisteredTarget) read.get("registered_target");
        assertEquals("target-001", readRegistered.target().targetId());
        assertEquals("REGISTERED_REVIEWED", readRegistered.target().executionProfile());

        Map<String, Object> learned = result(dispatcher.dispatch(
                "program.learn", request(Map.of(
                        "project_id", "project-001", "target_id", "target-001",
                        "program_id", "target-001"))));
        assertEquals("target-001", learned.get("program_id"));
        Files.writeString(source.resolve("added.txt"), "incremental source\n");
        Map<String, Object> incrementallyLearned = result(dispatcher.dispatch(
                "program.learn", request(Map.of(
                        "project_id", "project-001", "target_id", "target-001",
                        "program_id", "target-001"))));
        assertEquals(2, incrementallyLearned.get("revision"));
        @SuppressWarnings("unchecked")
        Map<String, Object> changeSet =
                (Map<String, Object>) incrementallyLearned.get("change_set");
        assertEquals("INCREMENTAL", changeSet.get("mode"));
        assertEquals(java.util.List.of("added.txt"), changeSet.get("added"));
        Path profile = temp.resolve(".onsure/profiles/target-001/program-profile.json");
        Map<String, Object> plan = result(dispatcher.dispatch(
                "plan.generate", request(Map.of(
                        "project_id", "project-001", "target_id", "target-001",
                        "program_profile_file", profile.toString()))));
        @SuppressWarnings("unchecked")
        Map<String, Object> approval = (Map<String, Object>) plan.get("approval");
        assertEquals("AWAITING_USER_APPROVAL", approval.get("state"));
        assertFalse(Boolean.TRUE.equals(plan.get("final_claim_allowed")));

        Map<String, Object> listed = result(dispatcher.dispatch(
                "project.list-targets", request(Map.of("project_id", "project-001"))));
        assertEquals(1, ((java.util.List<?>) listed.get("targets")).size());
    }

    @Test
    void unregisteredCrossProjectOverrideAndProfileDriftAreRejected() throws Exception {
        LocalWorkflowDispatcher dispatcher = new LocalWorkflowDispatcher(temp);
        dispatcher.dispatch("project.register-workspace", request(Map.of(
                "workspace_id", "workspace-001", "workspace_name", "Workspace")));
        dispatcher.dispatch("project.register", request(Map.of(
                "workspace_id", "workspace-001", "project_id", "project-001",
                "project_name", "Project")));
        dispatcher.dispatch("project.register", request(Map.of(
                "workspace_id", "workspace-001", "project_id", "project-002",
                "project_name", "Other")));
        Path source = temp.resolve("source");
        Files.createDirectories(source);
        Files.writeString(source.resolve("README.md"), "registered target\n");
        Files.writeString(source.resolve("onsure-target.json"), """
                {"contract":"ONSURE_TARGET_MANIFEST_V1","target_id":"target-001",
                 "target_type":"GENERAL_SOFTWARE","self_reported_final_decision":false,
                 "capabilities":[],"fixtures":[]}
                """);
        dispatcher.dispatch("project.register-target", request(Map.of(
                "project_id", "project-001", "target_id", "target-001",
                "target_name", "Target", "target_type", "GENERAL_SOFTWARE",
                "source_root", source.toString())));

        SecurityException unboundCrossProject = assertThrows(SecurityException.class,
                () -> dispatcher.dispatch("program.learn", request(Map.of(
                        "project_id", "project-002", "target_id", "target-001",
                        "program_id", "target-001"))));
        assertEquals("TENANT_RESOURCE_BINDING_MISSING:target:project-002:target-001",
                unboundCrossProject.getMessage());
        IllegalArgumentException override = assertThrows(IllegalArgumentException.class,
                () -> dispatcher.dispatch("program.learn", request(Map.of(
                        "project_id", "project-001", "target_id", "target-001",
                        "program_id", "target-001", "source_root", source.toString()))));
        assertEquals("REGISTERED_TARGET_FIELD_OVERRIDE_PROHIBITED:source_root", override.getMessage());
        assertThrows(IllegalArgumentException.class, () -> dispatcher.dispatch(
                "project.register-target", request(Map.of(
                        "project_id", "project-001", "target_id", "target-002",
                        "target_name", "Unsafe", "target_type", "GENERAL_SOFTWARE",
                        "source_root", source.toString(),
                        "execution_profile", "TRUSTED_LOCAL_FIXTURE"))));

        dispatcher.dispatch("program.learn", request(Map.of(
                "project_id", "project-001", "target_id", "target-001",
                "program_id", "target-001")));
        Path profile = temp.resolve(".onsure/profiles/target-001/program-profile.json");
        Files.writeString(source.resolve("README.md"), "source changed after learning\n");
        IllegalStateException drift = assertThrows(IllegalStateException.class,
                () -> dispatcher.dispatch("plan.generate", request(Map.of(
                        "project_id", "project-001", "target_id", "target-001",
                        "program_profile_file", profile.toString()))));
        assertEquals("PROGRAM_PROFILE_SOURCE_DRIFT", drift.getMessage());
    }

    private JsonNode request(Map<String, Object> value) {
        return mapper.valueToTree(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> result(Map<String, Object> envelope) {
        return (Map<String, Object>) envelope.get("result");
    }
}
