package io.onsure.provider.localmock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.onsure.provider.spi.CompletionRequest;
import io.onsure.provider.spi.ProviderContext;
import io.onsure.provider.spi.ProviderException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LocalMockProviderTest {
    @Test
    void completesWithoutNetworkOrFallback() throws Exception {
        try (LocalMockProvider provider = provider(10, 1)) {
            var response = provider.complete(request("local/exact", Duration.ofSeconds(1), Map.of()), context(10_000));
            assertEquals("deterministic answer", response.content());
            assertEquals("false", response.evidence().get("network_egress"));
            assertEquals("false", response.evidence().get("fallback_used"));
            assertFalse(provider.descriptor().networkEgressRequired());
        }
    }

    @Test
    void failsClosedForTimeoutRateLimitCostAndUnknownModel() throws Exception {
        try (LocalMockProvider timeout = provider(10, 0)) {
            assertCode("PROVIDER_TIMEOUT", () -> timeout.complete(
                    request("local/exact", Duration.ofMillis(10), Map.of("mock.delay_millis", "100")), context(0)));
        }
        try (LocalMockProvider rate = provider(1, 0)) {
            rate.complete(request("local/exact", Duration.ofSeconds(1), Map.of()), context(0));
            assertCode("RATE_LIMIT_EXCEEDED", () -> rate.complete(
                    request("local/exact", Duration.ofSeconds(1), Map.of()), context(0)));
        }
        try (LocalMockProvider cost = provider(10, 100)) {
            assertCode("COST_LIMIT_EXCEEDED", () -> cost.complete(
                    request("local/exact", Duration.ofSeconds(1), Map.of()), context(1)));
        }
        try (LocalMockProvider exact = provider(10, 0)) {
            assertCode("MODEL_NOT_AVAILABLE", () -> exact.complete(
                    request("local/fallback", Duration.ofSeconds(1), Map.of()), context(0)));
        }
    }

    private static LocalMockProvider provider(int rate, long cost) {
        return new LocalMockProvider("local-mock", Map.of("local/exact", "deterministic answer"), rate, cost);
    }

    private static CompletionRequest request(String model, Duration timeout, Map<String, String> metadata) {
        return new CompletionRequest("request-1", model,
                List.of(new CompletionRequest.Message("user", "hello")), 8, timeout, metadata);
    }

    private static ProviderContext context(long cost) {
        return new ProviderContext(false, false, cost, Map.of());
    }

    private static void assertCode(String code, ThrowingCall call) {
        ProviderException failure = assertThrows(ProviderException.class, call::run);
        assertEquals(code, failure.code());
    }

    @FunctionalInterface private interface ThrowingCall { void run() throws Exception; }
}
