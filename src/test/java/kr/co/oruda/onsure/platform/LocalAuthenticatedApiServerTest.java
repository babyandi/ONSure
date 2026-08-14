package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End-to-end coverage for the loopback authenticated API, including Idempotency-Key handling. */
class LocalAuthenticatedApiServerTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String token = "a".repeat(40);
    private LocalAuthenticatedApiServer server;
    private int port;

    @BeforeEach
    void start() throws Exception {
        server = new LocalAuthenticatedApiServer(temp, token);
        port = server.startAndGetPort(0);
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void healthEndpointRequiresNoAuthentication() throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v1/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("RUNNING", mapper.readTree(response.body()).path("state").asText());
    }

    @Test
    void workflowWithoutBearerTokenIsUnauthorized() throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v1/status")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode());
        assertEquals("UNAUTHORIZED", mapper.readTree(response.body()).path("error").asText());
    }

    @Test
    void statusEndpointReflectsAuthenticatedIdentityOnceAuthorized() throws Exception {
        HttpResponse<String> response = client.send(authed("GET", "/v1/status", null), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        JsonNode body = mapper.readTree(response.body());
        assertFalse(body.path("final_lock_allowed").asBoolean());
    }

    @Test
    void workflowWithoutIdempotencyKeyAlwaysExecutes() throws Exception {
        String requestBody = "{\"operation\":\"project.register-workspace\","
                + "\"request\":{\"workspace_id\":\"workspace-1\",\"workspace_name\":\"Workspace\"}}";
        HttpResponse<String> first = client.send(
                authed("POST", "/v1/workflow", requestBody), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, first.statusCode());
        assertFalse(mapper.readTree(first.body()).has("idempotency_replay"));
    }

    @Test
    void workflowWithIdempotencyKeyReplaysIdenticalRequestsAndRejectsConflicts() throws Exception {
        HttpRequest.Builder base = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/v1/workflow"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "e2e-key-00000001");

        String openRequest = "{\"operation\":\"project.register-workspace\","
                + "\"request\":{\"workspace_id\":\"workspace-2\",\"workspace_name\":\"Workspace Two\"}}";
        HttpResponse<String> first = client.send(
                base.copy().POST(HttpRequest.BodyPublishers.ofString(openRequest)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, first.statusCode());
        JsonNode firstBody = mapper.readTree(first.body());
        assertFalse(firstBody.path("idempotency_replay").asBoolean());

        HttpResponse<String> replay = client.send(
                base.copy().POST(HttpRequest.BodyPublishers.ofString(openRequest)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, replay.statusCode());
        JsonNode replayBody = mapper.readTree(replay.body());
        assertTrue(replayBody.path("idempotency_replay").asBoolean());
        assertEquals(firstBody.path("workflow"), replayBody.path("workflow"));

        String conflictingRequest = "{\"operation\":\"project.register-workspace\","
                + "\"request\":{\"workspace_id\":\"workspace-3\",\"workspace_name\":\"Different Body\"}}";
        HttpResponse<String> conflict = client.send(
                base.copy().POST(HttpRequest.BodyPublishers.ofString(conflictingRequest)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(409, conflict.statusCode());
        assertEquals("IDEMPOTENCY_KEY_REQUEST_MISMATCH", mapper.readTree(conflict.body()).path("error").asText());
    }

    @Test
    void malformedIdempotencyKeyIsRejectedAsBadRequest() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v1/workflow"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "short")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"operation\":\"project.register-workspace\",\"request\":{}}"))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, response.statusCode());
        assertEquals("IDEMPOTENCY_KEY_INVALID", mapper.readTree(response.body()).path("error").asText());
    }

    private HttpRequest authed(String method, String path, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Authorization", "Bearer " + token);
        if (body != null) {
            builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.GET();
        }
        return builder.build();
    }
}
