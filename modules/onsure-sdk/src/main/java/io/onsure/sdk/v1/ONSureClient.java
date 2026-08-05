package io.onsure.sdk.v1;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Versioned candidate SDK for ONSure's authenticated loopback Local API. */
public final class ONSureClient {
    private static final int MAX_BODY_BYTES = 10 * 1024 * 1024;
    private static final TypeReference<LinkedHashMap<String, Object>> JSON_MAP = new TypeReference<>() {};
    private final URI baseUri;
    private final String token;
    private final Duration timeout;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final RetryPolicy retryPolicy;

    private ONSureClient(URI baseUri, String token, Duration timeout, RetryPolicy retryPolicy) {
        this.baseUri = requireLoopback(baseUri);
        if (token == null || token.length() < 32 || token.length() > 4096
                || token.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Local API token must contain 32-4096 safe characters");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()
                || timeout.compareTo(Duration.ofMinutes(3)) > 0) {
            throw new IllegalArgumentException("timeout must be between 1ms and 3 minutes");
        }
        this.token = token;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.mapper = new ObjectMapper().findAndRegisterModules();
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
    }

    public static ONSureClient connect(URI baseUri, String bearerToken) {
        return new ONSureClient(baseUri, bearerToken, Duration.ofSeconds(30), RetryPolicy.noRetry());
    }

    public static ONSureClient connect(URI baseUri, String bearerToken, Duration timeout) {
        return new ONSureClient(baseUri, bearerToken, timeout, RetryPolicy.noRetry());
    }

    public static ONSureClient connect(
            URI baseUri, String bearerToken, Duration timeout, RetryPolicy retryPolicy) {
        return new ONSureClient(baseUri, bearerToken, timeout, retryPolicy);
    }

    public ApiResponse status() throws IOException, InterruptedException {
        return send("/v1/status", "GET", null);
    }

