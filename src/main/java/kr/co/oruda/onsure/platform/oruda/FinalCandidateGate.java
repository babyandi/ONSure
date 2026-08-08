package kr.co.oruda.onsure.platform.oruda;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import kr.co.oruda.onsure.assurance.Decision;
import kr.co.oruda.onsure.assurance.ValidationResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Determines Final Candidate eligibility from two independent complete reproductions. Never creates Final Lock. */
public final class FinalCandidateGate {
    public static final String CONTRACT = "ONSURE_ORUDA_FINAL_CANDIDATE_GATE_V1";
    public static final String FILE_NAME = "final-candidate-gate.json";
    private static final Path PACKAGE_CATALOG = Path.of("contracts/oruda-execution-packages.v1.json");

    public record GateResult(
            String contract,
            String candidateId,
            String targetId,
            String run1JobId,
            String run2JobId,
            Instant generatedAt,
            boolean eligible,
            String decision,
            List<String> reasons,
            String candidateDigest,
            boolean finalLockAllowed) {
        public GateResult { reasons = List.copyOf(reasons); }
    }

    private static final ObjectMapper CANONICAL_MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final ObjectMapper registryMapper = new ObjectMapper()
            .findAndRegisterModules()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private final ObjectMapper outputMapper = new ObjectMapper()
            .findAndRegisterModules()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .enable(SerializationFeature.INDENT_OUTPUT);

