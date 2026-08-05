package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
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

            HttpResponse<String> openApi = client.send(
                    HttpRequest.newBuilder(URI.create(
                                    "http://127.0.0.1:" + port + "/v1/openapi.json"))
                            .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, openApi.statusCode());
            assertTrue(openApi.headers().firstValue("Content-Type").orElse("")
                    .startsWith("application/vnd.oai.openapi+json"));
            JsonNode openApiBody = mapper.readTree(openApi.body());
            assertEquals("3.1.0", openApiBody.path("openapi").asText());
            Set<String> documentedPaths = new java.util.TreeSet<>();
            openApiBody.path("paths").fieldNames().forEachRemaining(documentedPaths::add);
            assertEquals(new java.util.TreeSet<>(LocalAuthenticatedApiServer.routePaths()),
                    documentedPaths);
            assertEquals("http", openApiBody.path("components").path("securitySchemes")
                    .path("bearerAuth").path("type").asText());

            HttpResponse<String> admin = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/admin"))
                            .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, admin.statusCode());
            assertTrue(admin.body().contains("ONSure Control Room"));
            assertTrue(admin.body().contains("value=\"UNIVERSAL\""));
            assertTrue(admin.body().contains("environment-profile"));
            assertTrue(admin.headers().firstValue("Content-Security-Policy").orElse("")
                    .contains("script-src 'self'"));

            HttpResponse<String> script = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/admin/app.js"))
                            .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, script.statusCode());
            assertTrue(script.body().contains("/v1/management-overview"));
            assertTrue(LocalAuthenticatedApiServer.routePaths().contains("/v1/validation-scorecards"));
            assertTrue(script.body().contains("environment_profile_file"));
            assertTrue(script.body().contains("execution_profile_file"));
            assertTrue(!script.body().contains("localStorage"));
            assertTrue(!script.body().contains("sessionStorage"));

            HttpResponse<String> unauthorized = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v1/status")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(401, unauthorized.statusCode());

            HttpResponse<String> forbiddenOrigin = client.send(
                    HttpRequest.newBuilder(URI.create(
                                    "http://127.0.0.1:" + port + "/v1/management-overview"))
                            .header("Authorization", "Bearer " + token)
                            .header("Origin", "http://attacker.invalid")
                            .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(403, forbiddenOrigin.statusCode());

            HttpResponse<String> overview = client.send(
                    HttpRequest.newBuilder(URI.create(
                                    "http://127.0.0.1:" + port + "/v1/management-overview"))
                            .header("Authorization", "Bearer " + token)
                            .header("Origin", "http://127.0.0.1:" + port)
                            .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, overview.statusCode(), overview.body());
            JsonNode overviewBody = mapper.readTree(overview.body());
            assertEquals("ONSURE_MANAGEMENT_OVERVIEW_V1", overviewBody.path("contract").asText());
            assertTrue(overviewBody.path("programs").isArray());
            assertTrue(!overviewBody.path("assurance").path("production_go").asBoolean(true));

            HttpResponse<String> scorecards = client.send(
                    HttpRequest.newBuilder(URI.create(
                                    "http://127.0.0.1:" + port + "/v1/validation-scorecards"))
                            .header("Authorization", "Bearer " + token)
                            .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, scorecards.statusCode(), scorecards.body());
            JsonNode scorecardBody = mapper.readTree(scorecards.body());
            assertEquals("ONSURE_VALIDATION_SCORECARD_PORTFOLIO_V1",
                    scorecardBody.path("contract").asText());
            assertTrue(!scorecardBody.path("final_claim_allowed").asBoolean(true));

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
}