    public ApiResponse statusWithRetry() throws IOException, InterruptedException {
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= retryPolicy.maximumAttempts(); attempt++) {
            try {
                ApiResponse response = status();
                if (!retryableStatus(response.statusCode()) || attempt == retryPolicy.maximumAttempts()) return response;
                lastFailure = response.asException();
            } catch (IOException failure) {
                lastFailure = failure;
                if (attempt == retryPolicy.maximumAttempts()) throw failure;
            }
            Thread.sleep(delayMillis(attempt));
        }
        throw lastFailure == null ? new IOException("ONSURE_RETRY_EXHAUSTED") : lastFailure;
    }

    public ApiResponse workflow(String operation, Map<String, ?> request)
            throws IOException, InterruptedException {
        if (operation == null || !operation.matches("[a-z][a-z0-9.-]{1,127}")) {
            throw new IllegalArgumentException("workflow operation is invalid");
        }
        return send("/v1/workflow", "POST", Map.of(
                "operation", operation,
                "request", Map.copyOf(request == null ? Map.of() : request)));
    }

    public ApiResponse anonymizeProjectKnowledge(
            String projectId, Map<String, String> knowledge, String workspaceSaltFile)
            throws IOException, InterruptedException {
        if (projectId == null || projectId.isBlank() || workspaceSaltFile == null || workspaceSaltFile.isBlank()) {
            throw new IllegalArgumentException("anonymization binding is required");
        }
        return workflow("knowledge.anonymize", Map.of(
                "project_id", projectId,
                "knowledge", Map.copyOf(knowledge == null ? Map.of() : knowledge),
                "workspace_salt_file", workspaceSaltFile));
    }

    public Page<Map<String, Object>> workflowPage(
            String operation, Map<String, ?> request, String cursor, int limit)
            throws IOException, InterruptedException {
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("limit must be 1-1000");
        Map<String, Object> paged = new LinkedHashMap<>();
        if (request != null) paged.putAll(request);
        paged.put("limit", limit);
        if (cursor != null && !cursor.isBlank()) paged.put("cursor", cursor);
        ApiResponse response = workflow(operation, paged);
        response.requireSuccess();
        Object workflow = response.body().get("workflow");
        if (!(workflow instanceof Map<?, ?> workflowMap) || !(workflowMap.get("result") instanceof Map<?, ?> result)) {
            throw new IOException("ONSURE_LOCAL_API_PAGE_RESULT_INVALID");
        }
        Object rawItems = result.get("items");
        if (!(rawItems instanceof List<?> values)) throw new IOException("ONSURE_LOCAL_API_PAGE_ITEMS_INVALID");
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> map)) throw new IOException("ONSURE_LOCAL_API_PAGE_ITEM_INVALID");
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put(String.valueOf(key), item));
            items.add(Map.copyOf(copy));
        }
        Object next = result.get("next_cursor");
        return new Page<>(items, next == null ? null : String.valueOf(next), limit);
    }

    private ApiResponse send(String route, String method, Object body)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(route))
                .timeout(timeout)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json");
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            byte[] encoded = mapper.writeValueAsBytes(body);
            if (encoded.length > MAX_BODY_BYTES) throw new IOException("ONSURE_LOCAL_API_REQUEST_TOO_LARGE");
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofByteArray(encoded));
        }
        HttpResponse<InputStream> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        byte[] responseBody;
        try (InputStream stream = response.body()) {
            responseBody = stream.readNBytes(MAX_BODY_BYTES + 1);
        }
        if (responseBody.length > MAX_BODY_BYTES) throw new IOException("ONSURE_LOCAL_API_RESPONSE_TOO_LARGE");
        Map<String, Object> payload;
        try {
            payload = mapper.readValue(responseBody, JSON_MAP);
        } catch (IOException error) {
            throw new IOException("ONSURE_LOCAL_API_RESPONSE_JSON_INVALID", error);
        }
        return new ApiResponse(response.statusCode(), payload);
    }

    private static URI requireLoopback(URI value) {
        Objects.requireNonNull(value, "baseUri");
        String host = value.getHost();
        int port = value.getPort();
        if (!"http".equals(value.getScheme()) || host == null || port < 1024 || port > 65535
                || value.getUserInfo() != null || value.getQuery() != null || value.getFragment() != null
                || !(value.getPath().isEmpty() || "/".equals(value.getPath()))) {
            throw new IllegalArgumentException("Local API URI must be an explicit loopback HTTP endpoint");
        }
        if (!("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "::1".equals(host))) {
            throw new IllegalArgumentException("Local API URI must be loopback");
        }
        return URI.create("http://" + (host.contains(":") ? "[" + host + "]" : host) + ":" + port + "/");
    }

    private long delayMillis(int attempt) {
        long initial = retryPolicy.initialDelay().toMillis();
        long maximum = retryPolicy.maximumDelay().toMillis();
        long multiplier = 1L << Math.min(attempt - 1, 4);
        return Math.min(maximum, initial * multiplier);
    }

    private static boolean retryableStatus(int status) {
        return status == 429 || status == 502 || status == 503 || status == 504;
    }

    public record ApiResponse(int statusCode, Map<String, Object> body) {
        public ApiResponse {
            if (statusCode < 100 || statusCode > 599) throw new IllegalArgumentException("statusCode");
            body = Map.copyOf(body == null ? Map.of() : body);
        }

        public boolean successful() { return statusCode >= 200 && statusCode < 300; }

        public ApiResponse requireSuccess() throws ONSureApiException {
            if (!successful()) throw asException();
            return this;
        }

        public ONSureApiException asException() {
            if (successful()) throw new IllegalStateException("ONSURE_SUCCESS_RESPONSE_HAS_NO_EXCEPTION");
            String code = String.valueOf(body.getOrDefault("error", "LOCAL_API_HTTP_" + statusCode));
            String message = String.valueOf(body.getOrDefault("message", code));
            String requestId = String.valueOf(body.getOrDefault("request_id", "NOT_AVAILABLE"));
            return new ONSureApiException(statusCode, code, message, requestId, retryableStatus(statusCode));
        }
    }
}
