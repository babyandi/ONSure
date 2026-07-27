package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
    void workspaceProjectAndTargetAreReachableThroughSharedDispatcher() throws Exception {
        LocalWorkflowDispatcher dispatcher = new LocalWorkflowDispatcher(temp);
        Map<String, Object> workspace = result(dispatcher.dispatch(
                "project.register-workspace", request(Map.of(
                        "workspace_id", "workspace-001",
                        "workspace_name", "Workspace"))));
        assertEquals(1L, ((Number) workspace.get("catalog_revision")).longValue());

        Map<String, Object> project = result(dispatcher.dispatch(
                "project.register", request(Map.of(
                        "workspace_id", "workspace-001",
                        "project_id", "project-001",
                        "project_name", "Project"))));
        assertEquals(2L, ((Number) project.get("catalog_revision")).longValue());

        Path source = temp.resolve("source");
        Files.createDirectories(source);
        Files.writeString(source.resolve("README.md"), "registered target\n");
        Map<String, Object> registered = result(dispatcher.dispatch(
                "project.register-target", request(Map.of(
                        "project_id", "project-001",
                        "target_id", "target-001",
                        "target_name", "Target",
                        "target_type", "GENERAL_SOFTWARE",
                        "source_root", source.toString()))));
        assertEquals(3L, ((Number) registered.get("catalog_revision")).longValue());

        Map<String, Object> read = result(dispatcher.dispatch(
                "project.read-target", request(Map.of("target_id", "target-001"))));
        ValidationModel.ValidationTarget target = mapper.convertValue(
                read.get("target"), ValidationModel.ValidationTarget.class);
        assertEquals("target-001", target.targetId());

        Map<String, Object> listed = result(dispatcher.dispatch(
                "project.list-targets", request(Map.of("project_id", "project-001"))));
        assertEquals(1, ((java.util.List<?>) listed.get("targets")).size());
        assertFalse(Boolean.TRUE.equals(listed.get("final_claim_allowed")));
    }

    private JsonNode request(Map<String, Object> value) {
        return mapper.valueToTree(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> result(Map<String, Object> envelope) {
        return (Map<String, Object>) envelope.get("result");
    }
}
