package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

class LocalManagementApiRbacTest {
    private static final String ADMIN = "admin-" + "a".repeat(40);
    private static final String VIEWER = "viewer-" + "v".repeat(40);
    private static final String OPERATOR = "operator-" + "o".repeat(40);
    private static final String APPROVER = "approver-" + "p".repeat(40);
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void enforcesRolesAndProjectsRegisteredValidationIntoManagementApi() throws Exception {
        Path workspace = temp.resolve("workspace");
        Path source = temp.resolve("source");
        Files.createDirectories(workspace);
        Files.createDirectories(source.resolve("src/main/java/example"));
        Files.writeString(source.resolve("pom.xml"), "<project/>\n");
        Files.writeString(source.resolve("LICENSE"), "test\n");
        Files.writeString(source.resolve("src/main/java/example/App.java"),
                "package example; public class App {}\n");
        LocalAuthenticatedApiServer server = new LocalAuthenticatedApiServer(workspace, ADMIN, Map.of(
                "ONSURE_LOCAL_API_VIEWER_TOKEN", VIEWER,
                "ONSURE_LOCAL_API_OPERATOR_TOKEN", OPERATOR,
                "ONSURE_LOCAL_API_APPROVER_TOKEN", APPROVER,
                "ONSURE_LLM_PROVIDER", "local-mock", "ONSURE_LLM_MODEL", "model"));
        int port = server.startAndGetPort(0);
        try {
            JsonNode viewerSession = json(get(port, "/v1/session", VIEWER));
            assertEquals("VIEWER", viewerSession.path("role").asText());
            assertEquals(403, post(port, "/v1/programs", VIEWER, registration(source)).statusCode());

            HttpResponse<String> registered = post(port, "/v1/programs", OPERATOR, registration(source));
            assertEquals(200, registered.statusCode(), registered.body());
            assertTrue(json(registered).path("read_only_registration").asBoolean());
            HttpResponse<String> validation = post(port, "/v1/programs/validate", OPERATOR, Map.of(
                    "project_id", "project", "target_id", "target", "profile", "INSPECT_ONLY"));
            assertEquals(200, validation.statusCode(), validation.body());
            assertFalse(json(validation).path("source_mutation_detected").asBoolean(true));
            JsonNode programs = json(get(port, "/v1/programs", VIEWER));
            assertEquals(1, programs.path("program_count").asInt());

            HttpResponse<String> requested = post(port, "/v1/gateway-settings/requests", ADMIN, Map.of(
                    "provider", "local-mock", "model", "new-model", "requests_per_second", 20,
                    "cost_per_token_micros", 1, "reason", "test request"));
            assertEquals(200, requested.statusCode(), requested.body());
            String requestId = json(requested).path("request_id").asText();
            assertEquals(403, post(port, "/v1/gateway-settings/approvals", ADMIN, Map.of(
                    "request_id", requestId, "decision", "APPROVE", "reason", "wrong role")).statusCode());
            HttpResponse<String> approved = post(port, "/v1/gateway-settings/approvals", APPROVER, Map.of(
                    "request_id", requestId, "decision", "APPROVE", "reason", "reviewed"));
            assertEquals(200, approved.statusCode(), approved.body());
            assertEquals("APPROVED_PENDING_EXTERNAL_APPLY", json(approved).path("state").asText());
            JsonNode audit = json(get(port, "/v1/audit-events", VIEWER));
            assertEquals(4, audit.path("event_count").asInt());
            assertTrue(audit.path("chain_valid").asBoolean());
        } finally {
            server.stop();
        }
    }

    private Map<String, Object> registration(Path source) {
        return Map.ofEntries(
                Map.entry("workspace_id", "local"), Map.entry("workspace_name", "Local"),
                Map.entry("project_id", "project"), Map.entry("project_name", "Project"),
                Map.entry("target_id", "target"), Map.entry("target_name", "Target"),
                Map.entry("target_type", "GENERAL_SOFTWARE"), Map.entry("source_root", source.toString()));
    }

    private HttpResponse<String> get(int port, String path, String token) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + path))
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(int port, String path, String token, Map<String, Object> body)
            throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + path))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode json(HttpResponse<String> response) throws Exception {
        assertEquals(200, response.statusCode(), response.body());
        return mapper.readTree(response.body());
    }
}
