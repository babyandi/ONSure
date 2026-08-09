package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.oruda.onsure.assurance.Decision;
import kr.co.oruda.onsure.platform.ValidationModel.Evidence;
import kr.co.oruda.onsure.platform.ValidationModel.FailureMode;
import kr.co.oruda.onsure.platform.ValidationModel.Finding;
import kr.co.oruda.onsure.platform.ValidationModel.FindingStatus;
import kr.co.oruda.onsure.platform.ValidationModel.FixtureResult;
import kr.co.oruda.onsure.platform.ValidationModel.RcaRecord;
import kr.co.oruda.onsure.platform.ValidationModel.RegressionLock;
import kr.co.oruda.onsure.platform.ValidationModel.Severity;
import kr.co.oruda.onsure.platform.ValidationModel.StageResult;
import kr.co.oruda.onsure.platform.ValidationModel.TargetType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Default generic stages used by the first commercial ONSURE engine slice. */
public final class BuiltInStages {
    private BuiltInStages() {}

    public static List<ValidatorStage> defaults() {
        return List.of(
                new TargetIntakeStage(),
                new SourceInventoryStage(),
                new StaticPatternStage(),
                new AiBehaviorStage(),
                new DependencyScanStage(),
                new RuntimeFixtureStage(),
                new ContainerValidationStage(),
                new FailureAnalysisStage(),
                new RegressionLockStage());
    }

    private static final class TargetIntakeStage implements ValidatorStage {
        @Override public String stageId() { return "TARGET_INTAKE"; }
        @Override public boolean supports(ValidationContext context) { return true; }

        @Override
        public StageResult execute(ValidationContext context) throws Exception {
            Instant start = Instant.now();
            context.adapter().validateRegistration(context.target());
            Map<String, Object> metadata = context.adapter().collectTargetMetadata(context.target());
            metadata.forEach(context::putAttribute);
            String canonical = new ObjectMapper().writeValueAsString(new java.util.TreeMap<>(metadata));
            String digest = Hashing.sha256(canonical);
            context.addEvidence(new Evidence(
                    "EV-TARGET-" + digest.substring(0, 16), "TARGET_METADATA",
                    context.target().sourceRoot().toString(), digest, Instant.now(), metadata));
            return result(stageId(), Decision.PASS, start, context, 0,
                    Map.of("metadata_fields", metadata.size()));
        }
    }

    private static final class SourceInventoryStage implements ValidatorStage {
        @Override public String stageId() { return "SOURCE_INVENTORY"; }
        @Override public boolean supports(ValidationContext context) { return true; }

        @Override
        public StageResult execute(ValidationContext context) throws Exception {
            Instant start = Instant.now();
            String digest = Hashing.tree(context.target().sourceRoot());
            SourceReferenceBinding.Verification sourceReference = SourceReferenceBinding.verify(
                    context.target().sourceRoot(),
                    context.target().immutableSourceReference(),
                    digest);
            long fileCount;
            try (var stream = Files.walk(context.target().sourceRoot())) {
                fileCount = stream.filter(Files::isRegularFile).count();
            }
            context.addEvidence(new Evidence(
                    "EV-SOURCE-" + digest.substring(0, 16), "SOURCE_TREE",
                    context.target().sourceRoot().toString(), digest, Instant.now(),
                    Map.of("file_count", fileCount,
                            "immutable_reference", context.target().immutableSourceReference(),
                            "immutable_reference_mode", sourceReference.mode(),
                            "immutable_reference_verified", true)));
            context.putAttribute("source_tree_sha256", digest);
            context.putAttribute("immutable_source_verified", true);
            context.putAttribute("immutable_source_reference_mode", sourceReference.mode());
            return result(stageId(), Decision.PASS, start, context, 0,
                    Map.of("files", fileCount));
        }
    }

