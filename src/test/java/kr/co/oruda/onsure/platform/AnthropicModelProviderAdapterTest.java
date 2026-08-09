package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import kr.co.oruda.onsure.platform.ModelProviderAdapter.ModelInvocationRecord;
import kr.co.oruda.onsure.platform.ModelProviderAdapter.ModelInvocationRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Two kinds of coverage for {@link AnthropicModelProviderAdapter}:
 *
 * <ol>
 *   <li>Structural tests against a local, in-process {@link HttpServer} fake -- real HTTP request
 *       construction and response parsing, no real network access, no real API key. These run
 *       unconditionally.
 *   <li>One real-network, key-gated live test mirroring
 *       {@code DatabaseSnapshotServiceTest}'s {@code Assumptions.assumeTrue} skip-guard: it is
 *       skipped cleanly whenever {@code ANTHROPIC_API_KEY} is not set.
 * </ol>
 */
class AnthropicModelProviderAdapterTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String FAKE_API_KEY = "sk-ant-test-fake-key";

    private HttpServer server;
    private ExecutorService serverExecutor;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
    }

    private String baseUrlOf(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private HttpServer startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer newServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        newServer.createContext("/v1/messages", handler);
        serverExecutor = Executors.newCachedThreadPool();
        newServer.setExecutor(serverExecutor);
        newServer.start();
        this.server = newServer;
        return newServer;
    }

    private static void respondJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("content-type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String sha256Hex(String text) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static final String CANNED_RESPONSE = """
            {
              "id": "msg_01Test123456789",
              "type": "message",
              "role": "assistant",
              "model": "claude-opus-5",
              "content": [{"type": "text", "text": "Acknowledged."}],
              "stop_reason": "end_turn",
              "stop_sequence": null,
              "usage": {"input_tokens": 42, "output_tokens": 7}
            }
            """;

    @Test
    void invokeSendsACorrectlyShapedRequestAndParsesARealResponse() throws Exception {
        AtomicReference<String> capturedMethod = new AtomicReference<>();
        AtomicReference<String> capturedPath = new AtomicReference<>();
        AtomicReference<String> capturedApiKeyHeader = new AtomicReference<>();
        AtomicReference<String> capturedVersionHeader = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();

        HttpServer fake = startServer(exchange -> {
            capturedMethod.set(exchange.getRequestMethod());
            capturedPath.set(exchange.getRequestURI().getPath());
            capturedApiKeyHeader.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            capturedVersionHeader.set(exchange.getRequestHeaders().getFirst("anthropic-version"));
            capturedBody.set(readBody(exchange));
            respondJson(exchange, 200, CANNED_RESPONSE);
        });

        AnthropicModelProviderAdapter adapter =
                new AnthropicModelProviderAdapter(baseUrlOf(fake), () -> FAKE_API_KEY);
        ModelInvocationRequest request = new ModelInvocationRequest(
                "REVIEW", "claude-opus-5", Map.of("temperature", "0"), "digest-abc123", 100);

        ModelInvocationRecord record = adapter.invoke(request);

        // --- request shape ---
        assertEquals("POST", capturedMethod.get());
        assertEquals("/v1/messages", capturedPath.get());
        assertEquals(FAKE_API_KEY, capturedApiKeyHeader.get());
        assertEquals("2023-06-01", capturedVersionHeader.get());
        JsonNode requestJson = OBJECT_MAPPER.readTree(capturedBody.get());
        assertEquals("claude-opus-5", requestJson.path("model").asText());
        assertTrue(requestJson.path("max_tokens").asInt() > 0);
        assertTrue(requestJson.path("messages").isArray());
        assertEquals(1, requestJson.path("messages").size());
        assertEquals("user", requestJson.path("messages").get(0).path("role").asText());
        String sentContent = requestJson.path("messages").get(0).path("content").asText();
        assertTrue(sentContent.contains("REVIEW"), "request content should reference the task class");
        assertTrue(sentContent.contains("digest-abc123"), "request content should reference the input digest");

        // --- response parsing ---
        assertEquals("anthropic", record.providerId());
        assertEquals("claude-opus-5", record.modelId());
        assertEquals("claude-opus-5", record.modelVersion());
        assertEquals("REVIEW", record.taskClass());
        assertEquals(42, record.inputTokenCount());
        assertEquals(7, record.outputTokenCount());
        // claude-opus-5 pricing: 5 micros/input token, 25 micros/output token -> 42*5 + 7*25 = 385
        assertEquals(385L, record.costMicros());
        assertEquals("EXTERNAL_NETWORK_EGRESS_ALLOWLISTED", record.dataTransferPolicy());
        assertEquals("sha256:" + sha256Hex("Acknowledged."), record.outputDigest());
        assertEquals("anthropic:msg_01Test123456789", record.invocationId());
        assertEquals(Map.of("temperature", "0"), record.configuration());
    }

    @Test
    void invokeFailsClosedOnNon2xxResponse() throws Exception {
        HttpServer fake = startServer(exchange -> respondJson(exchange, 401,
                "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}"));

        AnthropicModelProviderAdapter adapter =
                new AnthropicModelProviderAdapter(baseUrlOf(fake), () -> FAKE_API_KEY);
        ModelInvocationRequest request =
                new ModelInvocationRequest("REVIEW", "claude-opus-5", Map.of(), "digest-1", 10);

        Exception failure = assertThrows(Exception.class, () -> adapter.invoke(request));
        assertTrue(failure.getMessage().contains("401"), "failure message should mention the HTTP status");
        assertTrue(failure.getMessage().contains("authentication_error"));
    }

    @Test
    void invokeFailsClosedWhenUsageFieldIsMissing() throws Exception {
        HttpServer fake = startServer(exchange -> respondJson(exchange, 200, """
                {"id": "msg_01NoUsage", "model": "claude-opus-5",
                 "content": [{"type": "text", "text": "hi"}]}
                """));

        AnthropicModelProviderAdapter adapter =
                new AnthropicModelProviderAdapter(baseUrlOf(fake), () -> FAKE_API_KEY);
        ModelInvocationRequest request =
                new ModelInvocationRequest("REVIEW", "claude-opus-5", Map.of(), "digest-2", 10);

        Exception failure = assertThrows(Exception.class, () -> adapter.invoke(request));
        assertTrue(failure.getMessage().contains("usage"), "failure message should mention the missing usage field");
    }

    @Test
    void invokeFailsClosedWhenContentFieldIsMissing() throws Exception {
        HttpServer fake = startServer(exchange -> respondJson(exchange, 200, """
                {"id": "msg_01NoContent", "model": "claude-opus-5",
                 "usage": {"input_tokens": 5, "output_tokens": 1}}
                """));

        AnthropicModelProviderAdapter adapter =
                new AnthropicModelProviderAdapter(baseUrlOf(fake), () -> FAKE_API_KEY);
        ModelInvocationRequest request =
                new ModelInvocationRequest("REVIEW", "claude-opus-5", Map.of(), "digest-3", 10);

        Exception failure = assertThrows(Exception.class, () -> adapter.invoke(request));
        assertTrue(failure.getMessage().contains("content"), "failure message should mention the missing content field");
    }

    @Test
    void invokeFailsClosedOnMalformedJsonBody() throws Exception {
        HttpServer fake = startServer(exchange -> {
            byte[] bytes = "not json at all {".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });

        AnthropicModelProviderAdapter adapter =
                new AnthropicModelProviderAdapter(baseUrlOf(fake), () -> FAKE_API_KEY);
        ModelInvocationRequest request =
                new ModelInvocationRequest("REVIEW", "claude-opus-5", Map.of(), "digest-4", 10);

        assertThrows(Exception.class, () -> adapter.invoke(request));
    }

    @Test
    void invokeEnforcesABoundedRequestTimeoutRatherThanHangingForever() throws Exception {
        CountDownLatch requestReceived = new CountDownLatch(1);
        HttpServer fake = startServer(exchange -> {
            requestReceived.countDown();
            // Never respond. exchange is intentionally left open; the server is force-stopped in
            // @AfterEach via server.stop(0), which does not wait for in-flight handlers.
        });

        AnthropicModelProviderAdapter adapter = new AnthropicModelProviderAdapter(
                baseUrlOf(fake), () -> FAKE_API_KEY, Duration.ofMillis(500), Duration.ofMillis(500));
        ModelInvocationRequest request =
                new ModelInvocationRequest("REVIEW", "claude-opus-5", Map.of(), "digest-5", 10);

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            Exception failure = assertThrows(Exception.class, () -> adapter.invoke(request));
            assertNotNull(failure);
        });
        assertTrue(requestReceived.await(5, TimeUnit.SECONDS), "fake server should have received the request");
    }

    @Test
    void supportsTaskClassAndDeclaredModelIdsAreConsistentWithSupportedProviderConfiguration() {
        AnthropicModelProviderAdapter adapter = new AnthropicModelProviderAdapter(() -> FAKE_API_KEY);
        assertEquals("anthropic", adapter.providerId());
        assertTrue(adapter.supportsTaskClass("REVIEW"));
        assertTrue(adapter.supportsTaskClass("PLANNING"));
        assertTrue(!adapter.supportsTaskClass("SOMETHING_UNSUPPORTED"));
        assertTrue(!adapter.supportsTaskClass(null));
        assertTrue(adapter.declaredModelIds().contains("claude-opus-5"));
        assertTrue(adapter.declaredModelIds().contains("claude-sonnet-5"));
        assertTrue(adapter.declaredModelIds().contains("claude-haiku-4-5"));
    }

    @Test
    void invokeRejectsAnUndeclaredModelIdWithoutMakingAnyNetworkCall() {
        // No server is started at all -- if this made a network call it would fail with a connection
        // error, not the IllegalArgumentException asserted below.
        AnthropicModelProviderAdapter adapter =
                new AnthropicModelProviderAdapter("http://127.0.0.1:1", () -> FAKE_API_KEY);
        ModelInvocationRequest request =
                new ModelInvocationRequest("REVIEW", "not-a-real-model", Map.of(), "digest-6", 10);

        assertThrows(IllegalArgumentException.class, () -> adapter.invoke(request));
    }

    @Test
    void invokeFailsClosedWhenApiKeyIsBlank() throws Exception {
        HttpServer fake = startServer(exchange -> respondJson(exchange, 200, CANNED_RESPONSE));
        AnthropicModelProviderAdapter adapter =
                new AnthropicModelProviderAdapter(baseUrlOf(fake), () -> "   ");
        ModelInvocationRequest request =
                new ModelInvocationRequest("REVIEW", "claude-opus-5", Map.of(), "digest-7", 10);

        assertThrows(IllegalStateException.class, () -> adapter.invoke(request));
    }

    // --- Live, real-network test -----------------------------------------------------------------

    /**
     * Mirrors {@code DatabaseSnapshotServiceTest}'s {@code Assumptions.assumeTrue} skip-guard
     * pattern exactly: if {@code ANTHROPIC_API_KEY} is not set, this test is skipped cleanly with a
     * clear reason rather than failing or attempting a workaround. There is currently no real
     * Anthropic key configured in this sandbox, so this legitimately skips.
     */
    @Test
    void invokeAgainstTheRealAnthropicApiWhenAKeyIsConfigured() throws Exception {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
                "ANTHROPIC_API_KEY not set; skipping the real-network Anthropic live test");

        AnthropicModelProviderAdapter adapter = AnthropicModelProviderAdapter.fromEnvironment();
        ModelInvocationRequest request = new ModelInvocationRequest(
                "REVIEW", "claude-haiku-4-5", Map.of(), "live-test-digest", 10);

        ModelInvocationRecord record = adapter.invoke(request);

        assertEquals("anthropic", record.providerId());
        assertEquals("claude-haiku-4-5", record.modelId());
        assertTrue(record.inputTokenCount() > 0);
        assertTrue(record.outputTokenCount() > 0);
        assertTrue(record.costMicros() > 0);
        assertTrue(record.outputDigest().startsWith("sha256:"));
    }
}
