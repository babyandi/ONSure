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
            String inferredActor = actor(candidate, material);
            String inferredObject = object(candidate, material);
            actors.add(inferredActor);
            businessObjects.add(inferredObject);
            List<String> stages = stages(candidate);
            if (stages.isEmpty()) continue;
            double observedConfidence = ((Number) candidate.getOrDefault("confidence", 0)).doubleValue();
            double confidence = Math.floor(Math.min(0.95, observedConfidence * 0.85) * 100) / 100;
            Map<String, Object> flow = new LinkedHashMap<>();
            flow.put("flow_id", "FLOW-" + Hashing.sha256(candidate.get("candidate_id").toString()).substring(0, 16));
            flow.put("name", candidate.get("name"));
            flow.put("trigger_observation_id", candidate.get("candidate_id"));
            flow.put("inferred_actor", inferredActor);
            flow.put("inferred_business_object", inferredObject);
            flow.put("stages", stages);
            flow.put("evidence_sha256", candidate.get("evidence_sha256"));
            flow.put("inference_confidence", confidence);
            flow.put("semantic_state", "INFERRED_REVIEW_REQUIRED");
            flow.put("runtime_verified", false);
            flow.put("auto_execute", false);
            flow.put("score_eligible", false);
            if ("OPENAPI_OPERATION".equals(candidate.get("kind"))) {
                flow.put("operation", operation(candidate));
            }
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
        boolean hasOpenApiWithoutSecurity = candidates.stream().anyMatch(candidate ->
                "OPENAPI_OPERATION".equals(candidate.get("kind"))
                        && !Boolean.TRUE.equals(candidate.get("security_declared")));
        boolean hasSecuredApi = candidates.stream().anyMatch(candidate ->
                "OPENAPI_OPERATION".equals(candidate.get("kind"))
                        && Boolean.TRUE.equals(candidate.get("security_declared")));
        boolean hasDestructiveApi = candidates.stream().anyMatch(candidate ->
                Boolean.TRUE.equals(candidate.get("destructive_risk")));
        boolean hasRequestWithoutSchema = candidates.stream().anyMatch(candidate ->
                "OPENAPI_OPERATION".equals(candidate.get("kind"))
                        && List.of("POST", "PUT", "PATCH").contains(candidate.get("http_method"))
                        && !Boolean.TRUE.equals(candidate.get("request_schema_declared")));
        List<Map<String, Object>> lifecycles = apiLifecycles(flows);
        boolean hasLifecycleBindings = lifecycles.stream().anyMatch(lifecycle ->
                ((Number) lifecycle.getOrDefault("binding_count", 0)).intValue() > 0);
        List<Map<String, Object>> questions = new ArrayList<>();
        if (hasApi) questions.add(question("RUNTIME_ENDPOINT", "검증용 base URL과 시작 명령을 확인하십시오.", true));
        questions.add(question("SAFE_TEST_IDENTITY", "실데이터가 아닌 검증 계정·fixture의 사용 범위를 확인하십시오.", true));
        if (hasSecuredApi) questions.add(question(
                "AUTHENTICATION_CONTEXT", "검증 전용 인증값을 가리키는 env: 참조 ID를 확인하십시오.", true));
        if (hasOpenApiWithoutSecurity) questions.add(question(
                "UNAUTHENTICATED_API_BOUNDARY", "OpenAPI에 인증 선언이 없는 연산을 무인증으로 검증해도 되는지 확인하십시오.", true));
        if (hasRequestWithoutSchema) questions.add(question(
                "REQUEST_FIXTURE", "요청 스키마가 없는 쓰기 연산의 합성 fixture와 금지 필드를 확인하십시오.", true));
        if (hasDestructiveApi) questions.add(question(
                "DESTRUCTIVE_TEST_BOUNDARY", "DELETE 연산은 격리된 합성 데이터에서만 실행할 수 있는지 확인하십시오.", true));
        if (!hasTestOracle) questions.add(question(
                "SUCCESS_ORACLE", "각 주요 Flow의 성공·실패 판정 조건을 확인하십시오.", true));
        if (hasDataLifecycle) questions.add(question(
                "DATABASE_RECOVERY_BOUNDARY", "합성 DB, rollback 및 backup/restore 허용 범위를 확인하십시오.", true));
        if (hasLifecycleBindings) questions.add(question(
                "LIFECYCLE_BINDING_REVIEW",
                "생성 응답의 식별자를 후속 API path 입력으로 연결하는 후보와 JSON Pointer를 확인하십시오.", true));

        List<Map<String, Object>> plans = flows.stream().limit(200).map(flow -> Map.<String, Object>of(
                "plan_id", "E2E-" + flow.get("flow_id").toString().substring(5),
                "flow_id", flow.get("flow_id"),
                "proposed_steps", proposedSteps(flow),
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
        result.put("api_lifecycle_candidates", lifecycles);
        result.put("risk_flags", riskFlags(hasOpenApiWithoutSecurity, hasRequestWithoutSchema, hasDestructiveApi));
        result.put("e2e_plan_candidates", plans);
        result.put("minimal_questions", List.copyOf(questions));
        result.put("automatic_execution", "NOT_RUN_REVIEW_REQUIRED");
        result.put("inferences_are_pass_evidence", false);
        result.put("human_review", "NOT_RUN");
        result.put("runtime_verification", "NOT_RUN");
        result.put("final_claim_allowed", false);
        return Map.copyOf(result);
    }

    private static List<String> stages(Map<String, Object> candidate) {
        String kind = candidate.get("kind").toString();
        List<String> hints = roles(candidate);
        String lifecycleAction = candidate.getOrDefault("lifecycle_action", "INVOKE").toString();
        Set<String> result = new LinkedHashSet<>();
        if (kind.contains("OPENAPI") || kind.contains("ENTRYPOINT")) result.add("REQUEST");
        if (List.of("CREATE", "UPDATE", "DELETE").contains(lifecycleAction)) {
            result.add("PERSIST_OR_HANDOFF");
        }
        if ("DELETE".equals(lifecycleAction)) result.add("RECOVERY");
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

    private static List<Map<String, Object>> proposedSteps(Map<String, Object> flow) {
        List<String> stages = cast(flow.get("stages"));
        return stages.stream().map(stage -> {
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("stage", stage);
            step.put("state", "PROPOSED_NOT_RUN");
            step.put("required_evidence", evidence(stage));
            step.put("source_mutation_allowed", false);
            if ("REQUEST".equals(stage) && flow.get("operation") instanceof Map<?, ?> operation) {
                step.put("source_derived_invocation", operation);
            }
            return Map.copyOf(step);
        }).toList();
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

    private static String actor(Map<String, Object> candidate, String material) {
        if ("OPENAPI_OPERATION".equals(candidate.get("kind"))) {
            List<String> tags = stringList(candidate.get("tags"));
            String tagMaterial = String.join(" ", tags).toLowerCase(Locale.ROOT);
            if (contains(tagMaterial, "admin", "management", "operator")) return "OPERATOR";
            return "API_CLIENT";
        }
        String lower = material.toLowerCase(Locale.ROOT);
        if (contains(lower, "admin", "operator", "manage")) return "OPERATOR";
        if (contains(lower, "worker", "job", "queue", "event")) return "BACKGROUND_WORKER";
        if (contains(lower, "api", "controller", "route", "request")) return "API_CLIENT";
        return "UNKNOWN_ACTOR_REVIEW_REQUIRED";
    }

    private static String object(Map<String, Object> candidate, String material) {
        List<String> tags = stringList(candidate.get("tags"));
        if (!tags.isEmpty() && !List.of("API", "DEFAULT", "MANAGEMENT").contains(
                normalizeObject(tags.get(0)))) return normalizeObject(tags.get(0));
        Object routeValue = candidate.get("http_path");
        if (routeValue != null) {
            for (String segment : routeValue.toString().split("/")) {
                if (!segment.isBlank() && !segment.startsWith("{") && !segment.matches("v[0-9]+")) {
                    return normalizeObject(segment);
                }
            }
        }
        String normalized = material.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replaceAll("(?i)(controller|service|repository|test|spec|migration|route|api)", "")
                .replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "").toUpperCase(Locale.ROOT);
        return name.isBlank() ? "UNKNOWN_OBJECT_REVIEW_REQUIRED" : name.substring(0, Math.min(80, name.length()));
    }

    private static String normalizeObject(String value) {
        String normalized = value.replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_+|_+$", "").toUpperCase(Locale.ROOT);
        if (normalized.endsWith("IES") && normalized.length() > 3) {
            normalized = normalized.substring(0, normalized.length() - 3) + "Y";
        } else if (normalized.endsWith("S") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? "UNKNOWN_OBJECT_REVIEW_REQUIRED" : normalized;
    }

    private static Map<String, Object> operation(Map<String, Object> candidate) {
        Map<String, Object> operation = new LinkedHashMap<>();
        for (String key : List.of("http_method", "http_path", "operation_id", "tags",
                "request_schema_refs", "response_statuses", "security_declared",
                "response_scalar_json_pointers",
                "request_input_candidates",
                "request_schema_declared", "lifecycle_action", "destructive_risk", "source_path",
                "evidence_sha256")) {
            if (candidate.containsKey(key)) operation.put(key, candidate.get(key));
        }
        return Map.copyOf(operation);
    }

    private static List<Map<String, Object>> apiLifecycles(List<Map<String, Object>> flows) {
        Map<String, List<Map<String, Object>>> grouped = new java.util.TreeMap<>();
        for (Map<String, Object> flow : flows) {
            if (!(flow.get("operation") instanceof Map<?, ?>)) continue;
            grouped.computeIfAbsent(flow.get("inferred_business_object").toString(), ignored -> new ArrayList<>())
                    .add(flow);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : grouped.entrySet()) {
            List<Map<String, Object>> orderedFlows = entry.getValue().stream()
                    .sorted(Comparator.comparing((Map<String, Object> flow) -> lifecycleRank(operationAction(flow)))
                            .thenComparing(flow -> flow.get("flow_id").toString()))
                    .toList();
            List<Map<String, Object>> operations = orderedFlows.stream()
                    .map(flow -> Map.<String, Object>of(
                            "flow_id", flow.get("flow_id"),
                            "operation", flow.get("operation"),
                            "state", "PROPOSED_NOT_RUN"))
                    .toList();
            List<Map<String, Object>> bindings = lifecycleBindings(orderedFlows);
            Set<String> actions = new LinkedHashSet<>();
            entry.getValue().forEach(flow -> actions.add(operationAction(flow)));
            Map<String, Object> lifecycle = new LinkedHashMap<>();
            lifecycle.put("lifecycle_id", "LIFECYCLE-" + Hashing.sha256(entry.getKey()).substring(0, 16));
            lifecycle.put("business_object", entry.getKey());
            lifecycle.put("actions", actions.stream()
                    .sorted(Comparator.comparing(ProgramUnderstandingEngine::lifecycleRank)).toList());
            lifecycle.put("operations", operations);
            lifecycle.put("binding_count", bindings.size());
            lifecycle.put("proposed_bindings", bindings);
            lifecycle.put("coverage_state", lifecycleCoverage(actions));
            lifecycle.put("semantic_state", "INFERRED_REVIEW_REQUIRED");
            lifecycle.put("execution_state", "NOT_RUN_REVIEW_REQUIRED");
            lifecycle.put("runtime_verified", false);
            lifecycle.put("score_eligible", false);
            result.add(Map.copyOf(lifecycle));
        }
        return List.copyOf(result);
    }

    private static List<Map<String, Object>> lifecycleBindings(List<Map<String, Object>> flows) {
        Map<String, Object> producer = flows.stream()
                .filter(flow -> "CREATE".equals(operationAction(flow))).findFirst().orElse(null);
        if (producer == null) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> consumer : flows) {
            if (!Set.of("READ", "UPDATE", "DELETE").contains(operationAction(consumer))) continue;
            @SuppressWarnings("unchecked") Map<String, Object> operation =
                    (Map<String, Object>) consumer.get("operation");
            for (String parameter : pathParameters(operation.getOrDefault("http_path", "").toString()))
                addBinding(result, producer, consumer, "PATH", parameter,
                        bindingPointer(producer, parameter, true));
            for (Map<String, Object> input : mapList(operation.get("request_input_candidates"))) {
                if (!Boolean.TRUE.equals(input.get("required"))) continue;
                String location = String.valueOf(input.get("consumer_location"));
                String parameter = String.valueOf(input.get("consumer_parameter_name"));
                if (!Set.of("QUERY", "HEADER", "BODY").contains(location)) continue;
                if ("HEADER".equals(location) && sensitiveHeader(parameter)) continue;
                BindingPointer selected = bindingPointer(producer, pointerLeaf(parameter), false);
                if (selected == null) continue;
                addBinding(result, producer, consumer, location, parameter, selected);
            }
        }
        return List.copyOf(result);
    }

    private static void addBinding(List<Map<String, Object>> result, Map<String, Object> producer,
            Map<String, Object> consumer, String location, String parameter, BindingPointer selected) {
        if (selected == null) return;
        String pointer = selected.pointer();
        String material = producer.get("flow_id") + "\u0000" + consumer.get("flow_id")
                + "\u0000" + ("PATH".equals(location) ? "" : location + "\u0000")
                + parameter + "\u0000" + pointer;
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("binding_id", "BINDING-" + Hashing.sha256(material).substring(0, 16));
        binding.put("producer_flow_id", producer.get("flow_id"));
        binding.put("producer_json_pointer", pointer);
        binding.put("consumer_flow_id", consumer.get("flow_id"));
        binding.put("consumer_location", location);
        binding.put("consumer_parameter_name", parameter);
        binding.put("inference_basis", selected.basis());
        binding.put("inference_confidence", selected.confidence());
        binding.put("semantic_state", "INFERRED_REVIEW_REQUIRED");
        binding.put("runtime_verified", false);
        binding.put("auto_execute", false);
        binding.put("value_storage_allowed", false);
        binding.put("score_eligible", false);
        result.add(Map.copyOf(binding));
    }

    private static BindingPointer bindingPointer(
            Map<String, Object> producer, String parameter, boolean allowHeuristic) {
        @SuppressWarnings("unchecked") Map<String, Object> operation =
                (Map<String, Object>) producer.get("operation");
        List<String> pointers = stringList(operation.get("response_scalar_json_pointers"));
        List<String> exact = pointers.stream().filter(pointer ->
                pointerLeaf(pointer).equalsIgnoreCase(parameter)).toList();
        if (exact.size() == 1) {
            boolean schemaSingletonArray = containsSchemaSingletonArraySegment(exact.get(0));
            boolean singletonArray = containsSingletonArraySegment(exact.get(0));
            return new BindingPointer(exact.get(0), schemaSingletonArray
                    ? "OPENAPI_RESPONSE_SCHEMA_EXACT_PROPERTY_SCHEMA_SINGLETON_ARRAY"
                    : singletonArray ? "OPENAPI_RESPONSE_SCHEMA_EXACT_PROPERTY_SINGLETON_ARRAY"
                    : "OPENAPI_RESPONSE_SCHEMA_EXACT_PROPERTY",
                    schemaSingletonArray ? 0.90 : singletonArray ? 0.80 : 0.93);
        }
        List<String> identifiers = pointers.stream().filter(pointer ->
                "id".equalsIgnoreCase(pointerLeaf(pointer))).toList();
        if (parameter.toLowerCase(Locale.ROOT).endsWith("id") && identifiers.size() == 1) {
            boolean schemaSingletonArray = containsSchemaSingletonArraySegment(identifiers.get(0));
            boolean singletonArray = containsSingletonArraySegment(identifiers.get(0));
            return new BindingPointer(identifiers.get(0), schemaSingletonArray
                    ? "OPENAPI_RESPONSE_SCHEMA_ID_PROPERTY_SCHEMA_SINGLETON_ARRAY"
                    : singletonArray ? "OPENAPI_RESPONSE_SCHEMA_ID_PROPERTY_SINGLETON_ARRAY"
                    : "OPENAPI_RESPONSE_SCHEMA_ID_PROPERTY",
                    schemaSingletonArray ? 0.85 : singletonArray ? 0.76 : 0.88);
        }
        if (!allowHeuristic) return null;
        String fallback = parameter.toLowerCase(Locale.ROOT).endsWith("id") ? "/id" : "/" + parameter;
        return new BindingPointer(fallback,
                "BUSINESS_OBJECT_AND_LIFECYCLE_PATH_PARAMETER_HEURISTIC", 0.70);
    }

    private static String pointerLeaf(String pointer) {
        int separator = pointer.lastIndexOf('/');
        return pointer.substring(separator + 1).replace("~1", "/").replace("~0", "~");
    }

    private static boolean containsSingletonArraySegment(String pointer) {
        return pointer.matches(".*(?:/~[23])(?:/.*)?");
    }

    private static boolean containsSchemaSingletonArraySegment(String pointer) {
        return pointer.equals("/~3") || pointer.contains("/~3/");
    }

    private static boolean sensitiveHeader(String name) {
        return Set.of("authorization", "cookie", "proxy-authorization", "set-cookie", "x-api-key")
                .contains(name.toLowerCase(Locale.ROOT));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        return values.stream().filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item).toList();
    }

    private record BindingPointer(String pointer, String basis, double confidence) { }

    private static List<String> pathParameters(String route) {
        List<String> result = new ArrayList<>();
        int cursor = 0;
        while ((cursor = route.indexOf('{', cursor)) >= 0) {
            int end = route.indexOf('}', cursor + 1);
            if (end < 0) break;
            String name = route.substring(cursor + 1, end);
            if (name.matches("[A-Za-z][A-Za-z0-9._-]{0,127}")) result.add(name);
            cursor = end + 1;
        }
        return List.copyOf(result);
    }

    private static String operationAction(Map<String, Object> flow) {
        @SuppressWarnings("unchecked")
        Map<String, Object> operation = (Map<String, Object>) flow.get("operation");
        return operation.getOrDefault("lifecycle_action", "INVOKE").toString();
    }

    private static int lifecycleRank(String action) {
        return switch (action) { case "CREATE" -> 0; case "READ" -> 1; case "UPDATE" -> 2; case "DELETE" -> 3; default -> 4; };
    }

    private static String lifecycleCoverage(Set<String> actions) {
        if (actions.containsAll(List.of("CREATE", "READ", "UPDATE", "DELETE"))) return "CRUD_CANDIDATE_COMPLETE";
        if (actions.contains("CREATE") && actions.contains("READ")) return "CREATE_READ_CANDIDATE";
        return "PARTIAL_LIFECYCLE_REVIEW_REQUIRED";
    }

    private static List<String> riskFlags(boolean noSecurity, boolean noFixture, boolean destructive) {
        List<String> flags = new ArrayList<>();
        if (noSecurity) flags.add("OPENAPI_SECURITY_UNDECLARED");
        if (noFixture) flags.add("WRITE_REQUEST_SCHEMA_UNDECLARED");
        if (destructive) flags.add("DESTRUCTIVE_API_DISCOVERED");
        return List.copyOf(flags);
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

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<String> cast(Object value) { return (List<String>) value; }

    private static Map<String, Object> question(String id, String prompt, boolean blocking) {
        return Map.of("question_id", id, "prompt", prompt, "blocking_before_execution", blocking,
                "answer_state", "NOT_PROVIDED");
    }
}
