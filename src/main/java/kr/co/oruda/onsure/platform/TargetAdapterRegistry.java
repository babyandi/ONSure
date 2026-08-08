package kr.co.oruda.onsure.platform;

import kr.co.oruda.onsure.platform.ValidationModel.ValidationTarget;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Product registry for replaceable target adapters. */
public final class TargetAdapterRegistry {
    private final Map<String, TargetAdapter> adapters = new LinkedHashMap<>();

    public TargetAdapterRegistry(List<TargetAdapter> initial) {
        for (TargetAdapter adapter : initial) register(adapter);
        if (adapters.isEmpty()) throw new IllegalArgumentException("at least one adapter is required");
    }

    public synchronized void register(TargetAdapter adapter) {
        if (adapter == null || adapter.adapterId() == null || adapter.adapterId().isBlank()) {
            throw new IllegalArgumentException("INVALID_TARGET_ADAPTER");
        }
        if (adapters.putIfAbsent(adapter.adapterId(), adapter) != null) {
            throw new IllegalArgumentException("DUPLICATE_TARGET_ADAPTER: " + adapter.adapterId());
        }
    }

    public synchronized TargetAdapter require(ValidationTarget target) {
        TargetAdapter adapter = adapters.get(target.adapterId());
        if (adapter == null) throw new IllegalArgumentException("NO_TARGET_ADAPTER: " + target.adapterId());
        if (!adapter.supports(target.targetType())) {
            throw new IllegalArgumentException("TARGET_TYPE_NOT_SUPPORTED_BY_ADAPTER: " + target.targetType());
        }
        return adapter;
    }

    public synchronized List<String> adapterIds() {
        return List.copyOf(adapters.keySet());
    }
}
