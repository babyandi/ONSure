package io.onsure.platform;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Package-private provider/model seam; provider implementations remain optional dependencies. */
final class ModelProviderAdapterRegistry {
    static final String ADAPTER_CONTRACT = "ONSURE_MODEL_PROVIDER_ADAPTER_V1";
    static final String COMPATIBILITY_CONTRACT = "ONSURE_MODEL_PROVIDER_COMPATIBILITY_V1";
    private static final Set<String> EXECUTION_CLASSES = Set.of(
            "LOCAL_DETERMINISTIC", "LOCAL_MODEL", "REMOTE_PROVIDER");
    private static final Set<String> DATA_SCOPES = Set.of(
            "DIGEST_ONLY", "REDACTED_CONTEXT", "FULL_APPROVED_CONTEXT");

    interface Adapter {
        Descriptor descriptor();
        Response invoke(Request request) throws Exception;
    }

    record Descriptor(
            String providerId,
            String modelId,
            String modelVersion,
            String executionClass,
            boolean supportsOffline,
            int maximumInputTokens,
            int maximumOutputTokens,
            Set<String> allowedDataScopes) {
        Descriptor {
            providerId = id(providerId, "PROVIDER_ID_INVALID");
            modelId = id(modelId, "MODEL_ID_INVALID");
            modelVersion = text(modelVersion, "MODEL_VERSION_INVALID");
            if (!EXECUTION_CLASSES.contains(executionClass)) {
                throw new IllegalArgumentException("MODEL_EXECUTION_CLASS_INVALID");
            }
            if (maximumInputTokens < 1 || maximumOutputTokens < 1) {
                throw new IllegalArgumentException("MODEL_TOKEN_LIMIT_INVALID");
            }
            allowedDataScopes = Set.copyOf(allowedDataScopes);
            if (allowedDataScopes.isEmpty() || !DATA_SCOPES.containsAll(allowedDataScopes)) {
                throw new IllegalArgumentException("MODEL_DATA_SCOPE_INVALID");
            }
            if (supportsOffline && "REMOTE_PROVIDER".equals(executionClass)) {
                throw new IllegalArgumentException("REMOTE_PROVIDER_CANNOT_CLAIM_OFFLINE");
            }
        }

        Map<String, Object> contractView() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("contract", ADAPTER_CONTRACT);
            value.put("provider_id", providerId);
            value.put("model_id", modelId);
            value.put("model_version", modelVersion);
            value.put("execution_class", executionClass);
            value.put("supports_offline", supportsOffline);
            value.put("maximum_input_tokens", maximumInputTokens);
            value.put("maximum_output_tokens", maximumOutputTokens);
            value.put("allowed_data_scopes", allowedDataScopes.stream().sorted().toList());
            value.put("compatibility_contract", COMPATIBILITY_CONTRACT);
            value.put("final_claim_allowed", false);
            return Map.copyOf(value);
        }
    }

    record Request(
            String requestId,
            String purpose,
            String inputSha256,
            int estimatedInputTokens,
            int maximumOutputTokens,
            String dataScope,
            boolean networkAllowed) {
        Request {
            requestId = id(requestId, "MODEL_REQUEST_ID_INVALID");
            purpose = id(purpose, "MODEL_PURPOSE_INVALID");
            if (inputSha256 == null || !inputSha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("MODEL_INPUT_DIGEST_INVALID");
            }
            if (estimatedInputTokens < 0 || maximumOutputTokens < 1) {
                throw new IllegalArgumentException("MODEL_REQUEST_TOKEN_LIMIT_INVALID");
            }
            if (!DATA_SCOPES.contains(dataScope)) {
                throw new IllegalArgumentException("MODEL_REQUEST_DATA_SCOPE_INVALID");
            }
        }
    }

    record Response(
            String requestId,
            String providerId,
            String modelId,
            String outputSha256,
            int inputTokens,
            int outputTokens,
            boolean networkUsed,
            String providerReceiptId) {
        Response {
            requestId = id(requestId, "MODEL_RESPONSE_REQUEST_ID_INVALID");
            providerId = id(providerId, "MODEL_RESPONSE_PROVIDER_ID_INVALID");
            modelId = id(modelId, "MODEL_RESPONSE_MODEL_ID_INVALID");
            providerReceiptId = id(providerReceiptId, "MODEL_PROVIDER_RECEIPT_ID_INVALID");
            if (outputSha256 == null || !outputSha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("MODEL_OUTPUT_DIGEST_INVALID");
            }
            if (inputTokens < 0 || outputTokens < 0) {
                throw new IllegalArgumentException("MODEL_RESPONSE_TOKEN_COUNT_INVALID");
            }
        }
    }

    private final Map<String, Adapter> adapters;

    ModelProviderAdapterRegistry(List<Adapter> values) {
        Map<String, Adapter> indexed = new LinkedHashMap<>();
        for (Adapter adapter : values) {
            Adapter value = Objects.requireNonNull(adapter, "MODEL_ADAPTER_REQUIRED");
            Descriptor descriptor = Objects.requireNonNull(value.descriptor(), "MODEL_DESCRIPTOR_REQUIRED");
            String key = key(descriptor.providerId(), descriptor.modelId());
            if (indexed.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("MODEL_ADAPTER_DUPLICATE:" + key);
            }
        }
        if (indexed.isEmpty()) throw new IllegalArgumentException("MODEL_ADAPTER_REQUIRED");
        adapters = Map.copyOf(indexed);
    }

    Adapter require(String providerId, String modelId, boolean offlineRequired) {
        Adapter adapter = adapters.get(key(id(providerId, "PROVIDER_ID_INVALID"), id(modelId, "MODEL_ID_INVALID")));
        if (adapter == null) throw new IllegalArgumentException("MODEL_ADAPTER_NOT_REGISTERED");
        if (offlineRequired && !adapter.descriptor().supportsOffline()) {
            throw new IllegalStateException("MODEL_ADAPTER_OFFLINE_UNSUPPORTED");
        }
        return adapter;
    }

    Response invoke(String providerId, String modelId, boolean offlineRequired, Request request)
            throws Exception {
        Adapter adapter = require(providerId, modelId, offlineRequired);
        Descriptor descriptor = adapter.descriptor();
        if (request.estimatedInputTokens() > descriptor.maximumInputTokens()
                || request.maximumOutputTokens() > descriptor.maximumOutputTokens()) {
            throw new IllegalArgumentException("MODEL_REQUEST_EXCEEDS_ADAPTER_BUDGET");
        }
        if (!descriptor.allowedDataScopes().contains(request.dataScope())) {
            throw new IllegalArgumentException("MODEL_REQUEST_DATA_SCOPE_NOT_ALLOWED");
        }
        if (!request.networkAllowed() && "REMOTE_PROVIDER".equals(descriptor.executionClass())) {
            throw new IllegalStateException("MODEL_NETWORK_POLICY_DENIED");
        }
        Response response = Objects.requireNonNull(adapter.invoke(request), "MODEL_RESPONSE_REQUIRED");
        if (!request.requestId().equals(response.requestId())
                || !descriptor.providerId().equals(response.providerId())
                || !descriptor.modelId().equals(response.modelId())) {
            throw new IllegalStateException("MODEL_RESPONSE_BINDING_INVALID");
        }
        if (response.inputTokens() > descriptor.maximumInputTokens()
                || response.outputTokens() > request.maximumOutputTokens()
                || response.outputTokens() > descriptor.maximumOutputTokens()) {
            throw new IllegalStateException("MODEL_RESPONSE_TOKEN_BUDGET_EXCEEDED");
        }
        if (response.networkUsed() && !request.networkAllowed()) {
            throw new IllegalStateException("MODEL_RESPONSE_NETWORK_POLICY_BREACH");
        }
        return response;
    }

    List<Map<String, Object>> descriptors() {
        return adapters.values().stream().map(Adapter::descriptor)
                .map(Descriptor::contractView)
                .sorted((left, right) -> String.valueOf(left.get("provider_id"))
                        .compareTo(String.valueOf(right.get("provider_id"))))
                .toList();
    }

    private static String key(String providerId, String modelId) {
        return providerId + ":" + modelId;
    }

    private static String id(String value, String error) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,128}")) {
            throw new IllegalArgumentException(error);
        }
        return value;
    }

    private static String text(String value, String error) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException(error);
        }
        return value;
    }
}
