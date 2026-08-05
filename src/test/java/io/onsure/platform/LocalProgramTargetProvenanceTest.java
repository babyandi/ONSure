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

class LocalProgramTargetProvenanceTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void registrationUnderstandingProfileAndOverviewProjectTheSameProvenance() throws Exception {
        Path repository = Files.createDirectory(temp.resolve("customer-repository"));
        Files.writeString(repository.resolve("openapi.yaml"), """
                openapi: 3.1.0
                info: {title: Customer API, version: '1'}
                paths: {/orders: {get: {operationId: listOrders, responses: {'200': {description: ok}}}}}
                """);
        Files.createDirectories(repository.resolve("fixtures/sample"));
        Files.writeString(repository.resolve("fixtures/sample/input.json"), "{}\n");
        git(repository, "init", "-q");
        git(repository, "config", "user.email", "test@onsure.invalid");
        git(repository, "config", "user.name", "ONSure Test");
        git(repository, "add", ".");
        git(repository, "commit", "-q", "-m", "customer source");
        git(repository, "remote", "add", "origin", "https://example.invalid/customer/api.git");

        Path workspace = Files.createDirectory(temp.resolve("workspace"));
        LocalProgramManagementService service = new LocalProgramManagementService(workspace);
        Map<String, Object> registered = service.register(mapper.valueToTree(Map.of(
                "workspace_id", "local", "workspace_name", "Local",
                "project_id", "customer", "project_name", "Customer",
                "target_id", "actual", "target_name", "Actual application",
                "target_type", "GENERAL_SOFTWARE", "source_root", repository.toString(),
                "target_classification", "REAL_REPOSITORY")));
        @SuppressWarnings("unchecked")
        Map<String, Object> registeredProvenance =
                (Map<String, Object>) registered.get("target_provenance");
        assertEquals("REAL_REPOSITORY", registeredProvenance.get("target_classification"));
        assertTrue((Boolean) registered.get("real_target_universality_eligible"));
        assertTrue(Files.isRegularFile(workspace.resolve(
                ".onsure/target-provenance/actual.json")));

        Map<String, Object> understood = service.understand(mapper.valueToTree(Map.of(
                "project_id", "customer", "target_id", "actual")));
        assertEquals(registeredProvenance, understood.get("target_provenance"));
        @SuppressWarnings("unchecked")
        Map<String, Object> profile = mapper.readValue(
                Path.of(understood.get("profile_file").toString()).toFile(), Map.class);
        assertEquals(registeredProvenance, profile.get("target_provenance"));

        Map<String, Object> fixture = service.register(mapper.valueToTree(Map.of(
                "workspace_id", "local", "workspace_name", "Local",
                "project_id", "customer", "project_name", "Customer",
                "target_id", "fixture", "target_name", "Fixture",
                "target_type", "GENERAL_SOFTWARE",
                "source_root", repository.resolve("fixtures/sample").toString())));
        @SuppressWarnings("unchecked")
        Map<String, Object> fixtureProvenance = (Map<String, Object>) fixture.get("target_provenance");
        assertEquals("FIXTURE", fixtureProvenance.get("target_classification"));
        assertTrue((Boolean) fixtureProvenance.get("fixture_only"));
        assertFalse((Boolean) fixture.get("real_target_universality_eligible"));
    }

    private static void git(Path root, String... arguments) throws Exception {
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(root.toString());
        command.addAll(java.util.List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new IllegalStateException("git failed: " + output);
    }
}
