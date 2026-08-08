package kr.co.oruda.onsure.platform.oruda;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import kr.co.oruda.onsure.assurance.Decision;
import kr.co.oruda.onsure.assurance.ValidationResult;
import kr.co.oruda.onsure.platform.ValidationContext;
import kr.co.oruda.onsure.platform.ValidationModel.Evidence;
import kr.co.oruda.onsure.platform.ValidationModel.Finding;
import kr.co.oruda.onsure.platform.ValidationModel.FixtureResult;
import kr.co.oruda.onsure.platform.ValidationModel.RcaRecord;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** ORUDA-specific Evidence Registry population and independent integrity verification. */
public final class OrudaEvidenceRegistry {
    public static final String CONTRACT = "ONSURE_ORUDA_EVIDENCE_REGISTRY_V1";
    public static final String FILE_NAME = "oruda-evidence-registry.json";

    public record Row(
            String evidenceRecordId,
            String failureId,
            String fixtureId,
            String variantType,
            String severity,
            String primaryOwner,
            String secondaryGuard,
            String finalGuard,
            String oracleId,
            String harnessId,
            String inputHash,
            String sourceHash,
            String policyDigest,
            String receiptDigest,
            String rawLogHash,
            String runId,
            int runNumber,
            String expectedResult,
            String actualResult,
            String verdict,
            String rcaId,
            String fixRef,
            String regressionRun1,
            String regressionRun2,
            String blindReviewId,
            String lockStatus) {}

    public record Registry(
            String contract,
            String registryId,
            String targetId,
            String jobId,
            Instant generatedAt,
            String sourceTreeSha256,
            String policyDigest,
            String regressionLockDigest,
            List<Row> rows) {
        public Registry { rows = List.copyOf(rows); }
    }

    private final ObjectMapper registryMapper = new ObjectMapper()
            .findAndRegisterModules()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final ObjectMapper productMapper = new ObjectMapper().findAndRegisterModules();
    private final ExecutionResultClassifier classifier = new ExecutionResultClassifier();

