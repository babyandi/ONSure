package io.onsure.provider.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.onsure.provider.spi.CompletionRequest;
import io.onsure.provider.spi.CompletionResponse;
import io.onsure.provider.spi.ProviderContext;
import io.onsure.provider.spi.ProviderException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** File-input CLI for an explicitly approved OpenAI Responses request. */
public final class OpenAiProviderMain {
    private static final int MAX_INPUT_BYTES = 1_048_576;

    private OpenAiProviderMain() {}

    public static void main(String[] args) {
        try {
            if (args.length != 1) throw new IllegalArgumentException("usage: request.json");
            Path requestFile = Path.of(args[0]).toAbsolutePath().normalize();
            if (!Files.isRegularFile(requestFile, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(requestFile) || Files.size(requestFile) > MAX_INPUT_BYTES) {
                throw new IllegalArgumentException("REQUEST_FILE_INVALID");
            }
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(requestFile.toFile());
            CompletionRequest request = request(root);
            ProviderContext context = context(root.path("policy"));
            try (OpenAiResponsesProvider provider = OpenAiResponsesProvider.fromEnvironment()) {
                CompletionResponse response = provider.complete(request, context);
                Map<String, Object> output = new LinkedHashMap<>();
                output.put("contract", "ONSURE_OPENAI_RESPONSES_CLI_V1");
                output.put("request_id", response.requestId());
                output.put("provider_id", response.providerId());
                output.put("model_id", response.modelId());
                output.put("content", response.content());
                output.put("finish_reason", response.finishReason());
                output.put("input_tokens", response.inputTokens());
                output.put("output_tokens", response.outputTokens());
                output.put("completed_at", response.completedAt().toString());
                output.put("evidence", response.evidence());
                output.put("final_claim_allowed", false);
                System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(output));
            }
        } catch (ProviderException failure) {
            System.err.println("ONSURE_OPENAI_PROVIDER_FAIL " + failure.code()
                    + " retryable=" + failure.retryable());
            System.exit(1);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            System.err.println("ONSURE_OPENAI_PROVIDER_FAIL INTERRUPTED retryable=true");
            System.exit(1);
        } catch (Exception failure) {
            System.err.println("ONSURE_OPENAI_PROVIDER_FAIL " + safe(failure));
            System.exit(1);
        }
    }

    private static CompletionRequest request(JsonNode root) {
        List<CompletionRequest.Message> messages = new ArrayList<>();
        for (JsonNode message : root.path("messages")) {
            messages.add(new CompletionRequest.Message(
                    message.path("role").asText(), message.path("content").asText()));
        }
        return new CompletionRequest(
                root.path("request_id").asText(),
                root.path("model_id").asText(OpenAiResponsesProvider.DEFAULT_MODEL),
                messages,
                root.path("maximum_output_tokens").asInt(),
                Duration.ofMillis(root.path("timeout_millis").asLong()),
                Map.of());
    }

    private static ProviderContext context(JsonNode policy) {
        return new ProviderContext(
                policy.path("network_egress_approved").asBoolean(false),
                policy.path("customer_data_approved").asBoolean(false),
                policy.path("maximum_estimated_cost_micros").asLong(-1),
                Map.of());
    }

    private static String safe(Exception failure) {
        if (failure instanceof IllegalArgumentException || failure instanceof IllegalStateException) {
            String message = failure.getMessage();
            return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
        }
        return failure.getClass().getSimpleName();
    }
}