    private static final class StaticPatternStage implements ValidatorStage {
        private record Rule(String token, String category, Severity severity, String title, String remediation) {}
        private static final List<Rule> RULES = List.of(
                new Rule("TODO_BUG", "CORRECTNESS", Severity.HIGH,
                        "Known unresolved defect marker", "Replace the defect marker with tested implementation."),
                new Rule("SECRET_", "SECRET_EXPOSURE", Severity.HIGH,
                        "Hard-coded secret material", "Move the secret to an approved secret provider and rotate it."),
                new Rule("Runtime.getRuntime().exec", "COMMAND_EXECUTION", Severity.CRITICAL,
                        "Unbounded operating-system command execution", "Replace with an allowlisted isolated command adapter."),
                new Rule("eval(", "DYNAMIC_EXECUTION", Severity.HIGH,
                        "Dynamic code execution", "Remove dynamic evaluation or enforce a strict parser and sandbox."));

        @Override public String stageId() { return "STATIC_ANALYSIS"; }
        @Override public boolean supports(ValidationContext context) { return true; }

        @Override
        public StageResult execute(ValidationContext context) throws Exception {
            Instant start = Instant.now();
            int before = context.findings().size();
            for (Path file : textFiles(context.target().sourceRoot())) {
                String content;
                try { content = Files.readString(file, StandardCharsets.UTF_8); }
                catch (Exception ignored) { continue; }
                for (Rule rule : RULES) {
                    if (content.contains(rule.token())) {
                        String relative = Hashing.relative(context.target().sourceRoot(), file);
                        String fileDigest = Hashing.file(file);
                        String evidenceDigest = Hashing.sha256(fileDigest + "|" + rule.token());
                        String evidenceId = "EV-FILE-" + evidenceDigest.substring(0, 16);
                        context.addEvidence(new Evidence(evidenceId, "SOURCE_FILE_PATTERN", relative,
                                evidenceDigest, Instant.now(),
                                Map.of("file_sha256", fileDigest, "matched_token", rule.token())));
                        addFinding(context, stageId(), rule.category(), rule.severity(), rule.title(),
                                "Pattern '" + rule.token() + "' was detected in source.", relative,
                                List.of(evidenceId));
                        context.putAttribute("remediation:" + rule.category(), rule.remediation());
                    }
                }
            }
            int created = context.findings().size() - before;
            return result(stageId(), created == 0 ? Decision.PASS : Decision.FAIL,
                    start, context, before, Map.of("findings_created", created));
        }
    }

    private static final class AiBehaviorStage implements ValidatorStage {
        private record Rule(String token, String category, Severity severity, String title) {}
        private static final List<Rule> RULES = List.of(
                new Rule("ALLOW_UNTRUSTED_TOOL", "AI_TOOL_AUTHORIZATION", Severity.CRITICAL,
                        "Untrusted tool execution is allowed"),
                new Rule("SELF_APPROVE", "AI_SELF_APPROVAL", Severity.HIGH,
                        "Agent can approve its own output"),
                new Rule("PROMPT_INJECTION_BYPASS", "PROMPT_INJECTION", Severity.HIGH,
                        "Prompt-injection protection is bypassed"),
                new Rule("EXPORT_FULL_CONTEXT", "AI_DATA_EXFILTRATION", Severity.CRITICAL,
                        "Full model context can be exported"));

        @Override public String stageId() { return "AI_BEHAVIOR_VALIDATION"; }
        @Override public boolean supports(ValidationContext context) {
            return context.target().targetType() == TargetType.AI_APPLICATION
                    || context.target().targetType() == TargetType.AI_AGENTIC_PLATFORM;
        }

        @Override
        public StageResult execute(ValidationContext context) throws Exception {
            Instant start = Instant.now();
            int before = context.findings().size();
            for (Path file : textFiles(context.target().sourceRoot())) {
                String content;
                try { content = Files.readString(file, StandardCharsets.UTF_8); }
                catch (Exception ignored) { continue; }
                for (Rule rule : RULES) {
                    if (content.contains(rule.token())) {
                        String relative = Hashing.relative(context.target().sourceRoot(), file);
                        String fileDigest = Hashing.file(file);
                        String evidenceDigest = Hashing.sha256(fileDigest + "|" + rule.token());
                        String evidenceId = "EV-AI-" + evidenceDigest.substring(0, 16);
                        context.addEvidence(new Evidence(evidenceId, "AI_POLICY_SOURCE", relative,
                                evidenceDigest, Instant.now(),
                                Map.of("file_sha256", fileDigest, "matched_token", rule.token())));
                        addFinding(context, stageId(), rule.category(), rule.severity(), rule.title(),
                                "AI behavior policy contains unsafe marker '" + rule.token() + "'.",
                                relative, List.of(evidenceId));
                    }
                }
            }
            int created = context.findings().size() - before;
            return result(stageId(), created == 0 ? Decision.PASS : Decision.FAIL,
                    start, context, before, Map.of("findings_created", created));
        }
    }

