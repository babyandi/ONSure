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
import java.util.concurrent.atomic.AtomicInteger;
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

    @Test
    void exposesStructuredErrorsBoundedRetryPaginationAndAnonymization() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ObjectMapper mapper = new ObjectMapper();
        AtomicInteger statusCalls = new AtomicInteger();
        AtomicReference<Map<?, ?>> workflowBody = new AtomicReference<>();
        server.createContext("/v1/status", exchange -> {
            int call = statusCalls.incrementAndGet();
            byte[] response = (call == 1
                    ? "{\"error\":\"LOCAL_API_BUSY\",\"message\":\"retry\",\"request_id\":\"r-1\"}"
                    : "{\"state\":\"RUNNING\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(call == 1 ? 503 : 200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/v1/workflow", exchange -> {
            Map<?, ?> body = mapper.readValue(exchange.getRequestBody(), Map.class);
            workflowBody.set(body);
            String operation = String.valueOf(body.get("operation"));
            byte[] response = ("knowledge.anonymize".equals(operation)
                    ? "{\"workflow\":{\"result\":{\"contract\":\"ONSURE_PROJECT_KNOWLEDGE_SEPARATION_V1\"}}}"
                    : "{\"workflow\":{\"result\":{\"items\":[{\"id\":\"a\"}],\"next_cursor\":\"c2\"}}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            ONSureClient client = ONSureClient.connect(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "b".repeat(32),
                    Duration.ofSeconds(2), new RetryPolicy(2, Duration.ZERO, Duration.ZERO));
            assertTrue(client.statusWithRetry().successful());
            assertEquals(2, statusCalls.get());
            Page<Map<String, Object>> page = client.workflowPage("case.read", Map.of("case_id", "a"), null, 10);
            assertEquals("a", page.items().get(0).get("id"));
            assertEquals("c2", page.nextCursor());
            assertTrue(client.anonymizeProjectKnowledge(
                    "project-1", Map.of("common.fact", "value"), "/tmp/salt").successful());
            assertEquals("knowledge.anonymize", workflowBody.get().get("operation"));
            ONSureApiException error = new ONSureClient.ApiResponse(429, Map.of(
                    "error", "LOCAL_API_BUSY", "message", "busy", "request_id", "r-2")).asException();
            assertEquals("LOCAL_API_BUSY", error.code());
            assertTrue(error.retryable());
            assertEquals("LOCAL_API_ERROR", new ONSureApiException(
                    500, null, null, null, false).getMessage());
            assertThrows(IllegalStateException.class,
                    () -> new ONSureClient.ApiResponse(200, Map.of()).asException());
        } finally {
            server.stop(0);
        }
    }
}
