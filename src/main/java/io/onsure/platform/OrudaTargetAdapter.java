package io.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import io.onsure.platform.ValidationModel.TargetType;
import io.onsure.platform.ValidationModel.ValidationTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** ORUDA is an external first target; its claims never become ONSURE final decisions. */
public final class OrudaTargetAdapter extends GenericManifestTargetAdapter {
    public static final String ID = "ONSURE_ORUDA_TARGET_ADAPTER_V1";
    private static final String ORUDA_MANIFEST = "oruda-target.json";

    @Override
    public String adapterId() { return ID; }

    @Override
    public boolean supports(TargetType targetType) {
        return targetType == TargetType.AI_AGENTIC_PLATFORM;
    }

    @Override
    public void validateRegistration(ValidationTarget target) throws Exception {
        if (!adapterId().equals(target.adapterId())) throw new IllegalArgumentException("TARGET_ADAPTER_MISMATCH");
        if (!supports(target.targetType())) throw new IllegalArgumentException("ORUDA_TARGET_TYPE_INVALID");
        if (!Files.isDirectory(target.sourceRoot())) throw new IllegalArgumentException("TARGET_SOURCE_ROOT_MISSING");
        Path manifest = orudaManifest(target);
        if (!Files.isRegularFile(manifest)) throw new IllegalArgumentException("ORUDA_TARGET_MANIFEST_MISSING");
        JsonNode root = mapper.readTree(manifest.toFile());
        if (!"ONSURE_ORUDA_TARGET_PROFILE_V1".equals(root.path("contract").asText())) {
            throw new IllegalArgumentException("ORUDA_TARGET_CONTRACT_MISMATCH");
        }
        if (!"EXTERNAL_VALIDATION_TARGET".equals(root.path("relationship").asText())) {
            throw new IllegalArgumentException("ORUDA_RELATIONSHIP_INVALID");
        }
        if (root.path("onsure_runtime_dependency_on_oruda").asBoolean(true)) {
            throw new IllegalArgumentException("ONSURE_RUNTIME_MUST_NOT_DEPEND_ON_ORUDA");
        }
        if (root.path("oruda_can_write_onsure_final_decision").asBoolean(true)) {
            throw new IllegalArgumentException("ORUDA_CANNOT_WRITE_ONSURE_FINAL_DECISION");
        }
        if (!target.targetId().equals(root.path("target_id").asText())) {
            throw new IllegalArgumentException("TARGET_ID_MISMATCH");
        }
        parseFixtures(root);
    }

    @Override
    public Map<String, Object> collectTargetMetadata(ValidationTarget target) throws Exception {
        JsonNode root = mapper.readTree(orudaManifest(target).toFile());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("adapter_id", adapterId());
        metadata.put("manifest_contract", root.path("contract").asText());
        metadata.put("relationship", root.path("relationship").asText());
        metadata.put("target_claims_trust", "UNTRUSTED_UNTIL_ONSURE_RECALCULATION");
        metadata.put("adaptive_validation_assets", mapper.convertValue(
                root.path("adaptive_validation_assets"), List.class));
        metadata.put("declared_lanes", mapper.convertValue(root.path("lanes"), List.class));
        metadata.put("portable_receipt_required", true);
        metadata.put("fixture_count", root.path("fixtures").size());
        return Map.copyOf(metadata);
    }

    @Override
    public List<FixtureDefinition> loadFixtures(ValidationTarget target) throws Exception {
        return parseFixtures(mapper.readTree(orudaManifest(target).toFile()));
    }

    private Path orudaManifest(ValidationTarget target) {
        return target.sourceRoot().resolve(ORUDA_MANIFEST).toAbsolutePath().normalize();
    }
}