    /**
     * Wires {@link VulnerabilityDenylistVerifier} -- previously only run against ONSURE's own
     * pom.xml as part of its build-lane self-check (see {@link BuildLaneReceiptService}) -- into
     * the generic validation engine so every Maven target is checked against the same manually
     * curated denylist (contracts/dependency-vulnerability-denylist.v1.json). A denylisted
     * groupId:artifactId:version is vulnerable regardless of whose pom.xml declares it, so this
     * file doubles as ONSURE's org-wide known-bad-dependency registry.
     *
     * <p>Scoped to Maven only: {@link DependencyManifestVerifier} (which this verifier is built on)
     * only knows how to parse pom.xml today. Non-Maven ecosystems (package.json, requirements.txt,
     * go.mod, etc.) are a remaining gap, not attempted here -- {@link #supports} returns false and
     * the stage is skipped (not a forced PASS or FAIL) whenever the target has no pom.xml at its
     * source root, which the {@link ValidationEngine} filters out before execution.
     */
    private static final class DependencyScanStage implements ValidatorStage {
        private static final Path DENYLIST_PATH =
                Path.of("contracts/dependency-vulnerability-denylist.v1.json");

        @Override public String stageId() { return "DEPENDENCY_SCAN"; }

        @Override public boolean supports(ValidationContext context) {
            return Files.isRegularFile(context.target().sourceRoot().resolve("pom.xml"));
        }

        @Override
        public StageResult execute(ValidationContext context) throws Exception {
            return runDependencyScan(context, DENYLIST_PATH);
        }
    }

    /**
     * Package-visible so {@code DependencyScanStageTest} can exercise the FAIL/PASS finding logic
     * against a synthetic denylist file without ever touching the real, production
     * contracts/dependency-vulnerability-denylist.v1.json (which stays the sole default source of
     * truth in production -- see {@link DependencyScanStage#execute}).
     */
    static StageResult runDependencyScan(ValidationContext context, Path denylistPath) throws Exception {
        Instant start = Instant.now();
        int before = context.findings().size();
        Path pomXmlPath = context.target().sourceRoot().resolve("pom.xml");
        VulnerabilityDenylistVerifier.VerificationResult verification =
                VulnerabilityDenylistVerifier.verify(pomXmlPath, denylistPath);
        for (VulnerabilityDenylistVerifier.DenylistMatch match : verification.matches()) {
            String evidenceDigest = Hashing.sha256(
                    match.coordinate() + "|" + match.version() + "|" + match.cve());
            String evidenceId = "EV-DEPENDENCY-" + evidenceDigest.substring(0, 16);
            context.addEvidence(new Evidence(evidenceId, "DEPENDENCY_DENYLIST_MATCH", "pom.xml",
                    evidenceDigest, Instant.now(),
                    Map.of("coordinate", match.coordinate(), "version", match.version(),
                            "cve", match.cve(), "severity", match.severity(), "reason", match.reason())));
            addFinding(context, "DEPENDENCY_SCAN", "DEPENDENCY_VULNERABILITY", severityOf(match.severity()),
                    "Denylisted vulnerable dependency: " + match.coordinate() + ":" + match.version(),
                    "Dependency " + match.coordinate() + ":" + match.version()
                            + " matches vulnerability denylist entry " + match.cve()
                            + " (" + match.reason() + ").",
                    "pom.xml", List.of(evidenceId));
        }
        int created = context.findings().size() - before;
        return result("DEPENDENCY_SCAN", created == 0 ? Decision.PASS : Decision.FAIL,
                start, context, before, Map.of("dependency_denylist_matches", verification.matches().size()));
    }

