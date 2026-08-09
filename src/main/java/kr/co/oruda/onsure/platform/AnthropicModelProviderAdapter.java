package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Real Anthropic (Claude Messages API) implementation of {@link ModelProviderAdapter}. Closes
 * NFR-05's missing {@code REAL_PROVIDER_NETWORK_INTEGRATION_MISSING} control: a genuine outbound
 * provider integration alongside the interface's existing in-test-only fakes.
 *
 * <p>This class never performs network I/O outside an explicit {@link #invoke(ModelInvocationRequest)}
 * call: no static initializer, background thread, or constructor makes a request. Every call is a
 * single bounded HTTP round trip -- there is no internal retry loop. This makes it an intentional,
 * explicit exception to this codebase's {@code DENY_BY_DEFAULT} network-egress default (see
 * {@code ExecutionPlanService.networkEgress} and the build lane's {@code fail_closed_on:
 * unrestricted_network} policy) -- the exception is scoped to exactly this one outbound call.
 */
public final class AnthropicModelProviderAdapter implements ModelProviderAdapter {

    public static final String PROVIDER_ID = "anthropic";

    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String MESSAGES_PATH = "/v1/messages";

    /** Bounded, non-infinite timeouts -- required for an adapter that is an explicit egress exception. */
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(45);

    /**
     * The response to this synthetic acknowledgement prompt is intentionally short -- this adapter
     * only needs a real, billable round trip to prove out token accounting and cost math, not a long
     * completion.
     */
    private static final int MAX_RESPONSE_TOKENS = 64;

    /** Consistent with the {@code network_egress} / data-transfer-scope vocabulary already used by
     * {@code ExecutionPlanService} and by the in-repo fake providers in
     * {@code ModelProviderAdapterCompatibilityTest} (see {@code RemoteFakeProvider}). */
    private static final String DATA_TRANSFER_POLICY = "EXTERNAL_NETWORK_EGRESS_ALLOWLISTED";

    /**
     * Stays consistent with the only task-class vocabulary already established in this codebase
     * (see {@code ModelProviderAdapterCompatibilityTest}'s {@code RemoteFakeProvider} /
     * {@code LocalFakeProvider}), rather than inventing new terms.
     */
    private static final Set<String> SUPPORTED_TASK_CLASSES = Set.of("REVIEW", "PLANNING");

    /** Real, currently-existing Claude Messages API model ids. Keep in sync with declared pricing below. */
    private static final List<String> DECLARED_MODEL_IDS =
            List.of("claude-opus-5", "claude-sonnet-5", "claude-haiku-4-5");

    /**
     * Approximate current Anthropic list pricing (USD), expressed directly in micros-per-token so
     * {@link #invoke(ModelInvocationRequest)} needs only integer multiplication. This is a
     * deliberate unit simplification, not a rounding trick: 1 USD == 1,000,000 micros, and Anthropic
     * publishes prices per 1,000,000 tokens, so "$X per 1M tokens" and "X micros per token" are the
     * exact same number. E.g. Claude Opus 5 costs $5.00 / 1,000,000 input tokens == 5 micros/input
     * token.
     *
     * <p>Update this table when Anthropic publishes new list pricing; it intentionally does not
     * apply any time-limited introductory discount, since those expire and this table has no
     * mechanism to track an expiry date.
     */
    private static final Map<String, ModelPricing> PRICING_MICROS_PER_TOKEN = Map.of(
            "claude-opus-5", new ModelPricing(5L, 25L),
            "claude-sonnet-5", new ModelPricing(3L, 15L),
            "claude-haiku-4-5", new ModelPricing(1L, 5L));