    public Registry populate(ValidationContext context) throws Exception {
        Objects.requireNonNull(context, "context");
        if (context.regressionLock() == null) {
            throw new IllegalArgumentException("ORUDA_REGRESSION_LOCK_REQUIRED_BEFORE_EVIDENCE_REGISTRY");
        }
        Path manifestFile = context.target().sourceRoot().resolve("oruda-target.json");
        JsonNode manifest = productMapper.readTree(manifestFile.toFile());
        Map<String, FixtureResult> results = indexResults(context.fixtureResults());
        Map<String, Evidence> fixtureEvidence = indexFixtureEvidence(context.evidence());
        Map<String, Finding> fixtureFindings = indexFixtureFindings(context.findings());
        Map<String, RcaRecord> rcaByFinding = new HashMap<>();
        for (RcaRecord rca : context.rcaRecords()) rcaByFinding.put(rca.findingId(), rca);

        String sourceHash = requiredDigest(context.attributes().get("source_tree_sha256"), "ORUDA_SOURCE_HASH_MISSING");
        String policyDigest = sha256(context.target().policyProfile().getBytes(StandardCharsets.UTF_8));
        List<Row> rows = new ArrayList<>();
        int runNumber = context.attributes().getOrDefault("oruda_run_number", 1) instanceof Number number
                ? number.intValue() : 1;

        for (JsonNode fixture : manifest.path("fixtures")) {
            String fixtureId = requiredText(fixture, "id");
            FixtureResult result = results.get(fixtureId);
            if (result == null) throw new IllegalArgumentException("ORUDA_FIXTURE_RESULT_MISSING:" + fixtureId);
            Evidence evidence = fixtureEvidence.get(fixtureId);
            if (evidence == null) throw new IllegalArgumentException("ORUDA_FIXTURE_EVIDENCE_MISSING:" + fixtureId);

            String group = requiredText(fixture, "group");
            String inputHash = sha256(productMapper.writeValueAsBytes(fixture));
            String receiptDigest = sha256(productMapper.writeValueAsBytes(result));
            String rawLogHash = requiredDigest(evidence.attributes().get("output_sha256"),
                    "ORUDA_RAW_LOG_HASH_MISSING:" + fixtureId);
            ExecutionResultClassifier.Classification classification = classify(group, result, evidence);
            String verdict = classification.verdict().name();
            Finding finding = fixtureFindings.get(fixtureId);
            RcaRecord rca = finding == null ? null : rcaByFinding.get(finding.findingId());
            String lockStatus = lockStatus(group, classification);
            String recordSeed = context.job().jobId() + "|" + fixtureId + "|" + inputHash + "|" + receiptDigest;

            rows.add(new Row(
                    "EVR-" + sha256(recordSeed.getBytes(StandardCharsets.UTF_8)).substring(0, 24),
                    isExpectedFailure(group) ? "FAILURE-" + fixtureId : "NOT_APPLICABLE",
                    fixtureId,
                    group,
                    isExpectedFailure(group) ? "HIGH" : "INFO",
                    fixture.path("program_surface").asText("UNSPECIFIED_OWNER"),
                    "OTester",
                    "OAudit",
                    result.oracleId(),
                    result.harnessId(),
                    inputHash,
                    sourceHash,
                    policyDigest,
                    receiptDigest,
                    rawLogHash,
                    context.job().jobId(),
                    Math.max(1, runNumber),
                    result.expected(),
                    result.observed(),
                    verdict,
                    rca == null ? (classification.rcaRequired() ? "RCA_PENDING" : "NOT_REQUIRED") : rca.rcaId(),
                    "NOT_APPLICABLE",
                    "NOT_RUN",
                    "NOT_RUN",
                    "QUALITY".equals(group) ? "NOT_RUN" : "NOT_APPLICABLE",
                    lockStatus));
        }

        Registry registry = new Registry(
                CONTRACT,
                "ORUDA-EVR-" + context.job().jobId(),
                context.target().targetId(),
                context.job().jobId(),
                Instant.now(),
                sourceHash,
                policyDigest,
                context.regressionLock().lockDigest(),
                rows);
        writeAtomic(context.runRoot().resolve(FILE_NAME), registry);
        return registry;
    }

