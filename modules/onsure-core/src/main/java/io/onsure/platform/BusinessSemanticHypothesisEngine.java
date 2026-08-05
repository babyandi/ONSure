package io.onsure.platform;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Produces review-only business-semantic hypotheses from static program-understanding evidence.
 *
 * <p>This engine deliberately does not call an LLM or a live provider and never promotes a
 * hypothesis to an executable plan or scoring evidence.</p>
 */
final class BusinessSemanticHypothesisEngine {
    static final String CONTRACT = "ONSURE_BUSINESS_SEMANTIC_HYPOTHESES_V1";
    private static final Set<String> KNOWN_ACTIONS = Set.of(
            "CREATE", "READ", "LIST", "SEARCH", "UPDATE", "DELETE", "VALIDATE", "PROCESS", "EXPORT");

    private BusinessSemanticHypothesisEngine() {}

    static Map<String, Object> infer(
            Map<String, Object> programUnderstanding, List<Map<String, Object>> detectedComponents) {
        if (programUnderstanding == null
                || !ProgramUnderstandingEngine.CONTRACT.equals(programUnderstanding.get("contract"))) {
            throw new IllegalArgumentException("PROGRAM_UNDERSTANDING_CONTRACT_INVALID");
        }
        String sourceSha256 = String.valueOf(programUnderstanding.get("source_sha256"));
        if (!sourceSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("PROGRAM_UNDERSTANDING_SOURCE_DIGEST_INVALID");
        }

        List<Map<String, Object>> components = detectedComponents == null ? List.of()
                : detectedComponents.stream().filter(Map.class::isInstance)
                        .sorted(Comparator.comparing(BusinessSemanticHypothesisEngine::componentSortKey))
                        .toList();
        List<Map<String, Object>> hypotheses = new ArrayList<>();
        for (Map<String, Object> flow : mapList(programUnderstanding.get("flow_candidates"))) {
            hypotheses.add(capabilityHypothesis(flow, components, sourceSha256));
        }
        for (Map<String, Object> lifecycle : mapList(programUnderstanding.get("api_lifecycle_candidates"))) {
            hypotheses.add(workflowHypothesis(lifecycle, programUnderstanding, sourceSha256));
        }
        hypotheses.sort(Comparator.comparing(value -> value.get("hypothesis_id").toString()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contract", CONTRACT);
        result.put("source_sha256", sourceSha256);
        result.put("inference_method", "DETERMINISTIC_EVIDENCE_BOUND_SEMANTIC_HYPOTHESES_V1");
        result.put("hypothesis_count", hypotheses.size());
        result.put("hypotheses", List.copyOf(hypotheses));
        result.put("unknown_count", hypotheses.stream()
                .filter(item -> "UNKNOWN".equals(item.get("semantic_state"))).count());
        result.put("ambiguous_count", hypotheses.stream()
                .filter(item -> !"LOW".equals(item.get("ambiguity"))).count());
        result.put("review_required", true);
        result.put("automatic_execution", "NOT_RUN_REVIEW_REQUIRED");
        result.put("live_provider_invocation", "NOT_RUN");
        result.put("customer_rules_confirmed", false);
        result.put("runtime_verification", "NOT_RUN");
        result.put("score_eligible", false);
        result.put("final_claim_allowed", false);
        return Map.copyOf(result);
    }

    private static Map<String, Object> capabilityHypothesis(
            Map<String, Object> flow, List<Map<String, Object>> components, String sourceSha256) {
        String flowId = text(flow, "flow_id", "UNKNOWN_FLOW");
        String object = normalizedMeaning(text(flow, "inferred_business_object", ""));
        @SuppressWarnings("unchecked")
        Map<String, Object> operation = flow.get("operation") instanceof Map<?, ?> value
                ? (Map<String, Object>) value : Map.of();
        String action = action(operation);
        String evidenceDigest = digest(flow.get("evidence_sha256"), sourceSha256);
        List<Map<String, Object>> evidence = new ArrayList<>();
        addEvidence(evidence, "SOURCE_OBSERVATION", "flow_candidates/" + flowId, flowId, evidenceDigest);
        addOperationEvidence(evidence, operation, flowId, evidenceDigest);
        List<Map<String, Object>> matchingComponents = matchingComponents(components, object);
        for (Map<String, Object> component : matchingComponents) {
            addEvidence(evidence, "DETECTED_COMPONENT",
                    "components/" + text(component, "id", "UNKNOWN_COMPONENT"),
                    text(component, "name", text(component, "kind", "UNKNOWN")), sourceSha256);
        }

        boolean known = !object.startsWith("UNKNOWN_") && KNOWN_ACTIONS.contains(action)
                && hasOperationSemanticEvidence(operation);
        double confidence = known ? confidence(operation, matchingComponents, action, object) : 0.0;
        String label = known ? action + "_" + object : "UNKNOWN";
        return hypothesis(
                "CAPABILITY", flowId, label, known ? object : "UNKNOWN", known ? action : "UNKNOWN",
                evidence, confidence, known ? ambiguity(confidence, operation) : "HIGH", known);
    }

    private static Map<String, Object> workflowHypothesis(
            Map<String, Object> lifecycle, Map<String, Object> understanding, String sourceSha256) {
        String lifecycleId = text(lifecycle, "lifecycle_id", "UNKNOWN_LIFECYCLE");
        String object = normalizedMeaning(text(lifecycle, "business_object", ""));
        List<String> actions = strings(lifecycle.get("actions")).stream().filter(KNOWN_ACTIONS::contains).toList();
        List<Map<String, Object>> evidence = new ArrayList<>();
        Map<String, Map<String, Object>> flows = new LinkedHashMap<>();
        for (Map<String, Object> flow : mapList(understanding.get("flow_candidates"))) {
            flows.put(text(flow, "flow_id", "UNKNOWN_FLOW"), flow);
        }
        for (Map<String, Object> operation : mapList(lifecycle.get("operations"))) {
            String flowId = text(operation, "flow_id", "UNKNOWN_FLOW");
            Map<String, Object> flow = flows.get(flowId);
            addEvidence(evidence, "SOURCE_OBSERVATION", "flow_candidates/" + flowId, flowId,
                    flow == null ? sourceSha256 : digest(flow.get("evidence_sha256"), sourceSha256));
        }
        if (evidence.isEmpty()) {
            addEvidence(evidence, "SOURCE_OBSERVATION", "api_lifecycle_candidates/" + lifecycleId,
                    lifecycleId, sourceSha256);
        }
        boolean known = !object.startsWith("UNKNOWN_") && actions.size() >= 2;
        double confidence = known ? round(Math.min(0.86, 0.66 + actions.size() * 0.04)) : 0.0;
        return hypothesis(
                "WORKFLOW", lifecycleId, known ? object + "_LIFECYCLE" : "UNKNOWN",
                known ? object : "UNKNOWN", known ? "LIFECYCLE" : "UNKNOWN", evidence,
                confidence, known ? (actions.size() >= 4 ? "LOW" : "MEDIUM") : "HIGH", known);
    }

    private static Map<String, Object> hypothesis(String kind, String sourceId, String label,
            String object, String action, List<Map<String, Object>> evidence, double confidence,
            String ambiguity, boolean known) {
        String material = kind + "\u0000" + sourceId + "\u0000" + label;
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("hypothesis_id", "SEM-" + Hashing.sha256(material).substring(0, 16));
        value.put("hypothesis_kind", kind);
        value.put("semantic_label", label);
        value.put("business_object", object);
        value.put("action", action);
        value.put("evidence_references", List.copyOf(evidence));
        value.put("confidence", confidence);
        value.put("ambiguity", ambiguity);
        value.put("semantic_state", known ? "INFERRED_REVIEW_REQUIRED" : "UNKNOWN");
        value.put("review_required", true);
        value.put("auto_execute", false);
        value.put("customer_rule_confirmed", false);
        value.put("runtime_verified", false);
        value.put("score_eligible", false);
        return Map.copyOf(value);
    }

    private static void addOperationEvidence(List<Map<String, Object>> evidence,
            Map<String, Object> operation, String flowId, String digest) {
        addIfPresent(evidence, "OPENAPI_OPERATION_ID", flowId + "/operation/operation_id",
                operation.get("operation_id"), digest);
        for (String tag : strings(operation.get("tags"))) {
            addEvidence(evidence, "OPENAPI_TAG", flowId + "/operation/tags", tag, digest);
        }
        addIfPresent(evidence, "OPENAPI_PATH", flowId + "/operation/http_path",
                operation.get("http_path"), digest);
        for (String schema : strings(operation.get("request_schema_refs"))) {
            addEvidence(evidence, "OPENAPI_SCHEMA_REFERENCE",
                    flowId + "/operation/request_schema_refs", schema, digest);
        }
    }

    private static void addIfPresent(List<Map<String, Object>> evidence, String type,
            String reference, Object value, String digest) {
        if (value != null && !value.toString().isBlank()) {
            addEvidence(evidence, type, reference, value.toString(), digest);
        }
    }

    private static void addEvidence(List<Map<String, Object>> evidence, String type,
            String reference, String observedValue, String digest) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("evidence_type", type);
        item.put("reference", reference);
        item.put("observed_value", observedValue);
        item.put("evidence_sha256", digest);
        evidence.add(Map.copyOf(item));
    }

    private static boolean hasOperationSemanticEvidence(Map<String, Object> operation) {
        return operation.containsKey("operation_id") || !strings(operation.get("tags")).isEmpty()
                || operation.containsKey("http_path") || !strings(operation.get("request_schema_refs")).isEmpty();
    }

    private static String action(Map<String, Object> operation) {
        String lifecycle = text(operation, "lifecycle_action", "").toUpperCase(Locale.ROOT);
        if (KNOWN_ACTIONS.contains(lifecycle)) return lifecycle;
        String operationId = text(operation, "operation_id", "").toLowerCase(Locale.ROOT);
        if (operationId.matches("^(create|add|register|submit).*")) return "CREATE";
        if (operationId.matches("^(get|read|fetch|find).*")) return "READ";
        if (operationId.matches("^(list|browse).*")) return "LIST";
        if (operationId.matches("^(search|query).*")) return "SEARCH";
        if (operationId.matches("^(update|edit|patch|change).*")) return "UPDATE";
        if (operationId.matches("^(delete|remove|revoke).*")) return "DELETE";
        if (operationId.matches("^(validate|verify|check|test).*")) return "VALIDATE";
        if (operationId.matches("^(process|execute|run|generate|render).*")) return "PROCESS";
        if (operationId.matches("^(export|download).*")) return "EXPORT";
        return "UNKNOWN";
    }

    private static double confidence(Map<String, Object> operation,
            List<Map<String, Object>> components, String action, String object) {
        double score = 0.35;
        if (operation.containsKey("operation_id")) score += 0.15;
        if (!strings(operation.get("tags")).isEmpty()) score += 0.15;
        if (operation.containsKey("http_path")) score += 0.10;
        if (!strings(operation.get("request_schema_refs")).isEmpty()) score += 0.05;
        if (!components.isEmpty()) score += 0.05;
        if (!"UNKNOWN".equals(action) && !object.startsWith("UNKNOWN_")) score += 0.05;
        return round(Math.min(0.90, score));
    }

    private static String ambiguity(double confidence, Map<String, Object> operation) {
        Set<String> tags = new LinkedHashSet<>(strings(operation.get("tags")).stream()
                .map(BusinessSemanticHypothesisEngine::normalizedMeaning).toList());
        if (tags.size() > 1) return "MEDIUM";
        if (confidence >= 0.80) return "LOW";
        return confidence >= 0.60 ? "MEDIUM" : "HIGH";
    }

    private static List<Map<String, Object>> matchingComponents(
            List<Map<String, Object>> components, String object) {
        if (object.startsWith("UNKNOWN_")) return List.of();
        String token = object.replace("_", "").toLowerCase(Locale.ROOT);
        return components.stream().filter(component -> {
            String material = (text(component, "name", "") + " "
                    + String.join(" ", strings(component.get("source_locations"))))
                    .replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
            return token.length() >= 3 && material.contains(token);
        }).limit(20).toList();
    }

    private static String componentSortKey(Map<String, Object> component) {
        return text(component, "id", "") + "\u0000" + text(component, "name", "") + "\u0000"
                + String.join("\u0000", strings(component.get("source_locations")));
    }

    private static String normalizedMeaning(String value) {
        String normalized = value.replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_+|_+$", "").toUpperCase(Locale.ROOT);
        return normalized.isBlank() ? "UNKNOWN_UNSUPPORTED_SEMANTICS" : normalized;
    }

    private static String digest(Object candidate, String fallback) {
        String value = candidate == null ? "" : candidate.toString();
        return value.matches("[0-9a-f]{64}") ? value : fallback;
    }

    private static String text(Map<String, Object> value, String key, String fallback) {
        Object item = value.get(key);
        return item == null || item.toString().isBlank() ? fallback : item.toString();
    }

    private static double round(double value) {
        return Math.floor(value * 100.0) / 100.0;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item).toList();
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(item -> item != null && !item.toString().isBlank())
                .map(Object::toString).sorted().toList();
    }
}
