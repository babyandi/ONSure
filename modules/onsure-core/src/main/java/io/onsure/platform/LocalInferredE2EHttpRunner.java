package io.onsure.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Executes approved non-destructive HTTP candidates against an explicit loopback endpoint. */
final class LocalInferredE2EHttpRunner {
    static final String CONTRACT = "ONSURE_INFERRED_E2E_RUNTIME_RECEIPT_V1";
    private static final int MAX_RESPONSE_BYTES = 1_048_576;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final Path workspaceRoot;
    private final Map<String, String> environment;
    private final HttpClient client;

    LocalInferredE2EHttpRunner(Path workspaceRoot, Map<String, String> environment, HttpClient client) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.environment = Map.copyOf(environment == null ? Map.of() : environment);
        this.client = client;
    }

    Map<String, Object> run(JsonNode input, LocalAccessControl.Identity operator) throws Exception {
        requireExactFields(input, Set.of("execution_authorization_id", "execution_plan_sha256",
                "base_url_reference_id"));
        String authorizationId = text(input, "execution_authorization_id", 128);
        if (!authorizationId.matches("inferred-e2e-auth-[0-9a-f-]{36}"))
            throw new IllegalArgumentException("INFERRED_E2E_AUTHORIZATION_ID_INVALID");
        String planSha = text(input, "execution_plan_sha256", 64);
        if (!planSha.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("INFERRED_E2E_PLAN_DIGEST_INVALID");
        String reference = text(input, "base_url_reference_id", 256);
        if (!reference.matches("env:[A-Z][A-Z0-9_]{1,127}"))
            throw new IllegalArgumentException("INFERRED_E2E_BASE_URL_REFERENCE_INVALID");
        URI base = loopbackBase(environment.get(reference.substring(4)));
        Path planFile = workspaceRoot.resolve(".onsure/inferred-e2e-authorizations")
                .resolve(authorizationId).resolve("execution-plan.json").normalize();
        if (!planFile.startsWith(workspaceRoot) || !Files.isRegularFile(planFile, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(planFile) || !Hashing.file(planFile).equals(planSha))
            throw new IllegalArgumentException("INFERRED_E2E_PLAN_NOT_FOUND_OR_STALE");
        Map<String, Object> plan = mapper.readValue(planFile.toFile(), new TypeReference<>() {});
        if (!"ONSURE_INFERRED_E2E_EXECUTION_AUTHORIZATION_V1".equals(plan.get("contract"))
                || !authorizationId.equals(plan.get("execution_authorization_id"))
                || !"NOT_RUN".equals(plan.get("execution_state")))
            throw new IllegalArgumentException("INFERRED_E2E_PLAN_BINDING_INVALID");
        @SuppressWarnings("unchecked") Map<String, String> runtimeReferences = (Map<String, String>) plan
                .getOrDefault("runtime_reference_ids", Map.of());
        String requestId = plan.get("approval_request_id").toString();
        LocalProgramUnderstandingApprovalService approvals =
                new LocalProgramUnderstandingApprovalService(workspaceRoot);
        Map<String, Object> recovery = approvals.recoverInterruptedExecution(
                requestId, authorizationId, planSha, operator, Duration.ofMinutes(2));
        if ("RECOVERED_COMPLETED".equals(recovery.get("recovery_state")))
            return recoveredReceipt(planFile, recovery, plan, approvals);
        if ("RECOVERY_REAPPROVAL_REQUIRED".equals(recovery.get("recovery_state")))
            throw new IllegalArgumentException("INFERRED_E2E_WRITE_RECOVERY_REAPPROVAL_REQUIRED");
        ValidationModel.ValidationTarget target = new ProductCatalog(
                workspaceRoot.resolve(".onsure/product-catalog"))
                .requireTarget(plan.get("target_id").toString());
        if (!("sha256:" + plan.get("source_sha256")).equals(target.immutableSourceReference()))
            throw new IllegalArgumentException("INFERRED_E2E_TARGET_SOURCE_BINDING_INVALID");
        if (!plan.get("source_sha256").equals(
                new LocalProgramManagementService(workspaceRoot).currentSourceDigest(target.sourceRoot())))
            throw new IllegalArgumentException("INFERRED_E2E_TARGET_SOURCE_STALE");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) plan
                .getOrDefault("authorized_candidates", List.of());
        @SuppressWarnings("unchecked") List<Map<String, Object>> lifecycles =
                (List<Map<String, Object>>) plan.getOrDefault("authorized_lifecycles", List.of());
        List<Map<String, Object>> orderedCandidates = orderedCandidates(candidates, lifecycles);
        Map<String, List<Map<String, Object>>> bindingsByConsumer = bindingsByConsumer(lifecycles);
        Set<String> producerPlanIds = producerPlanIds(lifecycles);
        Map<String, OpenApiSyntheticFixtureEngine> fixtureEngines = new LinkedHashMap<>();
        for (Map<String, Object> candidate : orderedCandidates) {
            if (!runnable(candidate)) continue;
            String planId = candidate.get("plan_id").toString();
            if (fixtureEngines.putIfAbsent(planId, new OpenApiSyntheticFixtureEngine(
                    target.sourceRoot(), candidate)) != null)
                throw new IllegalArgumentException("INFERRED_E2E_DUPLICATE_PLAN_ID");
        }
        Map<String, Object> claim = approvals.claimExecution(requestId, authorizationId, planSha, operator);
        String runId = claim.get("execution_run_id").toString();
        Instant started = Instant.now();
        List<Map<String, Object>> steps = new ArrayList<>();
        Map<String, JsonNode> producerOutputs = new LinkedHashMap<>();
        boolean failed = false;
        boolean blocked = false;
        int executed = 0;
        for (Map<String, Object> candidate : orderedCandidates) {
            if (!runnable(candidate)) {
                steps.add(blocked(candidate)); blocked = true; continue;
            }
            String method = candidate.get("http_method").toString();
            String routeTemplate = candidate.get("http_path").toString();
            if (!List.of("GET", "HEAD", "OPTIONS", "POST", "PUT", "PATCH").contains(method)) {
                steps.add(blocked(candidate)); blocked = true; continue;
            }
            try {
                List<Map<String, Object>> inputBindings = new ArrayList<>();
                MaterializedBindings boundValues = materializeBindings(
                        candidate.get("plan_id").toString(), bindingsByConsumer, producerOutputs, inputBindings);
                OpenApiSyntheticFixtureEngine fixtureEngine = fixtureEngines.get(candidate.get("plan_id").toString());
                OpenApiSyntheticFixtureEngine.PreparedRequest prepared =
                        fixtureEngine.prepare(method, routeTemplate, environment, runtimeReferences,
                                new OpenApiSyntheticFixtureEngine.BoundRequestValues(
                                        boundValues.path(), boundValues.query(), boundValues.headers()));
                String route = prepared.route();
                if (!safeRoute(route)) throw new IllegalArgumentException("INFERRED_E2E_ROUTE_UNSAFE");
                URI uri = URI.create(base.toString() + route
                        + (prepared.query().isBlank() ? "" : "?" + prepared.query()));
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                        .header("Accept", "application/json").header("User-Agent", "ONSure-Inferred-E2E/1");
                prepared.headers().forEach(requestBuilder::header);
                if (prepared.contentType() != null) requestBuilder.header("Content-Type", prepared.contentType());
                HttpRequest request = requestBuilder.method(method, prepared.body().length == 0
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofByteArray(prepared.body())).build();
                long before = System.nanoTime();
                executed++;
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                byte[] body;
                try (InputStream stream = response.body()) { body = stream.readNBytes(MAX_RESPONSE_BYTES + 1); }
                if (body.length > MAX_RESPONSE_BYTES) throw new IllegalStateException("INFERRED_E2E_RESPONSE_TOO_LARGE");
                @SuppressWarnings("unchecked") List<String> expected =
                        (List<String>) candidate.getOrDefault("response_statuses", List.of());
                boolean statusOracle = expected.isEmpty() ? response.statusCode() >= 200 && response.statusCode() < 300
                        : statusExpected(expected, response.statusCode());
                String responseContentType = response.headers().firstValue("Content-Type").orElse(null);
                OpenApiSyntheticFixtureEngine.OracleResult schemaOracle = fixtureEngine.validateResponse(
                        method, response.statusCode(), responseContentType, body);
                boolean oracle = statusOracle && schemaOracle.passed();
                boolean oracleBlocked = statusOracle && schemaOracle.blocked();
                if (oracle && producerPlanIds.contains(candidate.get("plan_id").toString())) {
                    try {
                        JsonNode output = mapper.readTree(body);
                        if (output != null) producerOutputs.put(candidate.get("plan_id").toString(), output);
                    } catch (Exception ignored) {
                        // Schema/status Oracle owns the current step; an unavailable JSON binding blocks its consumer.
                    }
                }
                Map<String, Object> step = new LinkedHashMap<>();
                step.put("plan_id", candidate.get("plan_id")); step.put("http_method", method);
                step.put("http_path_template", routeTemplate);
                step.put("http_path", boundValues.path().isEmpty() ? route : routeTemplate);
                step.put("http_path_sha256", Hashing.sha256(route));
                step.put("request_uri", base.toString() + (boundValues.path().isEmpty() ? route : routeTemplate));
                step.put("request_uri_sha256", Hashing.sha256(uri.toString()));
                step.put("request_body_sha256", Hashing.sha256(prepared.body()));
                step.put("request_body_bytes", prepared.body().length);
                step.put("request_body_stored", false);
                step.put("request_query_parameter_names", prepared.queryParameterNames());
                step.put("request_header_names", prepared.headers().keySet().stream().sorted().toList());
                step.put("request_cookie_names", prepared.cookieNames());
                step.put("request_parameter_values_stored", false);
                step.put("request_header_values_stored", false);
                step.put("input_bindings", List.copyOf(inputBindings));
                step.put("bound_response_values_stored", false);
                if (prepared.authenticationReferenceId() != null) {
                    step.put("authentication_reference_id", prepared.authenticationReferenceId());
                    step.put("authentication_value_sha256", prepared.authenticationValueSha256());
                    step.put("authentication_scheme_type", prepared.authenticationSchemeType());
                    step.put("authentication_value_stored", false);
                }
                step.put("request_schema_sha256", prepared.requestSchemaSha256());
                step.put("fixture_strategy", prepared.fixtureStrategy());
                step.put("response_status", response.statusCode()); step.put("expected_statuses", expected);
                step.put("response_body_sha256", Hashing.sha256(body)); step.put("response_bytes", body.length);
                step.put("response_schema_declared", schemaOracle.schemaDeclared());
                if (schemaOracle.responseSchemaSha256() != null)
                    step.put("response_schema_sha256", schemaOracle.responseSchemaSha256());
                step.put("response_schema_errors", schemaOracle.errors());
                step.put("oracle_strategy", schemaOracle.strategy());
                step.put("duration_millis", Duration.ofNanos(System.nanoTime() - before).toMillis());
                step.put("oracle_outcome", oracle ? "PASS_NONFINAL" : oracleBlocked ? "BLOCKED" : "FAIL");
                step.put("response_body_stored", false); step.put("final_claim_allowed", false);
                steps.add(Map.copyOf(step));
                if (oracleBlocked) blocked = true;
                else if (!oracle) failed = true;
            } catch (Exception error) {
                String errorCode = errorCode(error);
                boolean executionBlocked = errorCode.startsWith("OPENAPI_");
                steps.add(Map.of("plan_id", candidate.get("plan_id"), "http_method", method,
                        "http_path", routeTemplate, "oracle_outcome", executionBlocked ? "BLOCKED" : "FAIL",
                        "error_code", errorCode, "response_body_stored", false,
                        "final_claim_allowed", false));
                if (executionBlocked) blocked = true; else failed = true;
            }
        }
        String outcome = failed ? "FAIL" : blocked || executed == 0 ? "BLOCKED" : "PASS_NONFINAL";
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("contract", CONTRACT); receipt.put("run_id", runId);
        receipt.put("execution_authorization_id", authorizationId); receipt.put("execution_plan_sha256", planSha);
        receipt.put("approval_request_sha256", plan.get("approval_request_sha256"));
        receipt.put("approval_receipt_sha256", plan.get("approval_receipt_sha256"));
        receipt.put("base_url_reference_id", reference); receipt.put("base_url_sha256", Hashing.sha256(base.toString()));
        receipt.put("started_at", started.toString()); receipt.put("completed_at", Instant.now().toString());
        receipt.put("step_count", steps.size()); receipt.put("executed_step_count", executed);
        receipt.put("steps", List.copyOf(steps)); receipt.put("outcome", outcome);
        receipt.put("customer_data_stored", false); receipt.put("response_bodies_stored", false);
        receipt.put("source_mutation_allowed", false); receipt.put("final_claim_allowed", false);
        Path receiptFile = planFile.resolveSibling("runtime-receipt.json");
        write(receiptFile, receipt);
        String receiptSha = Hashing.file(receiptFile);
        approvals.completeExecution(requestId, runId, outcome, receiptSha);
        receipt.put("runtime_receipt_sha256", receiptSha);
        receipt.put("runtime_receipt_file", workspaceRoot.relativize(receiptFile).toString().replace('\\', '/'));
        attachComparisonEnvelope(plan, receipt, receiptSha, approvals);
        return Map.copyOf(receipt);
    }

    private static Map<String, Object> blocked(Map<String, Object> candidate) {
        return Map.of("plan_id", candidate.get("plan_id"), "http_method", candidate.get("http_method"),
                "http_path", candidate.get("http_path"), "oracle_outcome", "BLOCKED",
                "reason", candidate.get("state"), "response_body_stored", false, "final_claim_allowed", false);
    }
    private static List<Map<String, Object>> orderedCandidates(
            List<Map<String, Object>> candidates, List<Map<String, Object>> lifecycles) {
        Map<String, Map<String, Object>> byPlan = new LinkedHashMap<>();
        for (Map<String, Object> candidate : candidates) byPlan.put(candidate.get("plan_id").toString(), candidate);
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> added = new java.util.LinkedHashSet<>();
        for (Map<String, Object> lifecycle : lifecycles) {
            Object raw = lifecycle.get("operation_plan_ids");
            if (!(raw instanceof List<?> operationPlanIds)) continue;
            for (Object planIdValue : operationPlanIds) {
                String planId = String.valueOf(planIdValue);
                Map<String, Object> candidate = byPlan.get(planId);
                if (candidate != null && added.add(planId)) result.add(candidate);
            }
        }
        for (Map<String, Object> candidate : candidates) {
            String planId = candidate.get("plan_id").toString();
            if (added.add(planId)) result.add(candidate);
        }
        return List.copyOf(result);
    }
    @SuppressWarnings("unchecked")
    private static Map<String, List<Map<String, Object>>> bindingsByConsumer(
            List<Map<String, Object>> lifecycles) {
        Map<String, List<Map<String, Object>>> result = new TreeMap<>();
        for (Map<String, Object> lifecycle : lifecycles) {
            Object raw = lifecycle.get("bindings");
            if (!(raw instanceof List<?> bindings)) continue;
            for (Object item : bindings) {
                if (!(item instanceof Map<?, ?>)) continue;
                Map<String, Object> binding = (Map<String, Object>) item;
                result.computeIfAbsent(binding.get("consumer_plan_id").toString(), ignored -> new ArrayList<>())
                        .add(binding);
            }
        }
        result.replaceAll((ignored, bindings) -> bindings.stream()
                .sorted(java.util.Comparator.comparing(binding -> binding.get("binding_id").toString())).toList());
        return Map.copyOf(result);
    }
    private static Set<String> producerPlanIds(List<Map<String, Object>> lifecycles) {
        Set<String> result = new java.util.LinkedHashSet<>();
        for (Map<String, Object> lifecycle : lifecycles) {
            Object raw = lifecycle.get("bindings");
            if (!(raw instanceof List<?> bindings)) continue;
            for (Object item : bindings) if (item instanceof Map<?, ?> binding)
                result.add(String.valueOf(binding.get("producer_plan_id")));
        }
        return Set.copyOf(result);
    }
    private static MaterializedBindings materializeBindings(String consumerPlanId,
            Map<String, List<Map<String, Object>>> bindingsByConsumer,
            Map<String, JsonNode> producerOutputs, List<Map<String, Object>> evidence) {
        Map<String, String> path = new TreeMap<>();
        Map<String, String> query = new TreeMap<>();
        Map<String, String> headers = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map<String, Object> binding : bindingsByConsumer.getOrDefault(consumerPlanId, List.of())) {
            String producerPlanId = binding.get("producer_plan_id").toString();
            JsonNode output = producerOutputs.get(producerPlanId);
            if (output == null) throw new IllegalArgumentException(
                    "OPENAPI_LIFECYCLE_PRODUCER_OUTPUT_NOT_AVAILABLE");
            String pointer = binding.get("producer_json_pointer").toString();
            JsonNode value = resolveBindingValue(output, pointer);
            if (value.isMissingNode() || value.isNull() || value.isContainerNode())
                throw new IllegalArgumentException("OPENAPI_LIFECYCLE_BINDING_VALUE_NOT_FOUND");
            String materialized = value.asText();
            if (materialized.isEmpty() || materialized.length() > 4096)
                throw new IllegalArgumentException("OPENAPI_LIFECYCLE_BINDING_VALUE_INVALID");
            String parameter = binding.get("consumer_parameter_name").toString();
            String location = binding.get("consumer_location").toString();
            Map<String, String> target = switch (location) {
                case "PATH" -> path;
                case "QUERY" -> query;
                case "HEADER" -> headers;
                case "BODY" -> throw new IllegalArgumentException(
                        "OPENAPI_BODY_BINDING_RUNNER_NOT_IMPLEMENTED");
                default -> throw new IllegalArgumentException("OPENAPI_BINDING_CONSUMER_LOCATION_INVALID");
            };
            if (target.putIfAbsent(parameter, materialized) != null)
                throw new IllegalArgumentException("OPENAPI_LIFECYCLE_BINDING_COLLISION");
            evidence.add(Map.of(
                    "binding_id", binding.get("binding_id"),
                    "producer_plan_id", producerPlanId,
                    "producer_json_pointer", pointer,
                    "consumer_location", location,
                    "consumer_parameter_name", parameter,
                    "value_sha256", Hashing.sha256(materialized),
                    "value_stored", false));
        }
        return new MaterializedBindings(Map.copyOf(path), Map.copyOf(query), Map.copyOf(headers));
    }

    private record MaterializedBindings(Map<String, String> path, Map<String, String> query,
            Map<String, String> headers) { }

    static JsonNode resolveBindingValue(JsonNode output, String pointer) {
        if (pointer == null || !pointer.matches("(?:/(?:[A-Za-z0-9._-]|~[0123]){1,128}){1,16}"))
            throw new IllegalArgumentException("OPENAPI_LIFECYCLE_BINDING_POINTER_INVALID");
        JsonNode current = output;
        String[] segments = pointer.substring(1).split("/", -1);
        for (String encoded : segments) {
            if (Set.of(StaticWorkflowInventory.SINGLETON_ARRAY_POINTER_SEGMENT,
                    StaticWorkflowInventory.SCHEMA_SINGLETON_ARRAY_POINTER_SEGMENT).contains(encoded)) {
                if (!current.isArray())
                    throw new IllegalArgumentException("OPENAPI_LIFECYCLE_BINDING_ARRAY_EXPECTED");
                if (current.isEmpty())
                    throw new IllegalArgumentException("OPENAPI_LIFECYCLE_BINDING_ARRAY_EMPTY");
                if (current.size() != 1)
                    throw new IllegalArgumentException("OPENAPI_LIFECYCLE_BINDING_ARRAY_AMBIGUOUS");
                current = current.get(0);
                continue;
            }
            String property = encoded.replace("~1", "/").replace("~0", "~");
            if (!current.isObject())
                throw new IllegalArgumentException("OPENAPI_LIFECYCLE_BINDING_OBJECT_EXPECTED");
            current = current.get(property);
            if (current == null)
                throw new IllegalArgumentException("OPENAPI_LIFECYCLE_BINDING_VALUE_NOT_FOUND");
        }
        return current;
    }
    private Map<String, Object> recoveredReceipt(Path planFile, Map<String, Object> recovery,
            Map<String, Object> plan, LocalProgramUnderstandingApprovalService approvals) throws Exception {
        Path receiptFile = planFile.resolveSibling("runtime-receipt.json");
        String expected = recovery.get("runtime_receipt_sha256").toString();
        if (!Files.isRegularFile(receiptFile, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(receiptFile)
                || !Hashing.file(receiptFile).equals(expected))
            throw new IllegalArgumentException("INFERRED_E2E_RECOVERED_RECEIPT_STALE");
        Map<String, Object> receipt = mapper.readValue(receiptFile.toFile(), new TypeReference<>() {});
        receipt.put("runtime_receipt_sha256", expected);
        receipt.put("runtime_receipt_file", recovery.get("runtime_receipt_file"));
        attachComparisonEnvelope(plan, receipt, expected, approvals);
        return Map.copyOf(receipt);
    }
    private void attachComparisonEnvelope(Map<String, Object> plan, Map<String, Object> receipt,
            String receiptSha, LocalProgramUnderstandingApprovalService approvals) {
        try {
            Map<String, Object> comparison = new InferredE2ERunComparisonService(workspaceRoot)
                    .record(plan, receipt, receiptSha, approvals);
            receipt.put("runtime_comparison_state", comparison.get("state"));
            receipt.put("runtime_comparison_sha256", comparison.get("runtime_comparison_sha256"));
            receipt.put("runtime_comparison_file", comparison.get("runtime_comparison_file"));
            if (comparison.containsKey("overall_change"))
                receipt.put("runtime_comparison_change", comparison.get("overall_change"));
        } catch (Exception unavailable) {
            receipt.put("runtime_comparison_state", "UNAVAILABLE");
            receipt.put("runtime_comparison_error", errorCode(unavailable));
        }
    }
    private static boolean safeRoute(String route) {
        return route.startsWith("/") && !route.contains("{") && !route.contains("..")
                && !route.contains("?") && !route.contains("#") && !route.contains("\\");
    }
    private static boolean runnable(Map<String, Object> candidate) {
        return Set.of("READY_FOR_ISOLATED_LOOPBACK_RUNNER", "READY_FOR_SYNTHETIC_FIXTURE_GENERATION",
                "READY_FOR_PATH_PARAMETER_FIXTURE_GENERATION").contains(candidate.get("state"));
    }
    private static boolean statusExpected(List<String> expected, int actual) {
        String exact = String.valueOf(actual);
        String family = (actual / 100) + "XX";
        return expected.stream().map(value -> value.toUpperCase(Locale.ROOT))
                .anyMatch(value -> value.equals(exact) || value.equals(family) || value.equals("DEFAULT"));
    }
    private static String errorCode(Exception error) {
        String message = error.getMessage();
        return message != null && message.matches("[A-Z0-9_.:-]{1,200}")
                ? message : error.getClass().getSimpleName();
    }
    private static URI loopbackBase(String raw) throws Exception {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("INFERRED_E2E_BASE_URL_NOT_CONFIGURED");
        URI uri = URI.create(raw);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"http".equals(uri.getScheme()) || !Set.of("127.0.0.1", "localhost", "::1").contains(host)
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath())))
            throw new IllegalArgumentException("INFERRED_E2E_BASE_URL_NOT_LOOPBACK");
        int port = uri.getPort();
        if (port < 1024 || port > 65535) throw new IllegalArgumentException("INFERRED_E2E_BASE_URL_PORT_INVALID");
        return new URI("http", null, host, port, null, null, null);
    }
    private void write(Path file, Object value) throws Exception {
        Path root = workspaceRoot.resolve(".onsure/inferred-e2e-authorizations").normalize();
        if (!file.startsWith(root)) throw new IllegalStateException("INFERRED_E2E_RECEIPT_PATH_INVALID");
        Path temp = file.resolveSibling(file.getFileName() + "." + UUID.randomUUID() + ".tmp");
        mapper.writeValue(temp.toFile(), value);
        try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        finally { Files.deleteIfExists(temp); }
    }
    private static void requireExactFields(JsonNode input, Set<String> fields) {
        if (input == null || !input.isObject()) throw new IllegalArgumentException("INFERRED_E2E_REQUEST_REQUIRED");
        Set<String> actual = new java.util.HashSet<>(); input.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(fields)) throw new IllegalArgumentException("INFERRED_E2E_REQUEST_FIELDS_INVALID");
    }
    private static String text(JsonNode input, String field, int maximum) {
        JsonNode value = input.path(field);
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > maximum)
            throw new IllegalArgumentException("INFERRED_E2E_REQUEST_TEXT_INVALID:" + field);
        return value.asText();
    }
}
