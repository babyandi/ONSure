package io.onsure.platform;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Recomputes product-neutral producer/consumer lineage from a target-generated portable receipt. */
final class WorkflowLineageReceiptVerifier {
    static final String CONTRACT = "ONSURE_PORTABLE_WORKFLOW_LINEAGE_V1";
    static final Path RECEIPT_PATH = Path.of(".onsure", "workflow-lineage.v1.json");
    private static final long MAX_RECEIPT_BYTES = 4L * 1024 * 1024;
    private static final long MAX_EVIDENCE_FILE_BYTES = 64L * 1024 * 1024;
    private static final int MAX_HANDOFFS = 256;
    private static final Set<String> SUPPORTED_SCHEMA_KEYWORDS = Set.of(
            "$schema", "$id", "title", "description", "type", "required", "properties",
            "additionalProperties", "items", "enum", "const", "minLength", "maxLength",
            "minimum", "maximum");

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION.mappedFeature());

    UniversalValidationRunner.StepExecution verify(Path snapshotRoot) {
        List<String> errors = new ArrayList<>();
        List<String> limitations = new ArrayList<>();
        Path root = snapshotRoot.toAbsolutePath().normalize();
        Path receipt = safeFile(root, RECEIPT_PATH, MAX_RECEIPT_BYTES, "RECEIPT", errors);
        if (receipt == null) return result(errors, limitations);
        try {
            JsonNode value = mapper.readTree(readBounded(receipt, MAX_RECEIPT_BYTES));
            if (!CONTRACT.equals(value.path("contract").asText())) errors.add("LINEAGE_CONTRACT_INVALID");
            requireId(value, "run_id", "RUN_ID_INVALID", errors);
            String runId = value.path("run_id").asText();

            JsonNode request = requireObject(value, "request", errors);
            JsonNode artifact = requireObject(value, "artifact", errors);
            Path requestFile = evidenceFile(root, request, "path", "REQUEST", errors);
            Path artifactFile = evidenceFile(root, artifact, "path", "ARTIFACT", errors);
            Path schemaFile = evidenceFile(root, artifact, "schema_path", "SCHEMA", errors);
            byte[] requestBytes = evidenceBytes(requestFile, "REQUEST", errors);
            byte[] artifactBytes = evidenceBytes(artifactFile, "ARTIFACT", errors);
            byte[] schemaBytes = evidenceBytes(schemaFile, "SCHEMA", errors);
            if (!"application/json".equals(artifact.path("media_type").asText())) {
                errors.add("ARTIFACT_MEDIA_TYPE_UNSUPPORTED");
            }
            String requestSha = verifiedDigest(requestBytes, request, "sha256", "REQUEST", errors);
            String artifactSha = verifiedDigest(artifactBytes, artifact, "sha256", "ARTIFACT", errors);
            String schemaSha = verifiedDigest(schemaBytes, artifact, "schema_sha256", "SCHEMA", errors);

            validateArtifactSchema(artifactBytes, schemaBytes, limitations, errors);
            validateHandoffs(value.path("handoffs"), artifactSha, schemaSha, errors);
            validatePermitAndDecisions(value, runId, requestSha, artifactSha, errors);
            validateTimes(value, errors);

            String report = "receipt=" + RECEIPT_PATH.toString().replace('\\', '/')
                    + "\nrequest_sha256=" + requestSha + "\nartifact_sha256=" + artifactSha
                    + "\nschema_sha256=" + schemaSha + "\nhandoff_count="
                    + (value.path("handoffs").isArray() ? value.path("handoffs").size() : 0)
                    + "\nerrors=" + errors + "\nlimitations=" + limitations;
            return result(errors, limitations, report);
        } catch (Exception error) {
            errors.add("LINEAGE_RECEIPT_READ_ERROR:" + error.getClass().getSimpleName());
            return result(errors, limitations);
        }
    }

    private void validateArtifactSchema(
            byte[] artifactBytes, byte[] schemaBytes, List<String> limitations, List<String> errors) {
        if (artifactBytes == null || schemaBytes == null) return;
        try {
            JsonNode artifact = mapper.readTree(artifactBytes);
            JsonNode schema = mapper.readTree(schemaBytes);
            collectUnsupportedKeywords(schema, "#", limitations);
            validateSchemaNode(schema, artifact, "$", errors);
        } catch (Exception error) {
            errors.add("ARTIFACT_SCHEMA_READ_ERROR:" + error.getClass().getSimpleName());
        }
    }

    private static void validateSchemaNode(JsonNode schema, JsonNode value, String path, List<String> errors) {
        if (!schema.isObject()) {
            errors.add("SCHEMA_NODE_INVALID:" + path);
            return;
        }
        if (schema.has("const") && !schema.get("const").equals(value)) errors.add("SCHEMA_CONST_MISMATCH:" + path);
        if (schema.path("enum").isArray()) {
            boolean matched = false;
            for (JsonNode candidate : schema.path("enum")) if (candidate.equals(value)) matched = true;
            if (!matched) errors.add("SCHEMA_ENUM_MISMATCH:" + path);
        }
        String type = schema.path("type").asText();
        if (!type.isBlank() && !matchesType(type, value)) {
            errors.add("SCHEMA_TYPE_MISMATCH:" + path + ":" + type);
            return;
        }
        if (value.isObject()) {
            JsonNode required = schema.path("required");
            if (required.isArray()) for (JsonNode field : required) {
                if (!field.isTextual() || !value.has(field.asText())) {
                    errors.add("SCHEMA_REQUIRED_MISSING:" + path + ":" + field.asText());
                }
            }
            JsonNode properties = schema.path("properties");
            if (properties.isObject()) properties.fields().forEachRemaining(entry -> {
                if (value.has(entry.getKey())) validateSchemaNode(
                        entry.getValue(), value.get(entry.getKey()), path + "/" + entry.getKey(), errors);
            });
            if (schema.has("additionalProperties") && !schema.path("additionalProperties").asBoolean(true)
                    && properties.isObject()) {
                value.fieldNames().forEachRemaining(name -> {
                    if (!properties.has(name)) errors.add("SCHEMA_ADDITIONAL_PROPERTY:" + path + ":" + name);
                });
            }
        }
        if (value.isArray() && schema.path("items").isObject()) {
            for (int index = 0; index < value.size(); index++) {
                validateSchemaNode(schema.path("items"), value.get(index), path + "/" + index, errors);
            }
        }
        if (value.isTextual()) {
            if (schema.has("minLength") && value.textValue().length() < schema.path("minLength").asInt()) {
                errors.add("SCHEMA_MIN_LENGTH:" + path);
            }
            if (schema.has("maxLength") && value.textValue().length() > schema.path("maxLength").asInt()) {
                errors.add("SCHEMA_MAX_LENGTH:" + path);
            }
        }
        if (value.isNumber()) {
            if (schema.has("minimum") && value.decimalValue().compareTo(schema.path("minimum").decimalValue()) < 0) {
                errors.add("SCHEMA_MINIMUM:" + path);
            }
            if (schema.has("maximum") && value.decimalValue().compareTo(schema.path("maximum").decimalValue()) > 0) {
                errors.add("SCHEMA_MAXIMUM:" + path);
            }
        }
    }

    private static boolean matchesType(String type, JsonNode value) {
        return switch (type) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "null" -> value.isNull();
            default -> false;
        };
    }

    private static void collectUnsupportedKeywords(JsonNode node, String path, List<String> limitations) {
        if (!node.isObject()) return;
        node.fields().forEachRemaining(entry -> {
            if (!SUPPORTED_SCHEMA_KEYWORDS.contains(entry.getKey())) {
                limitations.add("SCHEMA_KEYWORD_UNSUPPORTED:" + path + ":" + entry.getKey());
            }
            if (entry.getKey().equals("properties") && entry.getValue().isObject()) {
                entry.getValue().fields().forEachRemaining(property -> collectUnsupportedKeywords(
                        property.getValue(), path + "/properties/" + property.getKey(), limitations));
            } else if (entry.getKey().equals("items")) {
                collectUnsupportedKeywords(entry.getValue(), path + "/items", limitations);
            }
        });
    }

    private static void validateHandoffs(
            JsonNode handoffs, String artifactSha, String schemaSha, List<String> errors) {
        if (!handoffs.isArray() || handoffs.isEmpty()) {
            errors.add("HANDOFFS_REQUIRED");
            return;
        }
        if (handoffs.size() > MAX_HANDOFFS) errors.add("HANDOFF_LIMIT_EXCEEDED");
        Set<String> edges = new HashSet<>();
        for (int index = 0; index < Math.min(handoffs.size(), MAX_HANDOFFS); index++) {
            JsonNode handoff = handoffs.get(index);
            String producer = handoff.path("producer").asText();
            String consumer = handoff.path("consumer").asText();
            if (!validName(producer) || !validName(consumer) || producer.equals(consumer)) {
                errors.add("HANDOFF_ACTORS_INVALID:" + index);
            }
            if (!edges.add(producer + "\u0000" + consumer)) errors.add("HANDOFF_REUSED:" + index);
            checkDigest(handoff, "producer_output_sha256", artifactSha, "PRODUCER_OUTPUT", index, errors);
            checkDigest(handoff, "consumer_input_sha256", artifactSha, "CONSUMER_INPUT", index, errors);
            checkDigest(handoff, "artifact_sha256", artifactSha, "HANDOFF_ARTIFACT", index, errors);
            checkDigest(handoff, "producer_schema_sha256", schemaSha, "PRODUCER_SCHEMA", index, errors);
            checkDigest(handoff, "consumer_schema_sha256", schemaSha, "CONSUMER_SCHEMA", index, errors);
        }
    }

    private static void validatePermitAndDecisions(
            JsonNode value, String runId, String requestSha, String artifactSha, List<String> errors) {
        JsonNode permit = requireObject(value, "permit", errors);
        String permitId = permit.path("permit_id").asText();
        if (!validName(permitId)) errors.add("PERMIT_ID_INVALID");
        if (!runId.equals(permit.path("run_id").asText())) errors.add("PERMIT_RUN_ID_MISMATCH");
        checkDigest(permit, "request_sha256", requestSha, "PERMIT_REQUEST", -1, errors);
        checkDigest(permit, "artifact_sha256", artifactSha, "PERMIT_ARTIFACT", -1, errors);
        if (!"ALLOW".equals(permit.path("decision").asText())) errors.add("PERMIT_NOT_ALLOWED");

        JsonNode readBack = requireObject(value, "read_back", errors);
        checkDigest(readBack, "artifact_sha256", artifactSha, "READ_BACK_ARTIFACT", -1, errors);
        for (String role : List.of("tester", "audit")) {
            JsonNode decision = requireObject(value, role, errors);
            if (!"PASS".equals(decision.path("decision").asText())) {
                errors.add(role.toUpperCase(java.util.Locale.ROOT) + "_NOT_PASS");
            }
            checkDigest(decision, "artifact_sha256", artifactSha,
                    role.toUpperCase(java.util.Locale.ROOT) + "_ARTIFACT", -1, errors);
        }
        JsonNode exposure = requireObject(value, "exposure", errors);
        String expected = exposure.path("expected_decision").asText();
        String actual = exposure.path("actual_decision").asText();
        if (!Set.of("ALLOW", "DENY").contains(expected) || !expected.equals(actual)) {
            errors.add("EXPOSURE_DECISION_MISMATCH");
        }
        checkDigest(exposure, "artifact_sha256", artifactSha, "EXPOSURE_ARTIFACT", -1, errors);
        if (!permitId.equals(exposure.path("permit_id").asText())) errors.add("EXPOSURE_PERMIT_MISMATCH");
    }

    private static void validateTimes(JsonNode value, List<String> errors) {
        try {
            Instant generated = Instant.parse(value.path("generated_at").asText());
            JsonNode permit = value.path("permit");
            Instant issued = Instant.parse(permit.path("issued_at").asText());
            Instant expires = Instant.parse(permit.path("expires_at").asText());
            if (expires.isBefore(issued) || generated.isBefore(issued) || generated.isAfter(expires)) {
                errors.add("PERMIT_TIME_WINDOW_INVALID");
            }
            if (generated.isAfter(Instant.now().plusSeconds(300))) errors.add("LINEAGE_TIMESTAMP_IN_FUTURE");
        } catch (Exception error) {
            errors.add("LINEAGE_TIMESTAMP_INVALID");
        }
    }

    private static Path evidenceFile(
            Path root, JsonNode owner, String field, String label, List<String> errors) {
        String value = owner.path(field).asText();
        if (value.isBlank()) {
            errors.add(label + "_PATH_REQUIRED");
            return null;
        }
        if (value.length() > 1024) {
            errors.add(label + "_PATH_INVALID");
            return null;
        }
        try {
            return safeFile(root, Path.of(value), MAX_EVIDENCE_FILE_BYTES, label, errors);
        } catch (Exception error) {
            errors.add(label + "_PATH_INVALID");
            return null;
        }
    }

    private static Path safeFile(
            Path root, Path relative, long maxBytes, String label, List<String> errors) {
        try {
            if (relative.isAbsolute() || relative.normalize().startsWith("..")) {
                errors.add(label + "_PATH_ESCAPE");
                return null;
            }
            Path file = root.resolve(relative).normalize();
            if (!file.startsWith(root)) {
                errors.add(label + "_PATH_ESCAPE");
                return null;
            }
            for (Path current = file; current != null && current.startsWith(root); current = current.getParent()) {
                if (Files.isSymbolicLink(current)) {
                    errors.add(label + "_SYMLINK_FORBIDDEN");
                    return null;
                }
                if (current.equals(root)) break;
            }
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                errors.add(label + "_FILE_MISSING");
                return null;
            }
            if (Files.size(file) > maxBytes) {
                errors.add(label + "_FILE_TOO_LARGE");
                return null;
            }
            return file;
        } catch (Exception error) {
            errors.add(label + "_FILE_READ_ERROR:" + error.getClass().getSimpleName());
            return null;
        }
    }

    private static byte[] evidenceBytes(Path file, String label, List<String> errors) {
        if (file == null) return null;
        try {
            return readBounded(file, MAX_EVIDENCE_FILE_BYTES);
        } catch (Exception error) {
            errors.add(label + "_FILE_READ_ERROR:" + error.getClass().getSimpleName());
            return null;
        }
    }

    private static byte[] readBounded(Path file, long maximum) throws Exception {
        try (InputStream input = Files.newInputStream(file)) {
            byte[] bytes = input.readNBytes(Math.toIntExact(maximum + 1));
            if (bytes.length > maximum) throw new IllegalArgumentException("EVIDENCE_FILE_TOO_LARGE");
            return bytes;
        }
    }

    private static String verifiedDigest(
            byte[] bytes, JsonNode owner, String field, String label, List<String> errors) {
        String declared = owner.path(field).asText();
        if (!declared.matches("[0-9a-f]{64}")) {
            errors.add(label + "_SHA256_INVALID");
            return "INVALID";
        }
        if (bytes != null && !Hashing.sha256(bytes).equals(declared)) errors.add(label + "_SHA256_MISMATCH");
        return declared;
    }

    private static void checkDigest(
            JsonNode owner, String field, String expected, String label, int index, List<String> errors) {
        String suffix = index < 0 ? "" : ":" + index;
        String actual = owner.path(field).asText();
        if (expected == null || !actual.matches("[0-9a-f]{64}") || !actual.equals(expected)) {
            errors.add(label + "_SHA256_MISMATCH" + suffix);
        }
    }

    private static JsonNode requireObject(JsonNode owner, String field, List<String> errors) {
        JsonNode value = owner.path(field);
        if (!value.isObject()) errors.add(field.toUpperCase(java.util.Locale.ROOT) + "_OBJECT_REQUIRED");
        return value;
    }

    private static void requireId(JsonNode owner, String field, String reason, List<String> errors) {
        if (!validName(owner.path(field).asText())) errors.add(reason);
    }

    private static boolean validName(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    }

    private static UniversalValidationRunner.StepExecution result(
            List<String> errors, List<String> limitations) {
        return result(errors, limitations, "errors=" + errors + "\nlimitations=" + limitations);
    }

    private static UniversalValidationRunner.StepExecution result(
            List<String> errors, List<String> limitations, String report) {
        if (!errors.isEmpty()) return new UniversalValidationRunner.StepExecution(
                UniversalValidationProfile.Outcome.FAIL, 1, report, false,
                "WORKFLOW_LINEAGE_INVALID");
        if (!limitations.isEmpty()) return new UniversalValidationRunner.StepExecution(
                UniversalValidationProfile.Outcome.INCONCLUSIVE, 0, report, false,
                "WORKFLOW_LINEAGE_SCHEMA_PARTIALLY_SUPPORTED");
        return new UniversalValidationRunner.StepExecution(
                UniversalValidationProfile.Outcome.PASS_NONFINAL, 0, report, false,
                "WORKFLOW_LINEAGE_DIGEST_SCHEMA_PERMIT_VERIFIED");
    }
}
