package io.onsure.platform;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.onsure.platform.UniversalValidationProfile.EnvironmentRequirement;
import io.onsure.platform.UniversalValidationProfile.RequirementKind;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict external policy input for target-specific environment requirements. */
final class EnvironmentRequirementProfile {
    static final String CONTRACT = "ONSURE_ENVIRONMENT_REQUIREMENT_PROFILE_V1";
    private static final long MAX_BYTES = 1024L * 1024L;
    private static final int MAX_REQUIREMENTS = 128;
    private static final Set<String> ROOT_FIELDS = Set.of("contract", "profile_id", "requirements");
    private static final Set<String> REQUIREMENT_FIELDS = Set.of(
            "requirement_id", "kind", "value", "required");
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private EnvironmentRequirementProfile() {}

    static Loaded load(Path input) throws Exception {
        if (input == null) throw new IllegalArgumentException("ENVIRONMENT_PROFILE_FILE_REQUIRED");
        Path file = input.toAbsolutePath().normalize();
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)
                || Files.size(file) > MAX_BYTES) {
            throw new IllegalArgumentException("ENVIRONMENT_PROFILE_FILE_INVALID");
        }
        JsonNode root = MAPPER.readTree(Files.readAllBytes(file));
        if (root == null || !root.isObject() || !fieldNames(root).equals(ROOT_FIELDS)) {
            throw new IllegalArgumentException("ENVIRONMENT_PROFILE_STRUCTURE_INVALID");
        }
        if (!CONTRACT.equals(root.path("contract").asText())) {
            throw new IllegalArgumentException("ENVIRONMENT_PROFILE_CONTRACT_INVALID");
        }
        String profileId = root.path("profile_id").asText("");
        if (!profileId.matches("[A-Za-z0-9._-]{1,128}")) {
            throw new IllegalArgumentException("ENVIRONMENT_PROFILE_ID_INVALID");
        }
        JsonNode requirementsNode = root.path("requirements");
        if (!requirementsNode.isArray() || requirementsNode.isEmpty()
                || requirementsNode.size() > MAX_REQUIREMENTS) {
            throw new IllegalArgumentException("ENVIRONMENT_PROFILE_REQUIREMENTS_INVALID");
        }
        List<EnvironmentRequirement> requirements = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode item : requirementsNode) {
            if (!item.isObject() || !fieldNames(item).equals(REQUIREMENT_FIELDS)
                    || !item.path("required").isBoolean()) {
                throw new IllegalArgumentException("ENVIRONMENT_PROFILE_REQUIREMENT_INVALID");
            }
            String id = item.path("requirement_id").asText("");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("ENVIRONMENT_REQUIREMENT_ID_DUPLICATED:" + id);
            }
            RequirementKind kind;
            try {
                kind = RequirementKind.valueOf(item.path("kind").asText(""));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("ENVIRONMENT_REQUIREMENT_KIND_INVALID:" + id, invalid);
            }
            requirements.add(new EnvironmentRequirement(
                    id, kind, item.path("value").asText(""), item.path("required").asBoolean()));
        }
        List<Map<String, Object>> normalized = requirements.stream()
                .sorted(java.util.Comparator.comparing(EnvironmentRequirement::requirementId))
                .map(EnvironmentRequirementProfile::normalized).toList();
        Map<String, Object> semantics = new LinkedHashMap<>();
        semantics.put("contract", CONTRACT);
        semantics.put("profile_id", profileId);
        semantics.put("requirements", normalized);
        String semanticSha256 = Hashing.sha256(MAPPER.writeValueAsString(semantics));
        return new Loaded(profileId, List.copyOf(requirements), semanticSha256, Hashing.file(file), file);
    }

    static String semanticDigest(List<EnvironmentRequirement> requirements) throws Exception {
        List<Map<String, Object>> normalized = requirements.stream()
                .sorted(java.util.Comparator.comparing(EnvironmentRequirement::requirementId))
                .map(EnvironmentRequirementProfile::normalized).toList();
        return Hashing.sha256(MAPPER.writeValueAsString(normalized));
    }

    private static Map<String, Object> normalized(EnvironmentRequirement value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requirement_id", value.requirementId());
        result.put("kind", value.kind().name());
        result.put("value", value.value());
        result.put("required", value.required());
        return Map.copyOf(result);
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> result = new HashSet<>();
        node.fieldNames().forEachRemaining(result::add);
        return Set.copyOf(result);
    }

    record Loaded(
            String profileId,
            List<EnvironmentRequirement> requirements,
            String semanticSha256,
            String sourceFileSha256,
            Path sourceFile) {}
}
