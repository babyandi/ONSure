package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalAuthenticatedApiServerSmokeTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void loopbackApiRejectsUnauthenticatedAndBrowserOriginRequests() throws Exception {
        String token = "local-api-test-token-" + "x".repeat(32);
        LocalAuthenticatedApiServer server = new LocalAuthenticatedApiServer(temp, token);
        int port = server.startAndGetPort(0);
        try {
            HttpClient client = HttpClient.newHttpClient();
            URI health = URI.create("http://127.0.0.1:" + port + "/v1/health");
            assertEquals(200, client.send(
                    HttpRequest.newBuilder(health).GET().build(),
                    HttpResponse.BodyHandlers.ofString()).statusCode());

            URI status = URI.create("http://127.0.0.1:" + port + "/v1/status");
            assertEquals(401, client.send(
                    HttpRequest.newBuilder(status).GET().build(),
                    HttpResponse.BodyHandlers.ofString()).statusCode());

            HttpResponse<String> authenticated = client.send(
                    HttpRequest.newBuilder(status)
                            .header("Authorization", "Bearer " + token)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, authenticated.statusCode());
            assertTrue(authenticated.body().contains("SELF_VALIDATION_NONFINAL"));
            assertTrue(authenticated.body().contains("NOT_RUN"));

            assertEquals(403, client.send(
                    HttpRequest.newBuilder(status)
                            .header("Authorization", "Bearer " + token)
                            .header("Origin", "https://attacker.invalid")
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString()).statusCode());
        } finally {
            server.stop();
        }
    }

    @Test
    void unsupportedWorkflowFailsClosed() throws Exception {
        String token = "local-api-test-token-" + "y".repeat(32);
        LocalAuthenticatedApiServer server = new LocalAuthenticatedApiServer(temp, token);
        int port = server.startAndGetPort(0);
        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(
                                    "http://127.0.0.1:" + port + "/v1/workflow"))
                            .header("Authorization", "Bearer " + token)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(
                                    "{\"operation\":\"unknown.operation\",\"request\":{}}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(400, response.statusCode());
            assertTrue(response.body().contains("WORKFLOW_OPERATION_UNSUPPORTED"));
        } finally {
            server.stop();
        }
    }

    @Test
    void vscodeRegistrationLearningAndPlanFlowUsesRegisteredIdentityAndRequiresApproval() throws Exception {
        Files.writeString(temp.resolve("README.md"), "VS Code registered target\n");
        Files.writeString(temp.resolve("onsure-target.json"), """
                {
                  "contract":"ONSURE_TARGET_MANIFEST_V1",
                  "target_id":"target-001",
                  "target_type":"GENERAL_SOFTWARE",
                  "self_reported_final_decision":false,
                  "capabilities":[],
                  "fixtures":[]
                }
                """);
        String token = "local-api-test-token-" + "z".repeat(32);
        LocalAuthenticatedApiServer server = new LocalAuthenticatedApiServer(temp, token);
        int port = server.startAndGetPort(0);
        HttpClient client = HttpClient.newHttpClient();
        try {
            assertWorkflow(client, port, token, "project.register-workspace", Map.of(
                    "workspace_id", "workspace-001", "workspace_name", "Workspace"));
            assertWorkflow(client, port, token, "project.register", Map.of(
                    "workspace_id", "workspace-001", "project_id", "project-001",
                    "project_name", "Project"));
            assertWorkflow(client, port, token, "project.register-target", Map.of(
                    "project_id", "project-001", "target_id", "target-001",
                    "target_name", "Target", "target_type", "GENERAL_SOFTWARE",
                    "source_root", temp.toString()));

            JsonNode read = assertWorkflow(client, port, token, "project.read-target", Map.of(
                    "project_id", "project-001", "target_id", "target-001"));
            JsonNode registered = read.path("workflow").path("result").path("registered_target");
            assertEquals("project-001", registered.path("projectId").asText());
            assertEquals("target-001", registered.path("target").path("targetId").asText());
            assertEquals(temp.toAbsolutePath().normalize(), Path.of(URI.create(
                    registered.path("target").path("sourceRoot").asText())));

            JsonNode learned = assertWorkflow(client, port, token, "program.learn", Map.of(
                    "project_id", "project-001", "target_id", "target-001",
                    "program_id", "target-001"));
            assertEquals("target-001",
                    learned.path("workflow").path("result").path("program_id").asText());
            Path profile = temp.resolve(".onsure/profiles/target-001/program-profile.json");
            assertTrue(Files.isRegularFile(profile));

            JsonNode plan = assertWorkflow(client, port, token, "plan.generate", Map.of(
                    "project_id", "project-001", "target_id", "target-001",
                    "program_profile_file", profile.toString()));
            assertEquals("AWAITING_USER_APPROVAL", plan.path("workflow").path("result")
                    .path("approval").path("state").asText());
            assertTrue(Files.isRegularFile(
                    temp.resolve(".onsure/plans/target-001-execution-plan.json")));

            HttpResponse<String> snapshotResponse = client.send(
                    HttpRequest.newBuilder(URI.create(
                                    "http://127.0.0.1:" + port + "/v1/workspace-snapshot"))
                            .header("Authorization", "Bearer " + token)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of(
                                    "project_id", "project-001", "target_id", "target-001"))))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, snapshotResponse.statusCode(), snapshotResponse.body());
            JsonNode snapshot = mapper.readTree(snapshotResponse.body()).path("snapshot");
            assertEquals(LocalWorkspaceSnapshotService.CONTRACT, snapshot.path("contract").asText());
            assertEquals("AVAILABLE", snapshot.path("profile").path("state").asText());
            assertEquals("AVAILABLE", snapshot.path("plan").path("state").asText());
            assertEquals(0, snapshot.path("run_count").asInt(-1));
            assertTrue(!snapshot.path("final_claim_allowed").asBoolean(true));

            assertWorkflowFailure(client, port, token, "validation.run", Map.of(
                    "project_id", "project-001", "target_id", "target-001"),
                    400, "APPROVED_EXECUTION_PLAN_BUNDLE_REQUIRED");
        } finally {
            server.stop();
        }
    }

    private JsonNode assertWorkflow(
            HttpClient client, int port, String token, String operation, Map<String, ?> request)
            throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v1/workflow"))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of(
                                "operation", operation, "request", request))))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = mapper.readTree(response.body());
        assertEquals(operation, body.path("workflow").path("operation").asText());
        assertTrue(!body.path("final_claim_allowed").asBoolean(true));
        return body;
    }

    private void assertWorkflowFailure(
            HttpClient client, int port, String token, String operation, Map<String, ?> request,
            int expectedStatus, String expectedMessage) throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v1/workflow"))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of(
                                "operation", operation, "request", request))))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(expectedStatus, response.statusCode(), response.body());
        JsonNode body = mapper.readTree(response.body());
        assertEquals("INVALID_REQUEST", body.path("error").asText());
        assertEquals(expectedMessage, body.path("message").asText());
        assertTrue(!body.path("final_claim_allowed").asBoolean(true));
    }
}
