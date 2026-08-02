package io.onsure.provider.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelProviderContractTest {
    @Test
    void fakeProviderDemonstratesTransportNeutralContract() throws Exception {
        try (ModelProvider provider = new FakeProvider()) {
            CompletionRequest request = new CompletionRequest(
                    "request-1", "local-test", List.of(new CompletionRequest.Message("user", "hello")),
                    32, Duration.ofSeconds(5), Map.of("classification", "SYNTHETIC"));
            CompletionResponse response = provider.complete(
                    request, new ProviderContext(false, false, 0, Map.of("fixture", "provider-spi")));
            assertEquals("request-1", response.requestId());
            assertEquals("echo:hello", response.content());
            assertEquals(ProviderHealth.State.READY, provider.health().state());
        }
    }

    @Test
    void boundsAndLocalityConflictsFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> new ProviderDescriptor(
                "provider", "1", List.of("model"), true, true));
        assertThrows(IllegalArgumentException.class, () -> new CompletionRequest(
                "request", "model", List.of(), 1, Duration.ofSeconds(1), Map.of()));
    }

    private static final class FakeProvider implements ModelProvider {
        public ProviderDescriptor descriptor() {
            return new ProviderDescriptor("fake", "1", List.of("local-test"), true, false);
        }

        public ProviderHealth health() {
            return new ProviderHealth(ProviderHealth.State.READY, "synthetic", Instant.now());
        }

        public CompletionResponse complete(CompletionRequest request, ProviderContext context) {
            return new CompletionResponse(
                    request.requestId(), "fake", request.modelId(),
                    "echo:" + request.messages().get(0).content(), "STOP", 1, 1,
                    Instant.now(), context.evidenceLabels());
        }
    }
}
