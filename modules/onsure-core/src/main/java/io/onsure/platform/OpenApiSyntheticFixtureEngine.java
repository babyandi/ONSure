package io.onsure.platform;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Bounded local-$ref OpenAPI fixture generation and response validation for an approved candidate. */
final class OpenApiSyntheticFixtureEngine {
    private static final int MAX_SPEC_BYTES = 5 * 1024 * 1024;
    private static final int MAX_BODY_BYTES = 1024 * 1024;
    private static final int MAX_DEPTH = 32;
    private static final int MAX_ERRORS = 50;
    private static final Set<String> UNSUPPORTED_ASSERTIONS = Set.of(
            "not", "if", "then", "else", "dependentRequired", "dependentSchemas",
            "patternProperties", "propertyNames", "unevaluatedProperties", "unevaluatedItems",
            "contains", "minContains", "maxContains");
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private static final ObjectMapper YAML = new ObjectMapper(YAMLFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());

    record PreparedRequest(String route, String query, byte[] body, String contentType,
            Map<String, String> headers, List<String> queryParameterNames, List<String> headerNames,
            List<String> cookieNames, String authenticationReferenceId, String authenticationValueSha256,
            String authenticationSchemeType, String requestSchemaSha256, String fixtureStrategy) { }

    record OracleResult(boolean passed, boolean blocked, boolean schemaDeclared, String responseSchemaSha256,
            List<String> errors, String strategy) { }

    private record JsonMedia(String contentType, JsonNode value) { }
    private record RequestParameters(String query, Map<String, String> headers,
            List<String> queryNames, List<String> headerNames, List<String> cookieNames) { }
    private record Authentication(Map<String, String> headers, String referenceId,
            String valueSha256, String schemeType) { }

    private final JsonNode root;
    private final JsonNode pathItem;
    private final JsonNode operation;

    OpenApiSyntheticFixtureEngine(Path sourceRoot, Map<String, Object> candidate) throws Exception {
        Path rootPath = sourceRoot.toAbsolutePath().normalize();
        String relative = candidate.getOrDefault("openapi_source_path", "").toString();
        if (relative.isBlank() || Path.of(relative).isAbsolute())
            throw new IllegalArgumentException("OPENAPI_SOURCE_PATH_INVALID");
        Path source = rootPath.resolve(relative).normalize();
        if (!source.startsWith(rootPath) || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(source))
            throw new IllegalArgumentException("OPENAPI_SOURCE_FILE_INVALID");
        String expected = candidate.getOrDefault("openapi_source_sha256", "").toString();
        if (!expected.matches("[0-9a-f]{64}") || !Hashing.file(source).equals(expected))
            throw new IllegalArgumentException("OPENAPI_SOURCE_FILE_STALE");
        long size = Files.size(source);
        if (size <= 0 || size > MAX_SPEC_BYTES) throw new IllegalArgumentException("OPENAPI_SOURCE_SIZE_INVALID");
        byte[] bytes = Files.readAllBytes(source);
        root = relative.toLowerCase(Locale.ROOT).endsWith(".json")
                ? JSON.readTree(bytes) : YAML.readTree(bytes);
        if (root == null || !root.path("openapi").isTextual())
            throw new IllegalArgumentException("OPENAPI_DOCUMENT_INVALID");
        String route = candidate.getOrDefault("http_path", "").toString();
        String method = candidate.getOrDefault("http_method", "").toString().toLowerCase(Locale.ROOT);
        pathItem = root.path("paths").path(route);
        operation = pathItem.path(method);
        if (!pathItem.isObject() || !operation.isObject())
            throw new IllegalArgumentException("OPENAPI_OPERATION_STALE");
    }

    PreparedRequest prepare(String method, String routeTemplate) throws Exception {
        return prepare(method, routeTemplate, Map.of(), Map.of(), Map.of());
    }

    PreparedRequest prepare(String method, String routeTemplate, Map<String, String> environment,
            Map<String, String> runtimeReferences) throws Exception {
        return prepare(method, routeTemplate, environment, runtimeReferences, Map.of());
    }

