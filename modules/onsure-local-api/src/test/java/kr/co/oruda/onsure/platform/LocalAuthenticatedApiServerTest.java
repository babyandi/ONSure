package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalAuthenticatedApiServerTest {
    @TempDir Path temp;

    @Test
    void bindsLoopbackAndRejectsUnauthenticatedStatus() throws Exception {
        String token = "local-api-test-token-" + "x".repeat(32);
        int port;
        try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        LocalAuthenticatedApiServer server = new LocalAuthenticatedApiServer(temp, token);
        server.start(port);
        try {
            HttpClient client = HttpClient.newHttpClient();
            ObjectMapper mapper = new ObjectMapper();
            HttpResponse<String> health = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v1/health")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, health.statusCode());
            JsonNode healthBody = mapper.readTree(health.body());
            assertEquals("127.0.0.1", healthBody.path("binding").asText());
            assertEquals("SELF_VALIDATION_NONFINAL", healthBody.path("assurance_class").asText());
            assertTrue(!healthBody.path("final_claim_allowed").asBoolean(true));

            HttpResponse<String> unauthorized = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v1/status")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(401, unauthorized.statusCode());

            HttpResponse<String> authorized = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v1/status"))
                            .header("Authorization", "Bearer " + token).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, authorized.statusCode());
            JsonNode authorizedBody = mapper.readTree(authorized.body());
            assertEquals("AVAILABLE_SELF_VALIDATION_NONFINAL", authorizedBody.path("validation").asText());
            assertEquals("NOT_RUN", authorizedBody.path("independent_otester").asText());
            assertEquals("NOT_RUN", authorizedBody.path("independent_oaudit").asText());
            assertTrue(!authorizedBody.path("final_lock_allowed").asBoolean(true));
        } finally {
            server.stop();
        }
    }

    @Test
    void authenticatedViewerReceivesForbiddenForWriteWorkflow() throws Exception {
        String token = "local-api-viewer-token-" + "z".repeat(32);
        AuthenticatedWorkflowIdentity viewer = new AuthenticatedWorkflowIdentity(
                "organization", "tenant-a", "workspace", "viewer-a",
                Set.of(AuthenticatedWorkflowIdentity.Role.VIEWER), "EU",
                AuthenticatedWorkflowIdentity.AuthenticationMethod.SIGNED_ENTERPRISE_IDENTITY);
        LocalAuthenticatedApiServer server = new LocalAuthenticatedApiServer(temp, token, viewer);
        int port = server.startAndGetPort(0);
        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v1/workflow"))
                            .header("Authorization", "Bearer " + token)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("""
                                    {"operation":"project.register-workspace","request":{
                                      "workspace_id":"workspace-1","workspace_name":"Denied"
                                    }}
                                    """))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, response.statusCode());
            JsonNode body = new ObjectMapper().readTree(response.body());
            assertEquals("FORBIDDEN", body.path("error").asText());
            assertEquals("RBAC_OPERATION_DENIED:project.register-workspace:OPERATOR",
                    body.path("message").asText());
        } finally {
            server.stop();
        }
    }

    @Test
    void runArtifactIsAvailableOnlyToTheTenantThatCreatedTheRun() throws Exception {
        Path runRoot = temp.resolve(".onsure/validation-data/runs/run-1");
        Files.createDirectories(runRoot);
        Files.writeString(runRoot.resolve("validation-report.json"), "{\"decision\":\"PASS\"}");
        AuthenticatedWorkflowIdentity operator = new AuthenticatedWorkflowIdentity(
                "organization", "tenant-a", "workspace", "operator-a",
                Set.of(AuthenticatedWorkflowIdentity.Role.OPERATOR), "EU",
                AuthenticatedWorkflowIdentity.AuthenticationMethod.SIGNED_ENTERPRISE_IDENTITY);
        new TenantRbacService(temp).execute(
                operator, "validation.run", new ObjectMapper().createObjectNode(),
                () -> Map.of("result", Map.of("run_root", runRoot.toString())));

        AuthenticatedWorkflowIdentity viewerA = new AuthenticatedWorkflowIdentity(
                "organization", "tenant-a", "workspace", "viewer-a",
                Set.of(AuthenticatedWorkflowIdentity.Role.VIEWER), "EU",
                AuthenticatedWorkflowIdentity.AuthenticationMethod.SIGNED_ENTERPRISE_IDENTITY);
        HttpResponse<String> allowed = requestArtifact(viewerA, runRoot, "a");
        assertEquals(200, allowed.statusCode());
        assertEquals("PASS", new ObjectMapper().readTree(allowed.body())
                .path("body").path("decision").asText());

        AuthenticatedWorkflowIdentity viewerB = new AuthenticatedWorkflowIdentity(
                "organization", "tenant-b", "workspace", "viewer-b",
                Set.of(AuthenticatedWorkflowIdentity.Role.VIEWER), "EU",
                AuthenticatedWorkflowIdentity.AuthenticationMethod.SIGNED_ENTERPRISE_IDENTITY);
        HttpResponse<String> denied = requestArtifact(viewerB, runRoot, "b");
        assertEquals(403, denied.statusCode());
        assertTrue(denied.body().contains("CROSS_TENANT_RESOURCE_ACCESS_DENIED"));
    }

    private HttpResponse<String> requestArtifact(
            AuthenticatedWorkflowIdentity identity, Path runRoot, String suffix) throws Exception {
        String token = "local-api-artifact-token-" + suffix + "-" + "q".repeat(32);
        LocalAuthenticatedApiServer server = new LocalAuthenticatedApiServer(temp, token, identity);
        int port = server.startAndGetPort(0);
        try {
            String body = new ObjectMapper().writeValueAsString(Map.of(
                    "run_root", runRoot.toString(), "artifact", "validation-report.json"));
            return HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v1/run-artifact"))
                            .header("Authorization", "Bearer " + token)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        } finally {
            server.stop();
        }
    }
}