    public ValidationResult verify(Path runRoot, Path targetRoot) {
        List<String> violations = new ArrayList<>();
        try {
            Path registryFile = runRoot.resolve(FILE_NAME);
            Path manifestFile = targetRoot.resolve("oruda-target.json");
            if (!Files.isRegularFile(registryFile)) return ValidationResult.fail(List.of("ORUDA_EVIDENCE_REGISTRY_MISSING"));
            if (!Files.isRegularFile(manifestFile)) return ValidationResult.fail(List.of("ORUDA_TARGET_MANIFEST_MISSING"));

            Registry registry = registryMapper.readValue(registryFile.toFile(), Registry.class);
            if (!CONTRACT.equals(registry.contract())) violations.add("ORUDA_EVIDENCE_REGISTRY_CONTRACT_MISMATCH");
            JsonNode manifest = productMapper.readTree(manifestFile.toFile());
            List<FixtureResult> results = productMapper.readValue(
                    runRoot.resolve("fixture-results.json").toFile(), new TypeReference<>() {});
            List<Evidence> evidence = productMapper.readValue(
                    runRoot.resolve("evidence.json").toFile(), new TypeReference<>() {});
            JsonNode regressionLock = productMapper.readTree(runRoot.resolve("regression-lock.json").toFile());
            JsonNode target = productMapper.readTree(runRoot.resolve("target.json").toFile());

            Map<String, FixtureResult> resultIndex = indexResults(results);
            Map<String, Evidence> evidenceIndex = indexFixtureEvidence(evidence);
            Map<String, JsonNode> fixtureIndex = new LinkedHashMap<>();
            for (JsonNode fixture : manifest.path("fixtures")) fixtureIndex.put(requiredText(fixture, "id"), fixture);

            if (registry.rows().size() != fixtureIndex.size()) violations.add("ORUDA_EVIDENCE_REGISTRY_ROW_COUNT_MISMATCH");
            if (!Objects.equals(registry.targetId(), target.path("targetId").asText())) violations.add("ORUDA_EVIDENCE_TARGET_MISMATCH");
            if (!Objects.equals(registry.regressionLockDigest(), regressionLock.path("lockDigest").asText())) {
                violations.add("ORUDA_EVIDENCE_REGRESSION_LOCK_MISMATCH");
            }
            String expectedPolicyDigest = sha256(target.path("policyProfile").asText().getBytes(StandardCharsets.UTF_8));
            if (!Objects.equals(registry.policyDigest(), expectedPolicyDigest)) violations.add("ORUDA_EVIDENCE_POLICY_DIGEST_MISMATCH");

            Set<String> recordIds = new HashSet<>();
            Set<String> fixtureIds = new HashSet<>();
            for (Row row : registry.rows()) {
                if (!recordIds.add(row.evidenceRecordId())) violations.add("ORUDA_DUPLICATE_EVIDENCE_RECORD_ID");
                if (!fixtureIds.add(row.fixtureId())) violations.add("ORUDA_DUPLICATE_EVIDENCE_FIXTURE_ID");
                JsonNode fixture = fixtureIndex.get(row.fixtureId());
                FixtureResult result = resultIndex.get(row.fixtureId());
                Evidence fixtureResultEvidence = evidenceIndex.get(row.fixtureId());
                if (fixture == null || result == null || fixtureResultEvidence == null) {
                    violations.add("ORUDA_EVIDENCE_LINEAGE_COMPONENT_MISSING:" + row.fixtureId());
                    continue;
                }
                String fixtureDigest = sha256(productMapper.writeValueAsBytes(fixture));
                String resultDigest = sha256(productMapper.writeValueAsBytes(result));
                String outputDigest = requiredDigest(fixtureResultEvidence.attributes().get("output_sha256"),
                        "ORUDA_RAW_LOG_HASH_MISSING:" + row.fixtureId());
                if (!row.inputHash().equals(fixtureDigest)) violations.add("ORUDA_EVIDENCE_INPUT_HASH_MISMATCH:" + row.fixtureId());
                if (!row.receiptDigest().equals(resultDigest)) violations.add("ORUDA_EVIDENCE_RECEIPT_DIGEST_MISMATCH:" + row.fixtureId());
                if (!row.rawLogHash().equals(outputDigest)) violations.add("ORUDA_EVIDENCE_RAW_LOG_HASH_MISMATCH:" + row.fixtureId());
                if (!row.sourceHash().equals(registry.sourceTreeSha256())) violations.add("ORUDA_EVIDENCE_SOURCE_HASH_MISMATCH:" + row.fixtureId());
                if (!row.policyDigest().equals(registry.policyDigest())) violations.add("ORUDA_EVIDENCE_ROW_POLICY_MISMATCH:" + row.fixtureId());
                ExecutionResultClassifier.Classification expected = classify(row.variantType(), result, fixtureResultEvidence);
                if (!row.verdict().equals(expected.verdict().name())) {
                    violations.add("ORUDA_EVIDENCE_VERDICT_MISMATCH:" + row.fixtureId());
                }
                String expectedLockStatus = lockStatus(row.variantType(), expected);
                if (!Objects.equals(row.lockStatus(), expectedLockStatus)) {
                    violations.add("ORUDA_EVIDENCE_LOCK_STATUS_MISMATCH:" + row.fixtureId());
                }
                if (expected.rcaRequired() && "NOT_REQUIRED".equals(row.rcaId())) {
                    violations.add("ORUDA_EVIDENCE_RCA_REQUIRED_BUT_NOT_BOUND:" + row.fixtureId());
                }
                if ("QUALITY".equals(row.variantType()) && "LOCK_CANDIDATE".equals(row.lockStatus())
                        && "NOT_RUN".equals(row.blindReviewId())) {
                    violations.add("ORUDA_QUALITY_LOCK_WITHOUT_BLIND_REVIEW:" + row.fixtureId());
                }
            }
            if (!fixtureIds.equals(fixtureIndex.keySet())) violations.add("ORUDA_EVIDENCE_FIXTURE_SET_MISMATCH");
        } catch (Exception e) {
            violations.add("ORUDA_EVIDENCE_REGISTRY_UNREADABLE:" + e.getClass().getSimpleName());
        }
        return violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations);
    }

    private void writeAtomic(Path file, Registry registry) throws Exception {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        registryMapper.writeValue(temporary.toFile(), registry);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Map<String, FixtureResult> indexResults(List<FixtureResult> values) {
        Map<String, FixtureResult> result = new LinkedHashMap<>();
        for (FixtureResult value : values) {
            if (result.put(value.fixtureId(), value) != null) {
                throw new IllegalArgumentException("ORUDA_DUPLICATE_FIXTURE_RESULT:" + value.fixtureId());
            }
        }
        return result;
    }

    private static Map<String, Evidence> indexFixtureEvidence(List<Evidence> values) {
        Map<String, Evidence> result = new LinkedHashMap<>();
        for (Evidence value : values) {
            if (!"FIXTURE_EXECUTION".equals(value.evidenceType())) continue;
            if (result.put(value.source(), value) != null) {
                throw new IllegalArgumentException("ORUDA_DUPLICATE_FIXTURE_EVIDENCE:" + value.source());
            }
        }
        return result;
    }

    private static Map<String, Finding> indexFixtureFindings(List<Finding> values) {
        Map<String, Finding> result = new HashMap<>();
        for (Finding value : values) {
            if (value.location().startsWith("fixture:")) {
                result.put(value.location().substring("fixture:".length()), value);
            }
        }
        return result;
    }

    private ExecutionResultClassifier.Classification classify(
            String group, FixtureResult result, Evidence evidence) {
        boolean commandExecuted = Boolean.TRUE.equals(evidence.attributes().get("command_executed"));
        boolean timedOut = Boolean.TRUE.equals(evidence.attributes().get("timed_out"));
        int exitCode = evidence.attributes().get("exit_code") instanceof Number number ? number.intValue() : -1;
        boolean evidenceComplete = evidence.attributes().containsKey("output_sha256")
                && evidence.attributes().containsKey("oracle")
                && evidence.attributes().containsKey("harness")
                && evidence.attributes().containsKey("observed")
                && evidence.attributes().containsKey("expected");
        return classifier.classify(new ExecutionResultClassifier.Input(
                true,
                commandExecuted,
                false,
                timedOut,
                exitCode,
                result.decision() == Decision.PASS,
                isExpectedFailure(group),
                evidenceComplete,
                false));
    }

    private static String lockStatus(
            String group, ExecutionResultClassifier.Classification classification) {
        if (classification.rcaRequired()) return "RCA_REQUIRED";
        if (classification.verdict() == ExecutionResultClassifier.Verdict.NOT_RUN
                || classification.verdict() == ExecutionResultClassifier.Verdict.SKIPPED) {
            return "BLOCKED";
        }
        if (classification.verdict() == ExecutionResultClassifier.Verdict.INCONCLUSIVE
                || classification.verdict() == ExecutionResultClassifier.Verdict.HARNESS_ERROR) {
            return "BLOCKED";
        }
        if ("QUALITY".equals(group)) return "BLIND_REVIEW_REQUIRED";
        return "REGRESSION_REQUIRED";
    }

    private static boolean isExpectedFailure(String group) {
        return "NEGATIVE".equals(group) || "QUALITY".equals(group);
    }

    private static String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value == null || value.isBlank()) throw new IllegalArgumentException("ORUDA_REQUIRED_FIELD_MISSING:" + field);
        return value;
    }

    private static String requiredDigest(Object value, String error) {
        String text = value == null ? "" : value.toString();
        if (!text.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(error);
        return text;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
