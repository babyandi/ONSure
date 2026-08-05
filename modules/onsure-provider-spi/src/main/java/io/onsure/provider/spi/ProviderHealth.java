package io.onsure.provider.spi;

import java.time.Instant;

/** Nonfinal provider readiness observation. */
public record ProviderHealth(State state, String detail, Instant observedAt) {
    public enum State { READY, DEGRADED, UNAVAILABLE }

    public ProviderHealth {
        if (state == null || detail == null || detail.isBlank() || observedAt == null) {
            throw new IllegalArgumentException("provider health");
        }
    }
}
