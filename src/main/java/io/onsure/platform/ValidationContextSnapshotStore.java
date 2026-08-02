package io.onsure.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.onsure.platform.ValidationModel.Evidence;
import io.onsure.platform.ValidationModel.FailureMode;
import io.onsure.platform.ValidationModel.Finding;
import io.onsure.platform.ValidationModel.FixtureResult;
import io.onsure.platform.ValidationModel.RcaRecord;
import io.onsure.platform.ValidationModel.RegressionLock;
import io.onsure.platform.ValidationModel.StageResult;
import io.onsure.platform.ValidationModel.ValidationJob;
import io.onsure.platform.ValidationModel.ValidationTarget;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Digest-bound, atomic snapshot of the mutable validation aggregate at a stage boundary. */
final class ValidationContextSnapshotStore {
    static final String CONTRACT = "ONSURE_VALIDATION_CONTEXT_SNAPSHOT_V1";
    static final String FILE_NAME = "stage-context.json";
    private static final TypeReference<LinkedHashMap<String, Object>> MAP = new TypeReference<>() {};

    record Restored(ValidationContext context, int lastCompletedStageIndex, String boundaryState) {}

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final Path runRoot;
    private final Path file;

    ValidationContextSnapshotStore(Path runRoot) {
        this.runRoot = runRoot.toAbsolutePath().normalize();
        this.file = this.runRoot.resolve(FILE_NAME);
        if (!Files.isDirectory(this.runRoot, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(this.runRoot)) {
            throw new IllegalArgumentException("VALIDATION_CONTEXT_RUN_ROOT_INVALID");
        }
    }

    void save(ValidationContext context, int lastCompletedStageIndex, String boundaryState) throws Exception {
        if (!runRoot.equals(context.runRoot()) || lastCompletedStageIndex < -1) {
            throw new IllegalArgumentException("VALIDATION_CONTEXT_SNAPSHOT_BINDING_INVALID");
        }
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("contract", CONTRACT);
        value.put("version", 1);
        value.put("job_id", context.job().jobId());
        value.put("target_id", context.target().targetId());
        value.put("target_sha256", digestObject(context.target()));
        value.put("run_root", runRoot.toString());
        value.put("captured_at", Instant.now().toString());
        value.put("last_completed_stage_index", lastCompletedStageIndex);
        value.put("boundary_state", requireBoundary(boundaryState));
        value.put("job", context.job());
        value.put("evidence", context.evidence());
        value.put("findings", context.findings());
        value.put("failure_modes", context.failureModes());
        value.put("rca_records", context.rcaRecords());
        value.put("remediation_plans", context.remediationPlans());
        value.put("fixture_results", context.fixtureResults());
        value.put("stage_results", context.stageResults());
        value.put("attributes", context.attributes());
        value.put("regression_lock", context.regressionLock());
        value.put("automatic_engine_resume_supported", false);
        value.put("replay_requires_explicit_resume", true);
        value.put("final_claim_allowed", false);
        Map<String, Object> normalized = mapper.convertValue(value, MAP);
        normalized.put("snapshot_sha256", digest(normalized));
        atomicWrite(normalized);
    }

    Restored restore(ValidationTarget target, TargetAdapter adapter) throws Exception {
        Map<String, Object> value = verifyAndRead(target);
        ValidationJob job = convert(value.get("job"), ValidationJob.class);
        ValidationContext context = new ValidationContext(target, job, adapter, runRoot);
        list(value, "evidence", Evidence.class).forEach(context::addEvidence);
        list(value, "findings", Finding.class).forEach(context::addFinding);
        list(value, "failure_modes", FailureMode.class).forEach(context::addFailureMode);
        list(value, "rca_records", RcaRecord.class).forEach(context::addRcaRecord);
        list(value, "remediation_plans", RemediationPlan.class).forEach(context::addRemediationPlan);
        list(value, "fixture_results", FixtureResult.class).forEach(context::addFixtureResult);
        list(value, "stage_results", StageResult.class).forEach(context::addStageResult);
        Map<String, Object> attributes = mapper.convertValue(value.get("attributes"), MAP);
        attributes.forEach(context::putAttribute);
        if (value.get("regression_lock") != null) {
            context.regressionLock(convert(value.get("regression_lock"), RegressionLock.class));
        }
        return new Restored(
                context,
                ((Number) value.get("last_completed_stage_index")).intValue(),
                String.valueOf(value.get("boundary_state")));
    }

    Map<String, Object> verifyAndRead(ValidationTarget target) throws Exception {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
            throw new IllegalStateException("VALIDATION_CONTEXT_SNAPSHOT_FILE_INVALID");
        }
        Map<String, Object> value = mapper.readValue(file.toFile(), MAP);
        if (!CONTRACT.equals(value.get("contract"))
                || !target.targetId().equals(value.get("target_id"))
                || !runRoot.toString().equals(value.get("run_root"))
                || !digestObject(target).equals(value.get("target_sha256"))) {
            throw new IllegalStateException("VALIDATION_CONTEXT_SNAPSHOT_BINDING_INVALID");
        }
        String declared = String.valueOf(value.get("snapshot_sha256"));
        if (!declared.equals(digest(value))) {
            throw new IllegalStateException("VALIDATION_CONTEXT_SNAPSHOT_DIGEST_INVALID");
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }

    private <T> T convert(Object value, Class<T> type) {
        return mapper.convertValue(value, type);
    }

    private <T> List<T> list(Map<String, Object> value, String key, Class<T> type) {
        JavaType listType = mapper.getTypeFactory().constructCollectionType(List.class, type);
        return mapper.convertValue(value.get(key), listType);
    }

    private void atomicWrite(Map<String, Object> value) throws Exception {
        Path temporary = file.resolveSibling(file.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            mapper.writeValue(temporary.toFile(), value);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private String digestObject(Object value) throws Exception {
        return Hashing.sha256(mapper.writeValueAsBytes(value));
    }

    private String digest(Map<String, Object> value) throws Exception {
        Map<String, Object> canonical = new TreeMap<>(value);
        canonical.remove("snapshot_sha256");
        return Hashing.sha256(mapper.writeValueAsBytes(canonical));
    }

    private static String requireBoundary(String value) {
        if (value == null || !value.matches("[A-Z][A-Z0-9_]{0,79}")) {
            throw new IllegalArgumentException("VALIDATION_CONTEXT_BOUNDARY_INVALID");
        }
        return value;
    }
}