    PreparedRequest prepare(String method, String routeTemplate, Map<String, String> environment,
            Map<String, String> runtimeReferences, Map<String, String> boundPathValues) throws Exception {
        String route = materializePath(routeTemplate, boundPathValues);
        RequestParameters parameters = materializeRequiredParameters();
        Authentication authentication = authentication(environment, runtimeReferences);
        Map<String, String> headers = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll(parameters.headers());
        for (Map.Entry<String, String> entry : authentication.headers().entrySet())
            if (headers.putIfAbsent(entry.getKey(), entry.getValue()) != null)
                throw new IllegalArgumentException("OPENAPI_AUTHENTICATION_HEADER_COLLISION");
        byte[] body = new byte[0];
        String contentType = null;
        String schemaSha = Hashing.sha256(body);
        String strategy = "NO_REQUEST_BODY";
        if (List.of("POST", "PUT", "PATCH").contains(method)) {
            JsonNode requestBody = resolve(operation.path("requestBody"), new HashSet<>(), 0);
            JsonMedia media = jsonMedia(requestBody.path("content"));
            JsonNode schema = media.value().path("schema");
            if (schema.isMissingNode() || schema.isNull())
                throw new IllegalArgumentException("OPENAPI_REQUEST_SCHEMA_MISSING");
            JsonNode fixture = synthesize(schema, new HashSet<>(), 0);
            body = JSON.writeValueAsBytes(fixture);
            if (body.length > MAX_BODY_BYTES) throw new IllegalArgumentException("OPENAPI_FIXTURE_TOO_LARGE");
            contentType = media.contentType();
            schemaSha = schemaDigest(schema);
            strategy = "DETERMINISTIC_SYNTHETIC_FROM_OPENAPI_SCHEMA";
        }
        return new PreparedRequest(route, parameters.query(), body, contentType, Map.copyOf(headers),
                parameters.queryNames(), parameters.headerNames(), parameters.cookieNames(),
                authentication.referenceId(), authentication.valueSha256(), authentication.schemeType(),
                schemaSha, strategy);
    }

    OracleResult validateResponse(String method, int status, String responseContentType, byte[] body) throws Exception {
        JsonNode responses = operation.path("responses");
        JsonNode response = responses.path(String.valueOf(status));
        if (response.isMissingNode()) response = responses.path((status / 100) + "XX");
        if (response.isMissingNode()) response = responses.path((status / 100) + "xx");
        if (response.isMissingNode()) response = responses.path("default");
        if (response.isMissingNode()) {
            return new OracleResult(false, false, false, null, List.of("UNDECLARED_RESPONSE_STATUS"),
                    "DECLARED_RESPONSE_STATUS");
        }
        response = resolve(response, new HashSet<>(), 0);
        if ("HEAD".equals(method) || status == 204 || status == 304) {
            return new OracleResult(true, false, false, null, List.of(),
                    "DECLARED_RESPONSE_STATUS_BODY_PROHIBITED");
        }
        JsonNode content = response.path("content");
        if (!content.isObject() || content.isEmpty()) {
            return new OracleResult(true, false, false, null, List.of(), "DECLARED_RESPONSE_STATUS");
        }
        if (responseContentType == null || !responseContentType.toLowerCase(Locale.ROOT).contains("json")) {
            return new OracleResult(false, false, true, null, List.of("RESPONSE_CONTENT_TYPE_NOT_JSON"),
                    "DECLARED_RESPONSE_STATUS_AND_SCHEMA");
        }
        JsonNode schema = jsonMedia(content).value().path("schema");
        if (schema.isMissingNode() || schema.isNull()) {
            return new OracleResult(true, false, false, null, List.of(), "DECLARED_RESPONSE_STATUS");
        }
        if (body.length == 0) return new OracleResult(false, false, true, schemaDigest(schema),
                List.of("RESPONSE_BODY_REQUIRED"), "DECLARED_RESPONSE_STATUS_AND_SCHEMA");
        JsonNode value;
        try { value = JSON.readTree(body); }
        catch (Exception invalid) {
            return new OracleResult(false, false, true, schemaDigest(schema), List.of("RESPONSE_JSON_INVALID"),
                    "DECLARED_RESPONSE_STATUS_AND_SCHEMA");
        }
        List<String> errors = new ArrayList<>();
        validate(schema, value, "$", errors, new HashSet<>(), 0);
        boolean blocked = errors.stream().anyMatch(error -> error.contains("SCHEMA_KEYWORD_UNSUPPORTED")
                || error.contains("PATTERN_ORACLE_NOT_SUPPORTED"));
        return new OracleResult(errors.isEmpty(), blocked, true, schemaDigest(schema), List.copyOf(errors),
                "DECLARED_RESPONSE_STATUS_AND_SCHEMA");
    }

