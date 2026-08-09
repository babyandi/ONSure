package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Real OpenAI Chat Completions provider implementation of {@link ModelProviderAdapter} (closes
 * NFR-05's REAL_PROVIDER_NETWORK_INTEGRATION_MISSING gap: a genuine, network-calling provider
 * that satisfies the exact same contract {@code ModelProviderAdapterCompatibilityTest}'s
 * in-test-only fake providers satisfy, so callers can swap to/from it without change).
 *
 * <p>Makes exactly one real, bounded outbound HTTPS call per {@link #invoke}: never fired
 * automatically (only when a caller explicitly constructs this class with real configuration and
 * calls {@code invoke}), never retried in a loop, and always bounded by an explicit, configurable
 * request timeout. This is this codebase's intentional, explicit exception to the
 * DENY_BY_DEFAULT network egress policy enforced elsewhere (see {@code ExecutionPlanService}'s
 * {@code networkEgress} field and {@code contracts/assurance-lanes.v1.json}'s
 * {@code fail_closed_on: unrestricted_network}).
 *
 * <p>Fails closed on every error path -- non-2xx HTTP status, request timeout, or a response
 * missing the fields this class depends on -- by throwing a clearly-labelled exception. This
 * class never returns a fabricated or zeroed {@link ModelInvocationRecord} pretending success.
 */
public final class OpenAiModelProviderAdapter implements ModelProviderAdapter {

    public static final String PROVIDER_ID = "openai";
    public static final String DEFAULT_BASE_URL = "https://api.openai.com";
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration MAX_REQUEST_TIMEOUT = Duration.ofMinutes(5);

    /**
     * The message this adapter sends is a generic, deterministic placeholder derived from the
     * request (see {@link #buildUserMessage}), never real caller prompt text, so there is no
     * reason for a completion to run long; this keeps real/live calls small and cheap.
     */
    private static final int MAX_OUTPUT_TOKENS = 16;

    /**
     * Task classes this provider serves with a real LLM call, drawn from the vocabulary already
     * used for approvable actions in {@code ExecutionPlanService.APPROVABLE_ACTIONS} rather than
     * inventing new terms: the reasoning/analysis-shaped actions, not the mechanical ones
     * (STATIC_ANALYSIS, FIXTURE_EXECUTION, REGRESSION_LOCK, EVIDENCE_GENERATION) that don't need
     * an LLM call.
     */
    private static final Set<String> SUPPORTED_TASK_CLASSES =
            Set.of("REVIEW", "RCA", "IMPROVEMENT_PLAN", "AI_BEHAVIOR_VALIDATION", "BEHAVIOR_LEARNING");

    private static final List<String> DECLARED_MODEL_IDS = List.of("gpt-4o", "gpt-4o-mini");

    /**
     * Approximate current OpenAI Chat Completions pricing, expressed directly in USD micros per
     * 1,000,000 tokens (1 USD = 1,000,000 micros, so this table needs no separate USD-to-micros
     * conversion constant). These are realistic approximate figures, not exact/contractual --
     * update this table when OpenAI publishes new pricing.
     */
    private static final Map<String, ModelPricing> PRICE_PER_MILLION_TOKENS_USD_MICROS = Map.of(
            "gpt-4o", new ModelPricing(2_500_000L, 10_000_000L),
            "gpt-4o-mini", new ModelPricing(150_000L, 600_000L));

    private record ModelPricing(long inputMicrosPerMillionTokens, long outputMicrosPerMillionTokens) {}

    private final String baseUrl;
    private final Supplier<String> apiKeySupplier;
    private final Duration requestTimeout;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAiModelProviderAdapter(String baseUrl, Supplier<String> apiKeySupplier) {
        this(baseUrl, apiKeySupplier, DEFAULT_REQUEST_TIMEOUT);
    }

    public OpenAiModelProviderAdapter(String baseUrl, Supplier<String> apiKeySupplier, Duration requestTimeout) {
        if (baseUrl == null || baseUrl.isBlank()) throw new IllegalArgumentException("baseUrl");
        if (apiKeySupplier == null) throw new IllegalArgumentException("apiKeySupplier");
        if (requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()
                || requestTimeout.compareTo(MAX_REQUEST_TIMEOUT) > 0) {
            throw new IllegalArgumentException(
                    "requestTimeout must be a bounded positive duration <= " + MAX_REQUEST_TIMEOUT);
        }
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKeySupplier = apiKeySupplier;
        this.requestTimeout = requestTimeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(requestTimeout)
                .build();
    }

    /**
     * Reads {@code OPENAI_API_KEY} (and optionally {@code OPENAI_BASE_URL}) lazily at invocation
     * time via the supplied {@link Supplier}, mirroring
     * {@code PostgresqlMigrationConfiguration.fromEnvironment()}'s environment-driven
     * construction pattern in this same package. The key is never cached in a field, so a
     * rotated environment value takes effect on the next {@code invoke} without reconstruction.
     */
    public static OpenAiModelProviderAdapter fromEnvironment() {
        String baseUrl = System.getenv().getOrDefault("OPENAI_BASE_URL", DEFAULT_BASE_URL);
        return new OpenAiModelProviderAdapter(baseUrl, () -> System.getenv("OPENAI_API_KEY"));
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public boolean supportsTaskClass(String taskClass) {
        return SUPPORTED_TASK_CLASSES.contains(taskClass);
    }

    @Override
    public List<String> declaredModelIds() {
        return DECLARED_MODEL_IDS;
    }

    @Override
    public ModelInvocationRecord invoke(ModelInvocationRequest request) throws Exception {
        if (request == null) throw new IllegalArgumentException("request");
        if (!supportsTaskClass(request.taskClass())) {
            throw new IllegalArgumentException("OPENAI_TASK_CLASS_NOT_SUPPORTED: " + request.taskClass());
        }
        ModelPricing pricing = PRICE_PER_MILLION_TOKENS_USD_MICROS.get(request.modelId());
        if (pricing == null) {
            throw new IllegalArgumentException("OPENAI_MODEL_NOT_DECLARED: " + request.modelId());
        }
        String apiKey = apiKeySupplier.get();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY missing; refusing to call OpenAI without credentials");
        }

        ObjectNode body = mapper.createObjectNode();
        body.put("model", request.modelId());
        body.put("max_tokens", MAX_OUTPUT_TOKENS);
        ObjectNode userMessage = body.putArray("messages").addObject();
        userMessage.put("role", "user");
        // NOTE: ModelInvocationRequest carries only an inputDigest + inputTokenEstimate, not raw
        // caller prompt text -- that is not yet part of this record's contract. This content is a
        // minimal, honest, deterministic placeholder derived from the request itself; it is never
        // presented as real caller-supplied prompt content.
        userMessage.put("content", buildUserMessage(request));

        HttpRequest httpRequest;
        try {
            httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/chat/completions"))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
        } catch (IllegalArgumentException malformedUri) {
            throw new IllegalStateException("OPENAI_BASE_URL_INVALID: " + baseUrl, malformedUri);
        }

        HttpResponse<String> httpResponse;
        try {
            httpResponse = httpClient.send(httpRequest, BodyHandlers.ofString());
        } catch (HttpTimeoutException timeout) {
            throw new IOException("OPENAI_REQUEST_TIMED_OUT after " + requestTimeout, timeout);
        }

        if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
            throw new IOException("OPENAI_HTTP_ERROR status=" + httpResponse.statusCode()
                    + " body=" + truncate(httpResponse.body()));
        }

        JsonNode responseJson;
        try {
            responseJson = mapper.readTree(httpResponse.body());
        } catch (IOException malformed) {
            throw new IOException("OPENAI_RESPONSE_NOT_VALID_JSON: " + truncate(httpResponse.body()), malformed);
        }

        JsonNode usage = responseJson.path("usage");
        if (!usage.has("prompt_tokens") || !usage.has("completion_tokens")) {
            throw new IllegalStateException(
                    "OPENAI_MALFORMED_RESPONSE: missing usage fields: " + truncate(httpResponse.body()));
        }
        int inputTokens = usage.path("prompt_tokens").asInt(-1);
        int outputTokens = usage.path("completion_tokens").asInt(-1);
        if (inputTokens < 0 || outputTokens < 0) {
            throw new IllegalStateException(
                    "OPENAI_MALFORMED_RESPONSE: invalid usage token counts: " + truncate(httpResponse.body()));
        }

        JsonNode choices = responseJson.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new IllegalStateException(
                    "OPENAI_MALFORMED_RESPONSE: missing choices: " + truncate(httpResponse.body()));
        }
        JsonNode messageContent = choices.get(0).path("message").path("content");
        if (!messageContent.isTextual()) {
            throw new IllegalStateException(
                    "OPENAI_MALFORMED_RESPONSE: missing choices[0].message.content: " + truncate(httpResponse.body()));
        }
        String outputContent = messageContent.asText();

        String modelVersion = responseJson.path("model").asText(null);
        if (modelVersion == null || modelVersion.isBlank()) {
            throw new IllegalStateException(
                    "OPENAI_MALFORMED_RESPONSE: missing model field: " + truncate(httpResponse.body()));
        }

        long costMicros = roundedCost(inputTokens, pricing.inputMicrosPerMillionTokens())
                + roundedCost(outputTokens, pricing.outputMicrosPerMillionTokens());

        return new ModelInvocationRecord(
                "openai-" + UUID.randomUUID(),
                PROVIDER_ID,
                request.modelId(),
                modelVersion,
                request.taskClass(),
                request.configuration(),
                inputTokens,
                outputTokens,
                costMicros,
                "EXTERNAL_NETWORK_EGRESS_ALLOWLISTED",
                "sha256:" + Hashing.sha256(outputContent));
    }

    private static String buildUserMessage(ModelInvocationRequest request) {
        return "ONSURE task_class=" + request.taskClass() + " input_digest=" + request.inputDigest();
    }

    /** Rounds tokens * microsPerMillionTokens / 1,000,000 to the nearest micro (round-half-up). */
    private static long roundedCost(long tokens, long microsPerMillionTokens) {
        long product = tokens * microsPerMillionTokens;
        long quotient = product / 1_000_000L;
        long remainder = product % 1_000_000L;
        return remainder >= 500_000L ? quotient + 1 : quotient;
    }

    private static String truncate(String value) {
        if (value == null) return "";
        return value.length() > 500 ? value.substring(0, 500) + "..." : value;
    }
}
