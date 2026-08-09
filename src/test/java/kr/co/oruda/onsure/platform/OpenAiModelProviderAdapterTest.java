package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import kr.co.oruda.onsure.platform.ModelProviderAdapter.ModelInvocationRecord;
import kr.co.oruda.onsure.platform.ModelProviderAdapter.ModelInvocationRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Two kinds of tests, mirroring {@code DatabaseSnapshotServiceTest}'s and
 * {@code ContainerValidationStageTest}'s guarding pattern for the one that needs a real external
 * resource:
 *
 * <ol>
 *   <li>Structural tests against a real local {@link HttpServer} fake, run unconditionally, no
 *       network access or API key required -- these exercise real HTTP request construction and
 *       real response parsing, nothing mocked away.
 *   <li>One real-network, {@code OPENAI_API_KEY}-gated live test, skipped cleanly via
 *       {@link Assumptions#assumeTrue} when the key is not set.
 * </ol>
 */
class OpenAiModelProviderAdapterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;
    private ExecutorService executor;
    private int port;

    @BeforeEach
    void startFakeServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // Daemon threads + shutdownNow() in @AfterEach so a handler that deliberately never
        // responds (the timeout test) can never hang the surefire JVM at test-suite exit.
        executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stopFakeServer() {
        server.stop(0);
        executor.shutdownNow();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    private static final String CANNED_RESPONSE = """
            {
              "id": "chatcmpl-fake123",
              "object": "chat.completion",
              "model": "gpt-4o-mini-2024-07-18",
              "choices": [
                {
                  "index": 0,
                  "message": {"role": "assistant", "content": "Understood."},
                  "finish_reason": "stop"
                }
              ],
              "usage": {"prompt_tokens": 12, "completion_tokens": 4, "total_tokens": 16}
            }
            """;

    private void respondWith(String path, int status, String responseBody) {
        server.createContext(path, exchange -> {
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
    }

    private ModelInvocationRequest sampleRequest() {
        return new ModelInvocationRequest(
                "REVIEW", "gpt-4o-mini", Map.of("temperature", "0"), "digest-abc123", 42);
    }

    @Test
    void requestIsShapedCorrectlyPathHeadersAndBody() throws Exception {
        AtomicReference<HttpExchange> captured = new AtomicReference<>();
        AtomicReference<JsonNode> capturedBody = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            captured.set(exchange);
            byte[] requestBytes = exchange.getRequestBody().readAllBytes();
            capturedBody.set(MAPPER.readTree(requestBytes));
            byte[] responseBytes = CANNED_RESPONSE.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (var os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        });

        OpenAiModelProviderAdapter adapter =
                new OpenAiModelProviderAdapter(baseUrl(), () -> "test-api-key-xyz");
        adapter.invoke(sampleRequest());

        HttpExchange exchange = captured.get();
        assertEquals("POST", exchange.getRequestMethod());
        assertEquals("/v1/chat/completions", exchange.getRequestURI().getPath());
        assertEquals("Bearer test-api-key-xyz", exchange.getRequestHeaders().getFirst("Authorization"));
        assertEquals("application/json", exchange.getRequestHeaders().getFirst("Content-Type"));

        JsonNode body = capturedBody.get();
        assertEquals("gpt-4o-mini", body.path("model").asText());
        assertTrue(body.path("messages").isArray());
        assertEquals(1, body.path("messages").size());
        assertEquals("user", body.path("messages").get(0).path("role").asText());
        String content = body.path("messages").get(0).path("content").asText();
        assertTrue(content.contains("REVIEW"), "user content should reference the request's task class");
        assertTrue(content.contains("digest-abc123"), "user content should reference the request's inputDigest");
    }

    @Test
    void responseIsParsedCorrectlyTokenCountsCostAndDigest() throws Exception {
        respondWith("/v1/chat/completions", 200, CANNED_RESPONSE);

        OpenAiModelProviderAdapter adapter =
                new OpenAiModelProviderAdapter(baseUrl(), () -> "test-api-key-xyz");
        ModelInvocationRecord record = adapter.invoke(sampleRequest());

        assertEquals("openai", record.providerId());
        assertEquals("gpt-4o-mini", record.modelId());
        assertEquals("gpt-4o-mini-2024-07-18", record.modelVersion());
        assertEquals("REVIEW", record.taskClass());
        assertEquals(Map.of("temperature", "0"), record.configuration());
        assertEquals(12, record.inputTokenCount());
        assertEquals(4, record.outputTokenCount());
        // gpt-4o-mini pricing: 150_000 micros/million input, 600_000 micros/million output.
        // input: 12 * 150_000 = 1_800_000 -> /1e6 = 1 remainder 800_000 -> rounds up to 2.
        // output: 4 * 600_000 = 2_400_000 -> /1e6 = 2 remainder 400_000 -> stays 2.
        assertEquals(4, record.costMicros());
        assertEquals("EXTERNAL_NETWORK_EGRESS_ALLOWLISTED", record.dataTransferPolicy());
        assertTrue(record.invocationId().startsWith("openai-"));

        String expectedDigest = "sha256:" + sha256Hex("Understood.");
        assertEquals(expectedDigest, record.outputDigest());
    }

    @Test
    void nonTwoXxResponseFailsClosed() throws Exception {
        respondWith("/v1/chat/completions", 500, "{\"error\":\"internal\"}");

        OpenAiModelProviderAdapter adapter =
                new OpenAiModelProviderAdapter(baseUrl(), () -> "test-api-key-xyz");
        Exception failure = assertThrows(Exception.class, () -> adapter.invoke(sampleRequest()));
        assertTrue(failure.getMessage().contains("OPENAI_HTTP_ERROR"), failure.getMessage());
        assertTrue(failure.getMessage().contains("500"), failure.getMessage());
    }

    @Test
    void responseMissingUsageFailsClosed() throws Exception {
        respondWith("/v1/chat/completions", 200, """
                {"model": "gpt-4o-mini-2024-07-18",
                 "choices": [{"message": {"role": "assistant", "content": "hi"}}]}
                """);

        OpenAiModelProviderAdapter adapter =
                new OpenAiModelProviderAdapter(baseUrl(), () -> "test-api-key-xyz");
        Exception failure = assertThrows(Exception.class, () -> adapter.invoke(sampleRequest()));
        assertTrue(failure.getMessage().contains("OPENAI_MALFORMED_RESPONSE"), failure.getMessage());
    }

    @Test
    void responseMissingChoicesFailsClosed() throws Exception {
        respondWith("/v1/chat/completions", 200, """
                {"model": "gpt-4o-mini-2024-07-18",
                 "usage": {"prompt_tokens": 5, "completion_tokens": 1}}
                """);

        OpenAiModelProviderAdapter adapter =
                new OpenAiModelProviderAdapter(baseUrl(), () -> "test-api-key-xyz");
        Exception failure = assertThrows(Exception.class, () -> adapter.invoke(sampleRequest()));
        assertTrue(failure.getMessage().contains("OPENAI_MALFORMED_RESPONSE"), failure.getMessage());
    }

    @Test
    void missingApiKeyFailsClosedWithoutMakingARequest() {
        respondWith("/v1/chat/completions", 200, CANNED_RESPONSE);

        OpenAiModelProviderAdapter adapter = new OpenAiModelProviderAdapter(baseUrl(), () -> null);
        Exception failure = assertThrows(Exception.class, () -> adapter.invoke(sampleRequest()));
        assertTrue(failure.getMessage().contains("OPENAI_API_KEY"), failure.getMessage());
    }

    @Test
    void unsupportedTaskClassFailsClosedWithoutMakingARequest() {
        respondWith("/v1/chat/completions", 200, CANNED_RESPONSE);

        OpenAiModelProviderAdapter adapter =
                new OpenAiModelProviderAdapter(baseUrl(), () -> "test-api-key-xyz");
        ModelInvocationRequest unsupported = new ModelInvocationRequest(
                "STATIC_ANALYSIS", "gpt-4o-mini", Map.of(), "digest-abc123", 42);
        assertThrows(IllegalArgumentException.class, () -> adapter.invoke(unsupported));
    }

    @Test
    void undeclaredModelFailsClosed() {
        respondWith("/v1/chat/completions", 200, CANNED_RESPONSE);

        OpenAiModelProviderAdapter adapter =
                new OpenAiModelProviderAdapter(baseUrl(), () -> "test-api-key-xyz");
        ModelInvocationRequest unknownModel = new ModelInvocationRequest(
                "REVIEW", "not-a-real-model", Map.of(), "digest-abc123", 42);
        Exception failure = assertThrows(Exception.class, () -> adapter.invoke(unknownModel));
        assertTrue(failure.getMessage().contains("OPENAI_MODEL_NOT_DECLARED"), failure.getMessage());
    }

    @Test
    void timeoutIsEnforcedWithinABoundedTimeRatherThanHangingIndefinitely() throws Exception {
        // Handler deliberately never responds -- proves the adapter's own timeout fires, rather
        // than merely never being exercised because the fake server happens to respond quickly.
        server.createContext("/v1/chat/completions", exchange -> {
            try {
                Thread.sleep(Duration.ofSeconds(30).toMillis());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });

        OpenAiModelProviderAdapter adapter = new OpenAiModelProviderAdapter(
                baseUrl(), () -> "test-api-key-xyz", Duration.ofMillis(500));

        Instant start = Instant.now();
        Exception failure = assertThrows(Exception.class, () -> adapter.invoke(sampleRequest()));
        Duration elapsed = Duration.between(start, Instant.now());

        assertTrue(elapsed.compareTo(Duration.ofSeconds(10)) < 0,
                "timeout must fire well before the handler's 30s sleep; took " + elapsed);
        assertTrue(failure.getMessage().contains("OPENAI_REQUEST_TIMED_OUT")
                        || failure.getMessage().toLowerCase().contains("timeout"),
                failure.getMessage());
    }

    @Test
    void constructorRejectsUnboundedOrInvalidTimeouts() {
        assertThrows(IllegalArgumentException.class,
                () -> new OpenAiModelProviderAdapter(baseUrl(), () -> "key", Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new OpenAiModelProviderAdapter(baseUrl(), () -> "key", Duration.ofMinutes(10)));
        assertThrows(IllegalArgumentException.class,
                () -> new OpenAiModelProviderAdapter(baseUrl(), () -> "key", Duration.ofSeconds(-1)));
    }

    @Test
    void providerMetadataIsStableAndNonEmpty() {
        OpenAiModelProviderAdapter adapter =
                new OpenAiModelProviderAdapter(baseUrl(), () -> "test-api-key-xyz");
        assertEquals("openai", adapter.providerId());
        assertTrue(adapter.supportsTaskClass("REVIEW"));
        assertTrue(adapter.declaredModelIds().contains("gpt-4o"));
        assertTrue(adapter.declaredModelIds().contains("gpt-4o-mini"));
        assertEquals(List.of("gpt-4o", "gpt-4o-mini"), adapter.declaredModelIds());
    }

    /**
     * Real-network, {@code OPENAI_API_KEY}-gated live test. Skips cleanly (never fails) when no
     * key is configured in the environment -- as it legitimately does in this sandbox right now.
     * When a key IS present, makes exactly one real, minimal, cheap call against the cheapest
     * declared model and asserts the full response round-trips into a valid record.
     */
    @Test
    void liveCallAgainstRealOpenAiRoundTripsCorrectlyWhenApiKeyIsConfigured() throws Exception {
        String apiKey = System.getenv("OPENAI_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
                "OPENAI_API_KEY not set; skipping live OpenAI integration test");

        OpenAiModelProviderAdapter adapter = OpenAiModelProviderAdapter.fromEnvironment();
        ModelInvocationRequest request = new ModelInvocationRequest(
                "REVIEW", "gpt-4o-mini", Map.of(), "digest-live-smoke-test", 8);

        ModelInvocationRecord record = adapter.invoke(request);

        assertEquals("openai", record.providerId());
        assertEquals("gpt-4o-mini", record.modelId());
        assertTrue(!record.modelVersion().isBlank());
        assertTrue(record.inputTokenCount() > 0);
        assertTrue(record.outputTokenCount() > 0);
        assertTrue(record.costMicros() >= 0);
        assertTrue(record.outputDigest().startsWith("sha256:"));
    }

    private static String sha256Hex(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
