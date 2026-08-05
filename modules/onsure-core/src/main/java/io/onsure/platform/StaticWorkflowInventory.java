package io.onsure.platform;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic discovery-only workflow inventory; candidates are never executable authority. */
final class StaticWorkflowInventory {
    static final String CONTRACT = "ONSURE_STATIC_WORKFLOW_INVENTORY_V1";
    /** Reserved pointer-template segment: runtime singleton selection without a schema cardinality guarantee. */
    static final String SINGLETON_ARRAY_POINTER_SEGMENT = "~2";
    /** Reserved pointer-template segment backed by minItems/maxItems equal to one. */
    static final String SCHEMA_SINGLETON_ARRAY_POINTER_SEGMENT = "~3";
    private static final int MAX_CANDIDATES = 2_000;
    private static final long MAX_INSPECTED_FILE_BYTES = 5L * 1024 * 1024;
    private static final Pattern MAVEN_MODULE = Pattern.compile("<module>\\s*([^<]{1,512}?)\\s*</module>");
    private static final Pattern JAVA_MAIN = Pattern.compile(
            "\\bpublic\\s+static\\s+void\\s+main\\s*\\(\\s*String(?:\\[\\]|\\s*\\.\\.\\.)");
    private static final Pattern PYTHON_MAIN = Pattern.compile(
            "(?m)^\\s*if\\s+__name__\\s*==\\s*['\"]__main__['\"]\\s*:");
    private static final Set<String> HTTP_METHODS = Set.of(
            "get", "put", "post", "delete", "options", "head", "patch", "trace");
    private static final Map<String, String> CONTINUATION_RESPONSE_HEADERS = Map.of(
            "link", "PAGINATION_LINK",
            "location", "ASYNC_LOCATION",
            "next-cursor", "PAGINATION_CURSOR",
            "retry-after", "ASYNC_RETRY_AFTER",
            "x-continuation-token", "PAGINATION_CURSOR",
            "x-next-cursor", "PAGINATION_CURSOR");
    private static final ObjectMapper JSON = new ObjectMapper().enable(
            com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private static final ObjectMapper YAML = new ObjectMapper(YAMLFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());

    private StaticWorkflowInventory() {}

    static Map<String, Object> detect(Path sourceRoot) throws Exception {
        return detect(sourceRoot, Hashing.sourceFiles(sourceRoot));
    }

    static Map<String, Object> detect(Path sourceRoot, List<Path> sourceFiles) throws Exception {
        Path root = sourceRoot.toAbsolutePath().normalize();
        List<Map<String, Object>> candidates = new ArrayList<>();
        List<String> findings = new ArrayList<>();
        Map<Path, String> evidenceDigests = new java.util.HashMap<>();
        boolean truncated = false;
        for (Path file : sourceFiles) {
            if (candidates.size() >= MAX_CANDIDATES) {
                truncated = true;
                break;
            }
            if (!safeFile(root, file)) continue;
            String relative = Hashing.relative(root, file);
            String lower = relative.toLowerCase(Locale.ROOT);
            try {
                if (relative.equals("package.json")) {
                    nodeScripts(file, relative, candidates, findings, evidenceDigests);
                }
                if (lower.endsWith("pom.xml")) {
                    mavenModules(file, relative, candidates, evidenceDigests);
                }
                if (lower.endsWith(".java")) {
                    javaEntrypoint(file, relative, candidates, evidenceDigests);
                }
                if (lower.endsWith(".py")) {
                    pythonEntrypoint(file, relative, candidates, evidenceDigests);
                }
                if (isOpenApiCandidate(lower)) {
                    openApiOperations(file, relative, candidates, findings, evidenceDigests);
                }
                if (isMigration(lower)) {
                    add(candidates, "DATABASE_MIGRATION", relative, relative,
                            "MIGRATION_FILE", List.of("MIGRATION", "ROLLBACK_REVIEW"),
                            file, evidenceDigests, 0.95);
                }
                if (isDeployment(relative, lower)) {
                    add(candidates, "DEPLOYMENT_DEFINITION", relative, relative,
                            "DEPLOYMENT_FILE", List.of("DEPLOY", "OPERATIONS"),
                            file, evidenceDigests, 0.9);
                }
                if (Files.isExecutable(file) && isScript(lower)) {
                    shellEntrypoint(file, relative, candidates, evidenceDigests);
                }
            } catch (Exception error) {
                if (findings.size() < 200) {
                    findings.add("STATIC_WORKFLOW_PARSE_FAILED:" + relative + ":"
                            + error.getClass().getSimpleName());
                }
            }
        }
        candidates.sort(Comparator.comparing(value -> value.get("candidate_id").toString()));
        if (candidates.size() > MAX_CANDIDATES) {
            candidates = new ArrayList<>(candidates.subList(0, MAX_CANDIDATES));
            truncated = true;
        }
        Map<String, Long> counts = new java.util.TreeMap<>();
        for (Map<String, Object> candidate : candidates) {
            counts.merge(candidate.get("kind").toString(), 1L, Long::sum);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contract", CONTRACT);
        result.put("source_digest", Hashing.tree(root, sourceFiles));
        result.put("source_file_count", sourceFiles.size());
        result.put("execution_policy", "DISCOVERY_ONLY_REVIEW_REQUIRED");
        result.put("candidate_count", candidates.size());
        result.put("candidate_kind_counts", Map.copyOf(counts));
        result.put("candidates", List.copyOf(candidates));
        result.put("parse_findings", findings.stream().sorted().toList());
        result.put("truncated", truncated);
        result.put("runtime_verification", "NOT_RUN");
        result.put("auto_execute", false);
        result.put("final_claim_allowed", false);
        return Map.copyOf(result);
    }

    private static void nodeScripts(Path file, String relative,
            List<Map<String, Object>> candidates, List<String> findings,
            Map<Path, String> evidenceDigests) throws Exception {
        JsonNode scripts = JSON.readTree(readBounded(file)).path("scripts");
        if (!scripts.isObject()) return;
        List<String> names = new ArrayList<>();
        scripts.fieldNames().forEachRemaining(names::add);
        names.sort(String::compareTo);
        for (String name : names) {
            JsonNode command = scripts.get(name);
            if (!name.matches("[A-Za-z0-9:_-]{1,128}") || !command.isTextual()) {
                findings.add("NODE_SCRIPT_DESCRIPTOR_INVALID:" + name);
                continue;
            }
            add(candidates, "NODE_SCRIPT", name, relative, "NPM_OFFLINE_SCRIPT",
                    roleHints(name), file, evidenceDigests, 0.98);
        }
    }

    private static void mavenModules(Path file, String relative,
            List<Map<String, Object>> candidates, Map<Path, String> evidenceDigests) throws Exception {
        add(candidates, "MAVEN_PROJECT", relative, relative, "MAVEN_OFFLINE_PROJECT",
                List.of("BUILD", "TEST", "PACKAGE"), file, evidenceDigests, 0.98);
        Matcher matcher = MAVEN_MODULE.matcher(new String(readBounded(file), StandardCharsets.UTF_8));
        while (matcher.find()) {
            String module = matcher.group(1).trim().replace('\\', '/');
            if (!safeRelative(module)) continue;
            add(candidates, "MAVEN_MODULE", module, relative, "MAVEN_OFFLINE_MODULE",
                    List.of("BUILD", "TEST", "PACKAGE"), file, evidenceDigests, 0.9);
        }
    }

    private static void javaEntrypoint(Path file, String relative,
            List<Map<String, Object>> candidates, Map<Path, String> evidenceDigests) throws Exception {
        if (JAVA_MAIN.matcher(new String(readBounded(file), StandardCharsets.UTF_8)).find()) {
            add(candidates, "JAVA_ENTRYPOINT", file.getFileName().toString(), relative,
                    "JAVA_MAIN_CLASS_REQUIRES_CLASSPATH_REVIEW", roleHints(relative),
                    file, evidenceDigests, 0.9);
        }
    }

    private static void pythonEntrypoint(Path file, String relative,
            List<Map<String, Object>> candidates, Map<Path, String> evidenceDigests) throws Exception {
        if (PYTHON_MAIN.matcher(new String(readBounded(file), StandardCharsets.UTF_8)).find()) {
            add(candidates, "PYTHON_ENTRYPOINT", relative, relative,
                    "PYTHON_SCRIPT_REQUIRES_REVIEW", roleHints(relative),
                    file, evidenceDigests, 0.9);
        }
    }

    private static void shellEntrypoint(Path file, String relative,
            List<Map<String, Object>> candidates, Map<Path, String> evidenceDigests) throws Exception {
        byte[] raw = readBounded(file);
        if (raw.length >= 2 && raw[0] == '#' && raw[1] == '!') {
            add(candidates, "SHELL_ENTRYPOINT", relative, relative,
                    "SHELL_SCRIPT_REQUIRES_REVIEW", roleHints(relative),
                    file, evidenceDigests, 0.85);
        }
    }

    private static void openApiOperations(Path file, String relative,
            List<Map<String, Object>> candidates, List<String> findings,
            Map<Path, String> evidenceDigests) throws Exception {
        JsonNode root = relative.toLowerCase(Locale.ROOT).endsWith(".json")
                ? JSON.readTree(readBounded(file)) : YAML.readTree(readBounded(file));
        if (root == null || !root.path("openapi").isTextual() || !root.path("paths").isObject()) return;
        String documentDigest = evidenceDigest(file, evidenceDigests);
        String documentId = "OPENAPI-DOC-" + Hashing.sha256(
                relative + "\u0000" + documentDigest).substring(0, 16);
        String serviceBoundaryId = "SERVICE-" + Hashing.sha256(relative).substring(0, 16);
        List<String> paths = new ArrayList<>();
        root.path("paths").fieldNames().forEachRemaining(paths::add);
        paths.sort(String::compareTo);
        for (String route : paths) {
            JsonNode pathItem = root.path("paths").path(route);
            for (String method : HTTP_METHODS.stream().sorted().toList()) {
                JsonNode operation = pathItem.path(method);
                if (!operation.isObject()) continue;
                String operationId = operation.path("operationId").asText("");
                String name = operationId.isBlank() ? method.toUpperCase(Locale.ROOT) + " " + route : operationId;
                if (operationId.isBlank()) findings.add("OPENAPI_OPERATION_ID_MISSING:" + relative + ":" + method + ":" + route);
                Map<String, Object> semantics = new LinkedHashMap<>();
                semantics.put("http_method", method.toUpperCase(Locale.ROOT));
                semantics.put("http_path", route);
                semantics.put("operation_id", operationId.isBlank() ? null : safeText(operationId));
                semantics.put("openapi_document_id", documentId);
                semantics.put("service_boundary_id", serviceBoundaryId);
                semantics.put("service_boundary_state", "DOCUMENT_SCOPED_REVIEW_REQUIRED");
                semantics.put("tags", textArray(operation.path("tags")));
                semantics.put("request_schema_refs", requestSchemaRefs(operation));
                semantics.put("request_schema_declared", requestSchemaDeclared(operation));
                List<Map<String, Object>> requestInputs = requestInputCandidates(pathItem, operation, root);
                semantics.put("request_input_candidates", requestInputs);
                semantics.put("response_statuses", fieldNames(operation.path("responses")));
                List<Map<String, Object>> responseScalars = responseScalarCandidates(operation, root);
                semantics.put("response_scalar_json_pointers", responseScalars.stream()
                        .map(value -> value.get("json_pointer").toString()).toList());
                semantics.put("response_scalar_candidates", responseScalars);
                List<Map<String, Object>> responseHeaders = responseHeaderCandidates(operation, root);
                semantics.put("response_header_candidates", responseHeaders);
                semantics.put("continuation_candidates", continuationCandidates(
                        method, operationId.isBlank() ? method.toUpperCase(Locale.ROOT) + " " + route : operationId,
                        serviceBoundaryId, operation, requestInputs,
                        responseScalars, responseHeaders));
                semantics.put("security_declared", securityRequired(operation, root));
                semantics.put("lifecycle_action", lifecycleAction(method, operationId));
                semantics.put("destructive_risk", method.equals("delete"));
                semantics.put("source_path", relative);
                add(candidates, "OPENAPI_OPERATION", name, relative, "HTTP_OPERATION_REQUIRES_SERVER",
                        roleHints(name + " " + route), file, evidenceDigests,
                        operationId.isBlank() ? 0.75 : 0.98, semantics);
            }
        }
    }

    private static void add(List<Map<String, Object>> target, String kind, String name,
            String sourcePath, String invocationType, List<String> roleHints, Path evidence,
            Map<Path, String> evidenceDigests, double confidence)
            throws Exception {
        add(target, kind, name, sourcePath, invocationType, roleHints, evidence,
                evidenceDigests, confidence, Map.of());
    }

    private static void add(List<Map<String, Object>> target, String kind, String name,
            String sourcePath, String invocationType, List<String> roleHints, Path evidence,
            Map<Path, String> evidenceDigests, double confidence, Map<String, Object> metadata)
            throws Exception {
        if (target.size() >= MAX_CANDIDATES) return;
        String material = kind + "\u0000" + sourcePath + "\u0000" + name;
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("candidate_id", "WF-" + Hashing.sha256(material).substring(0, 16));
        value.put("kind", kind);
        value.put("name", safeText(name));
        value.put("source_path", sourcePath);
        value.put("evidence_sha256", evidenceDigest(evidence, evidenceDigests));
        value.put("invocation_type", invocationType);
        value.put("role_hints", List.copyOf(new LinkedHashSet<>(roleHints)));
        value.put("confidence", confidence);
        value.put("runtime_verified", false);
        value.put("review_required", true);
        value.put("auto_execute", false);
        metadata.forEach((key, metadataValue) -> {
            if (metadataValue != null) value.put(key, metadataValue);
        });
        target.add(Map.copyOf(value));
    }

    private static List<String> textArray(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        node.forEach(value -> {
            if (value.isTextual() && !value.asText().isBlank()) values.add(safeText(value.asText()));
        });
        return values.stream().distinct().sorted().toList();
    }

    private static List<String> fieldNames(JsonNode node) {
        if (!node.isObject()) return List.of();
        List<String> values = new ArrayList<>();
        node.fieldNames().forEachRemaining(values::add);
        return values.stream().sorted().toList();
    }

    private static List<String> requestSchemaRefs(JsonNode operation) {
        Set<String> refs = new java.util.TreeSet<>();
        collectRefs(operation.path("requestBody").path("content"), refs, 0);
        return List.copyOf(refs);
    }

    private static boolean requestSchemaDeclared(JsonNode operation) {
        JsonNode requestBody = operation.path("requestBody");
        if (!requestBody.isObject()) return false;
        if (requestBody.path("$ref").isTextual()) return true;
        JsonNode content = requestBody.path("content");
        if (!content.isObject()) return false;
        for (JsonNode media : content) if (media.path("schema").isObject()
                || media.path("schema").isBoolean()) return true;
        return false;
    }

    private static List<Map<String, Object>> requestInputCandidates(
            JsonNode pathItem, JsonNode operation, JsonNode root) {
        Map<String, Map<String, Object>> inputs = new java.util.TreeMap<>();
        collectRequestParameters(pathItem.path("parameters"), root, inputs);
        collectRequestParameters(operation.path("parameters"), root, inputs);
        Map<String, String> bodyPointers = new java.util.TreeMap<>();
        JsonNode requestBody = resolveLocal(operation.path("requestBody"), root, new LinkedHashSet<>(), 0);
        if (requestBody == null) requestBody = operation.path("requestBody");
        JsonNode content = requestBody.path("required").asBoolean(false)
                ? requestBody.path("content") : com.fasterxml.jackson.databind.node.MissingNode.getInstance();
        if (content.isObject()) for (JsonNode media : content) {
            collectRequiredRequestPointers(media.path("schema"), root, "", true,
                    bodyPointers, new LinkedHashSet<>(), 0);
        }
        for (Map.Entry<String, String> bodyPointer : bodyPointers.entrySet()) {
            String pointer = bodyPointer.getKey();
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("consumer_location", "BODY");
            input.put("consumer_parameter_name", pointer);
            input.put("consumer_schema_type", bodyPointer.getValue());
            input.put("required", true);
            inputs.put("BODY\u0000" + pointer, Map.copyOf(input));
        }
        return List.copyOf(inputs.values());
    }

    private static void collectRequestParameters(JsonNode parameters, JsonNode root,
            Map<String, Map<String, Object>> inputs) {
        if (!parameters.isArray()) return;
        for (JsonNode parameterNode : parameters) {
            JsonNode parameter = resolveLocal(parameterNode, root, new LinkedHashSet<>(), 0);
            if (parameter == null || !parameter.isObject()) continue;
            String location = parameter.path("in").asText("").toUpperCase(Locale.ROOT);
            String name = parameter.path("name").asText("");
            if (!Set.of("QUERY", "HEADER").contains(location)
                    || !name.matches("[A-Za-z][A-Za-z0-9._-]{0,127}")) continue;
            String key = location + "\u0000" + name.toLowerCase(Locale.ROOT);
            boolean required = parameter.path("required").asBoolean(false);
            String paginationRole = "QUERY".equals(location) ? paginationInputRole(name) : null;
            if (!required && paginationRole == null) {
                inputs.remove(key);
                continue;
            }
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("consumer_location", location);
            input.put("consumer_parameter_name", name);
            input.put("consumer_schema_type", scalarSchemaType(resolveLocal(
                    parameter.path("schema"), root, new LinkedHashSet<>(), 0)));
            input.put("required", required);
            if (paginationRole != null) {
                input.put("continuation_role", paginationRole);
                input.put("discovery_only", true);
                input.put("auto_bind", false);
            }
            inputs.put(key, Map.copyOf(input));
        }
    }

    private static String paginationInputRole(String name) {
        String normalized = name.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        if (Set.of("cursor", "nextcursor").contains(normalized)) return "CURSOR";
        if (Set.of("pagetoken", "nextpagetoken", "continuationtoken", "nexttoken").contains(normalized))
            return "PAGE_TOKEN";
        if (Set.of("page", "pagenumber", "pageindex").contains(normalized)) return "PAGE_NUMBER";
        if ("offset".equals(normalized)) return "OFFSET";
        return null;
    }

    private static void collectRequiredRequestPointers(JsonNode node, JsonNode root, String pointer,
            boolean requiredChain, Map<String, String> result, Set<String> refs, int depth) {
        if (node == null || node.isMissingNode() || depth > 16 || result.size() >= 200) return;
        JsonNode resolved = resolveLocal(node, root, refs, depth);
        if (resolved == null || !resolved.isObject()) return;
        JsonNode allOf = resolved.path("allOf");
        if (allOf.isArray()) allOf.forEach(value -> collectRequiredRequestPointers(
                    value, root, pointer, requiredChain, result, new LinkedHashSet<>(refs), depth + 1));
        JsonNode properties = resolved.path("properties");
        if (properties.isObject()) {
            Set<String> required = new LinkedHashSet<>(textArray(resolved.path("required")));
            List<String> names = new ArrayList<>();
            properties.fieldNames().forEachRemaining(names::add);
            names.sort(String::compareTo);
            for (String name : names) collectRequiredRequestPointers(properties.path(name), root,
                    pointer + "/" + escapePointerSegment(name), requiredChain && required.contains(name),
                    result, new LinkedHashSet<>(refs), depth + 1);
            return;
        }
        String type = resolved.path("type").asText("");
        if (requiredChain && !pointer.isEmpty() && !Set.of("object", "array").contains(type))
            mergeScalarType(result, pointer, scalarSchemaType(resolved));
    }

    private static List<Map<String, Object>> responseScalarCandidates(JsonNode operation, JsonNode root) {
        Map<String, String> result = new java.util.TreeMap<>();
        JsonNode responses = operation.path("responses");
        if (responses.isObject()) responses.fields().forEachRemaining(entry -> {
            if (entry.getKey().matches("2(?:[0-9]{2}|[xX]{2})"))
                collectResponseSchemas(entry.getValue(), root, result, new LinkedHashSet<>(), 0);
        });
        return result.entrySet().stream().limit(200).map(entry -> Map.<String, Object>of(
                "json_pointer", entry.getKey(), "schema_type", entry.getValue())).toList();
    }

    private static List<Map<String, Object>> responseHeaderCandidates(JsonNode operation, JsonNode root) {
        List<Map<String, Object>> result = new ArrayList<>();
        JsonNode responses = operation.path("responses");
        if (!responses.isObject()) return List.of();
        List<String> statuses = new ArrayList<>();
        responses.fieldNames().forEachRemaining(statuses::add);
        statuses.stream().filter(status -> status.matches("2(?:[0-9]{2}|[xX]{2})"))
                .sorted().forEach(status -> {
                    JsonNode response = resolveLocal(responses.path(status), root, new LinkedHashSet<>(), 0);
                    if (response == null) return;
                    JsonNode headers = response.path("headers");
                    if (!headers.isObject()) return;
                    List<String> names = new ArrayList<>();
                    headers.fieldNames().forEachRemaining(names::add);
                    names.stream().sorted(String.CASE_INSENSITIVE_ORDER).forEach(name -> {
                        String role = CONTINUATION_RESPONSE_HEADERS.get(name.toLowerCase(Locale.ROOT));
                        if (role == null) return;
                        JsonNode header = resolveLocal(headers.path(name), root, new LinkedHashSet<>(), 0);
                        Map<String, Object> candidate = new LinkedHashMap<>();
                        candidate.put("response_status", status.toUpperCase(Locale.ROOT));
                        candidate.put("header_name", canonicalHeaderName(name));
                        candidate.put("candidate_role", role);
                        candidate.put("schema_type", header == null ? "UNKNOWN"
                                : scalarSchemaType(resolveLocal(header.path("schema"), root,
                                        new LinkedHashSet<>(), 0)));
                        candidate.put("review_required", true);
                        candidate.put("runtime_state", "NOT_RUN");
                        candidate.put("value_storage_allowed", false);
                        result.add(Map.copyOf(candidate));
                    });
                });
        return result.stream().sorted(Comparator
                .<Map<String, Object>, String>comparing(value -> value.get("response_status").toString())
                .thenComparing(value -> value.get("header_name").toString())).limit(50).toList();
    }

    private static String canonicalHeaderName(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "link" -> "Link";
            case "location" -> "Location";
            case "next-cursor" -> "Next-Cursor";
            case "retry-after" -> "Retry-After";
            case "x-continuation-token" -> "X-Continuation-Token";
            case "x-next-cursor" -> "X-Next-Cursor";
            default -> throw new IllegalArgumentException("CONTINUATION_RESPONSE_HEADER_NOT_ALLOWLISTED");
        };
    }

    private static List<Map<String, Object>> continuationCandidates(String method,
            String sourceOperationReference, String serviceBoundaryId, JsonNode operation,
            List<Map<String, Object>> requestInputs,
            List<Map<String, Object>> responseScalars, List<Map<String, Object>> responseHeaders) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> input : requestInputs) {
            if (!"QUERY".equals(input.get("consumer_location"))
                    || !input.containsKey("continuation_role")) continue;
            String inputName = input.get("consumer_parameter_name").toString();
            Map<String, Object> producer = paginationProducer(inputName, responseScalars, responseHeaders);
            String producerLocation = producer == null ? "UNRESOLVED" : producer.get("location").toString();
            String producerReference = producer == null ? "NOT_RESOLVED" : producer.get("reference").toString();
            double confidence = producer == null ? 0.50d : ((Number) producer.get("confidence")).doubleValue();
            result.add(continuation("PAGINATION", sourceOperationReference, serviceBoundaryId,
                    producerLocation, producerReference,
                    "QUERY", inputName, confidence, 100, 60, 0, 0,
                    "STOP_ON_ABSENT_NULL_EMPTY_OR_REPEATED_TOKEN", "SAME_OPERATION_DISCOVERY_ONLY"));
        }
        if (hasAcceptedResponse(operation, "202") && List.of("post", "put", "patch").contains(method)) {
            Map<String, Object> producer = asyncProducer(responseScalars, responseHeaders);
            String producerLocation = producer == null ? "UNRESOLVED" : producer.get("location").toString();
            String producerReference = producer == null ? "NOT_RESOLVED" : producer.get("reference").toString();
            double confidence = producer == null ? 0.55d : ((Number) producer.get("confidence")).doubleValue();
            result.add(continuation("ASYNC_POLL", sourceOperationReference, serviceBoundaryId,
                    producerLocation, producerReference,
                    "UNRESOLVED", "NOT_RESOLVED", confidence, 60, 300, 1_000, 10_000,
                    "REVIEWED_TERMINAL_STATUS_SET_REQUIRED", "NOT_RUN_REVIEW_REQUIRED"));
        }
        return result.stream().sorted(Comparator.<Map<String, Object>, String>comparing(
                        value -> value.get("continuation_id").toString()))
                .limit(50).toList();
    }

    private static Map<String, Object> paginationProducer(String inputName,
            List<Map<String, Object>> responseScalars, List<Map<String, Object>> responseHeaders) {
        String input = normalizedName(inputName);
        for (Map<String, Object> scalar : responseScalars) {
            String pointer = scalar.get("json_pointer").toString();
            String leaf = normalizedName(pointerLeaf(pointer));
            if (leaf.equals(input) || leaf.equals("next" + input))
                return Map.of("location", "BODY_POINTER", "reference", pointer,
                        "confidence", leaf.equals(input) ? 0.86d : 0.90d);
        }
        for (Map<String, Object> header : responseHeaders) {
            String role = header.get("candidate_role").toString();
            if ("PAGINATION_CURSOR".equals(role)) return Map.of(
                    "location", "RESPONSE_HEADER", "reference", header.get("header_name"), "confidence", 0.84d);
            if ("PAGINATION_LINK".equals(role)) return Map.of(
                    "location", "RESPONSE_HEADER", "reference", header.get("header_name"), "confidence", 0.65d);
        }
        return null;
    }

    private static Map<String, Object> asyncProducer(List<Map<String, Object>> responseScalars,
            List<Map<String, Object>> responseHeaders) {
        for (Map<String, Object> header : responseHeaders) if ("ASYNC_LOCATION".equals(header.get("candidate_role")))
            return Map.of("location", "RESPONSE_HEADER", "reference", header.get("header_name"),
                    "confidence", 0.92d);
        Set<String> accepted = Set.of("jobid", "taskid", "runid", "statusurl", "pollurl", "operationid");
        for (Map<String, Object> scalar : responseScalars) {
            String pointer = scalar.get("json_pointer").toString();
            if (accepted.contains(normalizedName(pointerLeaf(pointer)))) return Map.of(
                    "location", "BODY_POINTER", "reference", pointer, "confidence", 0.88d);
        }
        return null;
    }

    private static Map<String, Object> continuation(String kind, String sourceOperationReference,
            String serviceBoundaryId, String producerLocation, String producerReference, String consumerLocation,
            String consumerReference, double confidence, int maxIterations, int maxDurationSeconds,
            int initialDelayMillis, int maxDelayMillis, String terminationPolicy,
            String targetResolutionState) {
        String material = kind + "\u0000" + sourceOperationReference + "\u0000" + serviceBoundaryId
                + "\u0000" + producerLocation + "\u0000"
                + producerReference + "\u0000" + consumerLocation + "\u0000" + consumerReference;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("continuation_id", "CONT-" + Hashing.sha256(material).substring(0, 16));
        result.put("kind", kind);
        result.put("source_operation_reference", safeText(sourceOperationReference));
        result.put("service_boundary_id", serviceBoundaryId);
        result.put("producer_location", producerLocation);
        result.put("producer_reference", producerReference);
        result.put("consumer_location", consumerLocation);
        result.put("consumer_reference", consumerReference);
        result.put("confidence", confidence);
        result.put("max_iterations", maxIterations);
        result.put("max_duration_seconds", maxDurationSeconds);
        result.put("initial_delay_millis", initialDelayMillis);
        result.put("max_delay_millis", maxDelayMillis);
        result.put("termination_policy", terminationPolicy);
        result.put("target_resolution_state", targetResolutionState);
        result.put("same_service_only", true);
        result.put("cross_service_auto_binding_allowed", false);
        result.put("review_required", true);
        result.put("runtime_state", "NOT_RUN");
        result.put("auto_execute", false);
        result.put("score_eligible", false);
        return Map.copyOf(result);
    }

    private static boolean hasAcceptedResponse(JsonNode operation, String status) {
        JsonNode responses = operation.path("responses");
        return responses.isObject() && responses.has(status);
    }

    private static String pointerLeaf(String pointer) {
        int separator = pointer.lastIndexOf('/');
        return pointer.substring(separator + 1).replace("~1", "/").replace("~0", "~");
    }

    private static String normalizedName(String value) {
        return value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private static void collectResponseSchemas(JsonNode node, JsonNode root, Map<String, String> result,
            Set<String> refs, int depth) {
        if (node == null || node.isMissingNode() || depth > 16 || result.size() >= 200) return;
        JsonNode resolved = resolveLocal(node, root, refs, depth);
        if (resolved == null) return;
        if (resolved.isObject()) {
            JsonNode schema = resolved.get("schema");
            if (schema != null) collectScalarPointers(schema, root, "", result, new LinkedHashSet<>(), depth + 1);
            resolved.fields().forEachRemaining(entry -> {
                if (!"schema".equals(entry.getKey()))
                    collectResponseSchemas(entry.getValue(), root, result, new LinkedHashSet<>(refs), depth + 1);
            });
        } else if (resolved.isArray()) {
            resolved.forEach(value -> collectResponseSchemas(value, root, result,
                    new LinkedHashSet<>(refs), depth + 1));
        }
    }

    private static void collectScalarPointers(JsonNode node, JsonNode root, String pointer,
            Map<String, String> result, Set<String> refs, int depth) {
        if (node == null || node.isMissingNode() || depth > 16 || result.size() >= 200) return;
        JsonNode resolved = resolveLocal(node, root, refs, depth);
        if (resolved == null || !resolved.isObject()) return;
        for (String composite : List.of("allOf", "oneOf", "anyOf")) {
            JsonNode values = resolved.path(composite);
            if (values.isArray()) values.forEach(value -> collectScalarPointers(
                    value, root, pointer, result, new LinkedHashSet<>(refs), depth + 1));
        }
        JsonNode items = resolved.get("items");
        if ("array".equals(resolved.path("type").asText("")) || items != null) {
            if (items != null && (items.isObject() || items.isBoolean())) {
                String arraySegment = resolved.path("minItems").canConvertToInt()
                        && resolved.path("minItems").asInt() == 1
                        && resolved.path("maxItems").canConvertToInt()
                        && resolved.path("maxItems").asInt() == 1
                        ? SCHEMA_SINGLETON_ARRAY_POINTER_SEGMENT
                        : SINGLETON_ARRAY_POINTER_SEGMENT;
                collectScalarPointers(items, root,
                        pointer + "/" + arraySegment,
                        result, new LinkedHashSet<>(refs), depth + 1);
            }
            return;
        }
        JsonNode properties = resolved.path("properties");
        if (properties.isObject()) {
            List<String> names = new ArrayList<>();
            properties.fieldNames().forEachRemaining(names::add);
            names.sort(String::compareTo);
            for (String name : names) collectScalarPointers(properties.path(name), root,
                    pointer + "/" + escapePointerSegment(name), result,
                    new LinkedHashSet<>(refs), depth + 1);
            return;
        }
        String type = resolved.path("type").asText("");
        if (!pointer.isEmpty() && !Set.of("object", "array").contains(type))
            mergeScalarType(result, pointer, scalarSchemaType(resolved));
    }

    private static void mergeScalarType(Map<String, String> result, String pointer, String type) {
        result.merge(pointer, type, (left, right) -> left.equals(right) ? left : "UNKNOWN");
    }

    private static String scalarSchemaType(JsonNode schema) {
        if (schema == null || !schema.isObject()) return "UNKNOWN";
        JsonNode type = schema.path("type");
        if (type.isTextual()) return normalizedScalarType(type.asText());
        if (type.isArray()) {
            Set<String> types = new LinkedHashSet<>();
            type.forEach(value -> { if (value.isTextual() && !"null".equals(value.asText()))
                types.add(normalizedScalarType(value.asText())); });
            if (types.size() == 1) return types.iterator().next();
        }
        JsonNode constant = schema.path("const");
        if (constant.isValueNode()) return nodeScalarType(constant);
        JsonNode values = schema.path("enum");
        if (values.isArray() && !values.isEmpty()) {
            Set<String> types = new LinkedHashSet<>();
            values.forEach(value -> types.add(nodeScalarType(value)));
            if (types.size() == 1) return types.iterator().next();
        }
        return "UNKNOWN";
    }

    private static String normalizedScalarType(String value) {
        return Set.of("string", "integer", "number", "boolean").contains(value)
                ? value.toUpperCase(Locale.ROOT) : "UNKNOWN";
    }

    private static String nodeScalarType(JsonNode value) {
        if (value.isTextual()) return "STRING";
        if (value.isIntegralNumber()) return "INTEGER";
        if (value.isNumber()) return "NUMBER";
        if (value.isBoolean()) return "BOOLEAN";
        return "UNKNOWN";
    }

    private static JsonNode resolveLocal(JsonNode node, JsonNode root, Set<String> refs, int depth) {
        JsonNode ref = node.path("$ref");
        if (!ref.isTextual()) return node;
        String value = ref.asText();
        if (!value.startsWith("#/") || !refs.add(value) || depth > 16) return null;
        JsonNode resolved = root.at(value.substring(1));
        return resolved.isMissingNode() ? null : resolveLocal(resolved, root, refs, depth + 1);
    }

    private static String escapePointerSegment(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }

    private static boolean securityRequired(JsonNode operation, JsonNode root) {
        JsonNode security = operation.has("security") ? operation.path("security") : root.path("security");
        if (!security.isArray() || security.isEmpty()) return false;
        for (JsonNode alternative : security) {
            if (alternative.isObject() && alternative.isEmpty()) return false;
        }
        return true;
    }

    private static void collectRefs(JsonNode node, Set<String> refs, int depth) {
        if (node == null || node.isMissingNode() || depth > 12) return;
        if (node.isObject()) {
            JsonNode ref = node.get("$ref");
            if (ref != null && ref.isTextual() && ref.asText().startsWith("#/")) refs.add(ref.asText());
            node.elements().forEachRemaining(child -> collectRefs(child, refs, depth + 1));
        } else if (node.isArray()) {
            node.elements().forEachRemaining(child -> collectRefs(child, refs, depth + 1));
        }
    }

    private static String lifecycleAction(String method, String operationId) {
        String material = operationId.toLowerCase(Locale.ROOT);
        if (method.equals("delete") || contains(material, "delete", "remove", "revoke")) return "DELETE";
        if (contains(material, "update", "change", "approve", "reject")
                || method.equals("put") || method.equals("patch")) return "UPDATE";
        if (contains(material, "get", "read", "list", "find")
                || method.equals("get") || method.equals("head")) return "READ";
        if (contains(material, "create", "register", "submit", "start") || method.equals("post")) return "CREATE";
        return "INVOKE";
    }

    private static String evidenceDigest(Path file, Map<Path, String> evidenceDigests) throws Exception {
        String value = evidenceDigests.get(file);
        if (value != null) return value;
        value = Hashing.file(file);
        evidenceDigests.put(file, value);
        return value;
    }

    private static List<String> roleHints(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        List<String> roles = new ArrayList<>();
        if (contains(lower, "test", "verify", "check", "validate")) roles.add("TEST_OR_VALIDATE");
        if (contains(lower, "render", "produce", "generate", "build")) roles.add("PRODUCE_OR_RENDER");
        if (contains(lower, "readback", "read-back")) roles.add("ARTIFACT_READBACK");
        if (contains(lower, "audit", "assurance")) roles.add("AUDIT");
        if (contains(lower, "permit", "approval", "gate")) roles.add("GATE_OR_PERMIT");
        if (contains(lower, "exposure", "publish", "release")) roles.add("EXPOSURE_OR_RELEASE");
        if (contains(lower, "migration", "migrate", "rollback", "restore", "backup")) roles.add("DATA_LIFECYCLE");
        if (contains(lower, "resume", "retry", "rerun", "replay", "recover")) roles.add("RECOVERY");
        if (roles.isEmpty()) roles.add("UNCLASSIFIED_REVIEW_REQUIRED");
        return List.copyOf(roles);
    }

    private static boolean contains(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    private static byte[] readBounded(Path file) throws Exception {
        if (Files.size(file) > MAX_INSPECTED_FILE_BYTES) {
            throw new IllegalArgumentException("STATIC_WORKFLOW_FILE_TOO_LARGE");
        }
        return Files.readAllBytes(file);
    }

    private static boolean safeFile(Path root, Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        return normalized.startsWith(root)
                && Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(normalized);
    }

    private static boolean safeRelative(String value) {
        if (value.isBlank()) return false;
        Path path = Path.of(value).normalize();
        return !path.isAbsolute() && !path.startsWith("..") && !value.contains("\u0000");
    }

    private static String safeText(String value) {
        String text = value.replaceAll("[\\p{Cntrl}]", " ").strip();
        if (text.isEmpty()) return "UNNAMED";
        return text.length() <= 512 ? text : text.substring(0, 512);
    }

    private static boolean isOpenApiCandidate(String lower) {
        return (lower.endsWith(".json") || lower.endsWith(".yaml") || lower.endsWith(".yml"))
                && (lower.contains("openapi") || lower.startsWith("contracts/openapi/"));
    }

    private static boolean isMigration(String lower) {
        return lower.endsWith(".sql") && (lower.contains("/db/migration/")
                || lower.startsWith("db/migration/") || lower.contains("/migrations/")
                || lower.startsWith("migrations/"));
    }

    private static boolean isDeployment(String relative, String lower) {
        String name = Path.of(relative).getFileName().toString().toLowerCase(Locale.ROOT);
        return name.equals("dockerfile") || name.startsWith("docker-compose")
                || lower.endsWith(".service") || lower.startsWith("deploy/")
                || lower.contains("/deploy/");
    }

    private static boolean isScript(String lower) {
        return lower.endsWith(".sh") || lower.endsWith(".bash");
    }
}
