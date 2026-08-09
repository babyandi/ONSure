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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Default generic stages used by the first commercial ONSURE engine slice. */
public final class BuiltInStages {
    private BuiltInStages() {}

    public static List<ValidatorStage> defaults() {
        return List.of(
                new TargetIntakeStage(),
                new SourceInventoryStage(),
                new StaticPatternStage(),
                new AiBehaviorStage(),
                new RuntimeFixtureStage(),
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
            default -> "Implement the missing control and add focused plus full regression tests.";
        };
    }
}