    private String materializePath(String template, Map<String, String> boundPathValues) throws Exception {
        String route = template;
        Map<String, JsonNode> parameters = new LinkedHashMap<>();
        collectParameters(pathItem.path("parameters"), parameters);
        collectParameters(operation.path("parameters"), parameters);
        Set<String> consumedBindings = new HashSet<>();
        int cursor = 0;
        while ((cursor = route.indexOf('{', cursor)) >= 0) {
            int end = route.indexOf('}', cursor + 1);
            if (end < 0) throw new IllegalArgumentException("OPENAPI_PATH_TEMPLATE_INVALID");
            String name = route.substring(cursor + 1, end);
            JsonNode parameter = parameters.get("path\u0000" + name);
            if (parameter == null || !"path".equals(parameter.path("in").asText()))
                throw new IllegalArgumentException("OPENAPI_PATH_PARAMETER_SCHEMA_MISSING:" + name);
            String rawValue;
            if (boundPathValues.containsKey(name)) {
                rawValue = boundPathValues.get(name);
                if (rawValue == null || rawValue.isEmpty() || rawValue.length() > 4096
                        || rawValue.chars().anyMatch(character -> character < 0x20 || character == 0x7f))
                    throw new IllegalArgumentException("OPENAPI_BOUND_PATH_PARAMETER_INVALID:" + safeCode(name));
                consumedBindings.add(name);
            } else {
                JsonNode value = synthesize(parameter.path("schema"), new HashSet<>(), 0);
                if (value.isContainerNode() || value.isNull())
                    throw new IllegalArgumentException("OPENAPI_PATH_PARAMETER_NOT_PRIMITIVE:" + name);
                rawValue = value.asText();
            }
            String encoded = percentEncode(rawValue);
            route = route.substring(0, cursor) + encoded + route.substring(end + 1);
            cursor += encoded.length();
        }
        if (!consumedBindings.equals(boundPathValues.keySet()))
            throw new IllegalArgumentException("OPENAPI_BOUND_PATH_PARAMETER_UNKNOWN");
        if (!route.startsWith("/") || route.contains("{") || route.contains("}") || route.contains("..")
                || route.contains("?") || route.contains("#") || route.contains("\\"))
            throw new IllegalArgumentException("OPENAPI_MATERIALIZED_PATH_UNSAFE");
        return route;
    }

    private void collectParameters(JsonNode values, Map<String, JsonNode> target) throws Exception {
        if (!values.isArray()) return;
        for (JsonNode raw : values) {
            JsonNode parameter = resolve(raw, new HashSet<>(), 0);
            String name = parameter.path("name").asText("");
            String location = parameter.path("in").asText("");
            if (!name.isBlank() && !location.isBlank()) target.put(location + "\u0000" + name, parameter);
        }
    }

