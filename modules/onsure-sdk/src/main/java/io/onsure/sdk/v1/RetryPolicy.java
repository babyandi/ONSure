package io.onsure.sdk.v1;

import java.time.Duration;

/** Bounded retry policy used only by explicitly idempotent SDK calls. */
public record RetryPolicy(int maximumAttempts, Duration initialDelay, Duration maximumDelay) {
    public RetryPolicy {
        if (maximumAttempts < 1 || maximumAttempts > 5) throw new IllegalArgumentException("maximumAttempts");
        if (initialDelay == null || initialDelay.isNegative() || initialDelay.compareTo(Duration.ofSeconds(10)) > 0) {
            throw new IllegalArgumentException("initialDelay");
        }
        if (maximumDelay == null || maximumDelay.compareTo(initialDelay) < 0
                || maximumDelay.compareTo(Duration.ofSeconds(30)) > 0) throw new IllegalArgumentException("maximumDelay");
    }

    public static RetryPolicy noRetry() { return new RetryPolicy(1, Duration.ZERO, Duration.ZERO); }
}
