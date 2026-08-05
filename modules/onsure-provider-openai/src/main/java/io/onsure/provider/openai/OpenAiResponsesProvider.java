package io.onsure.provider.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.onsure.provider.spi.CompletionRequest;
import io.onsure.provider.spi.CompletionResponse;
import io.onsure.provider.spi.ModelProvider;
import io.onsure.provider.spi.ProviderContext;
import io.onsure.provider.spi.ProviderDescriptor;
import io.onsure.provider.spi.ProviderException;
import io.onsure.provider.spi.ProviderHealth;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Fail-closed OpenAI Responses API adapter with no implicit retry or model fallback. */
public final class OpenAiResponsesProvider implements ModelProvider {
    public static final String DEFAULT_MODEL = "gpt-5.6-sol";
    private static final URI PRODUCTION_ENDPOINT = URI.create("https://api.openai.com/v1/responses");
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    private static final long PRICE_DENOMINATOR = 1_000_000L;

    private final String apiKey;
    private final String modelId;
    private final long inputMicrosPerMillionTokens;
    private final long outputMicrosPerMillionTokens;
    private final URI endpoint;
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final ProviderDescriptor descriptor;

    public OpenAiResponsesProvider(
            String apiKey,
            String modelId,
            long inputMicrosPerMillionTokens,
            long outputMicrosPerMillionTokens) {
        this(apiKey, modelId, inputMicrosPerMillionTokens, outputMicrosPerMillionTokens,
                PRODUCTION_ENDPOINT, HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
                new ObjectMapper(), Clock.systemUTC());
    }

    OpenAiResponsesProvider(
            String apiKey,
            String modelId,
            long inputMicrosPerMillionTokens,
            long outputMicrosPerMillionTokens,
            URI endpoint,
            HttpClient client,
            ObjectMapper mapper,
            Clock clock) {
        this.apiKey = requireSecret(apiKey);
        this.modelId = requireModel(modelId);
        this.inputMicrosPerMillionTokens = requirePrice(inputMicrosPerMillionTokens, "input price");
        this.outputMicrosPerMillionTokens = requirePrice(outputMicrosPerMillionTokens, "output price");
        this.endpoint = requireEndpoint(endpoint);
        this.client = Objects.requireNonNull(client, "client");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.descriptor = new ProviderDescriptor("openai-responses", "1.0.0", List.of(modelId), false, true);
    }

    public static OpenAiResponsesProvider fromEnvironment() {
        Map<String, String> environment = System.getenv();
        return new OpenAiResponsesProvider(
                environment.get("OPENAI_API_KEY"),
                environment.getOrDefault("ONSURE_OPENAI_MODEL", DEFAULT_MODEL),
                parsePrice(environment, "ONSURE_OPENAI_INPUT_MICROS_PER_MILLION_TOKENS"),
                parsePrice(environment, "ONSURE_OPENAI_OUTPUT_MICROS_PER_MILLION_TOKENS"));
    }

    @Override
    public ProviderDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public ProviderHealth health() {
        return new ProviderHealth(ProviderHealth.State.DEGRADED,
                "CONFIGURED_NOT_PROBED_NETWORK_APPROVAL_REQUIRED", Instant.now(clock));
    }

