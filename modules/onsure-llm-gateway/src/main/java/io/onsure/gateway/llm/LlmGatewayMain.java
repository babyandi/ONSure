package io.onsure.gateway.llm;

import io.onsure.provider.localmock.LocalMockProvider;
import io.onsure.provider.openai.OpenAiResponsesProvider;
import io.onsure.provider.spi.ModelProvider;
import java.nio.file.Path;
import java.util.Map;

/** Environment-configured LLM Gateway process with exact provider selection and no fallback. */
public final class LlmGatewayMain {
    private LlmGatewayMain() {}

    public static void main(String[] args) throws Exception {
        Map<String, String> environment = System.getenv();
        String mode = environment.getOrDefault("ONSURE_LLM_PROVIDER", "local-mock");
        String model = environment.getOrDefault("ONSURE_LLM_MODEL",
                "openai".equals(mode)
                        ? environment.getOrDefault("ONSURE_OPENAI_MODEL", OpenAiResponsesProvider.DEFAULT_MODEL)
                        : "onsure-local-mock-v1");
        if ("openai".equals(mode)
                && !model.equals(environment.getOrDefault("ONSURE_OPENAI_MODEL", OpenAiResponsesProvider.DEFAULT_MODEL))) {
            throw new IllegalArgumentException("ONSURE_LLM_MODEL_OPENAI_MODEL_MISMATCH");
        }
        ModelProvider provider = provider(mode, model, environment);
        Path evidenceRoot = Path.of(required(environment, "ONSURE_LLM_EVIDENCE_ROOT"));
        String token = required(environment, "ONSURE_LLM_GATEWAY_TOKEN");
        int port = Integer.parseInt(environment.getOrDefault("ONSURE_LLM_GATEWAY_PORT", "47312"));
        LlmGatewayServer gateway = new LlmGatewayServer(provider, new LlmEvidenceLedger(evidenceRoot), token);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { gateway.close(); } catch (Exception ignored) {}
        }, "onsure-llm-gateway-shutdown"));
        int actualPort = gateway.startAndGetPort(port);
        System.out.println("ONSURE_LLM_GATEWAY_READY 127.0.0.1:" + actualPort
                + " provider=" + provider.descriptor().providerId() + " model=" + model);
        Thread.currentThread().join();
    }

    static ModelProvider provider(String mode, String model, Map<String, String> environment) {
        return switch (mode) {
            case "local-mock" -> new LocalMockProvider(
                    "local-mock", Map.of(model, environment.getOrDefault(
                            "ONSURE_LLM_MOCK_RESPONSE", "ONSure local mock completion")),
                    integer(environment, "ONSURE_LLM_MOCK_REQUESTS_PER_SECOND", 20, 1, 10_000),
                    number(environment, "ONSURE_LLM_MOCK_COST_PER_TOKEN_MICROS", 0, 0, 1_000_000_000L));
            case "openai" -> OpenAiResponsesProvider.fromEnvironment();
            default -> throw new IllegalArgumentException("ONSURE_LLM_PROVIDER_UNSUPPORTED");
        };
    }

    private static String required(Map<String, String> environment, String key) {
        String value = environment.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + "_REQUIRED");
        return value;
    }

    private static int integer(Map<String, String> environment, String key, int fallback, int minimum, int maximum) {
        long value = number(environment, key, fallback, minimum, maximum);
        return Math.toIntExact(value);
    }

    private static long number(Map<String, String> environment, String key, long fallback, long minimum, long maximum) {
        String raw = environment.get(key);
        try {
            long value = raw == null ? fallback : Long.parseLong(raw);
            if (value < minimum || value > maximum) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(key + "_INVALID");
        }
    }
}
