package kr.co.oruda.onsure.platform;

import java.util.List;
import java.util.Map;

/**
 * Replaceable AI model/LLM provider adapter. No product logic depends on a specific provider or
 * model; every provider implementation must satisfy this same contract so ONSURE can swap
 * providers without changing calling code (NFR-05: Provider and model replaceability).
 */
public interface ModelProviderAdapter {

    record ModelInvocationRequest(
            String taskClass,
            String modelId,
            Map<String, String> configuration,
            String inputDigest,
            int inputTokenEstimate) {
        public ModelInvocationRequest {
            if (taskClass == null || taskClass.isBlank()) throw new IllegalArgumentException("taskClass");
            if (modelId == null || modelId.isBlank()) throw new IllegalArgumentException("modelId");
            configuration = configuration == null ? Map.of() : Map.copyOf(configuration);
            if (inputDigest == null || inputDigest.isBlank()) throw new IllegalArgumentException("inputDigest");
            if (inputTokenEstimate < 0) throw new IllegalArgumentException("inputTokenEstimate");
        }
    }

    record ModelInvocationRecord(
            String invocationId,
            String providerId,
            String modelId,
            String modelVersion,
            String taskClass,
            Map<String, String> configuration,
            int inputTokenCount,
            int outputTokenCount,
            long costMicros,
            String dataTransferPolicy,
            String outputDigest) {
        public ModelInvocationRecord {
            if (invocationId == null || invocationId.isBlank()) throw new IllegalArgumentException("invocationId");
            if (providerId == null || providerId.isBlank()) throw new IllegalArgumentException("providerId");
            if (modelId == null || modelId.isBlank()) throw new IllegalArgumentException("modelId");
            if (modelVersion == null || modelVersion.isBlank()) throw new IllegalArgumentException("modelVersion");
            if (taskClass == null || taskClass.isBlank()) throw new IllegalArgumentException("taskClass");
            configuration = configuration == null ? Map.of() : Map.copyOf(configuration);
            if (inputTokenCount < 0) throw new IllegalArgumentException("inputTokenCount");
            if (outputTokenCount < 0) throw new IllegalArgumentException("outputTokenCount");
            if (costMicros < 0) throw new IllegalArgumentException("costMicros");
            if (dataTransferPolicy == null || dataTransferPolicy.isBlank()) {
                throw new IllegalArgumentException("dataTransferPolicy");
            }
            if (outputDigest == null || outputDigest.isBlank()) throw new IllegalArgumentException("outputDigest");
        }
    }

    String providerId();

    boolean supportsTaskClass(String taskClass);

    List<String> declaredModelIds();

    ModelInvocationRecord invoke(ModelInvocationRequest request) throws Exception;
}
