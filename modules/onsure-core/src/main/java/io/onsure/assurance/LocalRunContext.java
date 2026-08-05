package io.onsure.assurance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LocalRunContext {
    public static final String CONTRACT = "ONSURE_LOCAL_RUN_CONTEXT_V1";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Context(String runId, Instant startedAt) {}

    public ValidationResult verify(Path file) {
        List<String> violations = new ArrayList<>();
        if (!Files.isRegularFile(file)) return ValidationResult.fail(List.of("RUN_CONTEXT_MISSING"));
        try {
            JsonNode node = MAPPER.readTree(file.toFile());
            if (!CONTRACT.equals(node.path("contract").asText())) violations.add("RUN_CONTEXT_CONTRACT_MISMATCH");
            String runId = node.path("run_id").asText();
            if (!runId.matches("[A-Za-z0-9._:-]{8,128}")) violations.add("RUN_CONTEXT_ID_INVALID");
            try { Instant.parse(node.path("started_at").asText()); }
            catch (Exception e) { violations.add("RUN_CONTEXT_STARTED_AT_INVALID"); }
        } catch (Exception e) {
            violations.add("RUN_CONTEXT_UNREADABLE");
        }
        return violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations);
    }

    public static Context read(Path file) throws Exception {
        ValidationResult result = new LocalRunContext().verify(file);
        if (result.decision() != Decision.PASS) throw new IllegalArgumentException(result.violations().toString());
        JsonNode node = MAPPER.readTree(file.toFile());
        return new Context(node.path("run_id").asText(), Instant.parse(node.path("started_at").asText()));
    }

    public static void write(Path file, String runId, Instant startedAt) throws Exception {
        if (runId == null || !runId.matches("[A-Za-z0-9._:-]{8,128}") || startedAt == null) {
            throw new IllegalArgumentException("invalid run context");
        }
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("contract", CONTRACT);
        value.put("run_id", runId);
        value.put("started_at", startedAt.toString());
        Files.createDirectories(file.toAbsolutePath().normalize().getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), value);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
