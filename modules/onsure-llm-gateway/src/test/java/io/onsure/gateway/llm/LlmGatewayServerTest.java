package io.onsure.gateway.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.onsure.provider.localmock.LocalMockProvider;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LlmGatewayServerTest {
    private static final String TOKEN = "gateway-test-token-" + "x".repeat(32);
    private final ObjectMapper mapper = new ObjectMapper();
    @TempDir Path temp;

    @Test
    void servesContractAndRecordsContentFreeUsageEvidence() throws Exception {
        String privatePrompt = "private-prompt-value-8f22";
        String privateCompletion = "private-completion-value-4a17";
        LlmEvidenceLedger ledger = new LlmEvidenceLedger(temp.resolve("evidence"));
        try (LlmGatewayServer gateway = gateway(ledger, privateCompletion, 1)) {
            int port = gateway.startAndGetPort(0);
            HttpResponse<String> openApi = get(port, "/v1/openapi.json", null, null);
            assertEquals(200, openApi.statusCode());
            assertTrue(openApi.headers().firstValue("Content-Type").orElse("")
                    .startsWith("application/vnd.oai.openapi+json"));
            JsonNode contract = mapper.readTree(openApi.body());
            assertEquals("3.1.0", contract.path("openapi").asText());
            Set<String> paths = new java.util.TreeSet<>();
            contract.path("paths").fieldNames().forEachRemaining(paths::add);
            assertEquals(new java.util.TreeSet<>(LlmGatewayServer.routePaths()), paths);

            assertEquals(401, get(port, "/v1/metrics", null, null).statusCode());
            assertEquals(403, get(port, "/v1/metrics", TOKEN, "http://attacker.invalid").statusCode());

            String request = mapper.writeValueAsString(Map.of(
                    "request_id", "gateway-request-1",
                    "model_id", "local/exact",
                    "messages", java.util.List.of(Map.of("role", "user", "content", privatePrompt)),
                    "maximum_output_tokens", 16,
                    "timeout_millis", 1_000,
                    "policy", Map.of(
                            "network_egress_approved", false,
                            "customer_data_approved", false,
                            "maximum_estimated_cost_micros", 10_000)));
            HttpResponse<String> completion = post(port, "/v1/completions", TOKEN, request);
            assertEquals(200, completion.statusCode(), completion.body());
            JsonNode result = mapper.readTree(completion.body());
            assertEquals(privateCompletion, result.path("content").asText());
            assertTrue(result.path("total_tokens").asLong() > 0);
            assertTrue(result.path("actual_cost_micros").asLong() > 0);
            assertFalse(result.path("fallback_used").asBoolean(true));
            assertEquals(64, result.path("evidence_sha256").asText().length());

            HttpResponse<String> metricsResponse = get(port, "/v1/metrics", TOKEN, null);
            assertEquals(200, metricsResponse.statusCode());
            JsonNode metrics = mapper.readTree(metricsResponse.body());
            assertEquals(1, metrics.path("request_count").asInt());
            assertEquals(1, metrics.path("success_count").asInt());
            assertTrue(metrics.path("chain_valid").asBoolean());
            assertFalse(metrics.path("prompt_or_completion_content_recorded").asBoolean(true));
        }
        String evidence = Files.readString(ledger.file());
        assertFalse(evidence.contains(privatePrompt));
        assertFalse(evidence.contains(privateCompletion));
        assertTrue(evidence.contains("request_sha256"));
        assertTrue(evidence.contains("response_sha256"));
    }

    @Test
    void recordsPolicyFailureAndRejectsTamperedLedger() throws Exception {
        LlmEvidenceLedger ledger = new LlmEvidenceLedger(temp.resolve("tamper"));
        try (LlmGatewayServer gateway = gateway(ledger, "answer", 2)) {
            int port = gateway.startAndGetPort(0);
            String request = mapper.writeValueAsString(Map.of(
                    "request_id", "gateway-request-denied",
                    "model_id", "local/exact",
                    "messages", java.util.List.of(Map.of("role", "user", "content", "hello")),
                    "maximum_output_tokens", 8,
                    "timeout_millis", 1_000,
                    "policy", Map.of(
                            "network_egress_approved", false,
                            "customer_data_approved", false,
                            "maximum_estimated_cost_micros", 0)));
            HttpResponse<String> denied = post(port, "/v1/completions", TOKEN, request);
            assertEquals(403, denied.statusCode(), denied.body());
            JsonNode metrics = mapper.readTree(get(port, "/v1/metrics", TOKEN, null).body());
            assertEquals(1, metrics.path("failure_count").asInt());
            assertEquals(0, metrics.path("success_count").asInt());
        }
        Files.writeString(ledger.file(), Files.readString(ledger.file()).replace(
                "gateway-request-denied", "gateway-request-changed"));
        assertThrows(java.io.IOException.class, () -> new LlmEvidenceLedger(temp.resolve("tamper")));
    }

    @Test
    void rejectsSecretLikeProviderEvidenceKeysBeforePersistence() throws Exception {
        LlmEvidenceLedger ledger = new LlmEvidenceLedger(temp.resolve("secret-key"));
        assertThrows(IllegalArgumentException.class, () -> LlmEvidenceLedger.observation(
                "request-one", "provider", "model", "SUCCESS", "NONE", false,
                "a".repeat(64), "b".repeat(64), 1, 1, 0, 0, 1,
                java.time.Instant.now(), Map.of("token", "must-not-persist")));
        assertEquals(0, Files.size(ledger.file()));
    }

    @Test
    void rejectsMalformedObservationWithoutCorruptingLedger() throws Exception {
        LlmEvidenceLedger ledger = new LlmEvidenceLedger(temp.resolve("malformed"));
        Map<String, Object> valid = LlmEvidenceLedger.observation(
                "request-one", "provider", "model", "SUCCESS", "NONE", false,
                "a".repeat(64), "b".repeat(64), 1, 1, 0, 0, 1,
                java.time.Instant.now(), Map.of());
        Map<String, Object> reserved = new java.util.LinkedHashMap<>(valid);
        reserved.put("sequence", 99);
        assertThrows(java.io.IOException.class, () -> ledger.append(reserved));
        Map<String, Object> negative = new java.util.LinkedHashMap<>(valid);
        negative.put("input_tokens", -1);
        assertThrows(java.io.IOException.class, () -> ledger.append(negative));
        assertEquals(0, Files.size(ledger.file()));
        assertEquals(0L, ledger.metrics().get("request_count"));
    }

    private static LlmGatewayServer gateway(LlmEvidenceLedger ledger, String response, long cost)
            throws Exception {
        return new LlmGatewayServer(new LocalMockProvider(
                "local-mock", Map.of("local/exact", response), 20, cost), ledger, TOKEN);
    }

    private static HttpResponse<String> get(int port, String path, String token, String origin)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + path)).GET();
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (origin != null) builder.header("Origin", origin);
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(int port, String path, String token, String body)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
