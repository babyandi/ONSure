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
        Files.createDirectories(source.resolve("module/target.root-owned-backup"));
        Files.createSymbolicLink(source.resolve("module/target.root-owned-backup/outside"), Path.of("/root/secret"));
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

    @Test
    void universalManagementValidationUsesSharedRunnerReceiptAndProjection() throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("universal-workspace"));
        Path source = Files.createDirectory(temp.resolve("universal-source"));
        Files.writeString(source.resolve("openapi.yaml"), """
                openapi: 3.1.0
                info: {title: managed, version: '1'}
                paths:
                  /health:
                    get:
                      operationId: getHealth
                      responses:
                        '200': {description: healthy}
                """);
        Path environmentProfile = workspace.resolve("environment-profile.json");
        Files.writeString(environmentProfile, """
                {"contract":"ONSURE_ENVIRONMENT_REQUIREMENT_PROFILE_V1","profile_id":"managed",
                 "requirements":[{"requirement_id":"optional.fixture","kind":"SOURCE_FILE",
                 "value":"fixtures/optional.json","required":false}]}
                """);
        LocalProgramManagementService service = new LocalProgramManagementService(workspace);
        service.register(mapper.valueToTree(Map.of(
                "workspace_id", "local", "workspace_name", "Local",
                "project_id", "project", "project_name", "Project",
                "target_id", "managed", "target_name", "Managed",
                "target_type", "GENERAL_SOFTWARE", "source_root", source.toString())));

        Map<String, Object> result = service.validate(mapper.valueToTree(Map.of(
                "project_id", "project", "target_id", "managed", "profile", "UNIVERSAL",
                "environment_profile_file", environmentProfile.toString())));

        Path runRoot = Path.of(result.get("run_root").toString());
        String projection = Files.readString(runRoot.resolve("validation-report.json"));
        assertEquals("UNIVERSAL", result.get("profile"));
        assertEquals("NOT_RUN", result.get("decision"), projection);
        assertFalse(Boolean.TRUE.equals(result.get("source_mutation_detected")));
        Path receipt = Path.of(result.get("receipt_file").toString());
        assertTrue(Files.isRegularFile(receipt));
        assertEquals(Hashing.file(receipt), result.get("receipt_sha256"));
        assertTrue(Files.readString(receipt).contains("\"external_environment_profile\""));
        assertTrue(projection.contains("\"verificationGroupOutcomes\""));
        assertTrue(Files.isRegularFile(runRoot.resolve("evidence.json")));
        assertTrue(Files.isRegularFile(runRoot.resolve("remediation-plans.json")));
        assertFalse(Files.exists(source.resolve(".onsure")));

        @SuppressWarnings("unchecked")
        var programs = (java.util.List<Map<String, Object>>) new LocalManagementOverviewService(workspace)
                .overview().get("programs");
        @SuppressWarnings("unchecked")
        var latest = (Map<String, Object>) programs.get(0).get("latest_validation");
        assertEquals("NOT_RUN", latest.get("decision"));
        assertEquals(2, ((Number) latest.get("evidence_count")).intValue());
    }

    @Test
    void universalManagementValidationRejectsSourceDriftBeforeCreatingRunEvidence() throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("drift-workspace"));
        Path source = Files.createDirectory(temp.resolve("drift-source"));
        Files.writeString(source.resolve("openapi.yaml"), "openapi: 3.1.0\ninfo: {title: drift, version: '1'}\npaths: {}\n");
        LocalProgramManagementService service = new LocalProgramManagementService(workspace);
        service.register(mapper.valueToTree(Map.of(
                "workspace_id", "local", "workspace_name", "Local",
                "project_id", "project", "project_name", "Project",
                "target_id", "drift", "target_name", "Drift",
                "target_type", "GENERAL_SOFTWARE", "source_root", source.toString())));
        Files.writeString(source.resolve("changed.txt"), "changed after registration\n");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.validate(mapper.valueToTree(Map.of(
                        "project_id", "project", "target_id", "drift", "profile", "UNIVERSAL"))));

        assertEquals("PROGRAM_SOURCE_REFERENCE_DRIFT", error.getMessage());
        Path validationData = workspace.resolve(".onsure/validation-data/drift");
        assertFalse(Files.exists(validationData));
    }
}