    private static Severity severityOf(String value) {
        try { return Severity.valueOf(value); }
        catch (IllegalArgumentException | NullPointerException ignored) { return Severity.HIGH; }
    }

    private static final class RuntimeFixtureStage implements ValidatorStage {
        @Override public String stageId() { return "FIXTURE_HARNESS_ORACLE"; }
        @Override public boolean supports(ValidationContext context) { return true; }

        @Override
        public StageResult execute(ValidationContext context) throws Exception {
            Instant start = Instant.now();
            int before = context.findings().size();
            FixtureHarness harness = new FixtureHarness("ONSURE_BUILTIN_HARNESS_V1");
            List<TargetAdapter.FixtureDefinition> fixtures = context.adapter().loadFixtures(context.target());
            long registered = context.attributes().get("registered_fixture_count") instanceof Number value
                    ? value.longValue() : -1;
            if (fixtures.isEmpty() || registered != fixtures.size()) {
                throw new IllegalStateException("RUNTIME_FIXTURE_REGISTRY_MISMATCH");
            }
            int failures = 0;
            int executedCommands = 0;
            int timeouts = 0;
            for (TargetAdapter.FixtureDefinition fixture : fixtures) {
                FixtureHarness.HarnessExecution execution = harness.execute(
                        fixture, context.target().sourceRoot());
                FixtureResult fixtureResult = execution.result();
                context.addFixtureResult(fixtureResult);
                if (execution.commandExecuted()) executedCommands++;
                if (execution.timedOut()) timeouts++;

                Map<String, Object> evidenceAttributes = Map.ofEntries(
                        Map.entry("expected", fixture.expected()),
                        Map.entry("observed", fixtureResult.observed()),
                        Map.entry("oracle", fixture.oracleId()),
                        Map.entry("harness", harness.harnessId()),
                        Map.entry("command_executed", execution.commandExecuted()),
                        Map.entry("command", execution.command()),
                        Map.entry("exit_code", execution.exitCode()),
                        Map.entry("timed_out", execution.timedOut()),
                        Map.entry("timeout_seconds", fixture.timeoutSeconds()),
                        Map.entry("environment_sha256",
                                FixtureEvidenceBinding.environmentDigest(fixture.environment())),
                        Map.entry("duration_ms", execution.durationMillis()),
                        Map.entry("output_sha256", execution.outputSha256()));
                String digest = FixtureEvidenceBinding.digest(fixture.fixtureId(), evidenceAttributes);
                String evidenceId = "EV-FIXTURE-" + digest.substring(0, 16);
                context.addEvidence(new Evidence(evidenceId, "FIXTURE_EXECUTION", fixture.fixtureId(), digest,
                        Instant.now(), evidenceAttributes));

                if (fixtureResult.decision() != Decision.PASS) {
                    failures++;
                    addFinding(context, stageId(), "RUNTIME_BEHAVIOR", Severity.HIGH,
                            "Fixture result differs from Oracle expectation",
                            "Fixture " + fixture.fixtureId() + " expected '" + fixture.expected()
                                    + "' but observed '" + fixtureResult.observed() + "'"
                                    + ", exit=" + execution.exitCode()
                                    + ", timeout=" + execution.timedOut() + ".",
                            "fixture:" + fixture.fixtureId(), List.of(evidenceId));
                }
            }
            context.putAttribute("executed_fixture_count", executedCommands);
            return result(stageId(), failures == 0 ? Decision.PASS : Decision.FAIL,
                    start, context, before, Map.of(
                            "fixtures", fixtures.size(),
                            "failures", failures,
                            "commands_executed", executedCommands,
                            "timeouts", timeouts));
        }
    }