    public GateResult evaluate(Path run1, Path run2, Path targetRoot) {
        List<String> reasons = new ArrayList<>();
        String targetId = "UNKNOWN";
        String job1 = "UNKNOWN-RUN-1";
        String job2 = "UNKNOWN-RUN-2";
        String digestSeed = "";
        try {
            Path first = run1.toAbsolutePath().normalize();
            Path second = run2.toAbsolutePath().normalize();
            if (first.equals(second)) reasons.add("ORUDA_FINAL_CANDIDATE_RUN_ROOT_REUSED");

            addPrefixed(reasons, "RUN1_", new ReceiptLineageVerifier().verify(first, targetRoot));
            addPrefixed(reasons, "RUN2_", new ReceiptLineageVerifier().verify(second, targetRoot));

            OrudaEvidenceRegistry.Registry evidence1 = readEvidenceRegistry(first);
            OrudaEvidenceRegistry.Registry evidence2 = readEvidenceRegistry(second);
            JsonNode report1 = mapper.readTree(first.resolve("validation-report.json").toFile());
            JsonNode report2 = mapper.readTree(second.resolve("validation-report.json").toFile());
            JsonNode regression1 = mapper.readTree(first.resolve("regression-lock.json").toFile());
            JsonNode regression2 = mapper.readTree(second.resolve("regression-lock.json").toFile());

            targetId = evidence1.targetId();
            job1 = evidence1.jobId();
            job2 = evidence2.jobId();
            checkRunIdentity(targetId, job1, job2, evidence1, evidence2, report1, report2,
                    regression1, regression2, reasons);
            compareEvidenceRows(evidence1.rows(), evidence2.rows(), reasons);
            requireExpectedRows("RUN1", evidence1.rows(), reasons);
            requireExpectedRows("RUN2", evidence2.rows(), reasons);

            OrudaPackageExecutionRegistry packageGate = new OrudaPackageExecutionRegistry();
            ValidationResult packageCheck1 = packageGate.verify(first, PACKAGE_CATALOG, targetId, job1);
            ValidationResult packageCheck2 = packageGate.verify(second, PACKAGE_CATALOG, targetId, job2);
            addPrefixed(reasons, "RUN1_", packageCheck1);
            addPrefixed(reasons, "RUN2_", packageCheck2);
            OrudaPackageExecutionRegistry.Registry packages1 = packageCheck1.decision() == Decision.PASS
                    ? packageGate.read(first) : null;
            OrudaPackageExecutionRegistry.Registry packages2 = packageCheck2.decision() == Decision.PASS
                    ? packageGate.read(second) : null;
            if (packages1 == null || !packageGate.allPackagesPass(packages1)) {
                reasons.add("RUN1_ORUDA_ALL_EXECUTION_PACKAGES_NOT_PASS");
            }
            if (packages2 == null || !packageGate.allPackagesPass(packages2)) {
                reasons.add("RUN2_ORUDA_ALL_EXECUTION_PACKAGES_NOT_PASS");
            }
            if (packages1 != null && packages2 != null) comparePackageSemantics(packages1, packages2, reasons);

            verifyProductReceipt(first.resolve("internal-verifier-receipt.json"),
                    "ONSURE_INTERNAL_VERIFIER_RECEIPT_V1", "ONSURE_INTERNAL_VERIFIER", job1,
                    "RUN1_INTERNAL_VERIFIER_", reasons);
            verifyProductReceipt(first.resolve("internal-audit-receipt.json"),
                    "ONSURE_INTERNAL_AUDIT_RECEIPT_V1", "ONSURE_INTERNAL_AUDIT", job1,
                    "RUN1_INTERNAL_AUDIT_", reasons);
            verifyProductReceipt(second.resolve("internal-verifier-receipt.json"),
                    "ONSURE_INTERNAL_VERIFIER_RECEIPT_V1", "ONSURE_INTERNAL_VERIFIER", job2,
                    "RUN2_INTERNAL_VERIFIER_", reasons);
            verifyProductReceipt(second.resolve("internal-audit-receipt.json"),
                    "ONSURE_INTERNAL_AUDIT_RECEIPT_V1", "ONSURE_INTERNAL_AUDIT", job2,
                    "RUN2_INTERNAL_AUDIT_", reasons);

            IndependentRunReceiptVerifier.Verification independent1 = new IndependentRunReceiptVerifier().verify(
                    first.resolve(IndependentRunReceiptVerifier.FILE_NAME), targetId, job1,
                    evidence1.sourceTreeSha256());
            IndependentRunReceiptVerifier.Verification independent2 = new IndependentRunReceiptVerifier().verify(
                    second.resolve(IndependentRunReceiptVerifier.FILE_NAME), targetId, job2,
                    evidence2.sourceTreeSha256());
            addPrefixed(reasons, "RUN1_", independent1.result());
            addPrefixed(reasons, "RUN2_", independent2.result());
            checkIndependentOperators(independent1, independent2, reasons);

            Set<String> quality1 = qualityFixtureIds(evidence1.rows());
            Set<String> quality2 = qualityFixtureIds(evidence2.rows());
            if (!quality1.equals(quality2)) reasons.add("ORUDA_FINAL_CANDIDATE_QUALITY_FIXTURE_SET_MISMATCH");
            if (!quality1.isEmpty()) {
                addPrefixed(reasons, "RUN1_", new BlindReviewReceiptVerifier().verify(
                        first.resolve(BlindReviewReceiptVerifier.FILE_NAME), targetId, job1, quality1));
                addPrefixed(reasons, "RUN2_", new BlindReviewReceiptVerifier().verify(
                        second.resolve(BlindReviewReceiptVerifier.FILE_NAME), targetId, job2, quality2));
            }

            digestSeed = targetId + "|" + job1 + "|" + job2 + "|"
                    + evidence1.sourceTreeSha256() + "|" + evidence1.policyDigest() + "|"
                    + regression1.path("resultDigest").asText() + "|"
                    + evidenceSemantics(evidence1.rows()) + "|"
                    + packageSemantics(packages1) + "|" + packageSemantics(packages2) + "|"
                    + independentSemantics(independent1, independent2);
        } catch (Exception e) {
            reasons.add("ORUDA_FINAL_CANDIDATE_UNREADABLE:" + e.getClass().getSimpleName());
            digestSeed = targetId + "|" + job1 + "|" + job2 + "|" + reasons;
        }

        List<String> uniqueReasons = reasons.stream().distinct().sorted().toList();
        boolean eligible = uniqueReasons.isEmpty();
        String candidateDigest = sha256(digestSeed + "|" + uniqueReasons);
        return new GateResult(
                CONTRACT,
                "ORUDA-CANDIDATE-" + candidateDigest.substring(0, 24),
                targetId,
                job1,
                job2,
                Instant.now(),
                eligible,
                eligible ? "PASS" : "BLOCKED",
                uniqueReasons,
                candidateDigest,
                false);
    }

