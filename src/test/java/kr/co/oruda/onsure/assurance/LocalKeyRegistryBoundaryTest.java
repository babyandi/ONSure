package kr.co.oruda.onsure.assurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalKeyRegistryBoundaryTest {
    @TempDir Path temp;

    @Test
    void publicKeyOutsideAuthorityRootIsRejected() throws Exception {
        Path authority = temp.resolve("authority");
        Path registry = authority.resolve("trusted-key-registry.json");
        Path outside = temp.resolve("workspace/attacker.public");
        Files.createDirectories(outside.getParent());
        Files.writeString(outside, "attacker\n");
        ValidationResult result = new LocalKeyRegistry(registry).register(record("key-outside", outside));
        assertEquals(Decision.FAIL, result.decision());
        assertTrue(result.violations().contains("PUBLIC_KEY_OUTSIDE_AUTHORITY_ROOT"));
    }

    @Test
    void concurrentRegistryInstancesPreserveEveryKey() throws Exception {
        Path authority = temp.resolve("authority");
        Files.createDirectories(authority);
        Path firstKey = authority.resolve("first.public");
        Path secondKey = authority.resolve("second.public");
        Files.writeString(firstKey, "first\n");
        Files.writeString(secondKey, "second\n");
        Path registry = authority.resolve("trusted-key-registry.json");
        LocalKeyRegistry first = new LocalKeyRegistry(registry);
        LocalKeyRegistry second = new LocalKeyRegistry(registry);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<ValidationResult> firstResult = new AtomicReference<>();
        AtomicReference<ValidationResult> secondResult = new AtomicReference<>();
        Thread one = new Thread(() -> run(start, () -> firstResult.set(first.register(record("key-1", firstKey)))));
        Thread two = new Thread(() -> run(start, () -> secondResult.set(second.register(record("key-2", secondKey)))));
        one.start(); two.start(); start.countDown(); one.join(); two.join();
        assertEquals(Decision.PASS, firstResult.get().decision());
        assertEquals(Decision.PASS, secondResult.get().decision());
        assertEquals(2, new LocalKeyRegistry(registry).load().size());
    }

    private static LocalKeyRegistry.KeyRecord record(String id, Path key) {
        Instant now = Instant.now();
        return new LocalKeyRegistry.KeyRecord(id, ApprovalReceiptVerifier.AUTHORITY,
                key.toAbsolutePath().normalize().toString(),
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS), false, null);
    }

    private static void run(CountDownLatch start, ThrowingRunnable runnable) {
        try {
            start.await();
            runnable.run();
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }
}
