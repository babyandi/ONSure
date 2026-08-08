package kr.co.oruda.onsure.platform;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Retries a bounded number of times with exponential backoff, recording a root-cause note per
 * failed attempt before retrying (matching this product's MAX_RETRY_WITH_RCA control). Once the
 * retry budget is exhausted the failure is rethrown, never silently swallowed (fail-closed).
 */
public final class BoundedRecoveryExecutor {
    private static final Duration MAX_SINGLE_SLEEP = Duration.ofSeconds(5);

    public record AttemptRecord(int attemptNumber, boolean succeeded, String rootCauseNote, Duration elapsed) {}

    public record RecoveryResult<T>(T value, List<AttemptRecord> attempts) {
        public RecoveryResult {
            attempts = List.copyOf(attempts);
        }

        public int totalAttempts() { return attempts.size(); }
    }

    public static final class RecoveryExhaustedException extends RuntimeException {
        private final List<AttemptRecord> attempts;

        RecoveryExhaustedException(String message, List<AttemptRecord> attempts, Throwable cause) {
            super(message, cause);
            this.attempts = List.copyOf(attempts);
        }

        public List<AttemptRecord> attempts() { return attempts; }
    }

    private BoundedRecoveryExecutor() {}

    public static <T> RecoveryResult<T> execute(
            Callable<T> operation, int maxAttempts, Duration initialBackoff, String authority)
            throws InterruptedException {
        Objects.requireNonNull(operation, "operation");
        if (maxAttempts < 1 || maxAttempts > 20) throw new IllegalArgumentException("RECOVERY_MAX_ATTEMPTS_INVALID");
        if (initialBackoff == null || initialBackoff.isNegative()) {
            throw new IllegalArgumentException("RECOVERY_BACKOFF_INVALID");
        }
        String label = authority == null || authority.isBlank() ? "RECOVERY" : authority;

        List<AttemptRecord> attempts = new ArrayList<>();
        Duration backoff = initialBackoff;
        Throwable lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Instant start = Instant.now();
            try {
                T value = operation.call();
                attempts.add(new AttemptRecord(attempt, true, "", Duration.between(start, Instant.now())));
                return new RecoveryResult<>(value, attempts);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw interrupted;
            } catch (Exception failure) {
                lastFailure = failure;
                String rootCauseNote = label + ":" + failure.getClass().getSimpleName() + ":"
                        + (failure.getMessage() == null ? "" : failure.getMessage());
                attempts.add(new AttemptRecord(attempt, false, rootCauseNote, Duration.between(start, Instant.now())));
                if (attempt == maxAttempts) break;
                sleepBounded(backoff);
                backoff = backoff.multipliedBy(2);
            }
        }
        throw new RecoveryExhaustedException(
                label + " exhausted " + maxAttempts + " attempts", attempts, lastFailure);
    }

    private static void sleepBounded(Duration backoff) throws InterruptedException {
        long millis = Math.min(backoff.toMillis(), MAX_SINGLE_SLEEP.toMillis());
        if (millis > 0) Thread.sleep(millis);
    }
}