    @Override
    public CompletionResponse complete(CompletionRequest request, ProviderContext context)
            throws ProviderException, InterruptedException {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(context, "context");
        if (!request.modelId().equals(modelId)) {
            throw failure("MODEL_NOT_AVAILABLE", "Exact requested model is unavailable; fallback prohibited", false);
        }
        if (!context.networkEgressApproved()) {
            throw failure("NETWORK_EGRESS_DENIED", "OpenAI network egress was not approved", false);
        }
        if (!context.customerDataApproved()) {
            throw failure("CUSTOMER_DATA_TRANSFER_DENIED", "Remote provider data transfer was not approved", false);
        }
        if (request.messages().stream().anyMatch(message -> "tool".equals(message.role()))) {
            throw failure("UNSUPPORTED_MESSAGE_ROLE", "Tool messages require a call binding not present in this SPI", false);
        }

        long estimatedInputTokens = request.messages().stream()
                .mapToLong(message -> 16L + message.content().getBytes(StandardCharsets.UTF_8).length).sum();
        long estimate = estimatedCost(estimatedInputTokens, request.maximumOutputTokens());
        if (estimate > context.maximumEstimatedCostMicros()) {
            throw failure("COST_LIMIT_EXCEEDED", "Estimated provider cost exceeds explicit limit", false);
        }

        byte[] requestBody;
        try {
            requestBody = mapper.writeValueAsBytes(requestBody(request));
        } catch (IOException invalid) {
            throw failure("REQUEST_ENCODING_FAILED", "Responses request could not be encoded", false);
        }
        HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                .timeout(request.timeout())
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "ONSure/0.1 openai-responses")
                .header("X-Client-Request-Id", request.requestId())
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                .build();
        HttpResponse<byte[]> response;
        try {
            response = client.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
        } catch (java.net.http.HttpTimeoutException timeout) {
            throw failure("PROVIDER_TIMEOUT", "OpenAI request exceeded the configured timeout", true);
        } catch (IOException transport) {
            throw failure("PROVIDER_TRANSPORT_FAILED", "OpenAI transport failed", true);
        }
        if (response.body().length > MAX_RESPONSE_BYTES) {
            throw failure("PROVIDER_RESPONSE_TOO_LARGE", "OpenAI response exceeded the bounded size", false);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            boolean retryable = response.statusCode() == 408 || response.statusCode() == 409
                    || response.statusCode() == 429 || response.statusCode() >= 500;
            throw failure("PROVIDER_HTTP_" + response.statusCode(),
                    "OpenAI returned HTTP " + response.statusCode(), retryable);
        }
        return parseResponse(request, estimate, response.body());
    }

    private ObjectNode requestBody(CompletionRequest request) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", modelId);
        body.put("max_output_tokens", request.maximumOutputTokens());
        body.put("store", false);
        ArrayNode input = body.putArray("input");
        for (CompletionRequest.Message message : request.messages()) {
            ObjectNode item = input.addObject();
            item.put("role", message.role());
            item.put("content", message.content());
        }
        return body;
    }

    private CompletionResponse parseResponse(CompletionRequest request, long estimate, byte[] responseBody)
            throws ProviderException {
        try {
            JsonNode root = mapper.readTree(responseBody);
            String responseId = requiredText(root, "id");
            String status = requiredText(root, "status");
            StringBuilder content = new StringBuilder();
            for (JsonNode output : root.path("output")) {
                if (!"message".equals(output.path("type").asText())) continue;
                for (JsonNode part : output.path("content")) {
                    if ("output_text".equals(part.path("type").asText())) {
                        content.append(part.path("text").asText(""));
                    }
                }
            }
            if (content.isEmpty()) throw new IOException("output_text missing");
            long inputTokens = requiredNonnegativeLong(root.path("usage"), "input_tokens");
            long outputTokens = requiredNonnegativeLong(root.path("usage"), "output_tokens");
            String finishReason = finishReason(status, root.path("incomplete_details").path("reason").asText(""));
            long actualCost = estimatedCost(inputTokens, outputTokens);
            Map<String, String> evidence = new LinkedHashMap<>();
            evidence.put("response_id", responseId);
            evidence.put("transport", endpoint.getScheme().equals("https") ? "HTTPS" : "LOOPBACK_TEST_HTTP");
            evidence.put("network_egress", "true");
            evidence.put("customer_data_transfer_approved", "true");
            evidence.put("fallback_used", "false");
            evidence.put("retry_attempts", "0");
            evidence.put("store", "false");
            evidence.put("estimated_cost_micros", Long.toString(estimate));
            evidence.put("actual_cost_micros", Long.toString(actualCost));
            return new CompletionResponse(request.requestId(), descriptor.providerId(), modelId,
                    content.toString(), finishReason, inputTokens, outputTokens, Instant.now(clock), evidence);
        } catch (IOException | ArithmeticException malformed) {
            throw failure("PROVIDER_RESPONSE_INVALID", "OpenAI response did not satisfy the expected contract", false);
        }
    }

    private long estimatedCost(long inputTokens, long outputTokens) throws ProviderException {
        try {
            return Math.addExact(ceilPrice(inputTokens, inputMicrosPerMillionTokens),
                    ceilPrice(outputTokens, outputMicrosPerMillionTokens));
        } catch (ArithmeticException overflow) {
            throw failure("COST_LIMIT_EXCEEDED", "Estimated provider cost overflowed", false);
        }
    }

    private static long ceilPrice(long tokens, long rate) {
        long product = Math.multiplyExact(tokens, rate);
        return product == 0 ? 0 : Math.addExact(product, PRICE_DENOMINATOR - 1) / PRICE_DENOMINATOR;
    }

    private static String finishReason(String status, String reason) {
        if ("completed".equals(status)) return "COMPLETED";
        String suffix = reason.replaceAll("[^A-Za-z0-9]+", "_").toUpperCase();
        return suffix.isBlank() ? status.toUpperCase() : status.toUpperCase() + "_" + suffix;
    }

    private static String requiredText(JsonNode node, String field) throws IOException {
        String value = node.path(field).asText("");
        if (value.isBlank() || value.length() > 256) throw new IOException(field);
        return value;
    }

    private static long requiredNonnegativeLong(JsonNode node, String field) throws IOException {
        JsonNode value = node.path(field);
        if (!value.canConvertToLong() || value.longValue() < 0) throw new IOException(field);
        return value.longValue();
    }

    private static URI requireEndpoint(URI endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        boolean production = endpoint.equals(PRODUCTION_ENDPOINT);
        boolean testLoopback = "http".equals(endpoint.getScheme())
                && ("127.0.0.1".equals(endpoint.getHost()) || "localhost".equals(endpoint.getHost()));
        if (!production && !testLoopback) throw new IllegalArgumentException("endpoint");
        return endpoint;
    }

    private static String requireSecret(String value) {
        if (value == null || value.isBlank() || value.length() > 4096 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("OPENAI_API_KEY is required");
        }
        return value;
    }

    private static String requireModel(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._:/-]{1,160}")) throw new IllegalArgumentException("modelId");
        return value;
    }

    private static long requirePrice(long value, String name) {
        if (value < 0 || value > 1_000_000_000_000L) throw new IllegalArgumentException(name);
        return value;
    }

    private static long parsePrice(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || !value.matches("[0-9]{1,13}")) throw new IllegalArgumentException(name + " is required");
        return requirePrice(Long.parseLong(value), name);
    }

    private static ProviderException failure(String code, String message, boolean retryable) {
        return new ProviderException(code, message, retryable);
    }
}
