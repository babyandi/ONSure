package io.onsure.platform.oruda;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Loads and fail-closed validates the 87-document ORUDA execution-package map. */
public final class OrudaExecutionPackageCatalog {
    public static final String CONTRACT = "ONSURE_ORUDA_EXECUTION_PACKAGE_MAP_V1";
    private static final Pattern LOOP_FILE = Pattern.compile("oruda_loop_(\\d{2})_.+\\.md");

    public record ExecutionPackage(
            String packageId,
            String name,
            String purpose,
            List<String> loopDocuments,
            List<String> supportingDocuments,
            List<String> requiredOutputs,
            String exitGate,
            boolean automaticFinalLock) {}

    public record Catalog(
            String authorityEntry,
            int totalDocuments,
            int loopDocuments,
            int supportingDocuments,
            String stopClass,
            String nextAction,
            String finalLock,
            List<ExecutionPackage> packages) {}

    private final ObjectMapper mapper = new ObjectMapper();

    public Catalog load(Path file) throws Exception {
        if (file == null || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("ORUDA_PACKAGE_CATALOG_MISSING");
        }
        JsonNode root = mapper.readTree(file.toFile());
        if (!CONTRACT.equals(root.path("contract").asText())) {
            throw new IllegalArgumentException("ORUDA_PACKAGE_CONTRACT_MISMATCH");
        }

        JsonNode sourceSet = root.path("source_set");
        JsonNode decision = root.path("current_decision");
        List<ExecutionPackage> packages = new ArrayList<>();
        for (JsonNode value : root.path("packages")) {
            packages.add(new ExecutionPackage(
                    requiredText(value, "package_id"),
                    requiredText(value, "name"),
                    requiredText(value, "purpose"),
                    textList(value.path("loop_documents")),
                    textList(value.path("supporting_documents")),
                    textList(value.path("required_outputs")),
                    requiredText(value, "exit_gate"),
                    value.path("automatic_final_lock").asBoolean(false)));
        }

        Catalog catalog = new Catalog(
                requiredText(root, "authority_entry"),
                sourceSet.path("total_documents").asInt(-1),
                sourceSet.path("loop_documents").asInt(-1),
                sourceSet.path("supporting_documents").asInt(-1),
                requiredText(decision, "stop_class"),
                requiredText(decision, "next_action"),
                requiredText(decision, "final_lock"),
                List.copyOf(packages));
        validate(catalog);
        return catalog;
    }

    public void validate(Catalog catalog) {
        if (catalog == null) throw new IllegalArgumentException("ORUDA_PACKAGE_CATALOG_NULL");
        if (catalog.packages().size() < 6 || catalog.packages().size() > 8) {
            throw new IllegalArgumentException("ORUDA_PACKAGE_COUNT_OUTSIDE_6_TO_8");
        }
        if (!"STOP_BLOCKED_BY_TRUE_EXHAUSTION_DECISION".equals(catalog.stopClass())
                || !"EXECUTION_REQUIRED_NEXT".equals(catalog.nextAction())
                || !"NOT_ALLOWED".equals(catalog.finalLock())) {
            throw new IllegalArgumentException("ORUDA_STOP_DECISION_MISMATCH");
        }

        Set<String> packageIds = new HashSet<>();
        Set<String> allDocuments = new LinkedHashSet<>();
        Set<String> loopDocuments = new LinkedHashSet<>();
        Set<String> supportDocuments = new LinkedHashSet<>();
        Set<Integer> loopNumbers = new HashSet<>();

        for (ExecutionPackage value : catalog.packages()) {
            if (!packageIds.add(value.packageId())) {
                throw new IllegalArgumentException("ORUDA_DUPLICATE_PACKAGE_ID:" + value.packageId());
            }
            if (value.requiredOutputs().isEmpty() || value.exitGate().isBlank()) {
                throw new IllegalArgumentException("ORUDA_INCOMPLETE_PACKAGE:" + value.packageId());
            }
            if (value.automaticFinalLock()) {
                throw new IllegalArgumentException("ORUDA_AUTOMATIC_FINAL_LOCK_FORBIDDEN:" + value.packageId());
            }
            for (String document : value.loopDocuments()) {
                if (!allDocuments.add(document)) {
                    throw new IllegalArgumentException("ORUDA_DOCUMENT_ASSIGNED_MULTIPLE_TIMES:" + document);
                }
                loopDocuments.add(document);
                Matcher matcher = LOOP_FILE.matcher(document);
                if (!matcher.matches()) {
                    throw new IllegalArgumentException("ORUDA_LOOP_FILENAME_INVALID:" + document);
                }
                int number = Integer.parseInt(matcher.group(1));
                if (!loopNumbers.add(number)) {
                    throw new IllegalArgumentException("ORUDA_DUPLICATE_LOOP_NUMBER:" + number);
                }
            }
            for (String document : value.supportingDocuments()) {
                if (!allDocuments.add(document)) {
                    throw new IllegalArgumentException("ORUDA_DOCUMENT_ASSIGNED_MULTIPLE_TIMES:" + document);
                }
                supportDocuments.add(document);
            }
        }

        if (loopDocuments.size() != catalog.loopDocuments()) {
            throw new IllegalArgumentException("ORUDA_LOOP_DOCUMENT_COUNT_MISMATCH");
        }
        if (supportDocuments.size() != catalog.supportingDocuments()) {
            throw new IllegalArgumentException("ORUDA_SUPPORT_DOCUMENT_COUNT_MISMATCH");
        }
        if (allDocuments.size() != catalog.totalDocuments()) {
            throw new IllegalArgumentException("ORUDA_TOTAL_DOCUMENT_COUNT_MISMATCH");
        }
        for (int number = 1; number <= 64; number++) {
            if (!loopNumbers.contains(number)) {
                throw new IllegalArgumentException("ORUDA_MISSING_LOOP_NUMBER:" + number);
            }
        }
        if (!supportDocuments.contains(catalog.authorityEntry())) {
            throw new IllegalArgumentException("ORUDA_AUTHORITY_ENTRY_NOT_ASSIGNED");
        }
    }

    private static String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ORUDA_REQUIRED_FIELD_MISSING:" + field);
        }
        return value;
    }

    private static List<String> textList(JsonNode array) {
        if (!array.isArray()) throw new IllegalArgumentException("ORUDA_ARRAY_REQUIRED");
        List<String> values = new ArrayList<>();
        for (JsonNode value : array) {
            String text = value.asText();
            if (text.isBlank()) throw new IllegalArgumentException("ORUDA_BLANK_DOCUMENT_ENTRY");
            values.add(text);
        }
        return List.copyOf(values);
    }
}
