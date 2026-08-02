package io.onsure.sdk.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ONSureClientTest {
    @Test
    void callsAuthenticatedLoopbackStatusAndWorkflow() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<Map<?, ?>> requestBody = new AtomicReference<>();
        ObjectMapper mapper = new ObjectMapper();
        server.createContext("/v1/status", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = "{\"state\":\"RUNNING\",\"final_claim_allowed\":false}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/v1/workflow", exchange -> {
            requestBody.set(mapper.readValue(exchange.getRequestBody(), Map.class));
            byte[] response = "{\"workflow\":{\"operation\":\"project.list-targets\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            ONSureClient client = ONSureClient.connect(uri, "a".repeat(32), Duration.ofSeconds(5));
            assertTrue(client.status().successful());
            assertEquals("Bearer " + "a".repeat(32), authorization.get());
            assertTrue(client.workflow("project.list-targets", Map.of("project_id", "p-1")).successful());
            assertEquals("project.list-targets", requestBody.get().get("operation"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsRemoteUrisWeakTokensAndUnboundedTimeouts() {
        assertThrows(IllegalArgumentException.class, () ->
                ONSureClient.connect(URI.create("https://127.0.0.1:47311"), "a".repeat(32)));
        assertThrows(IllegalArgumentException.class, () ->
                ONSureClient.connect(URI.create("http://example.com:47311"), "a".repeat(32)));
        assertThrows(IllegalArgumentException.class, () ->
                ONSureClient.connect(URI.create("http://127.0.0.1:47311"), "short"));
        assertThrows(IllegalArgumentException.class, () -> ONSureClient.connect(
                URI.create("http://127.0.0.1:47311"), "a".repeat(32), Duration.ofMinutes(4)));
    }
}
