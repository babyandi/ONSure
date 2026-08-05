package io.onsure.platform;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Builds review-only business-flow and E2E plan candidates from source-bound observations. */
final class ProgramUnderstandingEngine {
    static final String CONTRACT = "ONSURE_PROGRAM_UNDERSTANDING_CANDIDATE_V1";
    private static final List<String> STAGE_ORDER = List.of(
            "REQUEST", "PROCESS_OR_PRODUCE", "PERSIST_OR_HANDOFF", "ARTIFACT_READBACK",
            "TEST_OR_VALIDATE", "AUDIT", "GATE_OR_PERMIT", "EXPOSURE_OR_RELEASE", "RECOVERY");

    private ProgramUnderstandingEngine() {}

    static Map<String, Object> infer(Map<String, Object> workflowInventory, String sourceSha256) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) workflowInventory
                .getOrDefault("candidates", List.of());
        List<Map<String, Object>> observations = candidates.stream().map(candidate -> Map.<String, Object>of(
                "observation_id", candidate.get("candidate_id"),
                "kind", candidate.get("kind"),
                "name", candidate.get("name"),
                "source_path", candidate.get("source_path"),
                "evidence_sha256", candidate.get("evidence_sha256"),
                "fact_class", "STATIC_SOURCE_OBSERVATION",
                "runtime_verified", false)).toList();

        Set<String> actors = new LinkedHashSet<>();
        Set<String> businessObjects = new LinkedHashSet<>();
        List<Map<String, Object>> flows = new ArrayList<>();
        for (Map<String, Object> candidate : candidates) {
            String material = candidate.get("name") + " " + candidate.get("source_path");
            inferActors(material, actors);
            inferObjects(material, businessObjects);
            @SuppressWarnings("unchecked")
            List<String> hints = (List<String>) candidate.getOrDefault("role_hints", List.of());
            List<String> stages = stages(candidate.get("kind").toString(), hints);
            if (stages.isEmpty()) continue;
            double observedConfidence = ((Number) candidate.getOrDefault("confidence", 0)).doubleValue();
            double confidence = Math.floor(Math.min(0.95, observedConfidence * 0.85) * 100) / 100;
            Map<String, Object> flow = new LinkedHashMap<>();
            flow.put("flow_id", "FLOW-" + Hashing.sha256(candidate.get("candidate_id").toString()).substring(0, 16));
            flow.put("name", candidate.get("name"));
            flow.put("trigger_observation_id", candidate.get("candidate_id"));
            flow.put("inferred_actor", actor(material));
            flow.put("inferred_business_object", object(material));
            flow.put("stages", stages);
            flow.put("evidence_sha256", candidate.get("evidence_sha256"));
            flow.put("inference_confidence", confidence);
            flow.put("semantic_state", "INFERRED_REVIEW_REQUIRED");
            flow.put("runtime_verified", false);
            flow.put("auto_execute", false);
            flow.put("score_eligible", false);
            flows.add(Map.copyOf(flow));
        }
        flows.sort(Comparator.comparing(value -> value.get("flow_id").toString()));

        boolean hasTestOracle = candidates.stream().anyMatch(candidate ->
                candidate.get("kind").toString().contains("TEST")
                        || roles(candidate).contains("TEST_OR_VALIDATE"));
        boolean hasApi = candidates.stream().anyMatch(candidate ->
                candidate.get("kind").toString().contains("OPENAPI")
                        || candidate.get("kind").toString().contains("ENTRYPOINT"));
        boolean hasDataLifecycle = candidates.stream().anyMatch(candidate ->
                roles(candidate).contains("DATA_LIFECYCLE"));
        List<Map<String, Object>> questions = new ArrayList<>();
        if (hasApi) questions.add(question("RUNTIME_ENDPOINT", "검증용 base URL과 시작 명령을 확인하십시오.", true));
        questions.add(question("SAFE_TEST_IDENTITY", "실데이터가 아닌 검증 계정·fixture의 사용 범위를 확인하십시오.", true));
        if (!hasTestOracle) questions.add(question(
                "SUCCESS_ORACLE", "각 주요 Flow의 성공·실패 판정 조건을 확인하십시오.", true));
        if (hasDataLifecycle) questions.add(question(
                "DATABASE_RECOVERY_BOUNDARY", "합성 DB, rollback 및 backup/restore 허용 범위를 확인하십시오.", true));

        List<Map<String, Object>> plans = flows.stream().limit(200).map(flow -> Map.<String, Object>of(
                "plan_id", "E2E-" + flow.get("flow_id").toString().substring(5),
                "flow_id", flow.get("flow_id"),
                "proposed_steps", proposedSteps(cast(flow.get("stages"))),
                "oracle_state", hasTestOracle ? "STATIC_ORACLE_CANDIDATE_DISCOVERED" : "ORACLE_INPUT_REQUIRED",
                "execution_state", "NOT_RUN_REVIEW_REQUIRED",
                "sandbox_required", true,
                "customer_data_allowed", false,
                "destructive_action_allowed", false,
                "score_eligible", false)).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contract", CONTRACT);
        result.put("source_sha256", sourceSha256);
        result.put("inference_method", "DETERMINISTIC_STATIC_SEMANTIC_HEURISTICS_V1");
        result.put("observation_count", observations.size());
        result.put("observations", observations);
        result.put("inferred_actors", actors.stream().sorted().toList());
        result.put("inferred_business_objects", businessObjects.stream().sorted().toList());
        result.put("flow_candidate_count", flows.size());
        result.put("flow_candidates", List.copyOf(flows));
        result.put("e2e_plan_candidates", plans);
        result.put("minimal_questions", List.copyOf(questions));
        result.put("automatic_execution", "NOT_RUN_REVIEW_REQUIRED");
        result.put("inferences_are_pass_evidence", false);
        result.put("human_review", "NOT_RUN");
        result.put("runtime_verification", "NOT_RUN");
        result.put("final_claim_allowed", false);
        return Map.copyOf(result);
    }

    private static List<String> stages(String kind, List<String> hints) {
        Set<String> result = new LinkedHashSet<>();
        if (kind.contains("OPENAPI") || kind.contains("ENTRYPOINT")) result.add("REQUEST");
        if (kind.contains("MIGRATION")) result.add("PERSIST_OR_HANDOFF");
        if (kind.contains("TEST")) result.add("TEST_OR_VALIDATE");
        for (String hint : hints) switch (hint) {
            case "PRODUCE_OR_RENDER" -> result.add("PROCESS_OR_PRODUCE");
            case "ARTIFACT_READBACK" -> result.add("ARTIFACT_READBACK");
            case "TEST_OR_VALIDATE" -> result.add("TEST_OR_VALIDATE");
            case "AUDIT" -> result.add("AUDIT");
            case "GATE_OR_PERMIT" -> result.add("GATE_OR_PERMIT");
            case "EXPOSURE_OR_RELEASE" -> result.add("EXPOSURE_OR_RELEASE");
            case "DATA_LIFECYCLE" -> result.add("PERSIST_OR_HANDOFF");
            case "RECOVERY" -> result.add("RECOVERY");
            default -> { }
        }
        return STAGE_ORDER.stream().filter(result::contains).toList();
    }

    private static List<Map<String, Object>> proposedSteps(List<String> stages) {
        return stages.stream().map(stage -> Map.<String, Object>of(
                "stage", stage,
                "state", "PROPOSED_NOT_RUN",
                "required_evidence", evidence(stage),
                "source_mutation_allowed", false)).toList();
    }

    private static String evidence(String stage) {
        return switch (stage) {
            case "REQUEST" -> "REQUEST_FIXTURE_SHA256";
            case "PROCESS_OR_PRODUCE", "PERSIST_OR_HANDOFF" -> "PRODUCER_OUTPUT_SHA256";
            case "ARTIFACT_READBACK" -> "CONSUMER_INPUT_AND_ARTIFACT_SHA256";
            case "TEST_OR_VALIDATE" -> "ORACLE_AND_RESULT_SHA256";
            case "AUDIT" -> "AUDIT_RECEIPT_SHA256";
            case "GATE_OR_PERMIT" -> "PERMIT_SHA256";
            case "EXPOSURE_OR_RELEASE" -> "EXPOSURE_DECISION_SHA256";
            case "RECOVERY" -> "BEFORE_AFTER_RECOVERY_SHA256";
            default -> "DIGEST_BOUND_EVIDENCE";
        };
    }

    private static void inferActors(String material, Set<String> actors) {
        actors.add(actor(material));
    }

    private static String actor(String material) {
        String lower = material.toLowerCase(Locale.ROOT);
        if (contains(lower, "admin", "operator", "manage")) return "OPERATOR";
        if (contains(lower, "worker", "job", "queue", "event")) return "BACKGROUND_WORKER";
        if (contains(lower, "api", "controller", "route", "request")) return "API_CLIENT";
        return "UNKNOWN_ACTOR_REVIEW_REQUIRED";
    }

    private static void inferObjects(String material, Set<String> objects) {
        objects.add(object(material));
    }

    private static String object(String material) {
        String normalized = material.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replaceAll("(?i)(controller|service|repository|test|spec|migration|route|api)", "")
                .replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "").toUpperCase(Locale.ROOT);
        return name.isBlank() ? "UNKNOWN_OBJECT_REVIEW_REQUIRED" : name.substring(0, Math.min(80, name.length()));
    }

    private static boolean contains(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }

    private static List<String> roles(Map<String, Object> candidate) {
        @SuppressWarnings("unchecked")
        List<String> value = (List<String>) candidate.getOrDefault("role_hints", List.of());
        return value;
    }

    @SuppressWarnings("unchecked")
    private static List<String> cast(Object value) { return (List<String>) value; }

    private static Map<String, Object> question(String id, String prompt, boolean blocking) {
        return Map.of("question_id", id, "prompt", prompt, "blocking_before_execution", blocking,
                "answer_state", "NOT_PROVIDED");
    }
}
