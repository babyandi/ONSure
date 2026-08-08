package kr.co.oruda.onsure.platform;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Runs a child process while concurrently draining bounded output and enforcing wall-clock timeout. */
public final class BoundedProcessRunner {
    public static final int DEFAULT_MAX_OUTPUT_BYTES = 4 * 1024 * 1024;

    public record Result(int exitCode, String output, boolean outputTruncated) {}

    private BoundedProcessRunner() {}

    public static Result run(
            List<String> command,
            Path directory,
            Duration timeout,
            int maxOutputBytes,
            Map<String, String> allowedEnvironment,
            String authority) throws Exception {
        Objects.requireNonNull(command, "command");
        if (command.isEmpty()) throw new IllegalArgumentException("PROCESS_COMMAND_EMPTY");
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("PROCESS_TIMEOUT_INVALID");
        }
        if (maxOutputBytes < 1024) throw new IllegalArgumentException("PROCESS_OUTPUT_LIMIT_INVALID");
        String label = authority == null || authority.isBlank() ? "PROCESS" : authority;

        ProcessBuilder builder = new ProcessBuilder(List.copyOf(command)).redirectErrorStream(true);
        if (directory != null) builder.directory(directory.toAbsolutePath().normalize().toFile());
        Map<String, String> environment = builder.environment();
        environment.clear();
        if (allowedEnvironment != null) {
            for (Map.Entry<String, String> entry : new LinkedHashMap<>(allowedEnvironment).entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    environment.put(entry.getKey(), entry.getValue());
                }
            }
        }

        Process process = builder.start();
        ByteArrayOutputStream captured = new ByteArrayOutputStream(Math.min(maxOutputBytes, 64 * 1024));
        AtomicReference<Throwable> readerFailure = new AtomicReference<>();
        boolean[] truncated = new boolean[] {false};
        Thread reader = new Thread(() -> drain(
                process.getInputStream(), captured, maxOutputBytes, truncated, readerFailure),
                "onsure-process-output-drain");
        reader.setDaemon(true);
        reader.start();

        boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!completed) {
            process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            reader.join(5000);
            throw new IllegalStateException(label + "_COMMAND_TIMEOUT");
        }
        reader.join(5000);
        if (reader.isAlive()) {
            process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            throw new IllegalStateException(label + "_OUTPUT_DRAIN_TIMEOUT");
        }
        if (readerFailure.get() != null) {
            throw new IllegalStateException(label + "_OUTPUT_READ_FAILED", readerFailure.get());
        }
        String output = captured.toString(StandardCharsets.UTF_8);
        return new Result(process.exitValue(), output, truncated[0]);
    }

    public static Result run(
            List<String> command,
            Path directory,
            Duration timeout,
            Map<String, String> allowedEnvironment,
            String authority) throws Exception {
        return run(command, directory, timeout, DEFAULT_MAX_OUTPUT_BYTES, allowedEnvironment, authority);
    }

    private static void drain(
            InputStream input,
            ByteArrayOutputStream captured,
            int maxOutputBytes,
            boolean[] truncated,
            AtomicReference<Throwable> failure) {
        try (input) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                int remaining = maxOutputBytes - captured.size();
                if (remaining > 0) captured.write(buffer, 0, Math.min(remaining, read));
                if (read > remaining) truncated[0] = true;
            }
        } catch (Throwable error) {
            failure.set(error);
        }
    }
}
