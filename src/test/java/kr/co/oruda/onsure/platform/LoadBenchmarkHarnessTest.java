package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import kr.co.oruda.onsure.platform.LoadBenchmarkHarness.BenchmarkResult;
import kr.co.oruda.onsure.platform.LoadBenchmarkHarness.RegressionCheckResult;
import org.junit.jupiter.api.Test;

class LoadBenchmarkHarnessTest {

    @Test
    void benchmarksBoundedRecoveryExecutorUnderConcurrentLoad() throws Exception {
        AtomicInteger totalCalls = new AtomicInteger();
        BenchmarkResult result = LoadBenchmarkHarness.run(() -> {
            AtomicInteger attemptsForThisInvocation = new AtomicInteger();
            BoundedRecoveryExecutor.execute(() -> {
                totalCalls.incrementAndGet();
                if (attemptsForThisInvocation.incrementAndGet() < 2) {
                    throw new IllegalStateException("transient failure");
                }
                return "ok";
            }, 5, Duration.ofMillis(1), "BENCHMARK_RECOVERY_TEST");
        }, 30, 6);

        assertEquals(30, result.totalInvocations());
        assertEquals(30, result.successCount());
        assertEquals(0, result.failureCount());
        assertTrue(totalCalls.get() >= 60, "each invocation should have retried at least once");
    }

    @Test
    void runsAllInvocationsAndCountsSuccessesAndFailures() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        BenchmarkResult result = LoadBenchmarkHarness.run(
                () -> { if (calls.incrementAndGet() % 5 == 0) throw new IllegalStateException("injected"); },
                20, 4);

        assertEquals(20, result.totalInvocations());
        assertEquals(16, result.successCount());
        assertEquals(4, result.failureCount());
        assertTrue(result.p50Micros() <= result.p95Micros());
        assertTrue(result.p95Micros() <= result.p99Micros());
        assertTrue(result.throughputPerSecond() > 0);
    }

    @Test
    void rejectsInvalidInvocationCountAndConcurrency() {
        assertThrows(IllegalArgumentException.class, () -> LoadBenchmarkHarness.run(() -> {}, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> LoadBenchmarkHarness.run(() -> {}, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> LoadBenchmarkHarness.run(() -> {}, 10, 20));
    }

    @Test
    void regressionCheckPassesWhenCurrentIsNotWorseThanBaseline() {
        BenchmarkResult baseline = new BenchmarkResult(100, 100, 0, 1000, 2000, 3000, 500.0, 200);
        BenchmarkResult current = new BenchmarkResult(100, 100, 0, 1000, 2100, 3000, 480.0, 210);

        RegressionCheckResult check = LoadBenchmarkHarness.checkRegression(current, baseline, 1.2);
        assertTrue(check.passed());
        assertEquals(0, check.violations().size());
    }

    @Test
    void regressionCheckFailsClosedOnLatencyRegression() {
        BenchmarkResult baseline = new BenchmarkResult(100, 100, 0, 1000, 2000, 3000, 500.0, 200);
        BenchmarkResult current = new BenchmarkResult(100, 100, 0, 1000, 5000, 6000, 500.0, 200);

        RegressionCheckResult check = LoadBenchmarkHarness.checkRegression(current, baseline, 1.2);
        assertFalse(check.passed());
        assertTrue(check.violations().stream().anyMatch(v -> v.startsWith("P95_LATENCY_REGRESSION")));
    }

    @Test
    void regressionCheckFailsClosedOnThroughputRegression() {
        BenchmarkResult baseline = new BenchmarkResult(100, 100, 0, 1000, 2000, 3000, 500.0, 200);
        BenchmarkResult current = new BenchmarkResult(100, 100, 0, 1000, 2000, 3000, 100.0, 1000);

        RegressionCheckResult check = LoadBenchmarkHarness.checkRegression(current, baseline, 1.2);
        assertFalse(check.passed());
        assertTrue(check.violations().stream().anyMatch(v -> v.startsWith("THROUGHPUT_REGRESSION")));
    }

    @Test
    void regressionCheckFailsClosedOnNewFailures() {
        BenchmarkResult baseline = new BenchmarkResult(100, 100, 0, 1000, 2000, 3000, 500.0, 200);
        BenchmarkResult current = new BenchmarkResult(100, 95, 5, 1000, 2000, 3000, 500.0, 200);

        RegressionCheckResult check = LoadBenchmarkHarness.checkRegression(current, baseline, 1.2);
        assertFalse(check.passed());
        assertTrue(check.violations().stream().anyMatch(v -> v.startsWith("FAILURE_COUNT_REGRESSION")));
    }
}