    private final String baseUrl;
    private final Supplier<String> apiKeySupplier;
    private final Duration requestTimeout;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * @param baseUrl        Anthropic API base URL, e.g. {@code https://api.anthropic.com}, or a
     *                        {@code http://127.0.0.1:<port>} fake server for tests. No trailing slash
     *                        required.
     * @param apiKeySupplier supplies the {@code x-api-key} value on every call; read lazily inside
     *                        {@link #invoke(ModelInvocationRequest)} rather than once at construction,
     *                        so key rotation and test fakes both work without reconstructing this adapter.
     */
    public AnthropicModelProviderAdapter(String baseUrl, Supplier<String> apiKeySupplier) {
        this(baseUrl, apiKeySupplier, DEFAULT_CONNECT_TIMEOUT, DEFAULT_REQUEST_TIMEOUT);
    }

    /** Convenience constructor for the default, real Anthropic API base URL. */
    public AnthropicModelProviderAdapter(Supplier<String> apiKeySupplier) {
        this(DEFAULT_BASE_URL, apiKeySupplier);
    }

    /**
     * Package-visible overload so tests can bound the request timeout well below the production
     * default (otherwise a hung-server test would legitimately take {@link #DEFAULT_REQUEST_TIMEOUT}
     * to fail).
     */
    AnthropicModelProviderAdapter(
            String baseUrl, Supplier<String> apiKeySupplier, Duration connectTimeout, Duration requestTimeout) {
        if (baseUrl == null || baseUrl.isBlank()) throw new IllegalArgumentException("baseUrl");
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKeySupplier = Objects.requireNonNull(apiKeySupplier, "apiKeySupplier");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()) {
            throw new IllegalArgumentException("connectTimeout must be a bounded positive duration");
        }
        if (requestTimeout.isNegative() || requestTimeout.isZero()) {
            throw new IllegalArgumentException("requestTimeout must be a bounded positive duration");
        }
        this.httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    }

    /**
     * Reads {@code ANTHROPIC_API_KEY} from the process environment, mirroring
     * {@link kr.co.oruda.onsure.platform.migration.PostgresqlMigrationConfiguration#fromEnvironment()}'s
     * pattern in this package. The environment is consulted lazily on every {@link #invoke} call (not
     * captured once here), so a key configured after JVM startup still works.
     */
    public static AnthropicModelProviderAdapter fromEnvironment() {
        return new AnthropicModelProviderAdapter(DEFAULT_BASE_URL, () -> System.getenv("ANTHROPIC_API_KEY"));
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public boolean supportsTaskClass(String taskClass) {
        return taskClass != null && SUPPORTED_TASK_CLASSES.contains(taskClass);
    }

    @Override
    public List<String> declaredModelIds() {
        return DECLARED_MODEL_IDS;
    }

    @Override
    public ModelInvocationRecord invoke(ModelInvocationRequest request) throws Exception {
        Objects.requireNonNull(request, "request");
        if (!DECLARED_MODEL_IDS.contains(request.modelId())) {
            throw new IllegalArgumentException("Unsupported Anthropic model id: " + request.modelId());
        }
        ModelPricing pricing = PRICING_MICROS_PER_TOKEN.get(request.modelId());
        if (pricing == null) {
            // Should be unreachable given the DECLARED_MODEL_IDS check above, but fail closed rather
            // than silently reporting a zero/fabricated cost if the two tables ever drift apart.
            throw new IllegalStateException("No pricing configured for Anthropic model: " + request.modelId());
        }
        String apiKey = apiKeySupplier.get();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Anthropic API key not configured (ANTHROPIC_API_KEY environment variable, or the "
                            + "configured Supplier<String>, returned a blank/null value)");
        }

        String requestBody = buildRequestBody(request);
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl + MESSAGES_PATH))
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .header("content-type", "application/json")
                .timeout(requestTimeout)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> httpResponse;
        try {
            httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException timedOut) {
            throw new IOException(
                    "Anthropic API request timed out after " + requestTimeout + " (task_class="
                            + request.taskClass() + ", model=" + request.modelId() + ")",
                    timedOut);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        }

        return parseResponse(request, pricing, httpResponse);
    }

