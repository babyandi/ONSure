package kr.co.oruda.onsure.assurance;

import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/** Combines a JVM-local mutex with an operating-system file lock for safe file mutations. */
public final class ExclusiveFileLock {
    private static final ConcurrentMap<Path, ReentrantLock> JVM_LOCKS = new ConcurrentHashMap<>();

    private ExclusiveFileLock() {}

    public static <T> T call(Path lockFile, CheckedSupplier<T> action) throws Exception {
        Path normalized = lockFile.toAbsolutePath().normalize();
        Files.createDirectories(normalized.getParent());
        ReentrantLock jvmLock = JVM_LOCKS.computeIfAbsent(normalized, ignored -> new ReentrantLock(true));
        jvmLock.lockInterruptibly();
        try (FileChannel channel = FileChannel.open(normalized,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var ignored = channel.lock()) {
            return action.get();
        } finally {
            jvmLock.unlock();
        }
    }

    public static void run(Path lockFile, CheckedRunnable action) throws Exception {
        call(lockFile, () -> {
            action.run();
            return null;
        });
    }

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    public interface CheckedRunnable {
        void run() throws Exception;
    }
}