    /**
     * Adds a container-based execution type to the generic Validator Engine: when a target's
     * source root has its own {@code Dockerfile}, this stage builds it and runs a bounded
     * smoke-test container, observing exit code and output -- the exact same PASS/FAIL-oracle
     * philosophy {@link RuntimeFixtureStage} already applies to fixture command execution. This
     * closes the "container validation" execution-type gap in FR-05-A (validation execution types
     * must include static analysis, unit/integration/e2e, API, CLI, AI-behavior, and container
     * validation).
     *
     * <p>Scope: this validates a target repository's OWN {@code Dockerfile} as one check among
     * many. It intentionally does NOT accept a container image as the top-level thing being
     * validated -- that is the separate, larger, still-undefined {@code CONTAINER_IMAGE}
     * supported_source_kind on {@link TargetAdapter} / {@code contracts/target-adapter.v1.json},
     * and is out of scope here.
     *
     * <p>A target's Dockerfile is arbitrary, untrusted build/run instructions and is treated
     * exactly as untrusted as {@link RuntimeFixtureStage} treats target fixtures: every docker
     * invocation goes through {@link BoundedProcessRunner} (wall-clock timeout, bounded output
     * capture, fully-controlled allowlisted environment) -- never a raw {@code ProcessBuilder} and
     * never shell string concatenation. The build uses a per-run-unique image tag (never {@code
     * latest}), the smoke-test container runs with {@code --network none} (matching the {@code
     * DENY_BY_DEFAULT} network-egress posture {@link ExecutionPlanService} already enforces
     * elsewhere) and {@code --rm}, and the built image is always removed afterward -- success or
     * failure -- via a {@code finally} block.
     */
    private static final class ContainerValidationStage implements ValidatorStage {
        private static final Duration BUILD_TIMEOUT = Duration.ofSeconds(120);
        private static final Duration RUN_TIMEOUT = Duration.ofSeconds(30);
        private static final Duration CLEANUP_TIMEOUT = Duration.ofSeconds(30);
        private static final String IMAGE_TAG_PREFIX = "onsure-container-validation:";

        @Override public String stageId() { return "CONTAINER_VALIDATION"; }

        @Override public boolean supports(ValidationContext context) {
            return Files.isRegularFile(context.target().sourceRoot().resolve("Dockerfile"));
        }

        @Override
        public StageResult execute(ValidationContext context) throws Exception {
            Instant start = Instant.now();
            int before = context.findings().size();
            Path sourceRoot = context.target().sourceRoot();
            Map<String, String> environment = dockerEnvironment();
            String imageTag = IMAGE_TAG_PREFIX + UUID.randomUUID().toString().replace("-", "");

            try {
                CommandOutcome build = runDocker(
                        List.of("docker", "build", "-t", imageTag, "."),
                        sourceRoot, BUILD_TIMEOUT, environment, "CONTAINER_VALIDATION_BUILD");
                String buildEvidenceId = recordEvidence(context, "CONTAINER_BUILD_RESULT", imageTag, build);
                if (!build.succeeded()) {
                    addFinding(context, stageId(), "CONTAINER_BUILD_OR_RUNTIME_FAILURE", Severity.HIGH,
                            "Container image failed to build",
                            "docker build of " + sourceRoot.resolve("Dockerfile") + " " + build.summary(),
                            "Dockerfile", List.of(buildEvidenceId));
                    return result(stageId(), Decision.FAIL, start, context, before, Map.of(
                            "build_succeeded", false, "run_executed", false));
                }

                CommandOutcome run = runDocker(
                        List.of("docker", "run", "--rm", "--network", "none", imageTag),
                        sourceRoot, RUN_TIMEOUT, environment, "CONTAINER_VALIDATION_RUN");
                String runEvidenceId = recordEvidence(context, "CONTAINER_RUN_RESULT", imageTag, run);
                if (!run.succeeded()) {
                    addFinding(context, stageId(), "CONTAINER_BUILD_OR_RUNTIME_FAILURE", Severity.HIGH,
                            "Container smoke-test run failed",
                            "docker run of " + imageTag + " " + run.summary(),
                            "Dockerfile", List.of(runEvidenceId));
                    return result(stageId(), Decision.FAIL, start, context, before, Map.of(
                            "build_succeeded", true, "run_executed", true, "exit_code", run.exitCode()));
                }

                return result(stageId(), Decision.PASS, start, context, before, Map.of(
                        "build_succeeded", true, "run_executed", true, "exit_code", run.exitCode()));
            } finally {
                cleanupImage(sourceRoot, imageTag, environment);
            }
        }

