package io.onsure.provider.spi;

import java.util.Map;

/** Explicit policy facts supplied to a provider without exposing secret values. */
public record ProviderContext(
        boolean networkEgressApproved,
        boolean customerDataApproved,
        long maximumEstimatedCostMicros,
        Map<String, String> evidenceLabels) {
    public ProviderContext {
        if (maximumEstimatedCostMicros < 0) throw new IllegalArgumentException("maximumEstimatedCostMicros");
        evidenceLabels = Map.copyOf(evidenceLabels == null ? Map.of() : evidenceLabels);
        if (evidenceLabels.size() > 64) throw new IllegalArgumentException("evidenceLabels");
    }
}