    public GateResult evaluateAndWrite(Path run1, Path run2, Path targetRoot, Path outputFile) throws Exception {
        GateResult result = evaluate(run1, run2, targetRoot);
        Path normalized = outputFile.toAbsolutePath().normalize();
        if (normalized.getParent() == null) throw new IllegalArgumentException("ORUDA_CANDIDATE_OUTPUT_PARENT_MISSING");
        Files.createDirectories(normalized.getParent());
        Path temporary = normalized.resolveSibling(normalized.getFileName() + ".tmp");
        outputMapper.writeValue(temporary.toFile(), result);
        try {
            Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING);
        }
        return result;
    }

    private OrudaEvidenceRegistry.Registry readEvidenceRegistry(Path run) throws Exception {
        return registryMapper.readValue(run.resolve(OrudaEvidenceRegistry.FILE_NAME).toFile(),
                OrudaEvidenceRegistry.Registry.class);
    }

    private static void checkRunIdentity(String targetId, String job1, String job2,
            OrudaEvidenceRegistry.Registry evidence1, OrudaEvidenceRegistry.Registry evidence2,
            JsonNode report1, JsonNode report2, JsonNode regression1, JsonNode regression2,
            List<String> reasons) {
        if (Objects.equals(job1, job2)) reasons.add("ORUDA_FINAL_CANDIDATE_JOB_ID_REUSED");
        if (!Objects.equals(evidence1.targetId(), evidence2.targetId())) reasons.add("ORUDA_FINAL_CANDIDATE_TARGET_MISMATCH");
        if (!Objects.equals(evidence1.sourceTreeSha256(), evidence2.sourceTreeSha256())) reasons.add("ORUDA_FINAL_CANDIDATE_SOURCE_MISMATCH");
        if (!Objects.equals(evidence1.policyDigest(), evidence2.policyDigest())) reasons.add("ORUDA_FINAL_CANDIDATE_POLICY_MISMATCH");
        requireTechnicallyCleanRun("RUN1", report1, reasons);
        requireTechnicallyCleanRun("RUN2", report2, reasons);
        if (!Objects.equals(targetId, report1.path("target").path("targetId").asText())
                || !Objects.equals(targetId, report2.path("target").path("targetId").asText())) {
            reasons.add("ORUDA_FINAL_CANDIDATE_REPORT_TARGET_MISMATCH");
        }
        if (!Objects.equals(regression1.path("resultDigest").asText(), regression2.path("resultDigest").asText())) {
            reasons.add("ORUDA_FINAL_CANDIDATE_REGRESSION_RESULT_MISMATCH");
        }
    }

    private static void requireTechnicallyCleanRun(String run, JsonNode report, List<String> reasons) {
        String decision = report.path("decision").asText();
        if ("PASS".equals(decision)) return;
        boolean nonfinalHold = "HOLD".equals(decision)
                && "SELF_VALIDATION_NONFINAL".equals(
                        report.path("summary").path("assurance_class").asText());
        boolean noFindings = report.path("findings").isArray() && report.path("findings").isEmpty();
        boolean noFailureModes = report.path("failureModes").isArray()
                && report.path("failureModes").isEmpty();
        boolean noRca = report.path("rcaRecords").isArray() && report.path("rcaRecords").isEmpty();
        JsonNode fixtures = report.path("fixtureResults");
        boolean allFixturesPass = fixtures.isArray() && !fixtures.isEmpty();
        if (allFixturesPass) {
            for (JsonNode fixture : fixtures) {
                if (!"PASS".equals(fixture.path("decision").asText())) {
                    allFixturesPass = false;
                    break;
                }
            }
        }
        if (!(nonfinalHold && noFindings && noFailureModes && noRca && allFixturesPass)) {
            reasons.add(run + "_ORUDA_FINAL_CANDIDATE_RUN_NOT_TECHNICALLY_CLEAN:" + decision);
        }
    }

    private static void compareEvidenceRows(List<OrudaEvidenceRegistry.Row> first,
            List<OrudaEvidenceRegistry.Row> second, List<String> reasons) {
        Map<String, String> one = evidenceRowMap(first);
        Map<String, String> two = evidenceRowMap(second);
        if (!one.keySet().equals(two.keySet())) {
            reasons.add("ORUDA_FINAL_CANDIDATE_FIXTURE_SET_MISMATCH");
            return;
        }
        for (String fixtureId : one.keySet()) {
            if (!Objects.equals(one.get(fixtureId), two.get(fixtureId))) {
                reasons.add("ORUDA_FINAL_CANDIDATE_FIXTURE_RESULT_MISMATCH:" + fixtureId);
            }
        }
    }

    private static Map<String, String> evidenceRowMap(List<OrudaEvidenceRegistry.Row> rows) {
        Map<String, String> values = new LinkedHashMap<>();
        for (OrudaEvidenceRegistry.Row row : rows) {
            String semantic = row.variantType() + "|" + row.expectedResult() + "|" + row.actualResult()
                    + "|" + row.verdict() + "|" + row.oracleId() + "|" + row.harnessId()
                    + "|" + row.inputHash() + "|" + row.rawLogHash();
            if (values.put(row.fixtureId(), semantic) != null) {
                throw new IllegalArgumentException("ORUDA_FINAL_CANDIDATE_DUPLICATE_FIXTURE:" + row.fixtureId());
            }
        }
        return values;
    }

    private static String evidenceSemantics(List<OrudaEvidenceRegistry.Row> rows) {
        return evidenceRowMap(rows).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce("", (a, b) -> a + "|" + b);
    }

    private static void comparePackageSemantics(OrudaPackageExecutionRegistry.Registry first,
            OrudaPackageExecutionRegistry.Registry second, List<String> reasons) {
        if (!packageSemanticMap(first).equals(packageSemanticMap(second))) {
            reasons.add("ORUDA_FINAL_CANDIDATE_PACKAGE_RESULT_MISMATCH");
        }
    }

    private static Map<String, String> packageSemanticMap(OrudaPackageExecutionRegistry.Registry registry) {
        Map<String, String> result = new LinkedHashMap<>();
        if (registry == null) return result;
        for (OrudaPackageExecutionRegistry.PackageResult value : registry.packages()) {
            String outputs = value.outputReceipts().stream()
                    .sorted(Comparator.comparing(OrudaPackageExecutionRegistry.OutputReceipt::outputId))
                    .map(output -> output.outputId() + ":" + output.decision() + ":" + output.semanticDigest())
                    .reduce("", (a, b) -> a + "|" + b);
            result.put(value.packageId(), value.status() + "|" + outputs);
        }
        return result;
    }

    private static String packageSemantics(OrudaPackageExecutionRegistry.Registry registry) {
        return packageSemanticMap(registry).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce("", (a, b) -> a + "|" + b);
    }

    private static void requireExpectedRows(String run, List<OrudaEvidenceRegistry.Row> rows,
            List<String> reasons) {
        for (OrudaEvidenceRegistry.Row row : rows) {
            if (!Set.of("EXPECTED_PASS", "EXPECTED_FAIL").contains(row.verdict())) {
                reasons.add(run + "_ORUDA_NON_EXPECTED_VERDICT:" + row.fixtureId() + ":" + row.verdict());
            }
            if ("RCA_PENDING".equals(row.rcaId())) reasons.add(run + "_ORUDA_RCA_PENDING:" + row.fixtureId());
        }
    }

    private static Set<String> qualityFixtureIds(List<OrudaEvidenceRegistry.Row> rows) {
        Set<String> result = new HashSet<>();
        for (OrudaEvidenceRegistry.Row row : rows) {
            if ("QUALITY".equals(row.variantType())) result.add(row.fixtureId());
        }
        return result;
    }

    private static void checkIndependentOperators(
            IndependentRunReceiptVerifier.Verification first,
            IndependentRunReceiptVerifier.Verification second,
            List<String> reasons) {
        if (first.receipt() == null || second.receipt() == null) return;
        if (Objects.equals(first.receipt().operatorId(), second.receipt().operatorId())) {
            reasons.add("ORUDA_FINAL_CANDIDATE_OPERATOR_NOT_INDEPENDENT");
        }
        if (!Objects.equals(first.receipt().environmentDigest(), second.receipt().environmentDigest())) {
            reasons.add("ORUDA_FINAL_CANDIDATE_ENVIRONMENT_PARITY_MISMATCH");
        }
    }

    private static String independentSemantics(
            IndependentRunReceiptVerifier.Verification first,
            IndependentRunReceiptVerifier.Verification second) {
        if (first.receipt() == null || second.receipt() == null) return "INDEPENDENT_RUN_RECEIPT_MISSING";
        return first.receipt().operatorId() + "|" + second.receipt().operatorId()
                + "|" + first.receipt().environmentDigest();
    }

    private static void verifyProductReceipt(Path file, String contract, String authority, String jobId,
            String prefix, List<String> reasons) {
        try {
            if (!Files.isRegularFile(file)) {
                reasons.add(prefix + "RECEIPT_MISSING");
                return;
            }
            Map<String, Object> value = objectMap(CANONICAL_MAPPER.readTree(file.toFile()));
            Object stored = value.remove("receipt_sha256");
            if (!contract.equals(value.get("contract"))) reasons.add(prefix + "CONTRACT_MISMATCH");
            if (!authority.equals(value.get("authority"))) reasons.add(prefix + "AUTHORITY_MISMATCH");
            if (!jobId.equals(value.get("job_id"))) reasons.add(prefix + "JOB_MISMATCH");
            if (!"PASS".equals(value.get("decision"))) reasons.add(prefix + "NON_PASS");
            String expected = sha256(CANONICAL_MAPPER.writeValueAsBytes(new TreeMap<>(value)));
            if (!(stored instanceof String digest) || !digest.equals(expected)) reasons.add(prefix + "HASH_MISMATCH");
        } catch (Exception e) {
            reasons.add(prefix + "RECEIPT_UNREADABLE:" + e.getClass().getSimpleName());
        }
    }

    private static Map<String, Object> objectMap(JsonNode node) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException("JSON_OBJECT_REQUIRED");
        Map<String, Object> result = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            result.put(field.getKey(), jsonValue(field.getValue()));
        }
        return result;
    }

    private static Object jsonValue(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isObject()) return objectMap(node);
        if (node.isArray()) {
            List<Object> values = new ArrayList<>();
            node.forEach(item -> values.add(jsonValue(item)));
            return values;
        }
        if (node.isTextual()) return node.textValue();
        if (node.isBoolean()) return node.booleanValue();
        if (node.isIntegralNumber()) return node.canConvertToInt() ? node.intValue() : node.longValue();
        if (node.isFloatingPointNumber()) return node.decimalValue();
        throw new IllegalArgumentException("JSON_VALUE_TYPE_UNSUPPORTED:" + node.getNodeType());
    }

    private static void addPrefixed(List<String> reasons, String prefix, ValidationResult result) {
        if (result.decision() == Decision.PASS) return;
        for (String violation : result.violations()) reasons.add(prefix + violation);
    }

    private static String sha256(String value) {
        try {
            return sha256(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
