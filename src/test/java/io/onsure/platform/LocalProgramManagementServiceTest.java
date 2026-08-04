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

class LocalProgramManagementServiceTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void registersExternalSourceAndWritesNonfinalReadOnlyEvidence() throws Exception {
        Path workspace = temp.resolve("workspace");
        Path source = temp.resolve("source");
        Files.createDirectories(workspace);
        Files.createDirectories(source.resolve("src/main/java/example"));
        Files.writeString(source.resolve("pom.xml"), "<project/>\n");
        Files.writeString(source.resolve("src/main/java/example/App.java"), "package example; public class App {}\n");
        Files.writeString(source.resolve("LICENSE"), "test only\n");
        Files.createDirectories(source.resolve(".venv/bin"));
        Files.createSymbolicLink(source.resolve(".venv/bin/python"), Path.of("/usr/bin/python3"));
        LocalProgramManagementService service = new LocalProgramManagementService(workspace);
        Map<String, Object> registered = service.register(mapper.valueToTree(Map.of(
                "workspace_id", "local", "workspace_name", "Local",
                "project_id", "project", "project_name", "Project",
                "target_id", "target", "target_name", "Target",
                "target_type", "GENERAL_SOFTWARE", "source_root", source.toString())));
        assertTrue(Boolean.TRUE.equals(registered.get("read_only_registration")));
        String before = Files.readString(source.resolve("src/main/java/example/App.java"));
        Map<String, Object> validation = service.validate(mapper.valueToTree(Map.of(
                "project_id", "project", "target_id", "target", "profile", "INSPECT_ONLY")));
        assertFalse(Boolean.TRUE.equals(validation.get("source_mutation_detected")));
        assertEquals(before, Files.readString(source.resolve("src/main/java/example/App.java")));
        Path run = Path.of(validation.get("run_root").toString());
        assertTrue(Files.isRegularFile(run.resolve("validation-report.json")));
        assertTrue(Files.isRegularFile(run.resolve("evidence.json")));
        assertTrue(Files.isRegularFile(run.resolve("remediation-plans.json")));

        Files.writeString(source.resolve("src/main/java/example/App.java"),
                "package example; public class App { int changed; }\n");
        Map<String, Object> drifted = service.validate(mapper.valueToTree(Map.of(
                "project_id", "project", "target_id", "target", "profile", "INSPECT_ONLY")));
        assertEquals("HOLD", drifted.get("decision"));
        assertEquals(1, drifted.get("finding_count"));
    }
}
