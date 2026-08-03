package io.onsure.provider.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.onsure.provider.spi.CompletionRequest;
import io.onsure.provider.spi.CompletionResponse;
import io.onsure.provider.spi.ProviderContext;
import io.onsure.provider.spi.ProviderException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OpenAiResponsesProviderTest {
    private static final String SECRET = "test-key-never-report";
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendsBoundedResponsesRequestAndParsesUsageWithoutFallback() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<JsonNode> body = new AtomicReference<>();
        server = server(exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(mapper.readTree(exchange.getRequestBody()));
            respond(exchange, 200, """
                    {"id":"resp_test","status":"completed","output":[{"type":"message","content":[
                      {"type":"output_text","text":"verified"}]}],
                     "usage":{"input_tokens":12,"output_tokens":3}}
                    """);
        });
        OpenAiResponsesProvider provider = provider(1_000_000, 2_000_000);
        CompletionResponse response = provider.complete(request(Duration.ofSeconds(2)), approved(100));

        assertEquals("Bearer " + SECRET, authorization.get());
        assertEquals("gpt-5.6-sol", body.get().path("model").asText());
        assertEquals(20, body.get().path("max_output_tokens").asInt());
        assertFalse(body.get().path("store").asBoolean(true));
        assertEquals("verified", response.content());
        assertEquals(12, response.inputTokens());
        assertEquals(3, response.outputTokens());
        assertEquals("COMPLETED", response.finishReason());
        assertEquals("false", response.evidence().get("fallback_used"));
        assertEquals("0", response.evidence().get("retry_attempts"));
        assertFalse(response.evidence().toString().contains(SECRET));
    }

    @Test
    void policyAndCostFailuresOccurBeforeNetworkCall() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server = server(exchange -> {
            calls.incrementAndGet();
            respond(exchange, 500, "{}");
        });
        OpenAiResponsesProvider provider = provider(1_000_000, 2_000_000);

        assertCode("NETWORK_EGRESS_DENIED", () -> provider.complete(request(Duration.ofSeconds(1)),
                new ProviderContext(false, true, 1000, Map.of())));
        assertCode("CUSTOMER_DATA_TRANSFER_DENIED", () -> provider.complete(request(Duration.ofSeconds(1)),
                new ProviderContext(true, false, 1000, Map.of())));
        assertCode("COST_LIMIT_EXCEEDED", () -> provider.complete(request(Duration.ofSeconds(1)), approved(1)));
        assertEquals(0, calls.get());
    }

    @Test
    void exactModelIsRequiredAndToolMessagesAreRejected() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server = server(exchange -> calls.incrementAndGet());
        OpenAiResponsesProvider provider = provider(0, 0);
        CompletionRequest wrongModel = new CompletionRequest("req-2", "another-model",
                List.of(new CompletionRequest.Message("user", "hello")), 10, Duration.ofSeconds(1), Map.of());
        CompletionRequest tool = new CompletionRequest("req-3", "gpt-5.6-sol",
                List.of(new CompletionRequest.Message("tool", "unbound")), 10, Duration.ofSeconds(1), Map.of());

        assertCode("MODEL_NOT_AVAILABLE", () -> provider.complete(wrongModel, approved(10)));
        assertCode("UNSUPPORTED_MESSAGE_ROLE", () -> provider.complete(tool, approved(10)));
        assertEquals(0, calls.get());
    }

    @Test
    void rateLimitIsClassifiedRetryableWithoutLeakingResponseOrKey() throws Exception {
        server = server(exchange -> respond(exchange, 429, "{\"error\":{\"message\":\"sensitive remote body\"}}"));
        ProviderException error = assertThrows(ProviderException.class,
                () -> provider(0, 0).complete(request(Duration.ofSeconds(1)), approved(10)));
        assertEquals("PROVIDER_HTTP_429", error.code());
        assertTrue(error.retryable());
        assertFalse(error.getMessage().contains("sensitive remote body"));
        assertFalse(error.getMessage().contains(SECRET));
    }

    @Test
    void timeoutAndMalformedResponseAreClassified() throws Exception {
        server = server(exchange -> {
            try {
                Thread.sleep(250);
                respond(exchange, 200, "{}");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        ProviderException timeout = assertThrows(ProviderException.class,
                () -> provider(0, 0).complete(request(Duration.ofMillis(50)), approved(10)));
        assertEquals("PROVIDER_TIMEOUT", timeout.code());
        assertTrue(timeout.retryable());
        server.stop(0);
        server = server(exchange -> respond(exchange, 200, "{}"));
        ProviderException malformed = assertThrows(ProviderException.class,
                () -> provider(0, 0).complete(request(Duration.ofSeconds(1)), approved(10)));
        assertEquals("PROVIDER_RESPONSE_INVALID", malformed.code());
        assertFalse(malformed.retryable());
    }

    private OpenAiResponsesProvider provider(long inputRate, long outputRate) {
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/responses");
        return new OpenAiResponsesProvider(SECRET, "gpt-5.6-sol", inputRate, outputRate, endpoint,
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(), mapper,
                Clock.fixed(Instant.parse("2026-08-03T00:00:00Z"), ZoneOffset.UTC));
    }

    private CompletionRequest request(Duration timeout) {
        return new CompletionRequest("req-1", "gpt-5.6-sol",
                List.of(new CompletionRequest.Message("system", "be precise"),
                        new CompletionRequest.Message("user", "hello")),
                20, timeout, Map.of());
    }

    private static ProviderContext approved(long limit) {
        return new ProviderContext(true, true, limit, Map.of());
    }

    private HttpServer server(ThrowingHandler handler) throws IOException {
        HttpServer value = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        value.createContext("/v1/responses", exchange -> {
            try {
                handler.handle(exchange);
            } catch (Exception failure) {
                if (failure instanceof IOException io) throw io;
                throw new IOException("test handler failed", failure);
            } finally {
                exchange.close();
            }
        });
        value.start();
        return value;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static void assertCode(String expected, ThrowingCall call) {
        ProviderException failure = assertThrows(ProviderException.class, call::run);
        assertEquals(expected, failure.code());
    }

    @FunctionalInterface
    private interface ThrowingHandler {
        void handle(HttpExchange exchange) throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws Exception;
    }
}
