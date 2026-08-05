package io.onsure.gateway.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.onsure.provider.localmock.LocalMockProvider;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LlmGatewayMainTest {
    @Test
    void selectsExactLocalProviderAndRejectsUnsupportedProvider() throws Exception {
        try (var provider = LlmGatewayMain.provider(
                "local-mock", "local/exact", Map.of(
                        "ONSURE_LLM_MOCK_RESPONSE", "answer",
                        "ONSURE_LLM_MOCK_REQUESTS_PER_SECOND", "5",
                        "ONSURE_LLM_MOCK_COST_PER_TOKEN_MICROS", "2"))) {
            assertEquals(LocalMockProvider.class, provider.getClass());
            assertEquals(java.util.List.of("local/exact"), provider.descriptor().modelIds());
        }
        assertThrows(IllegalArgumentException.class, () ->
                LlmGatewayMain.provider("fallback-auto", "local/exact", Map.of()));
    }
}