    /**
     * Builds a minimal, honest Anthropic Messages API request. {@link ModelInvocationRequest} only
     * carries {@code taskClass} and {@code inputDigest} (plus a token estimate) -- it does not carry
     * raw caller-supplied prompt text, because that is not yet part of this request's contract. The
     * user message below is therefore a deterministic placeholder derived only from fields the
     * request actually has; it is NOT a fabrication pretending to be real caller input.
     */
    private String buildRequestBody(ModelInvocationRequest request) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", request.modelId());
        root.put("max_tokens", MAX_RESPONSE_TOKENS);
        ArrayNode messages = root.putArray("messages");
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content",
                "ONSURE model-provider integration check. task_class=" + request.taskClass()
                        + " input_digest=" + request.inputDigest()
                        + " input_token_estimate=" + request.inputTokenEstimate()
                        + ". Reply with a brief one-sentence acknowledgement.");
        return objectMapper.writeValueAsString(root);
    }

    private ModelInvocationRecord parseResponse(
            ModelInvocationRequest request, ModelPricing pricing, HttpResponse<String> httpResponse)
            throws IOException {
        int status = httpResponse.statusCode();
        JsonNode responseJson;
        try {
            responseJson = objectMapper.readTree(httpResponse.body());
        } catch (Exception malformedJson) {
            throw new IOException(
                    "Anthropic API returned a response body that is not valid JSON (HTTP " + status + "): "
                            + truncate(httpResponse.body()),
                    malformedJson);
        }

        if (status < 200 || status >= 300) {
            String errorMessage = responseJson.path("error").path("message").asText("<no error message>");
            String errorType = responseJson.path("error").path("type").asText("<no error type>");
            throw new IOException("Anthropic API request failed: HTTP " + status + " " + errorType + ": "
                    + errorMessage);
        }

        JsonNode usage = responseJson.path("usage");
        if (!usage.hasNonNull("input_tokens") || !usage.hasNonNull("output_tokens")) {
            throw new IOException(
                    "Anthropic API response is missing usage.input_tokens/usage.output_tokens: "
                            + truncate(httpResponse.body()));
        }
        JsonNode contentNode = responseJson.path("content");
        if (!contentNode.isArray()) {
            throw new IOException(
                    "Anthropic API response is missing a content array: " + truncate(httpResponse.body()));
        }
        String responseId = responseJson.path("id").asText(null);
        if (responseId == null || responseId.isBlank()) {
            throw new IOException(
                    "Anthropic API response is missing its message id: " + truncate(httpResponse.body()));
        }
        String responseModel = responseJson.path("model").asText(null);
        if (responseModel == null || responseModel.isBlank()) {
            throw new IOException(
                    "Anthropic API response is missing its model field: " + truncate(httpResponse.body()));
        }

        int inputTokens = usage.path("input_tokens").asInt();
        int outputTokens = usage.path("output_tokens").asInt();
        long costMicros = Math.addExact(
                Math.multiplyExact((long) inputTokens, pricing.inputMicrosPerToken()),
                Math.multiplyExact((long) outputTokens, pricing.outputMicrosPerToken()));

        StringBuilder outputText = new StringBuilder();
        for (JsonNode block : contentNode) {
            if ("text".equals(block.path("type").asText())) {
                outputText.append(block.path("text").asText(""));
            }
        }
        String outputDigest = "sha256:" + sha256Hex(outputText.toString());

        return new ModelInvocationRecord(
                "anthropic:" + responseId,
                providerId(),
                request.modelId(),
                responseModel,
                request.taskClass(),
                request.configuration(),
                inputTokens,
                outputTokens,
                costMicros,
                DATA_TRANSFER_POLICY,
                outputDigest);
    }

    private static String truncate(String body) {
        if (body == null) return "<empty body>";
        return body.length() <= 2000 ? body : body.substring(0, 2000) + "...<truncated>";
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 MessageDigest not available", impossible);
        }
    }

    /** Per-model list pricing, in USD micros per token (see {@link #PRICING_MICROS_PER_TOKEN}). */
    private record ModelPricing(long inputMicrosPerToken, long outputMicrosPerToken) {}
}
