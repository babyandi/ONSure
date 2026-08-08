package kr.co.oruda.onsure.platform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Concurrent load/performance benchmark harness (VERIFICATION-PERFORMANCE-RECOVERY:
 * RECOVERY_ONLY_NO_LOAD_OR_PERFORMANCE_BENCHMARK_HARNESS). Runs an operation a fixed number of
 * times under a declared concurrency level and reports latency percentiles and throughput.
 *
 * <p>Absolute numbers from a single host are NOT a certified production performance claim -- they
 * depend on the machine, JVM warmup and co-located load. {@link #checkRegression} exists so a
 * caller compares a current run against its own previously captured baseline on the same
 * environment, rather than treating any absolute number as a portable truth.
 */
public final class LoadBenchmarkHarness {

    @FunctionalInterface
    public interface Operation {
        void run() throws Exception;
    }

    public record BenchmarkResult(
            int totalInvocations,
            int successCount,
            int failureCount,
            long p50Micros,
            long p95Micros,
            long p99Micros,
            double throughputPerSecond,
            long wallClockMillis) {}

    public record RegressionCheckResult(boolean passed, List<String> violations) {
        public RegressionCheckResult { violations = List.copyOf(violations); }
    }

    private LoadBenchmarkHarness() {}

    public static BenchmarkResult run(Operation operation, int invocationCount, int concurrency) throws Exception {
        if (operation == null) throw new IllegalArgumentException("BENCHMARK_OPERATION_REQUIRED");
        if (invocationCount < 1) throw new IllegalArgumentException("BENCHMARK_INVOCATION_COUNT_INVALID");
        if (concurrency < 1 || concurrency > invocationCount) {
            throw new IllegalArgumentException("BENCHMARK_CONCURRENCY_INVALID");
        }

        List<Long> durationsNanos = Collections.synchronizedList(new ArrayList<>(invocationCount));
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        long wallStartNanos = System.nanoTime();
        try {
            List<Future<?>> futures = new ArrayList<>(invocationCount);
            for (int i = 0; i < invocationCount; i++) {
                futures.add(pool.submit(() -> {
                    long start = System.nanoTime();
                    boolean ok;
                    try {
                        operation.run();
                        ok = true;
                    } catch (Exception failure) {
                        ok = false;
                    }
                    durationsNanos.add(System.nanoTime() - start);
                    (ok ? successCount : failureCount).incrementAndGet();
                }));
            }
            for (Future<?> future : futures) future.get();
        } finally {
            pool.shutdown();
        }
        long wallElapsedNanos = System.nanoTime() - wallStartNanos;

        List<Long> sorted = new ArrayList<>(durationsNanos);
        Collections.sort(sorted);
        double throughput = wallElapsedNanos == 0 ? 0.0 : invocationCount / (wallElapsedNanos / 1_000_000_000.0);

        return new BenchmarkResult(
                invocationCount, successCount.get(), failureCount.get(),
                percentileMicros(sorted, 50), percentileMicros(sorted, 95), percentileMicros(sorted, 99),
                throughput, wallElapsedNanos / 1_000_000);
    }

    /** Fails closed when {@code current} is worse than {@code baseline} by more than {@code maxRegressionRatio}. */
    public static RegressionCheckResult checkRegression(
            BenchmarkResult current, BenchmarkResult baseline, double maxRegressionRatio) {
        if (current == null || baseline == null) throw new IllegalArgumentException("BENCHMARK_RESULT_REQUIRED");
        if (maxRegressionRatio <= 1.0) throw new IllegalArgumentException("BENCHMARK_MAX_REGRESSION_RATIO_INVALID");

        List<String> violations = new ArrayList<>();
        long p95Ceiling = Math.round(baseline.p95Micros() * maxRegressionRatio);
        if (current.p95Micros() > p95Ceiling) {
            violations.add("P95_LATENCY_REGRESSION:" + current.p95Micros() + "us>" + p95Ceiling + "us");
        }
        double throughputFloor = baseline.throughputPerSecond() / maxRegressionRatio;
        if (current.throughputPerSecond() < throughputFloor) {
            violations.add("THROUGHPUT_REGRESSION:" + current.throughputPerSecond()
                    + "/s<" + throughputFloor + "/s");
        }
        if (current.failureCount() > baseline.failureCount()) {
            violations.add("FAILURE_COUNT_REGRESSION:" + current.failureCount() + ">" + baseline.failureCount());
        }
        return new RegressionCheckResult(violations.isEmpty(), violations);
    }

    private static long percentileMicros(List<Long> sortedNanos, int percentile) {
        if (sortedNanos.isEmpty()) return 0;
        int index = (int) Math.ceil(percentile / 100.0 * sortedNanos.size()) - 1;
        index = Math.max(0, Math.min(index, sortedNanos.size() - 1));
        return sortedNanos.get(index) / 1000;
    }
}
