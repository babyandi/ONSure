package kr.co.oruda.onsure.platform.oruda;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import kr.co.oruda.onsure.assurance.Decision;
import kr.co.oruda.onsure.assurance.ValidationResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Reconstructs and verifies target -> fixture -> harness -> oracle -> receipt -> regression lineage. */
public final class ReceiptLineageVerifier {
    private static final Set<String> REQUIRED_RUN_FILES = Set.of(
            "target.json",
            "job.json",
            "evidence.json",
            "fixture-registry.json",
            "oracle-registry.json",
            "harness-command-manifest.json",
            "fixture-results.json",
            "stage-results.json",
            "regression-lock.json",
            "internal-verifier-receipt.json",
            "internal-audit-receipt.json",
            "validation-report.json",
            OrudaEvidenceRegistry.FILE_NAME);
    private static final Set<String> POST_RUN_SUPPLEMENTS = Set.of(
            BlindReviewReceiptVerifier.FILE_NAME,
            IndependentRunReceiptVerifier.FILE_NAME,
            FinalCandidateGate.FILE_NAME);

    private static final ObjectMapper CANONICAL_MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final ObjectMapper registryMapper = new ObjectMapper()
            .findAndRegisterModules()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    public ValidationResult verify(Path runRoot, Path targetRoot) {
        List<String> violations = new ArrayList<>();
        try {
            if (runRoot == null || !Files.isDirectory(runRoot)) {
                return ValidationResult.fail(List.of("ORUDA_RUN_ROOT_MISSING"));
            }
            if (targetRoot == null || !Files.isDirectory(targetRoot)) {
                return ValidationResult.fail(List.of("ORUDA_TARGET_ROOT_MISSING"));
            }
            Path normalizedRun = runRoot.toAbsolutePath().normalize();
            Path normalizedTarget = targetRoot.toAbsolutePath().normalize();
            for (String required : REQUIRED_RUN_FILES) {
                if (!Files.isRegularFile(normalizedRun.resolve(required))) {
                    violations.add("ORUDA_LINEAGE_REQUIRED_FILE_MISSING:" + required);
                }
            }
            if (!violations.isEmpty()) return ValidationResult.fail(violations);

            ValidationResult registryResult = new OrudaEvidenceRegistry().verify(normalizedRun, normalizedTarget);
            if (registryResult.decision() != Decision.PASS) violations.addAll(registryResult.violations());
            verifyManifest(normalizedRun, violations);
            verifyCommandManifest(normalizedRun, normalizedTarget, violations);

            JsonNode target = mapper.readTree(normalizedRun.resolve("target.json").toFile());
            JsonNode job = mapper.readTree(normalizedRun.resolve("job.json").toFile());
            JsonNode report = mapper.readTree(normalizedRun.resolve("validation-report.json").toFile());
            JsonNode lock = mapper.readTree(normalizedRun.resolve("regression-lock.json").toFile());
            OrudaEvidenceRegistry.Registry registry = registryMapper.readValue(
                    normalizedRun.resolve(OrudaEvidenceRegistry.FILE_NAME).toFile(),
                    OrudaEvidenceRegistry.Registry.class);

            String jobId = job.path("jobId").asText();
            String targetId = target.path("targetId").asText();
            if (jobId.isBlank() || targetId.isBlank()) violations.add("ORUDA_LINEAGE_ID_MISSING");
            if (!Objects.equals(jobId, report.path("jobId").asText())) violations.add("ORUDA_REPORT_JOB_LINEAGE_MISMATCH");
            if (!Objects.equals(jobId, lock.path("jobId").asText())) violations.add("ORUDA_LOCK_JOB_LINEAGE_MISMATCH");
            if (!Objects.equals(jobId, registry.jobId())) violations.add("ORUDA_REGISTRY_JOB_LINEAGE_MISMATCH");
            if (!Objects.equals(targetId, lock.path("targetId").asText())) violations.add("ORUDA_LOCK_TARGET_LINEAGE_MISMATCH");
            if (!Objects.equals(targetId, registry.targetId())) violations.add("ORUDA_REGISTRY_TARGET_LINEAGE_MISMATCH");
            if (!Objects.equals(targetId, report.path("target").path("targetId").asText())) {
                violations.add("ORUDA_REPORT_TARGET_LINEAGE_MISMATCH");
            }
            if (!Objects.equals(lock.path("lockDigest").asText(), registry.regressionLockDigest())) {
                violations.add("ORUDA_REGISTRY_LOCK_LINEAGE_MISMATCH");
            }
            verifyProductReceipt(normalizedRun.resolve("internal-verifier-receipt.json"),
                    "ONSURE_INTERNAL_VERIFIER_RECEIPT_V1", "ONSURE_INTERNAL_VERIFIER", jobId,
                    "ORUDA_INTERNAL_VERIFIER_", violations);
            verifyProductReceipt(normalizedRun.resolve("internal-audit-receipt.json"),
                    "ONSURE_INTERNAL_AUDIT_RECEIPT_V1", "ONSURE_INTERNAL_AUDIT", jobId,
                    "ORUDA_INTERNAL_AUDIT_", violations);

            for (OrudaEvidenceRegistry.Row row : registry.rows()) {
                if (!Objects.equals(jobId, row.runId())) {
                    violations.add("ORUDA_ROW_RUN_LINEAGE_MISMATCH:" + row.fixtureId());
                }
                if (!Objects.equals(registry.sourceTreeSha256(), row.sourceHash())) {
                    violations.add("ORUDA_ROW_SOURCE_LINEAGE_MISMATCH:" + row.fixtureId());
                }
                if (!Objects.equals(registry.policyDigest(), row.policyDigest())) {
                    violations.add("ORUDA_ROW_POLICY_LINEAGE_MISMATCH:" + row.fixtureId());
                }
            }
        } catch (Exception e) {
            violations.add("ORUDA_RECEIPT_LINEAGE_UNREADABLE:" + e.getClass().getSimpleName());
        }
        return violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations);
    }

    private void verifyCommandManifest(Path runRoot, Path targetRoot, List<String> violations) throws Exception {
        JsonNode commandManifest = mapper.readTree(runRoot.resolve("harness-command-manifest.json").toFile());
        JsonNode targetManifest = mapper.readTree(targetRoot.resolve("oruda-target.json").toFile());
        if (!"ONSURE_ORUDA_HARNESS_COMMAND_MANIFEST_V1".equals(commandManifest.path("contract").asText())) {
            violations.add("ORUDA_COMMAND_MANIFEST_CONTRACT_MISMATCH");
        }
        if (!"HOST_POLICY_NOT_ENFORCED".equals(commandManifest.path("network_policy").asText())) {
            violations.add("ORUDA_COMMAND_MANIFEST_NETWORK_POLICY_INVALID");
        }
        if (!targetRoot.toAbsolutePath().normalize().toString()
                .equals(commandManifest.path("working_directory").asText())) {
            violations.add("ORUDA_COMMAND_MANIFEST_WORKING_DIRECTORY_MISMATCH");
        }
        Set<String> allowed = new HashSet<>();
        for (JsonNode value : commandManifest.path("allowed_executables")) allowed.add(value.asText());
        if (!allowed.equals(Set.of("bash"))) violations.add("ORUDA_COMMAND_MANIFEST_EXECUTABLE_POLICY_INVALID");

        Map<String, JsonNode> expected = new LinkedHashMap<>();
        for (JsonNode fixture : targetManifest.path("fixtures")) {
            if (fixture.path("command").isArray() && fixture.path("command").size() > 0) {
                expected.put(fixture.path("id").asText(), fixture);
            }
        }
        Map<String, JsonNode> actual = new LinkedHashMap<>();
        for (JsonNode entry : commandManifest.path("entries")) {
            String fixtureId = entry.path("fixture_id").asText();
            if (fixtureId.isBlank() || actual.put(fixtureId, entry) != null) {
                violations.add("ORUDA_COMMAND_MANIFEST_DUPLICATE_OR_BLANK_FIXTURE");
            }
        }
        if (!actual.keySet().equals(expected.keySet())) {
            violations.add("ORUDA_COMMAND_MANIFEST_FIXTURE_SET_MISMATCH");
            return;
        }
        for (String fixtureId : expected.keySet()) {
            JsonNode fixture = expected.get(fixtureId);
            JsonNode command = actual.get(fixtureId);
            if (!fixture.path("command").equals(command.path("command"))) {
                violations.add("ORUDA_COMMAND_MANIFEST_COMMAND_MISMATCH:" + fixtureId);
            }
            if (fixture.path("timeout_seconds").asInt(30) != command.path("timeout_seconds").asInt()) {
                violations.add("ORUDA_COMMAND_MANIFEST_TIMEOUT_MISMATCH:" + fixtureId);
            }
            if (!fixture.path("oracle").asText("EQUALS").equals(command.path("oracle_id").asText())) {
                violations.add("ORUDA_COMMAND_MANIFEST_ORACLE_MISMATCH:" + fixtureId);
            }
            if (!fixture.path("expected").asText().equals(command.path("expected_result").asText())) {
                violations.add("ORUDA_COMMAND_MANIFEST_EXPECTED_RESULT_MISMATCH:" + fixtureId);
            }
            if (!"UTF8_STDOUT_STRIP".equals(command.path("output_parser").asText())) {
                violations.add("ORUDA_COMMAND_MANIFEST_OUTPUT_PARSER_INVALID:" + fixtureId);
            }
        }
    }

    private static void verifyManifest(Path runRoot, List<String> violations) throws Exception {
        Path manifest = runRoot.resolve("manifest.sha256");
        if (!Files.isRegularFile(manifest)) {
            violations.add("ORUDA_RUN_MANIFEST_MISSING");
            return;
        }
        Map<String, String> entries = new LinkedHashMap<>();
        for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
            int separator = line.indexOf("  ");
            if (separator != 64) {
                violations.add("ORUDA_RUN_MANIFEST_FORMAT_INVALID");
                continue;
            }
            String digest = line.substring(0, 64);
            String relative = line.substring(separator + 2);
            if (!digest.matches("[0-9a-f]{64}") || relative.isBlank() || entries.put(relative, digest) != null) {
                violations.add("ORUDA_RUN_MANIFEST_ENTRY_INVALID");
                continue;
            }
            Path file = runRoot.resolve(relative).normalize();
            if (!file.startsWith(runRoot) || !Files.isRegularFile(file)) {
                violations.add("ORUDA_RUN_MANIFEST_PATH_INVALID:" + relative);
            } else if (!digest.equals(sha256(file))) {
                violations.add("ORUDA_RUN_MANIFEST_DIGEST_MISMATCH:" + relative);
            }
        }

        Set<String> actual = new HashSet<>();
        try (var stream = Files.list(runRoot)) {
            stream.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> !"manifest.sha256".equals(name))
                    .filter(name -> !POST_RUN_SUPPLEMENTS.contains(name))
                    .forEach(actual::add);
        }
        if (!entries.keySet().equals(actual)) violations.add("ORUDA_RUN_MANIFEST_FILE_SET_MISMATCH");
        if (!entries.keySet().containsAll(REQUIRED_RUN_FILES)) violations.add("ORUDA_RUN_MANIFEST_REQUIRED_SET_MISSING");
    }

    private static void verifyProductReceipt(Path file, String contract, String authority, String jobId,
            String prefix, List<String> violations) {
        try {
            Map<String, Object> value = CANONICAL_MAPPER.readValue(file.toFile(), new TypeReference<>() {});
            Object stored = value.remove("receipt_sha256");
            if (!contract.equals(value.get("contract"))) violations.add(prefix + "CONTRACT_MISMATCH");
            if (!authority.equals(value.get("authority"))) violations.add(prefix + "AUTHORITY_MISMATCH");
            if (!jobId.equals(value.get("job_id"))) violations.add(prefix + "JOB_MISMATCH");
            if (!"PASS".equals(value.get("decision"))) violations.add(prefix + "NON_PASS");
            String expected = sha256(CANONICAL_MAPPER.writeValueAsBytes(new TreeMap<>(value)));
            if (!(stored instanceof String digest) || !digest.equals(expected)) violations.add(prefix + "HASH_MISMATCH");
        } catch (Exception e) {
            violations.add(prefix + "RECEIPT_UNREADABLE:" + e.getClass().getSimpleName());
        }
    }

    private static String sha256(Path file) throws Exception {
        return sha256(Files.readAllBytes(file));
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
