package kr.co.oruda.onsure.harness;

import kr.co.oruda.onsure.harness.HarnessModels.Axis;
import kr.co.oruda.onsure.harness.HarnessModels.AxisResult;
import kr.co.oruda.onsure.harness.HarnessModels.AxisSet;
import kr.co.oruda.onsure.harness.HarnessModels.Decision;
import kr.co.oruda.onsure.harness.HarnessModels.Fixture;
import kr.co.oruda.onsure.harness.HarnessModels.FixtureResult;
import kr.co.oruda.onsure.harness.HarnessModels.FixtureSet;
import kr.co.oruda.onsure.harness.HarnessModels.OracleSet;
import kr.co.oruda.onsure.harness.HarnessModels.OracleSpec;
import kr.co.oruda.onsure.harness.HarnessModels.RcaRecord;
import kr.co.oruda.onsure.harness.HarnessModels.RunReceipt;
import kr.co.oruda.onsure.harness.HarnessModels.RunSummary;
import kr.co.oruda.onsure.harness.HarnessModels.Severity;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class UniversalHarnessRunner {
    public record RunResult(Path runRoot, RunSummary summary) {}

    public RunResult run(Path repositoryRoot, Path axesFile, Path fixtureFile, Path oracleFile,
            Path outputRoot, String operatorId, String environmentLabel) throws Exception {
        if (operatorId == null || !operatorId.matches("[A-Za-z0-9._-]{2,128}")) {
            throw new IllegalArgumentException("OPERATOR_ID_INVALID");
        }
        Path repo = repositoryRoot.toAbsolutePath().normalize();
        AxisSet axisSet = JsonSupport.read(axesFile, AxisSet.class);
        FixtureSet fixtureSet = JsonSupport.read(fixtureFile, FixtureSet.class);
        OracleSet oracleSet = JsonSupport.read(oracleFile, OracleSet.class);
        validateContracts(axisSet, fixtureSet, oracleSet);
        Map<String, Axis> axes = indexAxes(axisSet);
        Map<String, OracleSpec> oracles = indexOracles(oracleSet);
        validateFixtures(fixtureSet.fixtures(), axes, oracles);

        Instant startedAt = Instant.now();
        String runId = createRunId(startedAt);
        Path runRoot = outputRoot.toAbsolutePath().normalize().resolve(runId);
        Files.createDirectories(runRoot);
        String environmentDigest = Hashing.environmentDigest(environmentLabel);
        List<FixtureResult> fixtureResults = new ArrayList<>();

        for (Fixture fixture : fixtureSet.fixtures()) {
            try {
                fixtureResults.add(new FixtureExecutor().execute(
                        repo, runRoot, runId, fixture, oracles.get(fixture.oracleId()), environmentDigest));
            } catch (Exception e) {
                fixtureResults.add(writeBlockedFixture(runRoot, runId, fixture, environmentDigest, e));
            }
        }

        List<AxisResult> axisResults = calculateAxisResults(axisSet.axes(), fixtureSet.fixtures(), fixtureResults);
        Decision decision = aggregateDecision(fixtureResults.stream().map(FixtureResult::decision).toList());
        int critical = countDefects(fixtureResults, Severity.CRITICAL);
        int major = countDefects(fixtureResults, Severity.MAJOR);
        int minor = countDefects(fixtureResults, Severity.MINOR);
        int notRun = (int) fixtureResults.stream().filter(value -> value.decision() == Decision.NOT_RUN).count();
        int blocked = (int) fixtureResults.stream().filter(value -> value.decision() == Decision.BLOCKED).count();
        writeRcaRecords(runRoot, runId, fixtureResults);
        String normalizedDigest = normalizedDigest(runRoot, fixtureResults, axisResults);
        Instant completedAt = Instant.now();

        RunSummary summary = new RunSummary(
                "ONSURE_UNIVERSAL_RUN_SUMMARY_V1",
                runId,
                fixtureSet.targetId(),
                operatorId,
                environmentDigest,
                startedAt,
                completedAt,
                decision,
                critical,
                major,
                minor,
                notRun,
                blocked,
                axisResults,
                fixtureResults,
                normalizedDigest);
        Path summaryFile = runRoot.resolve("run-summary.json");
        JsonSupport.writeAtomic(summaryFile, summary);

        Path manifestFile = runRoot.resolve("evidence-manifest.sha256");
        writeManifest(runRoot, manifestFile);
        String summaryHash = Hashing.sha256(summaryFile);
        String manifestHash = Hashing.sha256(manifestFile);
        Instant receiptAt = Instant.now();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contract", "ONSURE_UNIVERSAL_RUN_RECEIPT_V1");
        body.put("run_id", runId);
        body.put("run_summary_sha256", summaryHash);
        body.put("evidence_manifest_sha256", manifestHash);
        body.put("decision", decision.name());
        body.put("created_at", receiptAt.toString());
        String receiptHash = Hashing.sha256(JsonSupport.canonicalBytes(body));
        RunReceipt runReceipt = new RunReceipt(
                "ONSURE_UNIVERSAL_RUN_RECEIPT_V1", runId, summaryHash, manifestHash,
                decision, receiptAt, receiptHash);
        JsonSupport.writeAtomic(runRoot.resolve("run-receipt.json"), runReceipt);
        return new RunResult(runRoot, summary);
    }

    private static void validateContracts(AxisSet axes, FixtureSet fixtures, OracleSet oracles) {
        if (!"ONSURE_UNIVERSAL_VERIFICATION_AXES_V1".equals(axes.contract())) {
            throw new IllegalArgumentException("AXIS_CONTRACT_MISMATCH");
        }
        if (!"ONSURE_UNIVERSAL_FIXTURE_SET_V1".equals(fixtures.contract())) {
            throw new IllegalArgumentException("FIXTURE_SET_CONTRACT_MISMATCH");
        }
        if (!"ONSURE_UNIVERSAL_ORACLE_SET_V1".equals(oracles.contract())) {
            throw new IllegalArgumentException("ORACLE_SET_CONTRACT_MISMATCH");
        }
    }

    private static Map<String, Axis> indexAxes(AxisSet axisSet) {
        if (axisSet.axes().size() != 30) throw new IllegalArgumentException("UNIVERSAL_AXIS_COUNT_MUST_BE_30");
        Map<String, Axis> result = new LinkedHashMap<>();
        Set<String> codes = new HashSet<>();
        for (Axis axis : axisSet.axes()) {
            if (result.put(axis.id(), axis) != null || !codes.add(axis.code())) {
                throw new IllegalArgumentException("DUPLICATE_AXIS:" + axis.id());
            }
        }
        return result;
    }

    private static Map<String, OracleSpec> indexOracles(OracleSet oracleSet) {
        Map<String, OracleSpec> result = new LinkedHashMap<>();
        for (OracleSpec oracle : oracleSet.oracles()) {
            if (result.put(oracle.oracleId(), oracle) != null) {
                throw new IllegalArgumentException("DUPLICATE_ORACLE:" + oracle.oracleId());
            }
        }
        return result;
    }

    private static void validateFixtures(List<Fixture> fixtures, Map<String, Axis> axes,
            Map<String, OracleSpec> oracles) {
        Set<String> fixtureIds = new HashSet<>();
        Set<String> coveredAxes = new HashSet<>();
        for (Fixture fixture : fixtures) {
            if (!fixtureIds.add(fixture.fixtureId())) throw new IllegalArgumentException("DUPLICATE_FIXTURE");
            if (!oracles.containsKey(fixture.oracleId())) {
                throw new IllegalArgumentException("ORACLE_NOT_FOUND:" + fixture.oracleId());
            }
            for (String axisId : fixture.axisIds()) {
                if (!axes.containsKey(axisId)) throw new IllegalArgumentException("AXIS_NOT_FOUND:" + axisId);
                coveredAxes.add(axisId);
            }
        }
        Set<String> required = new HashSet<>();
        axes.values().stream().filter(Axis::required).map(Axis::id).forEach(required::add);
        if (!coveredAxes.containsAll(required)) {
            required.removeAll(coveredAxes);
            throw new IllegalArgumentException("REQUIRED_AXES_NOT_COVERED:" + required);
        }
    }

    private static FixtureResult writeBlockedFixture(Path runRoot, String runId, Fixture fixture,
            String environmentDigest, Exception failure) throws Exception {
        String reason = "EXECUTION_BLOCKED:" + failure.getClass().getSimpleName();
        String seed = runId + "|" + fixture.fixtureId() + "|" + reason;
        Path evidence = runRoot.resolve("evidence").resolve(fixture.fixtureId() + ".json");
        Files.createDirectories(evidence.getParent());
        Map<String, Object> evidenceBody = new LinkedHashMap<>();
        evidenceBody.put("contract", "ONSURE_UNIVERSAL_EVIDENCE_V1");
        evidenceBody.put("evidence_id", "EVD-" + Hashing.sha256(seed).substring(0, 24));
        evidenceBody.put("run_id", runId);
        evidenceBody.put("fixture_id", fixture.fixtureId());
        evidenceBody.put("axis_ids", fixture.axisIds());
        evidenceBody.put("command", fixture.command());
        evidenceBody.put("cwd", fixture.cwd());
        evidenceBody.put("started_at", Instant.now().toString());
        evidenceBody.put("completed_at", Instant.now().toString());
        evidenceBody.put("exit_code", null);
        evidenceBody.put("timed_out", false);
        evidenceBody.put("stdout_sha256", Hashing.sha256(""));
        evidenceBody.put("stderr_sha256", Hashing.sha256(reason));
        evidenceBody.put("environment_sha256", environmentDigest);
        evidenceBody.put("decision", Decision.BLOCKED.name());
        evidenceBody.put("reason", reason);
        JsonSupport.writeAtomic(evidence, evidenceBody);
        String evidenceHash = Hashing.sha256(evidence);

        Path receipt = runRoot.resolve("receipts").resolve(fixture.fixtureId() + ".json");
        Files.createDirectories(receipt.getParent());
        Map<String, Object> receiptBody = new LinkedHashMap<>();
        receiptBody.put("contract", "ONSURE_UNIVERSAL_RECEIPT_V1");
        receiptBody.put("receipt_id", "RCT-" + Hashing.sha256(seed).substring(0, 24));
        receiptBody.put("run_id", runId);
        receiptBody.put("fixture_id", fixture.fixtureId());
        receiptBody.put("oracle_id", fixture.oracleId());
        receiptBody.put("evidence_sha256", evidenceHash);
        receiptBody.put("decision", Decision.BLOCKED.name());
        receiptBody.put("reason", reason);
        receiptBody.put("severity", fixture.severity().name());
        receiptBody.put("rca_required", false);
        receiptBody.put("created_at", Instant.now().toString());
        receiptBody.put("receipt_sha256", Hashing.sha256(JsonSupport.canonicalBytes(receiptBody)));
        JsonSupport.writeAtomic(receipt, receiptBody);
        return new FixtureResult(
                fixture.fixtureId(), fixture.kind(), fixture.severity(), Decision.BLOCKED, reason,
                relative(runRoot, evidence), relative(runRoot, receipt), evidenceHash, Hashing.sha256(receipt));
    }

    private static List<AxisResult> calculateAxisResults(List<Axis> axes, List<Fixture> fixtures,
            List<FixtureResult> results) {
        Map<String, FixtureResult> resultIndex = new HashMap<>();
        results.forEach(value -> resultIndex.put(value.fixtureId(), value));
        List<AxisResult> axisResults = new ArrayList<>();
        for (Axis axis : axes) {
            List<String> ids = fixtures.stream().filter(value -> value.axisIds().contains(axis.id()))
                    .map(Fixture::fixtureId).sorted().toList();
            Decision decision = ids.isEmpty() ? Decision.NOT_RUN
                    : aggregateDecision(ids.stream().map(resultIndex::get).map(FixtureResult::decision).toList());
            String reason = ids.isEmpty() ? "NO_FIXTURE_BOUND" : "AGGREGATED_FROM_" + ids.size() + "_FIXTURES";
            axisResults.add(new AxisResult(axis.id(), decision, ids, reason));
        }
        return List.copyOf(axisResults);
    }

    private static Decision aggregateDecision(List<Decision> decisions) {
        if (decisions.stream().anyMatch(value -> value == Decision.FAIL)) return Decision.FAIL;
        if (decisions.stream().anyMatch(value -> value == Decision.BLOCKED)) return Decision.BLOCKED;
        if (decisions.stream().anyMatch(value -> value == Decision.NOT_RUN)) return Decision.NOT_RUN;
        return Decision.PASS;
    }

    private static int countDefects(List<FixtureResult> values, Severity severity) {
        return (int) values.stream().filter(value -> value.decision() == Decision.FAIL)
                .filter(value -> value.severity() == severity).count();
    }

    private static void writeRcaRecords(Path runRoot, String runId, List<FixtureResult> results) throws Exception {
        for (FixtureResult result : results) {
            if (result.decision() != Decision.FAIL) continue;
            RcaRecord rca = new RcaRecord(
                    "ONSURE_UNIVERSAL_RCA_V1",
                    "RCA-" + Hashing.sha256(runId + "|" + result.fixtureId()).substring(0, 24),
                    runId,
                    result.fixtureId(),
                    result.severity(),
                    result.reason(),
                    "PENDING_ANALYSIS",
                    "NOT_SET",
                    "NOT_RUN",
                    "NOT_RUN",
                    "RCA_PENDING");
            JsonSupport.writeAtomic(runRoot.resolve("rca").resolve(result.fixtureId() + ".json"), rca);
        }
    }

    private static String normalizedDigest(Path runRoot, List<FixtureResult> fixtures,
            List<AxisResult> axes) throws Exception {
        List<String> fixtureSemantics = new ArrayList<>();
        for (FixtureResult result : fixtures.stream().sorted(Comparator.comparing(FixtureResult::fixtureId)).toList()) {
            Map<?, ?> evidence = JsonSupport.MAPPER.readValue(
                    runRoot.resolve(result.evidencePath()).toFile(), Map.class);
            fixtureSemantics.add(result.fixtureId() + "|" + result.decision() + "|" + result.reason()
                    + "|" + evidence.get("exit_code") + "|" + evidence.get("timed_out")
                    + "|" + evidence.get("stdout_sha256") + "|" + evidence.get("stderr_sha256"));
        }
        String axisSemantics = axes.stream().sorted(Comparator.comparing(AxisResult::axisId))
                .map(value -> value.axisId() + "|" + value.decision())
                .reduce("", (a, b) -> a + "\n" + b);
        return Hashing.sha256(String.join("\n", fixtureSemantics) + axisSemantics);
    }

    private static void writeManifest(Path runRoot, Path manifestFile) throws Exception {
        List<Path> files;
        try (var stream = Files.walk(runRoot)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> !path.equals(manifestFile))
                    .filter(path -> !path.getFileName().toString().equals("run-receipt.json"))
                    .toList();
        }
        Files.writeString(manifestFile, Hashing.manifest(runRoot, files), StandardCharsets.UTF_8);
    }

    private static String createRunId(Instant instant) {
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .withZone(ZoneOffset.UTC).format(instant);
        return "RUN-" + stamp + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String relative(Path root, Path file) {
        return root.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize())
                .toString().replace('\\', '/');
    }
}