        /**
         * Only PATH is allowlisted: docker build/run against a locally-cached base image needs
         * nothing else (verified against this sandbox's real dockerd, which uses the default
         * rootful unix socket and needs no DOCKER_HOST/HOME/XDG_RUNTIME_DIR to be reachable).
         */
        private static Map<String, String> dockerEnvironment() {
            Map<String, String> environment = new LinkedHashMap<>();
            String path = System.getenv("PATH");
            if (path != null) environment.put("PATH", path);
            return environment;
        }

        private static CommandOutcome runDocker(List<String> command, Path directory, Duration timeout,
                Map<String, String> environment, String authority) {
            try {
                BoundedProcessRunner.Result processResult = BoundedProcessRunner.run(
                        command, directory, timeout, environment, authority);
                return new CommandOutcome(processResult.exitCode() == 0, false, processResult.exitCode(),
                        processResult.output(), processResult.outputTruncated());
            } catch (IllegalStateException timeoutOrDrainFailure) {
                return new CommandOutcome(false, true, -1,
                        String.valueOf(timeoutOrDrainFailure.getMessage()), false);
            } catch (Exception unexpected) {
                return new CommandOutcome(false, false, -1, String.valueOf(unexpected.getMessage()), false);
            }
        }

        /**
         * Best-effort image cleanup that always runs, whether the build/run steps succeeded,
         * failed, or threw. If the build never produced this tag (e.g. it failed before the final
         * layer was named), there is nothing to remove and docker reports a non-zero exit that is
         * intentionally swallowed here rather than masking the real stage result.
         */
        private static void cleanupImage(Path directory, String imageTag, Map<String, String> environment) {
            try {
                BoundedProcessRunner.run(List.of("docker", "image", "rm", "-f", imageTag),
                        directory, CLEANUP_TIMEOUT, environment, "CONTAINER_VALIDATION_CLEANUP");
            } catch (Exception ignored) {
                // Nothing to remove, or removal itself failed; the stage result already reflects
                // the real build/run outcome and must not be overridden by cleanup failure.
            }
        }

        private static String recordEvidence(ValidationContext context, String evidenceType, String imageTag,
                CommandOutcome outcome) {
            String outputDigest = Hashing.sha256(outcome.output() == null ? "" : outcome.output());
            String digest = Hashing.sha256(evidenceType + "|" + imageTag + "|" + outcome.exitCode()
                    + "|" + outputDigest);
            String evidenceId = "EV-CONTAINER-" + digest.substring(0, 16);
            context.addEvidence(new Evidence(evidenceId, evidenceType, imageTag, digest, Instant.now(),
                    Map.of(
                            "image_tag", imageTag,
                            "succeeded", outcome.succeeded(),
                            "timed_out", outcome.timedOut(),
                            "exit_code", outcome.exitCode(),
                            "output_sha256", outputDigest,
                            "output_truncated", outcome.outputTruncated())));
            return evidenceId;
        }

        private record CommandOutcome(
                boolean succeeded, boolean timedOut, int exitCode, String output, boolean outputTruncated) {
            String summary() {
                String excerpt = output == null ? "" : output.strip();
                if (excerpt.length() > 500) excerpt = excerpt.substring(0, 500) + "...(truncated)";
                String base = timedOut
                        ? "timed out"
                        : "exited with code " + exitCode + (outputTruncated ? " (docker output truncated)" : "");
                return excerpt.isEmpty() ? base : base + ": " + excerpt;
            }
        }
    }

    private static final class FailureAnalysisStage implements ValidatorStage {
        @Override public String stageId() { return "FAILURE_MODE_AND_RCA"; }
        @Override public boolean supports(ValidationContext context) { return true; }

