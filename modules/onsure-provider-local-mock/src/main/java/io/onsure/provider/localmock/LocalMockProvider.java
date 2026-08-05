package io.onsure.provider.localmock;

import io.onsure.provider.spi.CompletionRequest;
import io.onsure.provider.spi.CompletionResponse;
import io.onsure.provider.spi.ModelProvider;
import io.onsure.provider.spi.ProviderContext;
import io.onsure.provider.spi.ProviderDescriptor;
import io.onsure.provider.spi.ProviderException;
import io.onsure.provider.spi.ProviderHealth;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Network-free deterministic provider used to exercise the real provider SPI boundary. */
public final class LocalMockProvider implements ModelProvider {
    private final ProviderDescriptor descriptor;
    private final Map<String, String> responses;
    private final int requestsPerSecond;
    private final long costPerTokenMicros;
    private final ArrayDeque<Long> requestTimes = new ArrayDeque<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "onsure-local-mock-provider");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean closed;

    public LocalMockProvider(
            String providerId,
            Map<String, String> modelResponses,
            int requestsPerSecond,
            long costPerTokenMicros) {
        if (modelResponses == null || modelResponses.isEmpty() || modelResponses.size() > 64
                || modelResponses.entrySet().stream().anyMatch(entry -> entry.getKey() == null
                || entry.getValue() == null || entry.getValue().length() > 1_000_000)) {
            throw new IllegalArgumentException("modelResponses");
        }
        if (requestsPerSecond < 1 || requestsPerSecond > 10_000) throw new IllegalArgumentException("requestsPerSecond");
        if (costPerTokenMicros < 0 || costPerTokenMicros > 1_000_000_000L) throw new IllegalArgumentException("costPerTokenMicros");
        this.responses = Map.copyOf(new LinkedHashMap<>(modelResponses));
        this.requestsPerSecond = requestsPerSecond;
        this.costPerTokenMicros = costPerTokenMicros;
        this.descriptor = new ProviderDescriptor(
                Objects.requireNonNull(providerId, "providerId"), "1.0.0",
                this.responses.keySet().stream().sorted().toList(), true, false);
    }

    @Override public ProviderDescriptor descriptor() { return descriptor; }

    @Override public ProviderHealth health() {
        return new ProviderHealth(closed ? ProviderHealth.State.UNAVAILABLE : ProviderHealth.State.READY,
                closed ? "CLOSED" : "LOCAL_NO_NETWORK", Instant.now());
    }

    @Override
    public CompletionResponse complete(CompletionRequest request, ProviderContext context)
            throws ProviderException, InterruptedException {
        if (closed) throw new ProviderException("PROVIDER_CLOSED", "Provider is closed", false);
        if (!responses.containsKey(request.modelId())) {
            throw new ProviderException("MODEL_NOT_AVAILABLE", "Exact requested model is unavailable; fallback prohibited", false);
        }
        enforceRateLimit();
        long inputTokens = request.messages().stream().mapToLong(message -> Math.max(1L, (message.content().length() + 3L) / 4L)).sum();
        long estimate;
        try {
            estimate = Math.multiplyExact(inputTokens + request.maximumOutputTokens(), costPerTokenMicros);
        } catch (ArithmeticException overflow) {
            throw new ProviderException("COST_LIMIT_EXCEEDED", "Estimated provider cost overflowed", false);
        }
        if (estimate > context.maximumEstimatedCostMicros()) {
            throw new ProviderException("COST_LIMIT_EXCEEDED", "Estimated provider cost exceeds explicit limit", false);
        }
        long delayMillis = delay(request.metadata().get("mock.delay_millis"));
        Callable<CompletionResponse> work = () -> {
            if (delayMillis > 0) Thread.sleep(delayMillis);
            String content = responses.get(request.modelId());
            long outputTokens = Math.min(request.maximumOutputTokens(), Math.max(1L, (content.length() + 3L) / 4L));
            long actualCost;
            try {
                actualCost = Math.multiplyExact(Math.addExact(inputTokens, outputTokens), costPerTokenMicros);
            } catch (ArithmeticException overflow) {
                throw new ProviderException("COST_LIMIT_EXCEEDED", "Actual provider cost overflowed", false);
            }
            return new CompletionResponse(
                    request.requestId(), descriptor.providerId(), request.modelId(), content,
                    "STOP", inputTokens, outputTokens, Instant.now(), Map.of(
                    "transport", "LOCAL_IN_PROCESS",
                    "network_egress", "false",
                    "fallback_used", "false",
                    "estimated_cost_micros", Long.toString(estimate),
                    "actual_cost_micros", Long.toString(actualCost)));
        };
        Future<CompletionResponse> future = executor.submit(work);
        try {
            return future.get(request.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            future.cancel(true);
            throw new ProviderException("PROVIDER_TIMEOUT", "Local provider exceeded request timeout", true);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                throw (InterruptedException) cause;
            }
            throw new ProviderException("PROVIDER_EXECUTION_FAILED", cause.getClass().getSimpleName(), false);
        }
    }

    private synchronized void enforceRateLimit() throws ProviderException {
        long now = System.nanoTime();
        long cutoff = now - TimeUnit.SECONDS.toNanos(1);
        while (!requestTimes.isEmpty() && requestTimes.peekFirst() <= cutoff) requestTimes.removeFirst();
        if (requestTimes.size() >= requestsPerSecond) {
            throw new ProviderException("RATE_LIMIT_EXCEEDED", "Local provider rate limit exceeded", true);
        }
        requestTimes.addLast(now);
    }

    private static long delay(String value) throws ProviderException {
        if (value == null) return 0L;
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0 || parsed > 60_000) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException invalid) {
            throw new ProviderException("MOCK_DELAY_INVALID", "Mock delay must be 0-60000 milliseconds", false);
        }
    }

    @Override public void close() {
        closed = true;
        executor.shutdownNow();
    }
}
