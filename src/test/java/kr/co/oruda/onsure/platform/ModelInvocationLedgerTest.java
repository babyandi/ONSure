package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import kr.co.oruda.onsure.platform.ModelProviderAdapter.ModelInvocationRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real @TempDir-based tests for {@link ModelInvocationLedger}: no mocking, real file I/O. */
class ModelInvocationLedgerTest {
    @TempDir Path temp;

    @Test
    void missingLedgerFileReturnsAllZeroSummaryWithoutThrowing() throws Exception {
        Path ledger = temp.resolve("does-not-exist/ledger.jsonl");
        ModelInvocationLedger.Summary summary = assertDoesNotThrow(() -> ModelInvocationLedger.summarize(ledger));
        assertTrue(summary.byProvider().isEmpty());
        assertEquals(0, summary.overall().invocationCount());
        assertEquals(0, summary.overall().totalInputTokens());
        assertEquals(0, summary.overall().totalOutputTokens());
        assertEquals(0, summary.overall().totalCostMicros());
    }

    @Test
    void emptyLedgerFileReturnsAllZeroSummaryWithoutThrowing() throws Exception {
        Path ledger = temp.resolve("ledger.jsonl");
        Files.createDirectories(ledger.getParent());
        Files.writeString(ledger, "");
        ModelInvocationLedger.Summary summary = assertDoesNotThrow(() -> ModelInvocationLedger.summarize(ledger));
        assertTrue(summary.byProvider().isEmpty());
        assertEquals(0, summary.overall().invocationCount());
    }

    @Test
    void recordAppendsOneJsonLinePerInvocationAndSummarizeAggregatesPerProvider() throws Exception {
        Path ledger = temp.resolve("usage/ledger.jsonl");
        ModelInvocationLedger.record(ledger, invocation("inv-1", "provider-a", 100, 50, 10));
        ModelInvocationLedger.record(ledger, invocation("inv-2", "provider-a", 200, 75, 20));
        ModelInvocationLedger.record(ledger, invocation("inv-3", "provider-b", 30, 15, 5));

        List<String> lines = Files.readAllLines(ledger, StandardCharsets.UTF_8);
        assertEquals(3, lines.size());
        for (String line : lines) {
            assertTrue(line.contains(ModelInvocationLedger.CONTRACT));
            assertTrue(line.contains("\"recorded_at\""));
        }

        ModelInvocationLedger.Summary summary = ModelInvocationLedger.summarize(ledger);
        assertEquals(2, summary.byProvider().size());

        ModelInvocationLedger.ProviderUsage providerA = summary.byProvider().get("provider-a");
        assertEquals(2, providerA.invocationCount());
        assertEquals(300, providerA.totalInputTokens());
        assertEquals(125, providerA.totalOutputTokens());
        assertEquals(30, providerA.totalCostMicros());

        ModelInvocationLedger.ProviderUsage providerB = summary.byProvider().get("provider-b");
        assertEquals(1, providerB.invocationCount());
        assertEquals(30, providerB.totalInputTokens());
        assertEquals(15, providerB.totalOutputTokens());
        assertEquals(5, providerB.totalCostMicros());

        ModelInvocationLedger.ProviderUsage overall = summary.overall();
        assertEquals(3, overall.invocationCount());
        assertEquals(330, overall.totalInputTokens());
        assertEquals(140, overall.totalOutputTokens());
        assertEquals(35, overall.totalCostMicros());
    }

    @Test
    void concurrentRecordsFromMultipleThreadsDoNotCorruptTheFileAndSummarizeSeesEveryEntry() throws Exception {
        Path ledger = temp.resolve("concurrent/ledger.jsonl");
        int threadCount = 12;
        int writesPerThread = 15;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger sequence = new AtomicInteger();

        try {
            List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
            for (int thread = 0; thread < threadCount; thread++) {
                int providerIndex = thread % 3;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        for (int write = 0; write < writesPerThread; write++) {
                            int id = sequence.incrementAndGet();
                            ModelInvocationLedger.record(ledger, invocation(
                                    "inv-" + id, "provider-" + providerIndex, 10, 5, 1));
                        }
                    } catch (Exception failure) {
                        throw new RuntimeException(failure);
                    }
                }));
            }
            ready.await();
            start.countDown();
            for (var future : futures) future.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdown();
        }

        int totalWrites = threadCount * writesPerThread;
        List<String> lines = Files.readAllLines(ledger, StandardCharsets.UTF_8);
        assertEquals(totalWrites, lines.size(), "every concurrent append must land as exactly one intact line");
        for (String line : lines) {
            assertTrue(line.contains(ModelInvocationLedger.CONTRACT), "line must not be truncated/corrupted: " + line);
        }

        ModelInvocationLedger.Summary summary = ModelInvocationLedger.summarize(ledger);
        assertEquals(3, summary.byProvider().size());
        assertEquals(totalWrites, summary.overall().invocationCount());
        assertEquals(totalWrites * 10L, summary.overall().totalInputTokens());
        assertEquals(totalWrites * 5L, summary.overall().totalOutputTokens());
        assertEquals(totalWrites * 1L, summary.overall().totalCostMicros());
    }

    private static ModelInvocationRecord invocation(
            String invocationId, String providerId, int inputTokens, int outputTokens, long costMicros) {
        return new ModelInvocationRecord(
                invocationId, providerId, "fake-model-v1", "1.0", "REVIEW",
                Map.of("temperature", "0"), inputTokens, outputTokens, costMicros,
                "LOCAL_ONLY_NO_EGRESS", "sha256:fake-digest-" + invocationId);
    }
}