        @Override
        public StageResult execute(ValidationContext context) {
            Instant start = Instant.now();
            for (Finding finding : context.findings()) {
                String modeCode = failureModeCode(finding.category());
                String modeId = "FM-" + finding.fingerprint().substring(0, 16);
                context.addFailureMode(new FailureMode(
                        modeId, modeCode, failureModeTitle(finding.category()),
                        finding.title(), impact(finding.severity()), List.of(finding.findingId())));
                String remediation = Optional.ofNullable(context.attributes().get("remediation:" + finding.category()))
                        .map(Object::toString)
                        .orElse(defaultRemediation(finding.category()));
                context.addRcaRecord(new RcaRecord(
                        "RCA-" + finding.fingerprint().substring(0, 16), finding.findingId(),
                        finding.description(), rootCause(finding.category()),
                        "Control was absent, incomplete, or not independently verified.", remediation,
                        "Re-run the focused fixture and the complete Validator Engine pipeline."));
            }
            return result(stageId(), Decision.PASS, start, context, context.findings().size(),
                    Map.of("failure_modes", context.failureModes().size(),
                            "rca_records", context.rcaRecords().size()));
        }
    }

    private static final class RegressionLockStage implements ValidatorStage {
        @Override public String stageId() { return "REGRESSION_LOCK"; }
        @Override public boolean supports(ValidationContext context) { return true; }

        @Override
        public StageResult execute(ValidationContext context) {
            Instant start = Instant.now();
            String sourceDigest = context.evidence().stream()
                    .filter(value -> "SOURCE_TREE".equals(value.evidenceType()))
                    .findFirst().orElseThrow().sha256();
            String findings = context.findings().stream()
                    .sorted(Comparator.comparing(Finding::fingerprint))
                    .map(value -> value.fingerprint() + ":" + value.status() + ":" + value.severity())
                    .reduce("", (a, b) -> a + "|" + b);
            String fixtures = context.fixtureResults().stream()
                    .sorted(Comparator.comparing(FixtureResult::fixtureId))
                    .map(value -> value.fixtureId() + ":" + value.harnessId() + ":" + value.oracleId()
                            + ":" + value.expected() + ":" + value.observed() + ":" + value.decision())
                    .reduce("", (a, b) -> a + "|" + b);
            String resultDigest = Hashing.sha256(findings + fixtures);
            String lockDigest = Hashing.sha256("ONSURE_REGRESSION_LOCK_V1|" + context.target().targetId()
                    + "|" + sourceDigest + "|" + resultDigest);
            context.regressionLock(new RegressionLock(
                    "ONSURE_REGRESSION_LOCK_V1", context.target().targetId(), context.job().jobId(),
                    sourceDigest, resultDigest, lockDigest, Instant.now()));
            return result(stageId(), Decision.PASS, start, context, context.findings().size(),
                    Map.of("lock_digest", lockDigest));
        }
    }

    private static StageResult result(String stageId, Decision decision, Instant start,
            ValidationContext context, int findingStart, Map<String, Object> metrics) {
        List<String> ids = context.findings().stream().skip(Math.max(0, findingStart))
                .map(Finding::findingId).toList();
        return new StageResult(stageId, decision, start, Instant.now(), ids, metrics);
    }

    private static void addFinding(ValidationContext context, String stageId, String category,
            Severity severity, String title, String description, String location, List<String> evidenceIds) {
        String fingerprint = Hashing.sha256(category + "|" + location + "|" + title);
        context.addFinding(new Finding(
                "F-" + fingerprint.substring(0, 16), fingerprint, category, severity, FindingStatus.OPEN,
                title, description, location, evidenceIds, stageId));
    }

    /**
     * Reuses {@link Hashing#sourceFiles} (git-tracked files, or the same generated/vendored
     * exclusions when git isn't available) instead of a raw filesystem walk, so pattern-matching
     * evidence is derived only from real source -- not build output, node_modules, or old
     * .onsure/ receipt directories that can contain many byte-identical vendored files and would
     * otherwise collide on the same (file content, rule token) evidence id.
     */
    private static List<Path> textFiles(Path root) throws Exception {
        List<Path> files = new ArrayList<>();
        for (Path path : Hashing.sourceFiles(root)) {
            if (!isTextExtension(path.getFileName().toString())) continue;
            try {
                if (Files.size(path) > 1_000_000) continue;
            } catch (Exception ignored) {
                continue;
            }
            files.add(path);
        }
        return files;
    }

