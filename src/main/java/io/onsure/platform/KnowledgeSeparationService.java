package io.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Separates raw project memory from deliberately minimal, de-identified reusable patterns. */
public final class KnowledgeSeparationService {
    public static final String PROJECT_CONTRACT = "ONSURE_PROJECT_KNOWLEDGE_RECORD_V1";
    public static final String REUSABLE_CONTRACT = "ONSURE_REUSABLE_PATTERN_MEMORY_V1";
    private static final Set<String> SUPPORTED_SOURCE_CONTRACTS = Set.of(
            "ONSURE_FAILURE_MEMORY_V1", "ONSURE_IMPROVEMENT_MEMORY_V1");

    public record IndependentReproduction(String projectId, String receiptId, boolean passed) {
        public IndependentReproduction {
            requireText(projectId, "projectId");
            requireText(receiptId, "receiptId");
        }
    }

    public record SeparationResult(
            Path projectRecordFile,
            Path reusablePatternFile,
            String decision,
            List<String> violations) {
        public SeparationResult {
            projectRecordFile = projectRecordFile.toAbsolutePath().normalize();
            reusablePatternFile = reusablePatternFile == null
                    ? null : reusablePatternFile.toAbsolutePath().normalize();
            violations = List.copyOf(violations);
        }
    }

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final SecureRandom random = new SecureRandom();
    private final Path projectRoot;
    private final Path reusableRoot;

    public KnowledgeSeparationService(Path memoryRoot) {
        Path root = normalize(memoryRoot, "memoryRoot");
        requireNoSymlink(root, "KNOWLEDGE_MEMORY_ROOT_SYMLINK_PROHIBITED");
        this.projectRoot = root.resolve("project-memory").normalize();
        this.reusableRoot = root.resolve("reusable-pattern-memory").normalize();
        if (projectRoot.startsWith(reusableRoot) || reusableRoot.startsWith(projectRoot)) {
            throw new IllegalArgumentException("KNOWLEDGE_MEMORY_ROOTS_NOT_SEPARATE");
        }
    }

    public SeparationResult separate(
            Path sourceMemoryFile,
            List<IndependentReproduction> reproductions,
            boolean rightsReviewApproved,
            boolean privacyReviewApproved) throws Exception {
        Path source = normalize(sourceMemoryFile, "sourceMemoryFile");
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(source)) {
            throw new IllegalArgumentException("KNOWLEDGE_SOURCE_MEMORY_INVALID");
        }
        JsonNode memory = mapper.readTree(source.toFile());
        if (memory == null || !memory.isObject()
                || !SUPPORTED_SOURCE_CONTRACTS.contains(memory.path("contract").asText())) {
            throw new IllegalArgumentException("KNOWLEDGE_SOURCE_CONTRACT_UNSUPPORTED");
        }
        String memoryId = required(memory, "memory_id");
        String projectId = memory.hasNonNull("program_id")
                ? required(memory, "program_id") : firstRequired(memory, "applicability");
        byte[] salt = new byte[32];
        random.nextBytes(salt);
        String commitment = sha256(concat(salt, mapper.writeValueAsBytes(memory)));
        String projectKey = sha256(projectId).substring(0, 24);
        String recordId = "PROJECT-KNOWLEDGE-"
                + sha256(memoryId + ":" + commitment).substring(0, 24);
        Path projectFile = projectRoot.resolve(projectKey).resolve(recordId + ".json");

        List<IndependentReproduction> safeReproductions = reproductions == null
                ? List.of() : List.copyOf(reproductions);
        Set<String> independentProjects = new LinkedHashSet<>();
        Set<String> independentReceipts = new LinkedHashSet<>();
        for (IndependentReproduction reproduction : safeReproductions) {
            if (reproduction.passed()) {
                independentProjects.add(reproduction.projectId());
                independentReceipts.add(reproduction.receiptId());
            }
        }
        List<String> violations = new ArrayList<>();
        if (independentProjects.size() < 2) violations.add("REUSABLE_PATTERN_INDEPENDENT_REPRODUCTIONS_INSUFFICIENT");
        if (independentReceipts.size() < 2) violations.add("REUSABLE_PATTERN_DISTINCT_RECEIPTS_INSUFFICIENT");
        if (!rightsReviewApproved) violations.add("REUSABLE_PATTERN_RIGHTS_REVIEW_REQUIRED");
        if (!privacyReviewApproved) violations.add("REUSABLE_PATTERN_PRIVACY_REVIEW_REQUIRED");

        Map<String, Object> projectRecord = new LinkedHashMap<>();
        projectRecord.put("contract", PROJECT_CONTRACT);
        projectRecord.put("record_id", recordId);
        projectRecord.put("project_id", projectId);
        projectRecord.put("source_memory_id", memoryId);
        projectRecord.put("source_memory_sha256", Hashing.file(source));
        projectRecord.put("source_memory", mapper.convertValue(memory, Map.class));
        projectRecord.put("deidentification_salt", Base64.getEncoder().encodeToString(salt));
        projectRecord.put("lineage_commitment_sha256", commitment);
        projectRecord.put("cross_project_retrieval_allowed", false);
        projectRecord.put("reusable_candidate_decision", violations.isEmpty() ? "CANDIDATE" : "HOLD");
        projectRecord.put("created_at", Instant.now().toString());
        projectRecord.put("final_claim_allowed", false);
        writeAtomic(projectFile, projectRecord);

