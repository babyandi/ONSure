package io.onsure.gateway.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.onsure.provider.spi.CompletionRequest;
import io.onsure.provider.spi.CompletionResponse;
import io.onsure.provider.spi.ModelProvider;
import io.onsure.provider.spi.ProviderContext;
import io.onsure.provider.spi.ProviderException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loopback LLM gateway enforcing one exact provider call and recording content-free evidence. */
public final class LlmGatewayServer implements AutoCloseable {
    public static final String CONTRACT = "ONSURE_LLM_GATEWAY_V1";
    private static final int MAXIMUM_BODY_BYTES = 1_048_576;
    private static final String OPENAPI_RESOURCE = "/openapi/onsure-llm-gateway.v1.json";
    private static final Set<String> ROUTES = Set.of(
            "/v1/openapi.json", "/v1/health", "/v1/completions", "/v1/metrics");
    private static final Set<String> SAFE_EVIDENCE_KEYS = Set.of(
            "response_id", "transport", "network_egress", "customer_data_transfer_approved",
            "fallback_used", "retry_attempts", "store", "estimated_cost_micros",
            "actual_cost_micros");

    private final ModelProvider provider;
    private final LlmEvidenceLedger ledger;
    private final byte[] tokenDigest;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final JsonNode openApiDocument;
    private HttpServer server;
    private ExecutorService executor;

    public LlmGatewayServer(ModelProvider provider, LlmEvidenceLedger ledger, String token) {
        this.provider = java.util.Objects.requireNonNull(provider, "provider");
        this.ledger = java.util.Objects.requireNonNull(ledger, "ledger");
        if (token == null || token.length() < 32 || token.length() > 4096) {
            throw new IllegalArgumentException("LLM_GATEWAY_TOKEN_LENGTH_INVALID");
        }
        this.tokenDigest = digest(token.getBytes(StandardCharsets.UTF_8));
        this.openApiDocument = loadOpenApiDocument();
    }

