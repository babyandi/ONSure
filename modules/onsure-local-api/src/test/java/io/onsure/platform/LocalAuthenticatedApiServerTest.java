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
            assertEquals("SELF_VALIDATION_NONFINAL", authorizedBody.path("validation").asText());
            assertTrue(!authorizedBody.path("final_lock_allowed").asBoolean(true));
        } finally {
            server.stop();
        }
    }
}
