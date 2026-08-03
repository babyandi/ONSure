package io.onsure.gateway.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Append-only digest-chain ledger containing usage metadata but never prompt or completion text. */
public final class LlmEvidenceLedger {
    public static final String CONTRACT = "ONSURE_LLM_EVIDENCE_LEDGER_V1";
    private static final String ZERO_DIGEST = "0".repeat(64);
    private static final long MAXIMUM_LEDGER_BYTES = 64L * 1024L * 1024L;
    private static final Set<String> OBSERVATION_FIELDS = Set.of(
            "request_id", "provider_id", "model_id", "outcome", "failure_code", "retryable",
            "request_sha256", "response_sha256", "input_tokens", "output_tokens",
            "estimated_cost_micros", "actual_cost_micros", "duration_millis", "observed_at",
            "provider_evidence", "prompt_or_completion_content_recorded", "final_claim_allowed");
    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public LlmEvidenceLedger(Path evidenceRoot) throws IOException {
        Path root = evidenceRoot.toAbsolutePath().normalize();
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(root)) {
            throw new IllegalArgumentException("LLM_EVIDENCE_ROOT_SYMLINK");
        }
        Files.createDirectories(root);
        this.file = root.resolve("llm-gateway-evidence.jsonl");
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS) &&
                (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file))) {
            throw new IllegalArgumentException("LLM_EVIDENCE_LEDGER_INVALID");
        }
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) Files.createFile(file);
        try {
            Files.setPosixFilePermissions(file, Set.of(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX test hosts still retain process umask and regular-file validation.
        }
        verifyAndSummarize();
    }

    public synchronized Map<String, Object> append(Map<String, Object> observation) throws IOException {
        if (Files.size(file) > MAXIMUM_LEDGER_BYTES) throw new IOException("LLM_EVIDENCE_LEDGER_SIZE_LIMIT");
        validateObservation(observation);
        try (FileChannel channel = FileChannel.open(file,
                StandardOpenOption.READ, StandardOpenOption.WRITE);
             var ignored = channel.lock()) {
            Summary current = readAndVerify();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("contract", CONTRACT);
            entry.put("sequence", current.requestCount() + 1L);
            entry.put("previous_entry_sha256", current.chainHead());
            entry.putAll(observation);
            String digest = sha256(mapper.writeValueAsBytes(entry));
            entry.put("entry_sha256", digest);
            byte[] line = (mapper.writeValueAsString(entry) + "\n").getBytes(StandardCharsets.UTF_8);
            channel.position(channel.size());
            channel.write(ByteBuffer.wrap(line));
            channel.force(true);
            return Map.copyOf(entry);
        }
    }

    public synchronized Map<String, Object> metrics() throws IOException {
        Summary value = verifyAndSummarize();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contract", "ONSURE_LLM_MONITORING_V1");
        result.put("request_count", value.requestCount());
        result.put("success_count", value.successCount());
        result.put("failure_count", value.failureCount());
        result.put("input_tokens", value.inputTokens());
        result.put("output_tokens", value.outputTokens());
        result.put("total_tokens", Math.addExact(value.inputTokens(), value.outputTokens()));
        result.put("estimated_cost_micros", value.estimatedCostMicros());
        result.put("actual_cost_micros", value.actualCostMicros());
        result.put("total_duration_millis", value.totalDurationMillis());
        result.put("chain_valid", true);
        result.put("chain_head_sha256", value.chainHead());
        result.put("last_observed_at", value.lastObservedAt());
        result.put("prompt_or_completion_content_recorded", false);
        result.put("final_claim_allowed", false);
        return result;
    }

    public Path file() { return file; }

    private Summary verifyAndSummarize() throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ);
             var ignored = channel.lock(0L, Long.MAX_VALUE, true)) {
            return readAndVerify();
        }
    }

    private Summary readAndVerify() throws IOException {
        if (Files.size(file) > MAXIMUM_LEDGER_BYTES) throw new IOException("LLM_EVIDENCE_LEDGER_SIZE_LIMIT");
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        String previous = ZERO_DIGEST;
        long success = 0, failure = 0, input = 0, output = 0, estimated = 0, actual = 0, duration = 0;
        String lastObservedAt = "NOT_RUN";
        long sequence = 0;
        for (String line : lines) {
            if (line.isBlank()) throw new IOException("LLM_EVIDENCE_LEDGER_BLANK_LINE");
            Map<String, Object> entry = mapper.readValue(line, new TypeReference<>() {});
            sequence++;
            if (!CONTRACT.equals(entry.get("contract"))
                    || number(entry, "sequence") != sequence
                    || !previous.equals(entry.get("previous_entry_sha256"))) {
                throw new IOException("LLM_EVIDENCE_LEDGER_CHAIN_INVALID");
            }
            String claimed = text(entry, "entry_sha256");
            Map<String, Object> unsigned = new LinkedHashMap<>(entry);
            unsigned.remove("entry_sha256");
            if (!claimed.equals(sha256(mapper.writeValueAsBytes(unsigned)))) {
                throw new IOException("LLM_EVIDENCE_LEDGER_DIGEST_INVALID");
            }
            previous = claimed;
            boolean passed = "SUCCESS".equals(entry.get("outcome"));
            success += passed ? 1 : 0;
            failure += passed ? 0 : 1;
            input = Math.addExact(input, number(entry, "input_tokens"));
            output = Math.addExact(output, number(entry, "output_tokens"));
            estimated = Math.addExact(estimated, number(entry, "estimated_cost_micros"));
            actual = Math.addExact(actual, number(entry, "actual_cost_micros"));
            duration = Math.addExact(duration, number(entry, "duration_millis"));
            lastObservedAt = text(entry, "observed_at");
            if (containsProhibitedKey(entry)) {
                throw new IOException("LLM_EVIDENCE_CONTAINS_CONTENT_OR_SECRET");
            }
        }
        return new Summary(sequence, success, failure, input, output, estimated, actual,
                duration, previous, lastObservedAt);
    }

    private static long number(Map<String, Object> value, String field) throws IOException {
        Object item = value.get(field);
        if (!(item instanceof Number number) || number.longValue() < 0) throw new IOException("LLM_EVIDENCE_NUMBER:" + field);
        return number.longValue();
    }

    private static String text(Map<String, Object> value, String field) throws IOException {
        Object item = value.get(field);
        if (!(item instanceof String text) || text.isBlank()) throw new IOException("LLM_EVIDENCE_TEXT:" + field);
        return text;
    }

    static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static Map<String, Object> observation(
            String requestId, String providerId, String modelId, String outcome,
            String failureCode, boolean retryable, String requestSha256, String responseSha256,
            long inputTokens, long outputTokens, long estimatedCostMicros, long actualCostMicros,
            long durationMillis, Instant observedAt, Map<String, String> providerEvidence) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("request_id", requestId);
        value.put("provider_id", providerId);
        value.put("model_id", modelId);
        value.put("outcome", outcome);
        value.put("failure_code", failureCode);
        value.put("retryable", retryable);
        value.put("request_sha256", requestSha256);
        value.put("response_sha256", responseSha256);
        value.put("input_tokens", inputTokens);
        value.put("output_tokens", outputTokens);
        value.put("estimated_cost_micros", estimatedCostMicros);
        value.put("actual_cost_micros", actualCostMicros);
        value.put("duration_millis", durationMillis);
        value.put("observed_at", observedAt.toString());
        Map<String, String> safeProviderEvidence = Map.copyOf(
                providerEvidence == null ? Map.of() : providerEvidence);
        if (containsProhibitedKey(safeProviderEvidence)) {
            throw new IllegalArgumentException("LLM_PROVIDER_EVIDENCE_CONTAINS_CONTENT_OR_SECRET");
        }
        value.put("provider_evidence", safeProviderEvidence);
        value.put("prompt_or_completion_content_recorded", false);
        value.put("final_claim_allowed", false);
        return value;
    }

    private static boolean containsProhibitedKey(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey()).toLowerCase(java.util.Locale.ROOT);
                if (Set.of("prompt", "messages", "content", "completion", "api_key", "token", "password")
                        .contains(key) || containsProhibitedKey(entry.getValue())) return true;
            }
        } else if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) if (containsProhibitedKey(item)) return true;
        }
        return false;
    }

    private static void validateObservation(Map<String, Object> value) throws IOException {
        if (value == null || !value.keySet().equals(OBSERVATION_FIELDS)
                || containsProhibitedKey(value)) {
            throw new IOException("LLM_EVIDENCE_OBSERVATION_FIELDS_INVALID");
        }
        for (String field : List.of(
                "request_id", "provider_id", "model_id", "failure_code", "observed_at")) {
            text(value, field);
        }
        String outcome = text(value, "outcome");
        if (!Set.of("SUCCESS", "FAILURE").contains(outcome)) {
            throw new IOException("LLM_EVIDENCE_OUTCOME_INVALID");
        }
        for (String field : List.of(
                "input_tokens", "output_tokens", "estimated_cost_micros",
                "actual_cost_micros", "duration_millis")) {
            number(value, field);
        }
        for (String field : List.of("request_sha256", "response_sha256")) {
            if (!text(value, field).matches("[0-9a-f]{64}")) {
                throw new IOException("LLM_EVIDENCE_DIGEST_INVALID:" + field);
            }
        }
        if (!(value.get("retryable") instanceof Boolean)
                || !Boolean.FALSE.equals(value.get("prompt_or_completion_content_recorded"))
                || !Boolean.FALSE.equals(value.get("final_claim_allowed"))
                || !(value.get("provider_evidence") instanceof Map<?, ?>)) {
            throw new IOException("LLM_EVIDENCE_OBSERVATION_TYPE_INVALID");
        }
    }

    private record Summary(long requestCount, long successCount, long failureCount,
                           long inputTokens, long outputTokens, long estimatedCostMicros,
                           long actualCostMicros, long totalDurationMillis, String chainHead,
                           String lastObservedAt) {}
}
