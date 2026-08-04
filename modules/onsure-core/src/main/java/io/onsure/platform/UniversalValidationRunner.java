package io.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.onsure.platform.UniversalValidationProfile.Outcome;
import io.onsure.platform.UniversalValidationProfile.Phase;
import io.onsure.platform.UniversalValidationProfile.Profile;
import io.onsure.platform.UniversalValidationProfile.Step;
import io.onsure.platform.UniversalValidationProfile.StepKind;
import io.onsure.platform.UniversalValidationProfile.VerificationGroup;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

/** Runs one target-neutral profile and persists a non-final, four-phase receipt. */
public final class UniversalValidationRunner {
    private static final long MAX_OPENAPI_CONTRACT_BYTES = 16L * 1024 * 1024;
    private static final List<Path> TRUSTED_EXECUTABLE_DIRECTORIES = List.of(
            Path.of("/usr/local/sbin"), Path.of("/usr/local/bin"), Path.of("/usr/sbin"),
            Path.of("/usr/bin"), Path.of("/sbin"), Path.of("/bin"));
    private static final String TRUSTED_PROCESS_PATH = TRUSTED_EXECUTABLE_DIRECTORIES.stream()
            .map(Path::toString).collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator));
    public static final String CONTRACT = "ONSURE_UNIVERSAL_VALIDATION_RUN_V1";
    public static final String RECEIPT_FILE = "universal-validation-result.json";

    interface StepExecutor {
        StepExecution execute(Step step, Path snapshotRoot) throws Exception;

        default StepExecution probe(Path snapshotRoot) throws Exception {
            return new StepExecution(Outcome.PASS_NONFINAL, 0, "INJECTED_EXECUTOR", false,
                    "SANDBOX_PROBE_NOT_REQUIRED_FOR_INJECTED_EXECUTOR");
        }
    }

    public record StepExecution(
            Outcome outcome,
            int exitCode,
            String output,
            boolean outputTruncated,
            String reason) {
        public StepExecution {
            if (outcome == null) throw new IllegalArgumentException("STEP_OUTCOME_REQUIRED");
            output = output == null ? "" : output;
            reason = reason == null || reason.isBlank() ? "UNSPECIFIED" : reason;
        }
    }

    public record StepResult(
            String stepId,
            Phase phase,
            StepKind kind,
            boolean required,
            Outcome outcome,
            int exitCode,
            String outputSha256,
            String environmentSha256,
            String logFile,
            boolean outputTruncated,
            String reason,
            Instant startedAt,
            Instant completedAt) {}

    public record RunResult(
            String contract,
            String profileId,
            Path sourceRoot,
            Path snapshotRoot,
            String sourceDigest,
            String snapshotDigest,
            boolean sourceMutationDetected,
            List<UniversalValidationProfile.EnvironmentRequirement> environmentRequirements,
            Map<Phase, Outcome> phaseOutcomes,
            Map<VerificationGroup, Outcome> groupOutcomes,
            Outcome overallOutcome,
            List<StepResult> steps,
            Map<Phase, String> notRunReasons,
            Instant startedAt,
            Instant completedAt,
            boolean finalClaimAllowed,
            String assuranceClass,
            Path receiptFile,
            String receiptSha256) {}

    private final StepExecutor executor;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final ObjectMapper yamlMapper = new ObjectMapper(YAMLFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());

    public UniversalValidationRunner() {
        this(new SandboxedValidationStepExecutor());
    }

    UniversalValidationRunner(StepExecutor executor) {
        this.executor = java.util.Objects.requireNonNull(executor, "executor");
    }

    public RunResult run(Profile profile, Path runRoot) throws Exception {
        Instant started = Instant.now();
        Path root = requireRunRoot(profile.sourceRoot(), runRoot);
        Files.createDirectories(root);
        Path snapshotPath = root.resolve("execution-source");
        ValidationSourceSnapshot.Snapshot snapshot = ValidationSourceSnapshot.create(
                profile.sourceRoot(), snapshotPath);

        Map<String, Outcome> outcomes = new LinkedHashMap<>();
        List<StepResult> results = new ArrayList<>();
        boolean sourceMutation = false;
        String environmentDescription = environmentDescription();
        String environmentSha256 = Hashing.sha256(environmentDescription);
        Path logRoot = root.resolve("step-logs");
        Files.createDirectories(logRoot);
        for (Step step : profile.steps()) {
            List<String> unsatisfied = step.dependsOn().stream()
                    .filter(id -> outcomes.get(id) != Outcome.PASS_NONFINAL).toList();
            StepExecution execution;
            Instant stepStarted = Instant.now();
            if (!unsatisfied.isEmpty()) {
                execution = new StepExecution(Outcome.NOT_RUN, -1, "", false,
                        "DEPENDENCY_NOT_PASS:" + String.join(",", unsatisfied));
            } else if (!step.executable()) {
                execution = executeInternal(profile, step, snapshot.snapshotRoot(), results,
                        logRoot, environmentSha256);
            } else {
                execution = executor.execute(step, snapshot.snapshotRoot().resolve(step.workingDirectory()));
                execution = verifyExecutableEvidence(step, execution, snapshot.snapshotRoot());
            }
            Instant stepCompleted = Instant.now();
            Path logFile = logRoot.resolve(step.stepId() + ".log");
            atomicWrite(logFile, execution.output().getBytes(StandardCharsets.UTF_8));
            outcomes.put(step.stepId(), execution.outcome());
            results.add(new StepResult(step.stepId(), step.phase(), step.kind(), step.required(), execution.outcome(),
                    execution.exitCode(), Hashing.sha256(execution.output()), environmentSha256,
                    logFile.toString(), execution.outputTruncated(), execution.reason(),
                    stepStarted, stepCompleted));
            if (!ValidationSourceSnapshot.sourceUnchanged(snapshot)) {
                sourceMutation = true;
                outcomes.put("structure.inventory", Outcome.FAIL);
                break;
            }
        }

        StepExecution finalEvidence = validateEvidence(results, logRoot, environmentSha256);
        Path finalEvidenceLog = logRoot.resolve("evidence.finalize.log");
        atomicWrite(finalEvidenceLog, finalEvidence.output().getBytes(StandardCharsets.UTF_8));
        Map<Phase, Outcome> phaseOutcomes = new EnumMap<>(profile.phaseOutcomes(outcomes));
        if (sourceMutation) phaseOutcomes.put(Phase.STRUCTURE_STATIC, Outcome.FAIL);
        Map<VerificationGroup, Outcome> groupOutcomes = new EnumMap<>(profile.groupOutcomes(outcomes));
        if (sourceMutation) groupOutcomes.put(VerificationGroup.STRUCTURE, Outcome.FAIL);
        if (finalEvidence.outcome() == Outcome.FAIL) {
            groupOutcomes.put(VerificationGroup.EVIDENCE_DECISION, Outcome.FAIL);
        }
        Outcome overall = UniversalValidationProfile.aggregate(new ArrayList<>(groupOutcomes.values()));
        Instant completed = Instant.now();
        Path receipt = root.resolve(RECEIPT_FILE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contract", CONTRACT);
        body.put("profile_id", profile.profileId());
        body.put("source_root", profile.sourceRoot().toString());
        body.put("snapshot_root", snapshot.snapshotRoot().toString());
        body.put("source_digest", snapshot.sourceDigestBefore());
        body.put("snapshot_digest", snapshot.snapshotDigest());
        body.put("source_mutation_detected", sourceMutation);
        body.put("technologies", profile.technologies().stream().sorted().toList());
        body.put("environment_requirements", profile.environmentRequirements());
        Map<String, Object> environmentEvidence = new LinkedHashMap<>();
        environmentEvidence.put("description", environmentDescription);
        environmentEvidence.put("sha256", environmentSha256);
        body.put("environment_evidence", environmentEvidence);
        body.put("phase_outcomes", phaseOutcomes);
        body.put("verification_group_outcomes", groupOutcomes);
        body.put("overall_outcome", overall);
        body.put("steps", results);
        Map<String, Object> finalEvidenceIntegrity = new LinkedHashMap<>();
        finalEvidenceIntegrity.put("contract", "ONSURE_PASS_EVIDENCE_FINALIZATION_V1");
        finalEvidenceIntegrity.put("outcome", finalEvidence.outcome());
        finalEvidenceIntegrity.put("verified_pass_step_count", results.stream()
                .filter(result -> result.outcome() == Outcome.PASS_NONFINAL).count());
        finalEvidenceIntegrity.put("output_sha256", Hashing.sha256(finalEvidence.output()));
        finalEvidenceIntegrity.put("environment_sha256", environmentSha256);
        finalEvidenceIntegrity.put("log_file", finalEvidenceLog.toString());
        finalEvidenceIntegrity.put("reason", finalEvidence.reason());
        body.put("final_evidence_integrity", finalEvidenceIntegrity);
        body.put("not_run_reasons", profile.notRunReasons());
        body.put("started_at", started.toString());
        body.put("completed_at", completed.toString());
        body.put("assurance_class", "SELF_VALIDATION_NONFINAL");
        body.put("final_claim_allowed", false);
        atomicWrite(receipt, mapper.writeValueAsBytes(body));
        String receiptSha = Hashing.file(receipt);
        return new RunResult(CONTRACT, profile.profileId(), profile.sourceRoot(), snapshot.snapshotRoot(),
                snapshot.sourceDigestBefore(), snapshot.snapshotDigest(), sourceMutation,
                profile.environmentRequirements(),
                Map.copyOf(phaseOutcomes), Map.copyOf(groupOutcomes), overall, List.copyOf(results),
                profile.notRunReasons(), started, completed, false,
                "SELF_VALIDATION_NONFINAL", receipt, receiptSha);
    }

    private StepExecution executeInternal(Profile profile, Step step, Path snapshotRoot,
            List<StepResult> previousResults, Path logRoot, String environmentSha256) {
        try {
            return switch (step.kind()) {
                case ENVIRONMENT_PREFLIGHT -> validateEnvironment(profile, snapshotRoot);
                case INVENTORY -> validateStructureInventory(snapshotRoot);
                case VALIDATOR_META_CHECK -> validateProfile(profile);
                case API_CONTRACT -> validateOpenApi(snapshotRoot, step);
                case DATABASE_MIGRATION -> validateMigrations(snapshotRoot);
                case EVIDENCE_VERIFICATION -> validateEvidence(
                        previousResults, logRoot, environmentSha256);
                case NEGATIVE_TEST -> new StepExecution(Outcome.NOT_RUN, -1, "", false,
                        "NEGATIVE_AND_FAILURE_PATH_PACK_NOT_INSTALLED");
                case RETRY_TEST -> new StepExecution(Outcome.NOT_RUN, -1, "", false,
                        "RETRY_PATH_PACK_NOT_INSTALLED");
                case BLOCKING_TEST -> new StepExecution(Outcome.NOT_RUN, -1, "", false,
                        "BLOCKING_PATH_PACK_NOT_INSTALLED");
                case E2E_REQUEST_FLOW -> missingPack("E2E_REQUEST_FLOW_PACK_NOT_INSTALLED");
                case E2E_RENDER_OR_PRODUCE -> missingPack("E2E_RENDER_OR_PRODUCE_PACK_NOT_INSTALLED");
                case E2E_ARTIFACT_READBACK -> missingPack("E2E_ARTIFACT_READBACK_PACK_NOT_INSTALLED");
                case E2E_TESTER_CHECK -> missingPack("E2E_TESTER_CHECK_PACK_NOT_INSTALLED");
                case E2E_AUDIT_CHECK -> missingPack("E2E_AUDIT_CHECK_PACK_NOT_INSTALLED");
                case E2E_EXPOSURE_DECISION -> missingPack("E2E_EXPOSURE_DECISION_PACK_NOT_INSTALLED");
                case WORKFLOW_LINEAGE -> missingPack("WORKFLOW_LINEAGE_PACK_NOT_INSTALLED");
                case INTERRUPTION_TEST -> missingPack("INTERRUPTION_TEST_PACK_NOT_INSTALLED");
                case RESUME_TEST -> missingPack("RESUME_TEST_PACK_NOT_INSTALLED");
                case ROLLBACK_TEST -> missingPack("ROLLBACK_TEST_PACK_NOT_INSTALLED");
                case RERUN_TEST -> missingPack("RERUN_TEST_PACK_NOT_INSTALLED");
                default -> new StepExecution(Outcome.NOT_RUN, -1, "", false,
                        "INTERNAL_VALIDATOR_NOT_IMPLEMENTED:" + step.kind());
            };
        } catch (Exception error) {
            return new StepExecution(Outcome.FAIL, -1, "", false,
                    "INTERNAL_VALIDATION_ERROR:" + error.getClass().getSimpleName());
        }
    }

    private static StepExecution missingPack(String reason) {
        return new StepExecution(Outcome.NOT_RUN, -1, "", false, reason);
    }

    static StepExecution verifyExecutableEvidence(
            Step step, StepExecution execution, Path snapshotRoot) {
        if (execution.outcome() != Outcome.PASS_NONFINAL || step.kind() != StepKind.WORKFLOW_LINEAGE) {
            return execution;
        }
        StepExecution verified = new WorkflowLineageReceiptVerifier().verify(snapshotRoot);
        String combined = execution.output() + "\n--- ONSURE WORKFLOW LINEAGE READ-BACK ---\n"
                + verified.output();
        if (verified.outcome() != Outcome.PASS_NONFINAL) {
            return new StepExecution(verified.outcome(), verified.exitCode(), combined,
                    execution.outputTruncated() || verified.outputTruncated(), verified.reason());
        }
        return new StepExecution(Outcome.PASS_NONFINAL, execution.exitCode(), combined,
                execution.outputTruncated(), verified.reason());
    }

    private StepExecution validateEnvironment(Profile profile, Path snapshotRoot) throws Exception {
        List<String> requiredExecutables = profile.steps().stream().filter(Step::executable)
                .map(step -> Path.of(step.command().get(0)).getFileName().toString()).distinct().sorted().toList();
        List<String> missing = new ArrayList<>();
        requiredExecutables.stream().filter(executable -> executablePath(executable) == null)
                .map(executable -> "command:" + executable).forEach(missing::add);
        List<String> optionalMissing = new ArrayList<>();
        for (var requirement : profile.environmentRequirements()) {
            boolean present = requirementPresent(requirement, snapshotRoot);
            if (!present) {
                (requirement.required() ? missing : optionalMissing).add(requirement.requirementId());
            }
        }
        String report = "os=" + System.getProperty("os.name", "unknown") + "\njava="
                + System.getProperty("java.version", "unknown") + "\nrequired_executables=" + requiredExecutables
                + "\ndeclared_requirements=" + profile.environmentRequirements().stream()
                        .map(value -> value.requirementId() + ":" + value.kind()).toList()
                + "\nmissing_required=" + missing + "\nmissing_optional=" + optionalMissing;
        if (!missing.isEmpty()) {
            return new StepExecution(Outcome.BLOCKED, -1, report, false,
                    "REQUIRED_ENVIRONMENT_MISSING:" + String.join(",", missing));
        }
        if (requiredExecutables.isEmpty()) {
            return new StepExecution(Outcome.PASS_NONFINAL, 0, report, false,
                    "INTERNAL_VALIDATORS_REQUIRE_NO_PROCESS_SANDBOX");
        }
        StepExecution probe = executor.probe(snapshotRoot);
        if (probe.outcome() != Outcome.PASS_NONFINAL) {
            return new StepExecution(probe.outcome(), probe.exitCode(), report + "\n" + probe.output(),
                    probe.outputTruncated(), probe.reason());
        }
        return new StepExecution(Outcome.PASS_NONFINAL, 0, report + "\n" + probe.output(), false,
                "REQUIRED_EXECUTABLES_AND_SANDBOX_AVAILABLE");
    }

    private static boolean requirementPresent(
            UniversalValidationProfile.EnvironmentRequirement requirement, Path snapshotRoot) {
        return switch (requirement.kind()) {
            case EXECUTABLE -> executablePath(requirement.value()) != null;
            case SOURCE_FILE -> safeSourcePath(snapshotRoot, requirement.value(), false, false);
            case SOURCE_DIRECTORY -> safeSourcePath(snapshotRoot, requirement.value(), true, false);
            case EXECUTABLE_SOURCE_FILE -> safeSourcePath(snapshotRoot, requirement.value(), false, true);
            case FONT_FAMILY -> fontAvailable(requirement.value());
        };
    }

    private static boolean safeSourcePath(Path root, String relative, boolean directory, boolean executable) {
        Path value = root.resolve(relative).normalize();
        if (!value.startsWith(root) || Files.isSymbolicLink(value)) return false;
        boolean expectedType = directory
                ? Files.isDirectory(value, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                : Files.isRegularFile(value, java.nio.file.LinkOption.NOFOLLOW_LINKS);
        return expectedType && (!executable || Files.isExecutable(value));
    }

    private static boolean fontAvailable(String family) {
        Path fcMatch = executablePath("fc-match");
        if (fcMatch == null) return false;
        try {
            BoundedProcessRunner.Result result = BoundedProcessRunner.run(
                    List.of(fcMatch.toString(), "--format=%{family}", "--", family),
                    Path.of(System.getProperty("java.io.tmpdir", "/tmp")), java.time.Duration.ofSeconds(5),
                    65_536, Map.of("PATH", TRUSTED_PROCESS_PATH),
                    "UNIVERSAL_VALIDATION_FONT_PREFLIGHT");
            return result.exitCode() == 0 && result.output().toLowerCase(java.util.Locale.ROOT)
                    .contains(family.toLowerCase(java.util.Locale.ROOT));
        } catch (Exception ignored) {
            return false;
        }
    }

    private StepExecution validateProfile(Profile profile) {
        boolean hasEnvironment = profile.steps().stream()
                .anyMatch(step -> step.kind() == StepKind.ENVIRONMENT_PREFLIGHT);
        boolean hasInventory = profile.steps().stream().anyMatch(step -> step.kind() == StepKind.INVENTORY);
        boolean hasEvidence = profile.steps().stream()
                .anyMatch(step -> step.kind() == StepKind.EVIDENCE_VERIFICATION);
        Set<VerificationGroup> groups = profile.steps().stream()
                .map(step -> step.kind().group()).collect(java.util.stream.Collectors.toSet());
        Set<StepKind> kinds = profile.steps().stream()
                .map(Step::kind).collect(java.util.stream.Collectors.toSet());
        List<StepKind> requiredFunctionalKinds = List.of(
                StepKind.NEGATIVE_TEST, StepKind.RETRY_TEST, StepKind.BLOCKING_TEST);
        List<StepKind> requiredE2eKinds = List.of(
                StepKind.E2E_REQUEST_FLOW, StepKind.E2E_RENDER_OR_PRODUCE,
                StepKind.E2E_ARTIFACT_READBACK, StepKind.E2E_TESTER_CHECK,
                StepKind.E2E_AUDIT_CHECK, StepKind.E2E_EXPOSURE_DECISION,
                StepKind.WORKFLOW_LINEAGE);
        List<StepKind> requiredRecoveryKinds = List.of(
                StepKind.INTERRUPTION_TEST, StepKind.RESUME_TEST,
                StepKind.ROLLBACK_TEST, StepKind.RERUN_TEST);
        Set<VerificationGroup> missingGroups = new LinkedHashSet<>(List.of(VerificationGroup.values()));
        missingGroups.removeAll(groups);
        Set<StepKind> missingFunctionalKinds = new LinkedHashSet<>(requiredFunctionalKinds);
        missingFunctionalKinds.removeAll(kinds);
        Set<StepKind> missingE2eKinds = new LinkedHashSet<>(requiredE2eKinds);
        missingE2eKinds.removeAll(kinds);
        Set<StepKind> missingRecoveryKinds = new LinkedHashSet<>(requiredRecoveryKinds);
        missingRecoveryKinds.removeAll(kinds);
        List<String> prohibited = profile.steps().stream().filter(Step::executable)
                .filter(step -> step.command().contains("-c") || step.command().contains("--command"))
                .map(Step::stepId).toList();
        List<String> unsupported = profile.steps().stream().filter(Step::executable)
                .filter(step -> !SandboxedValidationStepExecutor.supportsCommand(step))
                .map(Step::stepId).toList();
        boolean valid = hasEnvironment && hasInventory && hasEvidence
                && missingGroups.isEmpty() && missingFunctionalKinds.isEmpty()
                && missingE2eKinds.isEmpty() && missingRecoveryKinds.isEmpty()
                && prohibited.isEmpty() && unsupported.isEmpty();
        String report = "environment=" + hasEnvironment + "\ninventory=" + hasInventory
                + "\nevidence=" + hasEvidence + "\nmissing_groups=" + missingGroups
                + "\nmissing_functional_kinds=" + missingFunctionalKinds
                + "\nmissing_e2e_kinds=" + missingE2eKinds
                + "\nmissing_recovery_kinds=" + missingRecoveryKinds
                + "\nprohibited_inline_shell=" + prohibited + "\nunsupported_commands=" + unsupported;
        return new StepExecution(valid ? Outcome.PASS_NONFINAL : Outcome.FAIL, valid ? 0 : 1,
                report, false, valid ? "VALIDATOR_PROFILE_COVERAGE_VALID" : "VALIDATOR_PROFILE_COVERAGE_INVALID");
    }

    private StepExecution validateStructureInventory(Path snapshotRoot) throws Exception {
        Map<String, List<String>> signals = new LinkedHashMap<>();
        signals.put("stages", new ArrayList<>());
        signals.put("flags", new ArrayList<>());
        signals.put("gates", new ArrayList<>());
        signals.put("roles", new ArrayList<>());
        signals.put("registries", new ArrayList<>());
        signals.put("traceability", new ArrayList<>());
        Map<String, List<String>> needles = Map.of(
                "stages", List.of("stage", "workflow", "pipeline"),
                "flags", List.of("flag", "feature-toggle", "feature_toggle"),
                "gates", List.of("gate", "policy", "approval"),
                "roles", List.of("role", "rbac", "permission"),
                "registries", List.of("registry", "catalog"),
                "traceability", List.of("trace", "receipt", "evidence", "audit", "manifest"));
        int fileCount = 0;
        try (var paths = Files.walk(snapshotRoot)) {
            for (Path path : paths.filter(value -> Files.isRegularFile(
                    value, java.nio.file.LinkOption.NOFOLLOW_LINKS)).sorted().toList()) {
                fileCount++;
                String relative = snapshotRoot.relativize(path).toString().replace('\\', '/');
                String searchable = relative.toLowerCase(java.util.Locale.ROOT);
                for (var category : needles.entrySet()) {
                    if (category.getValue().stream().anyMatch(searchable::contains)
                            && signals.get(category.getKey()).size() < 100) {
                        signals.get(category.getKey()).add(relative);
                    }
                }
            }
        }
        List<String> absent = signals.entrySet().stream().filter(entry -> entry.getValue().isEmpty())
                .map(Map.Entry::getKey).toList();
        String report = "file_count=" + fileCount + "\nstructure_signals=" + signals
                + "\nabsent_optional_signal_categories=" + absent;
        return new StepExecution(Outcome.PASS_NONFINAL, 0, report, false,
                "SOURCE_SNAPSHOT_AND_STRUCTURE_SIGNALS_INVENTORIED");
    }

    static StepExecution validateEvidence(
            List<StepResult> results, Path logRoot, String expectedEnvironmentSha256) {
        Path trustedLogRoot = logRoot.toAbsolutePath().normalize();
        List<String> invalid = new ArrayList<>();
        for (StepResult result : results) {
            if (result.outcome() != Outcome.PASS_NONFINAL) continue;
            String invalidReason = invalidPassEvidence(
                    result, trustedLogRoot, expectedEnvironmentSha256);
            if (invalidReason != null) invalid.add(result.stepId() + ":" + invalidReason);
        }
        String report = "prior_step_count=" + results.size() + "\ninvalid_pass_evidence=" + invalid;
        return new StepExecution(invalid.isEmpty() ? Outcome.PASS_NONFINAL : Outcome.FAIL,
                invalid.isEmpty() ? 0 : 1, report, false,
                invalid.isEmpty() ? "PASS_EVIDENCE_INTEGRITY_VERIFIED" : "PASS_EVIDENCE_INTEGRITY_INVALID");
    }

    private static String invalidPassEvidence(
            StepResult result, Path logRoot, String expectedEnvironmentSha256) {
        try {
            if (result.outputSha256() == null || !result.outputSha256().matches("[0-9a-f]{64}")) {
                return "OUTPUT_SHA256_INVALID";
            }
            if (result.environmentSha256() == null
                    || !result.environmentSha256().equals(expectedEnvironmentSha256)) {
                return "ENVIRONMENT_SHA256_MISMATCH";
            }
            if (result.logFile() == null || result.logFile().isBlank()) return "LOG_FILE_MISSING";
            Path log = Path.of(result.logFile()).toAbsolutePath().normalize();
            if (!log.startsWith(logRoot) || Files.isSymbolicLink(log)
                    || !Files.isRegularFile(log, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                return "LOG_FILE_UNTRUSTED_OR_MISSING";
            }
            if (!Hashing.file(log).equals(result.outputSha256())) return "LOG_SHA256_MISMATCH";
            if (result.reason() == null || result.reason().isBlank()
                    || result.startedAt() == null || result.completedAt() == null
                    || result.completedAt().isBefore(result.startedAt())) {
                return "RECEIPT_METADATA_INVALID";
            }
            return null;
        } catch (Exception error) {
            return "EVIDENCE_READ_ERROR:" + error.getClass().getSimpleName();
        }
    }

    private static String environmentDescription() {
        return String.join("\n", System.getProperty("os.name", "unknown"),
                System.getProperty("os.version", "unknown"), System.getProperty("os.arch", "unknown"),
                System.getProperty("java.vendor", "unknown"), System.getProperty("java.version", "unknown"));
    }

    private static Path executablePath(String executable) {
        if (executable == null || executable.isBlank() || !Path.of(executable).getFileName().toString().equals(executable)) {
            return null;
        }
        for (Path directory : TRUSTED_EXECUTABLE_DIRECTORIES) {
            Path candidate = directory.resolve(executable).normalize();
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                try { return candidate.toRealPath(); }
                catch (Exception ignored) { /* continue searching */ }
            }
        }
        return null;
    }

    private StepExecution validateOpenApi(Path root, Step step) throws Exception {
        List<Path> contracts = StandardValidationPackSupport.findOpenApiContracts(root);
        int index = openApiContractIndex(step.stepId());
        if (index < 0 || index >= contracts.size()) {
            return new StepExecution(Outcome.FAIL, 1, "step=" + step.stepId(), false,
                    "OPENAPI_PROFILE_CONTRACT_INDEX_INVALID");
        }
        Path contract = contracts.get(index);
        if (Files.size(contract) > MAX_OPENAPI_CONTRACT_BYTES) {
            return new StepExecution(Outcome.FAIL, 1, "contract=" + contract.getFileName(), false,
                    "OPENAPI_CONTRACT_TOO_LARGE");
        }
        JsonNode node = contract.getFileName().toString().endsWith(".json")
                ? mapper.readTree(contract.toFile()) : yamlMapper.readTree(contract.toFile());
        List<String> errors = new ArrayList<>();
        List<String> limitations = new ArrayList<>();
        String version = node.path("openapi").asText();
        if (!version.matches("3\\.(?:0|1)\\.[0-9]+")) errors.add("OPENAPI_VERSION_UNSUPPORTED_OR_MISSING");
        if (!node.path("info").isObject()
                || node.path("info").path("title").asText().isBlank()
                || node.path("info").path("version").asText().isBlank()) {
            errors.add("OPENAPI_INFO_REQUIRED_FIELDS_MISSING");
        }
        JsonNode paths = node.path("paths");
        if (!paths.isObject() || paths.isEmpty()) errors.add("OPENAPI_PATHS_EMPTY_OR_INVALID");
        Set<String> operationIds = new LinkedHashSet<>();
        int[] operationCount = {0};
        if (paths.isObject()) paths.fields().forEachRemaining(path -> {
            if (!path.getKey().startsWith("/")) errors.add("OPENAPI_PATH_INVALID:" + path.getKey());
            if (!path.getValue().isObject()) {
                errors.add("OPENAPI_PATH_ITEM_INVALID:" + path.getKey());
                return;
            }
            path.getValue().fields().forEachRemaining(operation -> {
                if (!Set.of("get", "put", "post", "delete", "patch", "options", "head", "trace")
                        .contains(operation.getKey())) return;
                operationCount[0]++;
                JsonNode value = operation.getValue();
                String operationId = value.path("operationId").asText();
                if (operationId.isBlank()) {
                    errors.add("OPENAPI_OPERATION_ID_MISSING:" + operation.getKey() + ":" + path.getKey());
                } else if (!operationIds.add(operationId)) {
                    errors.add("OPENAPI_OPERATION_ID_DUPLICATED:" + operationId);
                }
                if (!value.path("responses").isObject() || value.path("responses").isEmpty()) {
                    errors.add("OPENAPI_RESPONSES_MISSING:" + operation.getKey() + ":" + path.getKey());
                }
            });
        });
        inspectReferences(node, node, errors, limitations);
        String report = "contract=" + root.relativize(contract).toString().replace('\\', '/')
                + "\nopenapi=" + version + "\npath_count=" + paths.size()
                + "\noperation_count=" + operationCount[0] + "\nerrors=" + errors
                + "\nlimitations=" + limitations;
        if (!errors.isEmpty()) return new StepExecution(Outcome.FAIL, 1, report, false, "OPENAPI_CONTRACT_INVALID");
        if (!limitations.isEmpty()) return new StepExecution(
                Outcome.INCONCLUSIVE, 0, report, false, "OPENAPI_EXTERNAL_REFERENCES_NOT_RESOLVED");
        return new StepExecution(Outcome.PASS_NONFINAL, 0, report, false, "OPENAPI_CONTRACT_VALID");
    }

    private static int openApiContractIndex(String stepId) {
        if ("openapi.contract".equals(stepId)) return 0;
        if (stepId != null && stepId.startsWith("openapi.contract-")) {
            try { return Integer.parseInt(stepId.substring("openapi.contract-".length())) - 1; }
            catch (NumberFormatException ignored) { return -1; }
        }
        return -1;
    }

    private static void inspectReferences(JsonNode root, JsonNode current,
            List<String> errors, List<String> limitations) {
        if (current.isObject()) {
            JsonNode reference = current.get("$ref");
            if (reference != null && reference.isTextual()) {
                String value = reference.asText();
                if (value.startsWith("#/")) {
                    if (root.at(value.substring(1)).isMissingNode()) errors.add("OPENAPI_LOCAL_REF_MISSING:" + value);
                } else {
                    limitations.add("OPENAPI_EXTERNAL_REF_NOT_RESOLVED:" + value);
                }
            }
            current.elements().forEachRemaining(child -> inspectReferences(root, child, errors, limitations));
        } else if (current.isArray()) {
            current.elements().forEachRemaining(child -> inspectReferences(root, child, errors, limitations));
        }
    }

    private StepExecution validateMigrations(Path root) throws Exception {
        Path directory = StandardValidationPackSupport.findMigrationDirectory(root);
        if (directory == null) return new StepExecution(Outcome.NOT_RUN, -1, "", false, "MIGRATION_ROOT_NOT_FOUND");
        List<Path> scripts;
        try (var stream = Files.walk(directory)) {
            scripts = stream.filter(path -> Files.isRegularFile(path) && !Files.isSymbolicLink(path))
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted(Comparator.comparing(Path::toString)).toList();
        }
        if (scripts.isEmpty()) return new StepExecution(Outcome.FAIL, 1, "", false, "MIGRATION_SQL_NOT_FOUND");
        String inventory = scripts.stream().map(path -> root.relativize(path).toString().replace('\\', '/'))
                .reduce("", (left, right) -> left + right + "\n");
        return new StepExecution(Outcome.PASS_NONFINAL, 0, inventory, false, "MIGRATION_STATIC_INVENTORY_VALID");
    }

    private static void atomicWrite(Path target, byte[] bytes) throws Exception {
        Path temporary = Files.createTempFile(target.getParent(), ".universal-validation-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Path requireRunRoot(Path sourceRoot, Path runRoot) throws Exception {
        if (runRoot == null) throw new IllegalArgumentException("VALIDATION_RUN_ROOT_REQUIRED");
        Path source = sourceRoot.toAbsolutePath().normalize();
        Path root = runRoot.toAbsolutePath().normalize();
        if (root.equals(root.getRoot()) || root.startsWith(source) || source.startsWith(root)) {
            throw new IllegalArgumentException("VALIDATION_RUN_ROOT_OVERLAPS_SOURCE");
        }
        for (Path current = root; current != null; current = current.getParent()) {
            if (Files.exists(current, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    && Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException("VALIDATION_RUN_ROOT_SYMLINK_FORBIDDEN");
            }
        }
        if (Files.exists(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("VALIDATION_RUN_ROOT_INVALID");
            }
            try (var entries = Files.list(root)) {
                if (entries.findAny().isPresent()) {
                    throw new IllegalArgumentException("VALIDATION_RUN_ROOT_NOT_EMPTY");
                }
            }
        }
        return root;
    }
}