    private RequestParameters materializeRequiredParameters() throws Exception {
        Map<String, JsonNode> parameters = new LinkedHashMap<>();
        collectParameters(pathItem.path("parameters"), parameters);
        collectParameters(operation.path("parameters"), parameters);
        Map<String, String> query = new TreeMap<>();
        Map<String, String> headers = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, String> cookies = new TreeMap<>();
        for (Map.Entry<String, JsonNode> entry : new TreeMap<>(parameters).entrySet()) {
            String location = entry.getValue().path("in").asText("");
            if (!entry.getValue().path("required").asBoolean(false) || "path".equals(location)) continue;
            String name = entry.getValue().path("name").asText();
            String style = entry.getValue().path("style").asText(switch (location) {
                case "header" -> "simple"; case "query", "cookie" -> "form"; default -> "";
            });
            if (!("query".equals(location) && "form".equals(style))
                    && !("header".equals(location) && "simple".equals(style))
                    && !("cookie".equals(location) && "form".equals(style)))
                throw new IllegalArgumentException("OPENAPI_PARAMETER_STYLE_UNSUPPORTED:"
                        + location.toUpperCase(Locale.ROOT) + ":" + safeCode(style));
            JsonNode schema = entry.getValue().path("schema");
            if (schema.isMissingNode()) throw new IllegalArgumentException(
                    "OPENAPI_REQUIRED_PARAMETER_SCHEMA_MISSING:" + location.toUpperCase(Locale.ROOT)
                            + ":" + safeCode(name));
            JsonNode fixture = synthesize(schema, new HashSet<>(), 0);
            if (fixture.isContainerNode() || fixture.isNull()) throw new IllegalArgumentException(
                    "OPENAPI_REQUIRED_PARAMETER_NOT_PRIMITIVE:" + location.toUpperCase(Locale.ROOT)
                            + ":" + safeCode(name));
            String value = fixture.asText();
            switch (location) {
                case "query" -> query.put(name, value);
                case "header" -> {
                    requireSafeHeaderName(name, false);
                    headers.put(name, requireSafeHeaderValue(value));
                }
                case "cookie" -> {
                    requireSafeHeaderName(name, false);
                    cookies.put(name, value);
                }
                default -> throw new IllegalArgumentException("OPENAPI_REQUIRED_PARAMETER_LOCATION_UNSUPPORTED:"
                        + location.toUpperCase(Locale.ROOT));
            }
        }
        if (!cookies.isEmpty()) headers.put("Cookie", cookies.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + percentEncode(entry.getValue()))
                .collect(java.util.stream.Collectors.joining("; ")));
        String queryString = query.entrySet().stream()
                .map(entry -> percentEncode(entry.getKey()) + "=" + percentEncode(entry.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
        return new RequestParameters(queryString, Map.copyOf(headers), List.copyOf(query.keySet()),
                List.copyOf(headers.keySet()), List.copyOf(cookies.keySet()));
    }

    private Authentication authentication(Map<String, String> environment,
            Map<String, String> runtimeReferences) {
        JsonNode security = operation.has("security") ? operation.path("security") : root.path("security");
        if (!security.isArray() || security.isEmpty()) return new Authentication(Map.of(), null, null, null);
        for (JsonNode alternative : security) if (alternative.isObject() && alternative.isEmpty())
            return new Authentication(Map.of(), null, null, null);
        JsonNode requirement = security.get(0);
        if (!requirement.isObject()) throw new IllegalArgumentException("OPENAPI_SECURITY_REQUIREMENT_INVALID");
        List<String> schemes = new ArrayList<>(); requirement.fieldNames().forEachRemaining(schemes::add);
        Collections.sort(schemes);
        if (schemes.size() != 1) throw new IllegalArgumentException("OPENAPI_MULTI_SCHEME_AUTH_REVIEW_REQUIRED");
        String reference = runtimeReferences.get("authentication");
        if (reference == null || !reference.matches("env:[A-Z][A-Z0-9_]{1,127}"))
            throw new IllegalArgumentException("OPENAPI_AUTHENTICATION_CONTEXT_NOT_MATERIALIZED");
        String secret = environment.get(reference.substring(4));
        if (secret == null || secret.isBlank() || secret.length() > 8192)
            throw new IllegalArgumentException("OPENAPI_AUTHENTICATION_VALUE_NOT_CONFIGURED");
        requireSafeHeaderValue(secret);
        JsonNode scheme = resolve(root.path("components").path("securitySchemes").path(schemes.get(0)),
                new HashSet<>(), 0);
        String type = scheme.path("type").asText("");
        String headerName;
        String headerValue;
        String schemeType;
        if ("http".equals(type) && "bearer".equalsIgnoreCase(scheme.path("scheme").asText())) {
            headerName = "Authorization"; headerValue = "Bearer " + secret; schemeType = "HTTP_BEARER";
        } else if ("http".equals(type) && "basic".equalsIgnoreCase(scheme.path("scheme").asText())) {
            headerName = "Authorization";
            headerValue = "Basic " + Base64.getEncoder().encodeToString(secret.getBytes(StandardCharsets.UTF_8));
            schemeType = "HTTP_BASIC";
        } else if (Set.of("oauth2", "openIdConnect").contains(type)) {
            headerName = "Authorization"; headerValue = "Bearer " + secret; schemeType = "OAUTH_BEARER";
        } else if ("apiKey".equals(type) && "header".equals(scheme.path("in").asText())) {
            headerName = scheme.path("name").asText(); requireSafeHeaderName(headerName, true);
            headerValue = secret; schemeType = "API_KEY_HEADER";
        } else throw new IllegalArgumentException("OPENAPI_AUTHENTICATION_SCHEME_UNSUPPORTED");
        return new Authentication(Map.of(headerName, headerValue), reference,
                Hashing.sha256(secret), schemeType);
    }

    private JsonNode synthesize(JsonNode raw, Set<String> refs, int depth) throws Exception {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("OPENAPI_SCHEMA_DEPTH_EXCEEDED");
        JsonNode schema = resolve(raw, refs, depth);
        if (schema.isBoolean()) {
            if (!schema.asBoolean()) throw new IllegalArgumentException("OPENAPI_FALSE_SCHEMA_BLOCKED");
            return JSON.createObjectNode();
        }
        for (String keyword : UNSUPPORTED_ASSERTIONS) if (schema.has(keyword))
            throw new IllegalArgumentException("OPENAPI_FIXTURE_KEYWORD_UNSUPPORTED:"
                    + keyword.toUpperCase(Locale.ROOT));
        if (schema.path("const").isValueNode()) return schema.path("const").deepCopy();
        if (schema.path("enum").isArray() && !schema.path("enum").isEmpty()) {
            List<JsonNode> safe = new ArrayList<>();
            schema.path("enum").forEach(value -> { if (safeEnum(value)) safe.add(value); });
            if (safe.isEmpty()) throw new IllegalArgumentException("OPENAPI_ENUM_REQUIRES_REVIEW");
            safe.sort((left, right) -> left.toString().compareTo(right.toString()));
            return safe.get(0).deepCopy();
        }
        if (schema.path("allOf").isArray()) {
            ObjectNode merged = JSON.createObjectNode();
            for (JsonNode part : schema.path("allOf")) {
                JsonNode value = synthesize(part, new HashSet<>(refs), depth + 1);
                if (!value.isObject()) throw new IllegalArgumentException("OPENAPI_ALLOF_FIXTURE_NOT_OBJECT");
                value.fields().forEachRemaining(entry -> merged.set(entry.getKey(), entry.getValue()));
            }
            return merged;
        }
        JsonNode alternatives = schema.path("oneOf").isArray() ? schema.path("oneOf") : schema.path("anyOf");
        if (alternatives.isArray() && !alternatives.isEmpty())
            return synthesize(alternatives.get(0), new HashSet<>(refs), depth + 1);
        String type = schemaType(schema);
        if (type.isBlank() && schema.path("properties").isObject()) type = "object";
        return switch (type) {
            case "object" -> synthesizeObject(schema, refs, depth);
            case "array" -> synthesizeArray(schema, refs, depth);
            case "integer" -> JSON.getNodeFactory().numberNode(number(schema, true).longValueExact());
            case "number" -> JSON.getNodeFactory().numberNode(number(schema, false));
            case "boolean" -> JSON.getNodeFactory().booleanNode(false);
            case "string" -> JSON.getNodeFactory().textNode(stringValue(schema));
            case "null" -> JSON.getNodeFactory().nullNode();
            default -> throw new IllegalArgumentException("OPENAPI_SCHEMA_TYPE_UNSUPPORTED:" + type);
        };
    }

    private JsonNode synthesizeObject(JsonNode schema, Set<String> refs, int depth) throws Exception {
        ObjectNode result = JSON.createObjectNode();
        List<String> required = new ArrayList<>();
        schema.path("required").forEach(value -> required.add(value.asText()));
        Collections.sort(required);
        int minimum = Math.max(0, schema.path("minProperties").asInt(0));
        if (minimum > required.size())
            throw new IllegalArgumentException("OPENAPI_MIN_PROPERTIES_FIXTURE_REVIEW_REQUIRED");
        if (schema.path("maxProperties").isInt() && required.size() > schema.path("maxProperties").asInt())
            throw new IllegalArgumentException("OPENAPI_OBJECT_BOUNDS_INVALID");
        for (String name : required) {
            JsonNode property = schema.path("properties").path(name);
            if (property.isMissingNode()) throw new IllegalArgumentException("OPENAPI_REQUIRED_PROPERTY_SCHEMA_MISSING:" + name);
            JsonNode resolved = resolve(property, new HashSet<>(refs), depth + 1);
            if (resolved.path("readOnly").asBoolean(false))
                throw new IllegalArgumentException("OPENAPI_REQUIRED_READONLY_PROPERTY:" + name);
            result.set(name, synthesize(property, new HashSet<>(refs), depth + 1));
        }
        return result;
    }

    private JsonNode synthesizeArray(JsonNode schema, Set<String> refs, int depth) throws Exception {
        ArrayNode result = JSON.createArrayNode();
        int minimum = Math.max(0, schema.path("minItems").asInt(0));
        if (minimum > 100) throw new IllegalArgumentException("OPENAPI_ARRAY_FIXTURE_TOO_LARGE");
        if (schema.path("maxItems").isInt() && minimum > schema.path("maxItems").asInt())
            throw new IllegalArgumentException("OPENAPI_ARRAY_BOUNDS_INVALID");
        if (schema.path("uniqueItems").asBoolean(false) && minimum > 1)
            throw new IllegalArgumentException("OPENAPI_UNIQUE_ARRAY_FIXTURE_REVIEW_REQUIRED");
        for (int index = 0; index < minimum; index++)
            result.add(synthesize(schema.path("items"), new HashSet<>(refs), depth + 1));
        return result;
    }

    private static BigDecimal number(JsonNode schema, boolean integer) {
        BigDecimal value = BigDecimal.ZERO;
        if (schema.path("minimum").isNumber()) value = schema.path("minimum").decimalValue();
        if (schema.path("exclusiveMinimum").isNumber()) value = schema.path("exclusiveMinimum").decimalValue().add(BigDecimal.ONE);
        if (schema.path("multipleOf").isNumber()) {
            BigDecimal multiple = schema.path("multipleOf").decimalValue();
            if (multiple.signum() <= 0) throw new IllegalArgumentException("OPENAPI_MULTIPLE_OF_INVALID");
            value = value.divide(multiple, 0, java.math.RoundingMode.CEILING).multiply(multiple);
        }
        if (schema.path("maximum").isNumber() && value.compareTo(schema.path("maximum").decimalValue()) > 0)
            throw new IllegalArgumentException("OPENAPI_NUMERIC_BOUNDS_INVALID");
        if (integer) value = value.setScale(0, java.math.RoundingMode.CEILING);
        return value;
    }

    private static String stringValue(JsonNode schema) {
        if (schema.path("pattern").isTextual())
            throw new IllegalArgumentException("OPENAPI_PATTERN_FIXTURE_REVIEW_REQUIRED");
        String value = switch (schema.path("format").asText("")) {
            case "uuid" -> "00000000-0000-4000-8000-000000000001";
            case "date" -> "2000-01-01";
            case "date-time" -> "2000-01-01T00:00:00Z";
            case "email" -> "onsure@example.invalid";
            case "hostname" -> "onsure.invalid";
            case "ipv4" -> "127.0.0.1";
            case "uri", "url" -> "https://example.invalid/onsure-synthetic";
            default -> "ONSURE_SYNTHETIC";
        };
        int minimum = Math.max(0, schema.path("minLength").asInt(0));
        int maximum = schema.path("maxLength").isInt() ? schema.path("maxLength").asInt() : 4096;
        if (minimum > 4096 || maximum < minimum) throw new IllegalArgumentException("OPENAPI_STRING_BOUNDS_INVALID");
        if (value.length() > maximum) value = value.substring(0, maximum);
        if (value.length() < minimum) value += "X".repeat(minimum - value.length());
        return value;
    }

    private void validate(JsonNode raw, JsonNode value, String path, List<String> errors,
            Set<String> refs, int depth) throws Exception {
        if (errors.size() >= MAX_ERRORS) return;
        if (depth > MAX_DEPTH) { errors.add(path + ":SCHEMA_DEPTH_EXCEEDED"); return; }
        JsonNode schema = resolve(raw, refs, depth);
        if (schema.isBoolean()) {
            if (!schema.asBoolean()) errors.add(path + ":FALSE_SCHEMA");
            return;
        }
        for (String keyword : UNSUPPORTED_ASSERTIONS) if (schema.has(keyword)) {
            errors.add(path + ":SCHEMA_KEYWORD_UNSUPPORTED:" + keyword.toUpperCase(Locale.ROOT));
            return;
        }
        if (value.isNull() && schema.path("nullable").asBoolean(false)) return;
        if (schema.path("allOf").isArray()) {
            for (JsonNode part : schema.path("allOf")) validate(part, value, path, errors, new HashSet<>(refs), depth + 1);
            return;
        }
        if (schema.path("oneOf").isArray() || schema.path("anyOf").isArray()) {
            JsonNode choices = schema.path("oneOf").isArray() ? schema.path("oneOf") : schema.path("anyOf");
            int matches = 0;
            for (JsonNode choice : choices) {
                List<String> candidateErrors = new ArrayList<>();
                validate(choice, value, path, candidateErrors, new HashSet<>(refs), depth + 1);
                if (candidateErrors.isEmpty()) matches++;
            }
            boolean one = schema.path("oneOf").isArray();
            if ((one && matches != 1) || (!one && matches == 0)) errors.add(path + ":COMPOSITION_MISMATCH");
            return;
        }
        if (schema.has("const") && !schema.path("const").equals(value)) errors.add(path + ":CONST_MISMATCH");
        if (schema.path("enum").isArray()) {
            boolean found = false;
            for (JsonNode allowed : schema.path("enum")) if (allowed.equals(value)) { found = true; break; }
            if (!found) errors.add(path + ":ENUM_MISMATCH");
        }
        String type = schemaType(schema);
        if (type.isBlank() && schema.path("properties").isObject()) type = "object";
        boolean correct = switch (type) {
            case "object" -> value.isObject(); case "array" -> value.isArray();
            case "integer" -> value.isIntegralNumber(); case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean(); case "string" -> value.isTextual();
            case "null" -> value.isNull(); default -> true;
        };
        if (!correct) { errors.add(path + ":TYPE_MISMATCH:" + type); return; }
        if ("object".equals(type)) validateObject(schema, value, path, errors, refs, depth);
        if ("array".equals(type)) {
            if (schema.has("minItems") && value.size() < schema.path("minItems").asInt()) errors.add(path + ":MIN_ITEMS");
            if (schema.has("maxItems") && value.size() > schema.path("maxItems").asInt()) errors.add(path + ":MAX_ITEMS");
            if (schema.path("uniqueItems").asBoolean(false)) {
                Set<JsonNode> unique = new HashSet<>();
                for (JsonNode item : value) if (!unique.add(item)) { errors.add(path + ":UNIQUE_ITEMS"); break; }
            }
            int index = 0;
            for (JsonNode item : value) validate(schema.path("items"), item, path + "[" + index++ + "]",
                    errors, new HashSet<>(refs), depth + 1);
        }
        if ("string".equals(type)) {
            int length = value.asText().length();
            if (schema.has("minLength") && length < schema.path("minLength").asInt()) errors.add(path + ":MIN_LENGTH");
            if (schema.has("maxLength") && length > schema.path("maxLength").asInt()) errors.add(path + ":MAX_LENGTH");
            if (schema.path("pattern").isTextual()) errors.add(path + ":PATTERN_ORACLE_NOT_SUPPORTED");
        }
        if (("integer".equals(type) || "number".equals(type)) && value.isNumber()) {
            BigDecimal actual = value.decimalValue();
            if (schema.path("minimum").isNumber()
                    && actual.compareTo(schema.path("minimum").decimalValue()) < 0) errors.add(path + ":MINIMUM");
            if (schema.path("maximum").isNumber()
                    && actual.compareTo(schema.path("maximum").decimalValue()) > 0) errors.add(path + ":MAXIMUM");
            if (schema.path("exclusiveMinimum").isNumber()
                    && actual.compareTo(schema.path("exclusiveMinimum").decimalValue()) <= 0) errors.add(path + ":EXCLUSIVE_MINIMUM");
            if (schema.path("exclusiveMaximum").isNumber()
                    && actual.compareTo(schema.path("exclusiveMaximum").decimalValue()) >= 0) errors.add(path + ":EXCLUSIVE_MAXIMUM");
            if (schema.path("multipleOf").isNumber()) {
                BigDecimal multiple = schema.path("multipleOf").decimalValue();
                if (multiple.signum() <= 0 || actual.remainder(multiple).signum() != 0)
                    errors.add(path + ":MULTIPLE_OF");
            }
        }
    }

    private void validateObject(JsonNode schema, JsonNode value, String path, List<String> errors,
            Set<String> refs, int depth) throws Exception {
        if (schema.has("minProperties") && value.size() < schema.path("minProperties").asInt())
            errors.add(path + ":MIN_PROPERTIES");
        if (schema.has("maxProperties") && value.size() > schema.path("maxProperties").asInt())
            errors.add(path + ":MAX_PROPERTIES");
        for (JsonNode required : schema.path("required"))
            if (!value.has(required.asText())) errors.add(path + ":REQUIRED_MISSING:" + required.asText());
        Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
        while (fields.hasNext() && errors.size() < MAX_ERRORS) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode property = schema.path("properties").path(field.getKey());
            if (!property.isMissingNode()) validate(property, field.getValue(), path + "." + field.getKey(),
                    errors, new HashSet<>(refs), depth + 1);
            else if (schema.path("additionalProperties").isObject())
                validate(schema.path("additionalProperties"), field.getValue(), path + "." + field.getKey(),
                        errors, new HashSet<>(refs), depth + 1);
            else if (schema.path("additionalProperties").isBoolean()
                    && !schema.path("additionalProperties").asBoolean())
                errors.add(path + ":ADDITIONAL_PROPERTY:" + field.getKey());
        }
    }

