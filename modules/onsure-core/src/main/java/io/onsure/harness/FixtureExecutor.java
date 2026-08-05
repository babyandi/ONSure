package io.onsure.harness;

import io.onsure.harness.HarnessModels.Decision;
import io.onsure.harness.HarnessModels.Evidence;
import io.onsure.harness.HarnessModels.Fixture;
import io.onsure.harness.HarnessModels.FixtureResult;
import io.onsure.harness.HarnessModels.OracleSpec;
import io.onsure.harness.HarnessModels.Receipt;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class FixtureExecutor {
    private static final Set<String> ALLOWED_EXECUTABLES = Set.of("bash");
    private static final int MAX_OUTPUT_BYTES = 1_048_576;

    public FixtureResult execute(Path repositoryRoot, Path runRoot, String runId,
            Fixture fixture, OracleSpec oracle, String environmentDigest) throws Exception {
        Path repo = repositoryRoot.toAbsolutePath().normalize();
        Path workingDirectory = repo.resolve(fixture.cwd()).normalize();
        validateCommand(repo, workingDirectory, fixture.command());

        Path logDirectory = runRoot.resolve("logs");
        Path evidenceDirectory = runRoot.resolve("evidence");
        Path receiptDirectory = runRoot.resolve("receipts");
        Path temporaryDirectory = runRoot.resolve("tmp")
                .resolve(Hashing.sha256(fixture.fixtureId()).substring(0, 16));
        Files.createDirectories(logDirectory);
        Files.createDirectories(evidenceDirectory);
        Files.createDirectories(receiptDirectory);
        Files.createDirectories(temporaryDirectory);

        Path stdoutFile = logDirectory.resolve(fixture.fixtureId() + ".stdout.log");
        Path stderrFile = logDirectory.resolve(fixture.fixtureId() + ".stderr.log");
        Instant startedAt = Instant.now();
        boolean processStarted = false;
        boolean timedOut = false;
        Integer exitCode = null;
        String stdout = "";
        String stderr = "";

        try {
            ProcessBuilder builder = new ProcessBuilder(fixture.command())
                    .directory(workingDirectory.toFile())
                    .redirectOutput(stdoutFile.toFile())
                    .redirectError(stderrFile.toFile());
            restrictEnvironment(builder.environment(), temporaryDirectory);
            Process process = builder.start();
            processStarted = true;
            boolean completed = process.waitFor(fixture.timeoutSec(), TimeUnit.SECONDS);
            if (!completed) {
                timedOut = true;
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                exitCode = 124;
            } else {
                exitCode = process.exitValue();
            }
            stdout = readLimited(stdoutFile);
            stderr = readLimited(stderrFile);
        } catch (Exception e) {
            stderr = e.getClass().getSimpleName() + ":" + String.valueOf(e.getMessage());
            Files.writeString(stderrFile, stderr, StandardCharsets.UTF_8);
        }

        boolean evidenceComplete = requiredEvidenceComplete(
                fixture.requiredEvidence(), processStarted, exitCode, stdoutFile, stderrFile, environmentDigest);
        OracleEngine.OracleDecision oracleDecision = new OracleEngine().evaluate(
                oracle, fixture, stdout, exitCode, processStarted, timedOut, evidenceComplete, workingDirectory);
        Instant completedAt = Instant.now();
        String stdoutHash = Hashing.sha256(stdout.getBytes(StandardCharsets.UTF_8));
        String stderrHash = Hashing.sha256(stderr.getBytes(StandardCharsets.UTF_8));
        String evidenceId = "EVD-" + Hashing.sha256(runId + "|" + fixture.fixtureId()
                + "|" + stdoutHash + "|" + stderrHash).substring(0, 24);

        Evidence evidence = new Evidence(
                "ONSURE_UNIVERSAL_EVIDENCE_V1",
                evidenceId,
                runId,
                fixture.fixtureId(),
                fixture.axisIds(),
                fixture.command(),
                fixture.cwd(),
                startedAt,
                completedAt,
                exitCode,
                timedOut,
                stdoutHash,
                stderrHash,
                environmentDigest,
                oracleDecision.decision(),
                oracleDecision.reason());
        Path evidenceFile = evidenceDirectory.resolve(fixture.fixtureId() + ".json");
        JsonSupport.writeAtomic(evidenceFile, evidence);
        String evidenceHash = Hashing.sha256(evidenceFile);

        Instant receiptTime = Instant.now();
        Map<String, Object> receiptBody = new LinkedHashMap<>();
        receiptBody.put("contract", "ONSURE_UNIVERSAL_RECEIPT_V1");
        receiptBody.put("receipt_id", "RCT-" + Hashing.sha256(runId + "|" + fixture.fixtureId()).substring(0, 24));
        receiptBody.put("run_id", runId);
        receiptBody.put("fixture_id", fixture.fixtureId());
        receiptBody.put("oracle_id", fixture.oracleId());
        receiptBody.put("evidence_sha256", evidenceHash);
        receiptBody.put("decision", oracleDecision.decision().name());
        receiptBody.put("reason", oracleDecision.reason());
        receiptBody.put("severity", fixture.severity().name());
        receiptBody.put("rca_required", oracleDecision.decision() == Decision.FAIL);
        receiptBody.put("created_at", receiptTime.toString());
        String receiptHash = Hashing.sha256(JsonSupport.canonicalBytes(receiptBody));
        Receipt receipt = new Receipt(
                "ONSURE_UNIVERSAL_RECEIPT_V1",
                String.valueOf(receiptBody.get("receipt_id")),
                runId,
                fixture.fixtureId(),
                fixture.oracleId(),
                evidenceHash,
                oracleDecision.decision(),
                oracleDecision.reason(),
                fixture.severity(),
                oracleDecision.decision() == Decision.FAIL,
                receiptTime,
                receiptHash);
        Path receiptFile = receiptDirectory.resolve(fixture.fixtureId() + ".json");
        JsonSupport.writeAtomic(receiptFile, receipt);

        return new FixtureResult(
                fixture.fixtureId(), fixture.kind(), fixture.severity(), oracleDecision.decision(),
                oracleDecision.reason(), relative(runRoot, evidenceFile), relative(runRoot, receiptFile),
                evidenceHash, Hashing.sha256(receiptFile));
    }

    private static void validateCommand(Path repo, Path cwd, List<String> command) {
        if (!cwd.startsWith(repo) || !Files.isDirectory(cwd)) {
            throw new IllegalArgumentException("FIXTURE_CWD_INVALID");
        }
        if (command.size() < 2 || command.size() > 64) {
            throw new IllegalArgumentException("FIXTURE_COMMAND_SIZE_INVALID");
        }
        String executable = Path.of(command.get(0)).getFileName().toString();
        if (!ALLOWED_EXECUTABLES.contains(executable)) {
            throw new IllegalArgumentException("FIXTURE_EXECUTABLE_NOT_ALLOWED:" + executable);
        }
        if (command.contains("-c") || command.contains("--command")) {
            throw new IllegalArgumentException("INLINE_SHELL_COMMAND_PROHIBITED");
        }
        Path script = Path.of(command.get(1));
        if (script.isAbsolute()) throw new IllegalArgumentException("ABSOLUTE_SCRIPT_PROHIBITED");
        Path resolved = cwd.resolve(script).normalize();
        if (!resolved.startsWith(cwd) || !Files.isRegularFile(resolved)) {
            throw new IllegalArgumentException("FIXTURE_SCRIPT_INVALID");
        }
    }

    private static void restrictEnvironment(Map<String, String> environment, Path temporaryDirectory) {
        Map<String, String> host = System.getenv();
        environment.clear();
        for (String key : List.of("PATH", "JAVA_HOME", "HOME", "LANG", "LC_ALL", "TZ")) {
            String value = host.get(key);
            if (value != null) environment.put(key, value);
        }
        environment.put("TMPDIR", temporaryDirectory.toAbsolutePath().normalize().toString());
    }

    private static boolean requiredEvidenceComplete(List<String> required, boolean started,
            Integer exitCode, Path stdout, Path stderr, String environmentDigest) {
        for (String item : required) {
            boolean present = switch (item) {
                case "stdout" -> Files.isRegularFile(stdout);
                case "stderr" -> Files.isRegularFile(stderr);
                case "exit_code" -> started && exitCode != null;
                case "environment" -> environmentDigest != null && environmentDigest.matches("[0-9a-f]{64}");
                default -> false;
            };
            if (!present) return false;
        }
        return true;
    }

    private static String readLimited(Path file) throws Exception {
        if (!Files.isRegularFile(file)) return "";
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length > MAX_OUTPUT_BYTES) throw new IllegalArgumentException("FIXTURE_OUTPUT_LIMIT_EXCEEDED");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String relative(Path root, Path file) {
        return root.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize())
                .toString().replace('\\', '/');
    }
}
