package io.onsure.provider.spi;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Bounded completion input; secrets and raw customer identifiers are forbidden by contract. */
public record CompletionRequest(
        String requestId,
        String modelId,
        List<Message> messages,
        int maximumOutputTokens,
        Duration timeout,
        Map<String, String> metadata) {
    public CompletionRequest {
        if (requestId == null || !requestId.matches("[A-Za-z0-9._-]{1,128}")) throw new IllegalArgumentException("requestId");
        if (modelId == null || !modelId.matches("[A-Za-z0-9._:/-]{1,160}")) throw new IllegalArgumentException("modelId");
        messages = List.copyOf(messages == null ? List.of() : messages);
        if (messages.isEmpty() || messages.size() > 256) throw new IllegalArgumentException("messages");
        if (maximumOutputTokens < 1 || maximumOutputTokens > 131072) throw new IllegalArgumentException("maximumOutputTokens");
        if (timeout == null || timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(30)) > 0) {
            throw new IllegalArgumentException("timeout");
        }
        metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
        if (metadata.size() > 64) throw new IllegalArgumentException("metadata");
    }

    public record Message(String role, String content) {
        public Message {
            if (!List.of("system", "user", "assistant", "tool").contains(role)) throw new IllegalArgumentException("role");
            if (content == null || content.isBlank() || content.length() > 1_000_000) throw new IllegalArgumentException("content");
        }
    }
}