    public synchronized int startAndGetPort(int port) throws IOException {
        if (server != null) throw new IllegalStateException("LLM_GATEWAY_ALREADY_RUNNING");
        if (port != 0 && (port < 1024 || port > 65535)) {
            throw new IllegalArgumentException("LLM_GATEWAY_PORT_INVALID");
        }
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 32);
        server.createContext("/v1/openapi.json", this::openApi);
        server.createContext("/v1/health", this::health);
        server.createContext("/v1/completions", authenticated(this::completion));
        server.createContext("/v1/metrics", authenticated(this::metrics));
        executor = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "onsure-llm-gateway");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.start();
        return server.getAddress().getPort();
    }

    static Set<String> routePaths() { return ROUTES; }

    private void openApi(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, error("METHOD_NOT_ALLOWED", "GET is required"));
            return;
        }
        respond(exchange, 200, openApiDocument, "application/vnd.oai.openapi+json;version=3.1.0");
    }

    private void health(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, error("METHOD_NOT_ALLOWED", "GET is required"));
            return;
        }
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("contract", CONTRACT);
        value.put("state", "RUNNING");
        value.put("binding", "127.0.0.1");
        value.put("provider", provider.descriptor().providerId());
        try {
            value.put("provider_health", provider.health().state().name());
        } catch (ProviderException unavailable) {
            value.put("provider_health", "UNAVAILABLE");
            value.put("provider_health_error", unavailable.code());
        }
        value.put("evidence_ledger_configured", true);
        value.put("final_claim_allowed", false);
        respond(exchange, 200, value);
    }

    private void completion(HttpExchange exchange) throws Exception {
        if (!"POST".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, error("METHOD_NOT_ALLOWED", "POST is required"));
            return;
        }
        ReadBody body = readBody(exchange);
        JsonNode root = body.value();
        String requestId = requiredText(root, "request_id", 128);
        String modelId = requiredText(root, "model_id", 160);
        List<CompletionRequest.Message> messages = messages(root.path("messages"));
        int maximumOutputTokens = requiredInt(root, "maximum_output_tokens", 1, 131072);
        int timeoutMillis = requiredInt(root, "timeout_millis", 1, 1_800_000);
        JsonNode policy = root.path("policy");
        if (!policy.isObject()) throw new IllegalArgumentException("POLICY_OBJECT_REQUIRED");
        ProviderContext context = new ProviderContext(
                requiredBoolean(policy, "network_egress_approved"),
                requiredBoolean(policy, "customer_data_approved"),
                requiredLong(policy, "maximum_estimated_cost_micros", 0, Long.MAX_VALUE),
                Map.of("gateway_contract", CONTRACT));
        CompletionRequest request = new CompletionRequest(requestId, modelId, messages,
                maximumOutputTokens, Duration.ofMillis(timeoutMillis), Map.of());
        long started = System.nanoTime();
        try {
            CompletionResponse response = provider.complete(request, context);
            long duration = elapsedMillis(started);
            Map<String, String> evidence = safeEvidence(response.evidence());
            long estimated = evidenceLong(evidence, "estimated_cost_micros");
            long actual = evidenceLong(evidence, "actual_cost_micros");
            Map<String, Object> receipt = ledger.append(LlmEvidenceLedger.observation(
                    requestId, response.providerId(), response.modelId(), "SUCCESS", "NONE", false,
                    body.sha256(), LlmEvidenceLedger.sha256(response.content().getBytes(StandardCharsets.UTF_8)),
                    response.inputTokens(), response.outputTokens(), estimated, actual,
                    duration, response.completedAt(), evidence));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("contract", CONTRACT);
            result.put("request_id", requestId);
            result.put("provider_id", response.providerId());
            result.put("model_id", response.modelId());
            result.put("content", response.content());
            result.put("finish_reason", response.finishReason());
            result.put("input_tokens", response.inputTokens());
            result.put("output_tokens", response.outputTokens());
            result.put("total_tokens", Math.addExact(response.inputTokens(), response.outputTokens()));
            result.put("estimated_cost_micros", estimated);
            result.put("actual_cost_micros", actual);
            result.put("duration_millis", duration);
            result.put("evidence_sequence", receipt.get("sequence"));
            result.put("evidence_sha256", receipt.get("entry_sha256"));
            result.put("fallback_used", false);
            result.put("retry_attempts", 0);
            result.put("final_claim_allowed", false);
            respond(exchange, 200, result);
        } catch (ProviderException failure) {
            long duration = elapsedMillis(started);
            ledger.append(LlmEvidenceLedger.observation(
                    requestId, provider.descriptor().providerId(), modelId, "FAILURE", failure.code(),
                    failure.retryable(), body.sha256(), "0".repeat(64), 0, 0, 0, 0,
                    duration, Instant.now(), Map.of("fallback_used", "false", "retry_attempts", "0")));
            respond(exchange, providerStatus(failure), error(failure.code(), failure.getMessage()));
        }
    }

    private void metrics(HttpExchange exchange) throws Exception {
        if (!"GET".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, error("METHOD_NOT_ALLOWED", "GET is required"));
            return;
        }
        respond(exchange, 200, ledger.metrics());
    }

    private HttpHandler authenticated(CheckedHandler handler) {
        return exchange -> {
            try {
                if (!isLoopback(exchange)) {
                    respond(exchange, 403, error("NON_LOOPBACK_CLIENT", "Loopback client required"));
                    return;
                }
                String origin = exchange.getRequestHeaders().getFirst("Origin");
                if (origin != null && !origin.isBlank() && !"null".equals(origin)) {
                    respond(exchange, 403, error("BROWSER_ORIGIN_PROHIBITED", "Browser origins are not accepted"));
                    return;
                }
                String authorization = exchange.getRequestHeaders().getFirst("Authorization");
                if (authorization == null || !authorization.startsWith("Bearer ")
                        || !MessageDigest.isEqual(digest(authorization.substring(7)
                                .getBytes(StandardCharsets.UTF_8)), tokenDigest)) {
                    respond(exchange, 401, error("UNAUTHORIZED", "A valid gateway bearer token is required"));
                    return;
                }
                handler.handle(exchange);
            } catch (IllegalArgumentException invalid) {
                respond(exchange, 400, error("INVALID_REQUEST", safe(invalid)));
            } catch (Exception failure) {
                respond(exchange, 500, error("INTERNAL_ERROR", safe(failure)));
            }
        };
    }

    private ReadBody readBody(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
            throw new IllegalArgumentException("CONTENT_TYPE_APPLICATION_JSON_REQUIRED");
        }
        try (InputStream input = exchange.getRequestBody(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            for (int read; (read = input.read(buffer)) >= 0;) {
                total += read;
                if (total > MAXIMUM_BODY_BYTES) throw new IllegalArgumentException("REQUEST_BODY_TOO_LARGE");
                output.write(buffer, 0, read);
            }
            byte[] bytes = output.toByteArray();
            JsonNode value = mapper.readTree(bytes);
            if (value == null || !value.isObject()) throw new IllegalArgumentException("JSON_OBJECT_REQUIRED");
            return new ReadBody(value, LlmEvidenceLedger.sha256(bytes));
        }
    }

    private JsonNode loadOpenApiDocument() {
        try (InputStream input = LlmGatewayServer.class.getResourceAsStream(OPENAPI_RESOURCE)) {
            if (input == null) throw new IllegalStateException("LLM_OPENAPI_RESOURCE_MISSING");
            JsonNode value = mapper.readTree(input);
            Set<String> documented = new java.util.TreeSet<>();
            value.path("paths").fieldNames().forEachRemaining(documented::add);
            if (!"3.1.0".equals(value.path("openapi").asText())
                    || !documented.equals(new java.util.TreeSet<>(ROUTES))) {
                throw new IllegalStateException("LLM_OPENAPI_ROUTE_DRIFT");
            }
            return value;
        } catch (IOException invalid) {
            throw new IllegalStateException("LLM_OPENAPI_RESOURCE_INVALID", invalid);
        }
    }

    private void respond(HttpExchange exchange, int status, Object body) throws IOException {
        respond(exchange, status, body, "application/json; charset=utf-8");
    }

    private void respond(HttpExchange exchange, int status, Object body, String contentType) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'none'");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
        finally { exchange.close(); }
    }

    private Map<String, Object> error(String code, String message) {
        return Map.of("contract", CONTRACT, "decision", "FAIL", "error", code,
                "message", message, "final_claim_allowed", false);
    }

    private static List<CompletionRequest.Message> messages(JsonNode value) {
        if (!value.isArray() || value.isEmpty() || value.size() > 256) {
            throw new IllegalArgumentException("MESSAGES_ARRAY_REQUIRED");
        }
        List<CompletionRequest.Message> messages = new ArrayList<>();
        for (JsonNode item : value) {
            messages.add(new CompletionRequest.Message(
                    requiredText(item, "role", 16), requiredText(item, "content", 1_000_000)));
        }
        return List.copyOf(messages);
    }

    private static String requiredText(JsonNode node, String field, int maximum) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.textValue().isBlank() || value.textValue().length() > maximum) {
            throw new IllegalArgumentException(field.toUpperCase() + "_INVALID");
        }
        return value.textValue();
    }

    private static int requiredInt(JsonNode node, String field, int minimum, int maximum) {
        JsonNode value = node.path(field);
        if (!value.canConvertToInt() || value.intValue() < minimum || value.intValue() > maximum) {
            throw new IllegalArgumentException(field.toUpperCase() + "_INVALID");
        }
        return value.intValue();
    }

    private static long requiredLong(JsonNode node, String field, long minimum, long maximum) {
        JsonNode value = node.path(field);
        if (!value.canConvertToLong() || value.longValue() < minimum || value.longValue() > maximum) {
            throw new IllegalArgumentException(field.toUpperCase() + "_INVALID");
        }
        return value.longValue();
    }

    private static boolean requiredBoolean(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isBoolean()) throw new IllegalArgumentException(field.toUpperCase() + "_INVALID");
        return value.booleanValue();
    }

    private static Map<String, String> safeEvidence(Map<String, String> evidence) {
        Map<String, String> safe = new LinkedHashMap<>();
        evidence.forEach((key, value) -> {
            if (SAFE_EVIDENCE_KEYS.contains(key) && value != null && value.length() <= 512) safe.put(key, value);
        });
        safe.putIfAbsent("fallback_used", "false");
        safe.putIfAbsent("retry_attempts", "0");
        return Map.copyOf(safe);
    }

    private static long evidenceLong(Map<String, String> evidence, String key) {
        String value = evidence.get(key);
        if (value == null) return 0L;
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("PROVIDER_EVIDENCE_" + key.toUpperCase() + "_INVALID");
        }
    }

    private static int providerStatus(ProviderException failure) {
        if ("PROVIDER_TIMEOUT".equals(failure.code())) return 504;
        if ("RATE_LIMIT_EXCEEDED".equals(failure.code()) || failure.code().endsWith("_429")) return 429;
        if (failure.code().endsWith("_DENIED") || "COST_LIMIT_EXCEEDED".equals(failure.code())) return 403;
        return 502;
    }

    private static long elapsedMillis(long started) {
        return Math.max(0L, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
    }

    private static byte[] digest(byte[] value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }

    private static boolean isLoopback(HttpExchange exchange) {
        return exchange.getRemoteAddress() != null && exchange.getRemoteAddress().getAddress() != null
                && exchange.getRemoteAddress().getAddress().isLoopbackAddress();
    }

    private static String safe(Exception failure) {
        if (failure instanceof IllegalArgumentException || failure instanceof IllegalStateException
                || failure instanceof IOException) {
            String value = failure.getMessage();
            return value == null || value.isBlank() ? failure.getClass().getSimpleName() : value;
        }
        return failure.getClass().getSimpleName();
    }

    public synchronized void stop() {
        if (server != null) { server.stop(1); server = null; }
        if (executor != null) { executor.shutdownNow(); executor = null; }
    }

    @Override public void close() throws Exception {
        stop();
        provider.close();
    }

    @FunctionalInterface private interface CheckedHandler { void handle(HttpExchange exchange) throws Exception; }
    private record ReadBody(JsonNode value, String sha256) {}
}