    private static boolean isTextExtension(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".java") || lower.endsWith(".kt") || lower.endsWith(".py")
                || lower.endsWith(".js") || lower.endsWith(".ts") || lower.endsWith(".json")
                || lower.endsWith(".yaml") || lower.endsWith(".yml") || lower.endsWith(".md")
                || lower.endsWith(".txt") || lower.endsWith(".properties") || lower.endsWith(".xml")
                || lower.endsWith(".sh");
    }

    private static String failureModeCode(String category) {
        return switch (category) {
            case "SECRET_EXPOSURE" -> "FM-SECRET-EXPOSURE";
            case "COMMAND_EXECUTION", "DYNAMIC_EXECUTION" -> "FM-UNTRUSTED-EXECUTION";
            case "AI_TOOL_AUTHORIZATION" -> "FM-AI-TOOL-AUTHORIZATION";
            case "AI_SELF_APPROVAL" -> "FM-AI-SELF-APPROVAL";
            case "PROMPT_INJECTION" -> "FM-PROMPT-INJECTION";
            case "AI_DATA_EXFILTRATION" -> "FM-AI-DATA-EXFILTRATION";
            case "RUNTIME_BEHAVIOR" -> "FM-ORACLE-DIVERGENCE";
            case "DEPENDENCY_VULNERABILITY" -> "FM-DEPENDENCY-VULNERABILITY";
            case "CONTAINER_BUILD_OR_RUNTIME_FAILURE" -> "FM-CONTAINER-VALIDATION-FAILURE";
            default -> "FM-GENERIC-CORRECTNESS";
        };
    }

    private static String failureModeTitle(String category) {
        return "Failure mode for " + category;
    }

    private static String impact(Severity severity) {
        return switch (severity) {
            case CRITICAL -> "System compromise, unauthorized execution, or material data exposure.";
            case HIGH -> "Incorrect behavior or security control failure blocks release.";
            case MEDIUM -> "Material quality degradation requires remediation or approval.";
            case LOW, INFO -> "Limited impact; track and remediate in planned work.";
        };
    }

    private static String rootCause(String category) {
        return switch (category) {
            case "SECRET_EXPOSURE" -> "Secret lifecycle was implemented in application source instead of a secret boundary.";
            case "AI_TOOL_AUTHORIZATION" -> "Tool authorization did not enforce trust, scope, and permit checks.";
            case "AI_SELF_APPROVAL" -> "Producer and independent approval authorities were not separated.";
            case "PROMPT_INJECTION" -> "Untrusted instructions were not isolated from system policy and tool control.";
            case "AI_DATA_EXFILTRATION" -> "Context egress controls and data minimization were absent.";
            case "RUNTIME_BEHAVIOR" -> "Implemented runtime behavior diverged from the declared acceptance Oracle.";
            case "DEPENDENCY_VULNERABILITY" -> "A dependency with a known-vulnerable exact version was declared and not caught before validation.";
            case "CONTAINER_BUILD_OR_RUNTIME_FAILURE" -> "The target's container image did not build cleanly or its smoke-test run diverged from the expected zero-exit oracle.";
            default -> "Implementation and acceptance criteria were not fully bound by executable validation.";
        };
    }

    private static String defaultRemediation(String category) {
        return switch (category) {
            case "AI_TOOL_AUTHORIZATION" -> "Add allowlisted tool scopes, explicit permits, and independent audit.";
            case "AI_SELF_APPROVAL" -> "Separate producer, verifier, and approval authorities.";
            case "PROMPT_INJECTION" -> "Add instruction provenance, untrusted-content isolation, and tool-call policy gates.";
            case "AI_DATA_EXFILTRATION" -> "Apply context minimization, egress allowlists, redaction, and receipt binding.";
            case "RUNTIME_BEHAVIOR" -> "Correct the implementation and preserve the failing fixture as a regression test.";
            case "DEPENDENCY_VULNERABILITY" -> "Upgrade the dependency past the denylisted version, or remove it, then re-run the dependency scan.";
            case "CONTAINER_BUILD_OR_RUNTIME_FAILURE" -> "Fix the Dockerfile build or the container's runtime behavior so the image builds cleanly and the smoke-test container exits 0, then re-run the container validation stage.";
            default -> "Implement the missing control and add focused plus full regression tests.";
        };
    }
}
