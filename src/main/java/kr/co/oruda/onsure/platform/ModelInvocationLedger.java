package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import kr.co.oruda.onsure.assurance.ExclusiveFileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Append-only usage ledger of {@link ModelProviderAdapter.ModelInvocationRecord}-shaped entries.
 *
 * <p>Unlike {@link kr.co.oruda.onsure.learning.OfficialLearningLedger}, which hash-chains every
 * entry because it gates a much higher-stakes learning-promotion decision, this ledger is a plain
 * append-only JSON Lines file: one line per invocation plus a {@code recorded_at} timestamp. No
 * chaining, no promotion gate -- it exists purely to answer "which providers have been used and
 * how much" (token/cost visibility). Concurrent-safe appends reuse the same
 * {@link ExclusiveFileLock} mechanism the rest of this codebase uses for atomic file mutation.
 */
public final class ModelInvocationLedger {
    public static final String CONTRACT = "ONSURE_MODEL_INVOCATION_LEDGER_ENTRY_V1";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private ModelInvocationLedger() {}

    /** Per-provider (or overall) aggregate usage. */
    public record ProviderUsage(
            long invocationCount, long totalInputTokens, long totalOutputTokens, long totalCostMicros) {
        static final ProviderUsage ZERO = new ProviderUsage(0, 0, 0, 0);

        ProviderUsage plus(long inputTokens, long outputTokens, long costMicros) {
            return new ProviderUsage(
                    invocationCount + 1,
                    totalInputTokens + inputTokens,
                    totalOutputTokens + outputTokens,
                    totalCostMicros + costMicros);
        }
    }

    /** Ledger summary: per-provider aggregates plus an overall total across all providers. */
    public record Summary(Map<String, ProviderUsage> byProvider, ProviderUsage overall) {
        public Summary {
            byProvider = Map.copyOf(byProvider);
        }
    }

    /** Appends one entry to {@code ledgerFile}, creating parent directories as needed. */
    public static void record(
            Path ledgerFile, ModelProviderAdapter.ModelInvocationRecord invocation) throws Exception {
        if (ledgerFile == null) throw new IllegalArgumentException("LEDGER_FILE_REQUIRED");
        if (invocation == null) throw new IllegalArgumentException("MODEL_INVOCATION_RECORD_REQUIRED");
        Path normalized = ledgerFile.toAbsolutePath().normalize();
        Path lockFile = lockFileFor(normalized);
        Files.createDirectories(normalized.getParent());
        ExclusiveFileLock.run(lockFile, () -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("contract", CONTRACT);
            body.put("invocation_id", invocation.invocationId());
            body.put("provider_id", invocation.providerId());
            body.put("model_id", invocation.modelId());
            body.put("model_version", invocation.modelVersion());
            body.put("task_class", invocation.taskClass());
            body.put("configuration", new TreeMap<>(invocation.configuration()));
            body.put("input_token_count", invocation.inputTokenCount());
            body.put("output_token_count", invocation.outputTokenCount());
            body.put("cost_micros", invocation.costMicros());
            body.put("data_transfer_policy", invocation.dataTransferPolicy());
            body.put("output_digest", invocation.outputDigest());
            body.put("recorded_at", Instant.now().toString());
            String line = MAPPER.writeValueAsString(body);
            try (var writer = Files.newBufferedWriter(
                    normalized, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(line);
                writer.newLine();
            }
        });
    }

    /**
     * Reads every entry and groups by {@code providerId}. A missing or empty ledger file is the
     * normal "no usage yet" state and returns an all-zero summary rather than throwing.
     */
    public static Summary summarize(Path ledgerFile) throws Exception {
        if (ledgerFile == null) throw new IllegalArgumentException("LEDGER_FILE_REQUIRED");
        Path normalized = ledgerFile.toAbsolutePath().normalize();
        if (!Files.exists(normalized)) return new Summary(Map.of(), ProviderUsage.ZERO);
        return ExclusiveFileLock.call(lockFileFor(normalized), () -> summarizeUnderLock(normalized));
    }

    private static Summary summarizeUnderLock(Path ledgerFile) throws Exception {
        if (!Files.exists(ledgerFile)) return new Summary(Map.of(), ProviderUsage.ZERO);
        Map<String, ProviderUsage> byProvider = new LinkedHashMap<>();
        ProviderUsage overall = ProviderUsage.ZERO;
        for (String line : Files.readAllLines(ledgerFile, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            JsonNode node = MAPPER.readTree(line);
            String providerId = node.path("provider_id").asText("UNKNOWN_PROVIDER");
            long inputTokens = node.path("input_token_count").asLong(0);
            long outputTokens = node.path("output_token_count").asLong(0);
            long costMicros = node.path("cost_micros").asLong(0);
            ProviderUsage current = byProvider.getOrDefault(providerId, ProviderUsage.ZERO);
            byProvider.put(providerId, current.plus(inputTokens, outputTokens, costMicros));
            overall = overall.plus(inputTokens, outputTokens, costMicros);
        }
        return new Summary(byProvider, overall);
    }

    private static Path lockFileFor(Path ledgerFile) {
        return ledgerFile.resolveSibling(ledgerFile.getFileName() + ".lock");
    }
}