    private JsonNode resolve(JsonNode node, Set<String> refs, int depth) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("OPENAPI_REF_DEPTH_EXCEEDED");
        if (!node.isObject() || !node.path("$ref").isTextual()) return node;
        String ref = node.path("$ref").asText();
        if (!ref.startsWith("#/")) throw new IllegalArgumentException("OPENAPI_EXTERNAL_REF_BLOCKED");
        if (!refs.add(ref)) throw new IllegalArgumentException("OPENAPI_REF_CYCLE");
        JsonNode resolved = root.at(ref.substring(1));
        if (resolved.isMissingNode()) throw new IllegalArgumentException("OPENAPI_REF_NOT_FOUND");
        return resolve(resolved, refs, depth + 1);
    }

    private static JsonMedia jsonMedia(JsonNode content) {
        if (!content.isObject()) throw new IllegalArgumentException("OPENAPI_JSON_MEDIA_TYPE_MISSING");
        JsonNode exact = content.path("application/json");
        if (!exact.isMissingNode()) return new JsonMedia("application/json", exact);
        List<String> names = new ArrayList<>();
        content.fieldNames().forEachRemaining(names::add);
        Collections.sort(names);
        for (String name : names) if (name.toLowerCase(Locale.ROOT).endsWith("+json"))
            return new JsonMedia(name, content.path(name));
        throw new IllegalArgumentException("OPENAPI_JSON_MEDIA_TYPE_MISSING");
    }

    private static boolean safeEnum(JsonNode value) {
        if (value.isBoolean() || value.isNumber()) return true;
        return value.isTextual() && value.asText().matches("[A-Za-z0-9_.-]{1,64}");
    }

    private static String schemaType(JsonNode schema) {
        JsonNode type = schema.path("type");
        if (type.isTextual()) return type.asText();
        if (type.isArray()) {
            for (JsonNode candidate : type) if (candidate.isTextual() && !"null".equals(candidate.asText()))
                return candidate.asText();
            if (type.size() > 0 && type.get(0).isTextual()) return type.get(0).asText();
        }
        return "";
    }

    private static String percentEncode(String value) {
        StringBuilder encoded = new StringBuilder();
        for (byte next : value.getBytes(StandardCharsets.UTF_8)) {
            int unsigned = next & 0xff;
            char character = (char) unsigned;
            if ((character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9') || "-._~".indexOf(character) >= 0) {
                encoded.append(character);
            } else encoded.append('%').append(String.format(Locale.ROOT, "%02X", unsigned));
        }
        return encoded.toString();
    }

    private static void requireSafeHeaderName(String name, boolean authentication) {
        if (name == null || !name.matches("[!#$%&'*+.^_`|~A-Za-z0-9-]{1,128}"))
            throw new IllegalArgumentException("OPENAPI_HEADER_NAME_INVALID");
        String normalized = name.toLowerCase(Locale.ROOT);
        Set<String> prohibited = Set.of("host", "connection", "content-length", "transfer-encoding",
                "upgrade", "proxy-authorization", "proxy-authenticate", "te", "trailer");
        if (prohibited.contains(normalized) || (!authentication
                && Set.of("authorization", "cookie", "accept", "content-type").contains(normalized)))
            throw new IllegalArgumentException("OPENAPI_HEADER_NAME_PROHIBITED:" + safeCode(name));
    }

    private static String requireSafeHeaderValue(String value) {
        if (value == null || value.isEmpty() || value.length() > 8192
                || value.chars().anyMatch(character -> character < 0x20 || character == 0x7f))
            throw new IllegalArgumentException("OPENAPI_HEADER_VALUE_INVALID");
        return value;
    }

    private static String safeCode(String value) {
        String normalized = value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_.-]", "_");
        return normalized.substring(0, Math.min(80, normalized.length()));
    }

    private String schemaDigest(JsonNode schema) throws Exception {
        return Hashing.sha256(JSON.writeValueAsBytes(resolve(schema, new HashSet<>(), 0)));
    }
}
