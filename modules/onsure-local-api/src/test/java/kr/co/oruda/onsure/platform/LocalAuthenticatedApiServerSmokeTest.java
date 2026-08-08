package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalAuthenticatedApiServerSmokeTest {
    @TempDir Path temp;

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
}