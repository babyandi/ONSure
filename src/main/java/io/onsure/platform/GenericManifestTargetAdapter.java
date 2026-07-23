package io.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.onsure.platform.ValidationModel.TargetType;
import io.onsure.platform.ValidationModel.ValidationTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Generic adapter for ordinary and AI software using onsure-target.json. */
public class GenericManifestTargetAdapter implements TargetAdapter {
    public static final String ID = "ONSURE_GENERIC_MANIFEST_V1";
    protected static final String MANIFEST = "onsure-target.json";
    protected final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String adapterId() { return ID; }

    @Override
    public boolean supports(TargetType targetType) {
        return targetType == TargetType.GENERAL_SOFTWARE || targetType == TargetType.AI_APPLICATION;
    }

    @Override
    public void validateRegistration(ValidationTarget target) throws Exception {
        if (!adapterId().equals(target.adapterId())) throw new IllegalArgumentException("TARGET_ADAPTER_MISMATCH");
        if (!supports(target.targetType())) throw new IllegalArgumentException("UNSUPPORTED_TARGET_TYPE");
        if (!Files.isDirectory(target.sourceRoot())) throw new IllegalArgumentException("TARGET_SOURCE_ROOT_MISSING");
        Path manifest = manifestPath(target);
        if (!Files.isRegularFile(manifest)) throw new IllegalArgumentException("TARGET_MANIFEST_MISSING");
        JsonNode root = mapper.readTree(manifest.toFile());
        if (!"ONSURE_TARGET_MANIFEST_V1".equals(root.path("contract").asText())) {
            throw new IllegalArgumentException("TARGET_MANIFEST_CONTRACT_MISMATCH");
        }
        if (!target.targetId().equals(root.path("target_id").asText())) {
            throw new IllegalArgumentException("TARGET_ID_MISMATCH");
        }
        if (!target.targetType().name().equals(root.path("target_type").asText())) {
            throw new IllegalArgumentException("TARGET_TYPE_MISMATCH");
        }
        if (root.path("self_reported_final_decision").asBoolean(false)) {
            throw new IllegalArgumentException("TARGET_CANNOT_SELF_REPORT_FINAL_DECISION");
        }
        parseFixtures(root);
    }

    @Override
    public Map<String, Object> collectTargetMetadata(ValidationTarget target) throws Exception {
        JsonNode root = mapper.readTree(manifestPath(target).toFile());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("adapter_id", adapterId());
        metadata.put("manifest_contract", root.path("contract").asText());
        metadata.put("target_claims_trust", "UNTRUSTED_UNTIL_RECALCULATED");
        metadata.put("language", root.path("language").asText("UNKNOWN"));
        metadata.put("framework", root.path("framework").asText("UNKNOWN"));
        metadata.put("entrypoint", root.path("entrypoint").asText("UNKNOWN"));
        metadata.put("declared_capabilities", mapper.convertValue(root.path("capabilities"), List.class));
        metadata.put("fixture_count", root.path("fixtures").size());
        return Map.copyOf(metadata);
    }

    @Override
    public List<FixtureDefinition> loadFixtures(ValidationTarget target) throws Exception {
        return parseFixtures(mapper.readTree(manifestPath(target).toFile()));
    }

    protected List<FixtureDefinition> parseFixtures(JsonNode root) {
        JsonNode nodes = root.path("fixtures");
        if (!nodes.isArray()) throw new IllegalArgumentException("TARGET_FIXTURES_INVALID");
        if (nodes.size() > 1000) throw new IllegalArgumentException("TARGET_FIXTURE_LIMIT_EXCEEDED");
        List<FixtureDefinition> fixtures = new ArrayList<>();
        for (JsonNode fixture : nodes) {
            List<String> command = new ArrayList<>();
            for (JsonNode argument : fixture.path("command")) command.add(argument.asText());
            Map<String, String> environment = new LinkedHashMap<>();
            fixture.path("environment").fields().forEachRemaining(
                    entry -> environment.put(entry.getKey(), entry.getValue().asText()));
            fixtures.add(new FixtureDefinition(
                    fixture.path("id").asText(),
                    fixture.path("input").asText(),
                    fixture.path("expected").asText(),
                    fixture.path("observed").asText(),
                    fixture.path("oracle").asText("EQUALS"),
                    command,
                    fixture.path("timeout_seconds").asInt(30),
                    environment));
        }
        return List.copyOf(fixtures);
    }

    protected Path manifestPath(ValidationTarget target) {
        return target.sourceRoot().resolve(MANIFEST).toAbsolutePath().normalize();
    }
}
