package io.onsure.provider.spi;

import java.time.Instant;
import java.util.Map;

/** Provider output with the minimum usage and evidence binding required by ONSure. */
public record CompletionResponse(
        String requestId,
        String providerId,
        String modelId,
        String content,
        String finishReason,
        long inputTokens,
        long outputTokens,
        Instant completedAt,
        Map<String, String> evidence) {
    public CompletionResponse {
        if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("requestId");
        if (providerId == null || providerId.isBlank()) throw new IllegalArgumentException("providerId");
        if (modelId == null || modelId.isBlank()) throw new IllegalArgumentException("modelId");
        if (content == null) throw new IllegalArgumentException("content");
        if (finishReason == null || finishReason.isBlank()) throw new IllegalArgumentException("finishReason");
        if (inputTokens < 0 || outputTokens < 0) throw new IllegalArgumentException("tokens");
        if (completedAt == null) throw new IllegalArgumentException("completedAt");
        evidence = Map.copyOf(evidence == null ? Map.of() : evidence);
    }
}