        if (!violations.isEmpty()) {
            return new SeparationResult(projectFile, null, "HOLD", violations);
        }

        String failureClass = classify(memory);
        String patternId = "PATTERN-" + sha256(failureClass + ":" + commitment).substring(0, 24);
        Map<String, Object> reusable = new LinkedHashMap<>();
        reusable.put("contract", REUSABLE_CONTRACT);
        reusable.put("pattern_id", patternId);
        reusable.put("pattern_class", failureClass);
        reusable.put("trigger_class", triggerClass(memory));
        reusable.put("remediation_class", remediationClass(memory));
        reusable.put("independent_reproduction_count", independentProjects.size());
        reusable.put("lineage_commitment_sha256", commitment);
        reusable.put("deidentification", Map.of(
                "strategy", "ALLOWLISTED_TAXONOMY_ONLY",
                "raw_text_copied", false,
                "project_identifiers_copied", false,
                "evidence_identifiers_copied", false,
                "privacy_review", "PASS",
                "rights_review", "PASS"));
        reusable.put("state", "REUSABLE_CANDIDATE");
        reusable.put("activation_allowed", false);
        reusable.put("created_at", Instant.now().toString());
        reusable.put("final_claim_allowed", false);
        assertDeidentified(reusable, memory);
        Path reusableFile = reusableRoot.resolve(patternId + ".json");
        writeAtomic(reusableFile, reusable);
        return new SeparationResult(projectFile, reusableFile, "REUSABLE_CANDIDATE", List.of());
    }

    private String classify(JsonNode memory) {
        String text = combined(memory, "first_failure_point", "direct_cause", "root_cause", "decision");
        if (containsAny(text, "authoriz", "permission", "access", "deny")) return "AUTHORIZATION_POLICY_GAP";
        if (containsAny(text, "inject", "untrusted", "prompt")) return "UNTRUSTED_INPUT_CONTROL_GAP";
        if (containsAny(text, "timeout", "latency", "unavailable")) return "AVAILABILITY_BOUNDARY_FAILURE";
        if (containsAny(text, "regression")) return "REGRESSION_CONTROL_GAP";
        return "BEHAVIORAL_CONTRACT_DEVIATION";
    }

    private String triggerClass(JsonNode memory) {
        String text = combined(memory, "first_failure_point", "reproduction");
        if (containsAny(text, "fixture", "scenario")) return "CONTROLLED_SCENARIO_EXECUTION";
        if (containsAny(text, "build", "compile")) return "BUILD_OR_COMPILE_EXECUTION";
        return "VALIDATION_EXECUTION";
    }

    private String remediationClass(JsonNode memory) {
        String text = combined(memory, "decision", "prohibitions", "root_cause");
        if (containsAny(text, "authorization", "deny", "permission")) return "POLICY_ENFORCEMENT_AND_NEGATIVE_REGRESSION";
        if (containsAny(text, "regression")) return "REGRESSION_COVERAGE_STRENGTHENING";
        return "CONTROL_AND_REGRESSION_STRENGTHENING";
    }

    private void assertDeidentified(Map<String, Object> reusable, JsonNode source) throws Exception {
        String output = mapper.writeValueAsString(reusable);
        for (String field : List.of("memory_id", "program_id", "finding_id")) {
            String sensitive = source.path(field).asText("");
            if (!sensitive.isBlank() && output.contains(sensitive)) {
                throw new IllegalStateException("REUSABLE_PATTERN_IDENTIFIER_LEAK:" + field);
            }
        }
        for (JsonNode evidence : source.path("evidence_refs")) {
            if (!evidence.asText("").isBlank() && output.contains(evidence.asText())) {
                throw new IllegalStateException("REUSABLE_PATTERN_EVIDENCE_IDENTIFIER_LEAK");
            }
        }
    }

    private String combined(JsonNode memory, String... fields) {
        StringBuilder value = new StringBuilder();
        for (String field : fields) value.append(' ').append(memory.path(field).toString());
        return value.toString().toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }

    private static String required(JsonNode value, String field) {
        String result = value.path(field).asText("");
        if (result.isBlank()) throw new IllegalArgumentException("KNOWLEDGE_SOURCE_FIELD_MISSING:" + field);
        return result;
    }

    private static String firstRequired(JsonNode value, String field) {
        JsonNode values = value.path(field);
        if (!values.isArray() || values.isEmpty() || values.get(0).asText("").isBlank()) {
            throw new IllegalArgumentException("KNOWLEDGE_SOURCE_FIELD_MISSING:" + field);
        }
        return values.get(0).asText();
    }

    private void writeAtomic(Path file, Map<String, Object> value) throws Exception {
        requireNoSymlink(file, "KNOWLEDGE_OUTPUT_SYMLINK_PROHIBITED");
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        mapper.writeValue(temporary.toFile(), value);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] result = new byte[left.length + right.length];
        System.arraycopy(left, 0, result, 0, left.length);
        System.arraycopy(right, 0, result, left.length, right.length);
        return result;
    }

    private static Path normalize(Path value, String name) {
        return Objects.requireNonNull(value, name).toAbsolutePath().normalize();
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required");
    }

    private static void requireNoSymlink(Path path, String code) {
        for (Path current = path; current != null; current = current.getParent()) {
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException(code);
            }
        }
    }

    private static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception failure) {
            throw new IllegalStateException("KNOWLEDGE_HASH_FAILED", failure);
        }
    }
}
