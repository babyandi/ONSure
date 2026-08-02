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

    private ONSureClient(URI baseUri, String token, Duration timeout) {
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
    }

    public static ONSureClient connect(URI baseUri, String bearerToken) {
        return new ONSureClient(baseUri, bearerToken, Duration.ofSeconds(30));
    }

    public static ONSureClient connect(URI baseUri, String bearerToken, Duration timeout) {
        return new ONSureClient(baseUri, bearerToken, timeout);
    }

    public ApiResponse status() throws IOException, InterruptedException {
        return send("/v1/status", "GET", null);
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

    public record ApiResponse(int statusCode, Map<String, Object> body) {
        public ApiResponse {
            if (statusCode < 100 || statusCode > 599) throw new IllegalArgumentException("statusCode");
            body = Map.copyOf(body == null ? Map.of() : body);
        }

        public boolean successful() { return statusCode >= 200 && statusCode < 300; }
    }
}
