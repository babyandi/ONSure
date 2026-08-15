package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import kr.co.oruda.onsure.platform.BoundedRecoveryExecutor.RecoveryExhaustedException;
import kr.co.oruda.onsure.platform.BoundedRecoveryExecutor.RecoveryResult;
import org.junit.jupiter.api.Test;

/** Failure-injection tests for recovery: deliberately fails an operation N times, then verifies
 * recovery either succeeds within budget or fails closed once the budget is exhausted.
 * NFR-REL (멱등성, 재시도, 중복 이벤트 방어): retry half. */
class BoundedRecoveryExecutorTest {

    @Test
    void recoversAfterInjectedTransientFailuresWithinBudget() throws Exception {
        AtomicInteger callCount = new AtomicInteger();
        RecoveryResult<String> result = BoundedRecoveryExecutor.execute(
                () -> {
                    if (callCount.incrementAndGet() < 3) {
                        throw new IllegalStateException("injected-transient-failure");
                    }
                    return "RECOVERED";
                },
                5, Duration.ofMillis(1), "worker-crash-simulation");

        assertEquals("RECOVERED", result.value());
        assertEquals(3, result.totalAttempts());
        assertFalse(result.attempts().get(0).succeeded());
        assertFalse(result.attempts().get(1).succeeded());
        assertTrue(result.attempts().get(2).succeeded());
        assertTrue(result.attempts().get(0).rootCauseNote().contains("IllegalStateException"));
        assertTrue(result.attempts().get(0).rootCauseNote().contains("injected-transient-failure"));
    }

    @Test
    void failsClosedWithoutSwallowingWhenRecoveryBudgetIsExhausted() {
        AtomicInteger callCount = new AtomicInteger();
        RecoveryExhaustedException thrown = assertThrows(RecoveryExhaustedException.class, () ->
                BoundedRecoveryExecutor.execute(
                        () -> {
                            callCount.incrementAndGet();
                            throw new IllegalStateException("permanent-failure");
                        },
                        4, Duration.ofMillis(1), "permanent-provider-outage"));

        assertEquals(4, callCount.get());
        assertEquals(4, thrown.attempts().size());
        assertTrue(thrown.attempts().stream().noneMatch(BoundedRecoveryExecutor.AttemptRecord::succeeded));
        assertTrue(thrown.getCause() instanceof IllegalStateException);
        assertTrue(thrown.getMessage().contains("exhausted 4 attempts"));
    }

    @Test
    void rejectsInvalidBudgetParameters() {
        assertThrows(IllegalArgumentException.class, () ->
                BoundedRecoveryExecutor.execute(() -> "x", 0, Duration.ofMillis(1), "t"));
        assertThrows(IllegalArgumentException.class, () ->
                BoundedRecoveryExecutor.execute(() -> "x", 21, Duration.ofMillis(1), "t"));
        assertThrows(IllegalArgumentException.class, () ->
                BoundedRecoveryExecutor.execute(() -> "x", 3, Duration.ofMillis(-1), "t"));
    }

    @Test
    void interruptionPropagatesImmediatelyWithoutFurtherRetries() {
        AtomicInteger callCount = new AtomicInteger();
        assertThrows(InterruptedException.class, () ->
                BoundedRecoveryExecutor.execute(
                        () -> {
                            callCount.incrementAndGet();
                            throw new InterruptedException("interrupted-mid-operation");
                        },
                        5, Duration.ofMillis(1), "t"));
        assertEquals(1, callCount.get());
        assertTrue(Thread.interrupted());
    }
}
