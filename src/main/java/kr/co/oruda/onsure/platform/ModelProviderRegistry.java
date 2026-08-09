package kr.co.oruda.onsure.platform;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Product registry for replaceable model providers. Callers select a provider by task class and
 * an explicit provider id; no code path is hard-wired to a single provider or model.
 */
public final class ModelProviderRegistry {
    private final Map<String, ModelProviderAdapter> providers = new LinkedHashMap<>();
    private final Map<String, String> defaultProviderByTaskClass = new LinkedHashMap<>();

    public ModelProviderRegistry(List<ModelProviderAdapter> initial) {
        for (ModelProviderAdapter provider : initial) register(provider);
        if (providers.isEmpty()) throw new IllegalArgumentException("at least one model provider is required");
    }

    public synchronized void register(ModelProviderAdapter provider) {
        if (provider == null || provider.providerId() == null || provider.providerId().isBlank()) {
            throw new IllegalArgumentException("INVALID_MODEL_PROVIDER");
        }
        if (providers.putIfAbsent(provider.providerId(), provider) != null) {
            throw new IllegalArgumentException("DUPLICATE_MODEL_PROVIDER: " + provider.providerId());
        }
    }

    /** Declares which provider currently serves a task class. Callable again to swap providers. */
    public synchronized void bindDefault(String taskClass, String providerId) {
        if (taskClass == null || taskClass.isBlank()) throw new IllegalArgumentException("taskClass");
        ModelProviderAdapter provider = providers.get(providerId);
        if (provider == null) throw new IllegalArgumentException("NO_MODEL_PROVIDER: " + providerId);
        if (!provider.supportsTaskClass(taskClass)) {
            throw new IllegalArgumentException("TASK_CLASS_NOT_SUPPORTED_BY_PROVIDER: " + taskClass);
        }
        defaultProviderByTaskClass.put(taskClass, providerId);
    }

    public synchronized ModelProviderAdapter require(String taskClass) {
        String providerId = defaultProviderByTaskClass.get(taskClass);
        if (providerId == null) throw new IllegalArgumentException("NO_DEFAULT_PROVIDER_BOUND: " + taskClass);
        return require(taskClass, providerId);
    }

    public synchronized ModelProviderAdapter require(String taskClass, String providerId) {
        ModelProviderAdapter provider = providers.get(providerId);
        if (provider == null) throw new IllegalArgumentException("NO_MODEL_PROVIDER: " + providerId);
        if (!provider.supportsTaskClass(taskClass)) {
            throw new IllegalArgumentException("TASK_CLASS_NOT_SUPPORTED_BY_PROVIDER: " + taskClass);
        }
        return provider;
    }

    public synchronized List<String> providerIds() {
        return List.copyOf(providers.keySet());
    }

    /** Returns the registered adapter for {@code providerId} regardless of task-class bindings. */
    public synchronized ModelProviderAdapter adapter(String providerId) {
        ModelProviderAdapter provider = providers.get(providerId);
        if (provider == null) throw new IllegalArgumentException("NO_MODEL_PROVIDER: " + providerId);
        return provider;
    }
}
