package io.onsure.platform;

import io.onsure.assurance.Decision;
import io.onsure.platform.RemediationPlan.ChangeClass;
import io.onsure.platform.ValidationModel.Finding;
import io.onsure.platform.ValidationModel.FindingStatus;
import io.onsure.platform.ValidationModel.JobStatus;
import io.onsure.platform.ValidationModel.Severity;
import io.onsure.platform.ValidationModel.StageResult;
import io.onsure.platform.ValidationModel.ValidationJob;
import io.onsure.platform.ValidationModel.ValidationReport;
import io.onsure.platform.ValidationModel.ValidationTarget;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Generic commercial Validator Engine: target -> findings/RCA -> lock -> report. */
public final class ValidationEngine {
    public static final String REPORT_CONTRACT = "ONSURE_VALIDATION_REPORT_V1";

    public record RunResult(Path runRoot, ValidationReport report) {}

    private final TargetAdapterRegistry adapterRegistry;
    private final List<ValidatorStage> stages;
    private final FileValidationStore store;

    public ValidationEngine(List<TargetAdapter> adapters, List<ValidatorStage> stages, FileValidationStore store) {
        this.adapterRegistry = new TargetAdapterRegistry(adapters);
        this.stages = List.copyOf(stages);
        this.store = Objects.requireNonNull(store, "store");
        if (this.stages.isEmpty()) throw new IllegalArgumentException("at least one stage is required");
    }

    /** Standalone default: Generic adapter only. Target-specific adapters must be explicit. */
    public static ValidationEngine defaultEngine(Path storeRoot) {
        return withOptionalAdapters(storeRoot, List.of());
    }

    /** Explicit optional profile used by the ORUDA adapter fixture suite. */
    public static ValidationEngine withOrudaAdapter(Path storeRoot) {
        return withOptionalAdapters(storeRoot, List.of(new OrudaTargetAdapter()));
    }

    /** Creates a standalone core engine with explicitly selected optional target adapters. */
    public static ValidationEngine withOptionalAdapters(
            Path storeRoot, List<TargetAdapter> optionalAdapters) {
        List<TargetAdapter> adapters = new ArrayList<>();
        adapters.add(new GenericManifestTargetAdapter());
        if (optionalAdapters != null) adapters.addAll(optionalAdapters);
        return new ValidationEngine(adapters, defaultStages(), new FileValidationStore(storeRoot));
    }

    private static List<ValidatorStage> defaultStages() {
        List<ValidatorStage> values = new ArrayList<>(BuiltInStages.defaults());
        int runtimeFixtureIndex = indexOf(values, "FIXTURE_HARNESS_ORACLE");
        values.add(runtimeFixtureIndex, new FixtureRegistryStage());
        int regressionLockIndex = indexOf(values, "REGRESSION_LOCK");
        values.add(regressionLockIndex, new RemediationPlanningStage());
        int afterRegressionLock = indexOf(values, "REGRESSION_LOCK") + 1;
        values.add(afterRegressionLock, new IndependentProductVerifierStage());
        values.add(afterRegressionLock + 1, new IndependentProductAuditStage());
        return List.copyOf(values);
    }

    private static int indexOf(List<ValidatorStage> values, String stageId) {
        for (int i = 0; i < values.size(); i++) {
            if (stageId.equals(values.get(i).stageId())) return i;
        }
        throw new IllegalStateException("missing built-in stage: " + stageId);
    }

    public RunResult run(ValidationTarget target) throws Exception {
        TargetAdapter adapter = adapterRegistry.require(target);
        Instant created = Instant.now();
        String jobId = createJobId(target.targetId(), created);
        Path runRoot = store.createRunRoot(target.targetId(), jobId);
        ValidationJob running = new ValidationJob(
                jobId, target.targetId(), JobStatus.RUNNING, created, created, null, null);
        ValidationContext context = new ValidationContext(target, running, adapter, runRoot);
        context.putAttribute("registered_adapter_ids", adapterRegistry.adapterIds());

        Exception executionFailure = null;
        for (ValidatorStage stage : stages) {
            if (!stage.supports(context)) continue;
            try {
                StageResult result = stage.execute(context);
                context.addStageResult(result);
            } catch (Exception e) {
                executionFailure = e;
                Instant now = Instant.now();
                context.addStageResult(new StageResult(
                        stage.stageId(), Decision.FAIL, now, now, List.of(),
                        Map.of("exception", e.getClass().getName(), "message", safeMessage(e))));
                context.putAttribute("execution_failure_stage", stage.stageId());
                context.putAttribute("execution_failure", safeMessage(e));
                break;
            }
        }

        Instant completed = Instant.now();
        JobStatus status = executionFailure == null ? JobStatus.COMPLETED : JobStatus.FAILED;
        context.job(new ValidationJob(
                jobId, target.targetId(), status, created, created, completed, null));
        Decision decision = finalDecision(context, executionFailure);
        ValidationReport report = createReport(context, decision, completed);
        store.persist(context, report);

        if (executionFailure != null) {
            throw new ValidationExecutionException(
                    "validation failed at " + context.attributes().get("execution_failure_stage"),
                    executionFailure, runRoot, report);
        }
        return new RunResult(runRoot, report);
    }

    private static ValidationReport createReport(ValidationContext context, Decision decision, Instant generatedAt) {
        ValidationCompletionGate.Evaluation completion = ValidationCompletionGate.evaluate(context);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("open_critical", count(context.findings(), Severity.CRITICAL));
        summary.put("open_high", count(context.findings(), Severity.HIGH));
        summary.put("open_medium", count(context.findings(), Severity.MEDIUM));
        summary.put("finding_count", context.findings().size());
        summary.put("failure_mode_count", context.failureModes().size());
        summary.put("rca_count", context.rcaRecords().size());
        summary.put("remediation_plan_count", context.remediationPlans().size());
        summary.put("approval_required_count", context.remediationPlans().stream()
                .filter(value -> value.changeClass() == ChangeClass.APPROVAL_REQUIRED).count());
        summary.put("remediation_plans", context.remediationPlans());
        summary.put("fixture_count", context.fixtureResults().size());
        summary.put("fixture_failures", context.fixtureResults().stream()
                .filter(value -> value.decision() != Decision.PASS).count());
        summary.put("source_tree_sha256", context.attributes().getOrDefault("source_tree_sha256", "NOT_AVAILABLE"));
        summary.put("adapter_id", context.adapter().adapterId());
        summary.put("registered_adapter_ids", context.attributes().get("registered_adapter_ids"));
        summary.put("internal_verifier", stageDecision(context, "INTERNAL_PRODUCT_VERIFIER"));
        summary.put("internal_audit", stageDecision(context, "INTERNAL_PRODUCT_AUDIT"));
        summary.put("independent_verifier", "NOT_RUN");
        summary.put("independent_audit", "NOT_RUN");
        summary.put("assurance_class", "SELF_VALIDATION_NONFINAL");
        summary.put("completion_gate_contract", ValidationCompletionGate.CONTRACT);
        summary.put("completion_gate_eligible", completion.eligible());
        summary.put("completion_gate_reasons", completion.reasons());
        return new ValidationReport(
                REPORT_CONTRACT,
                "REPORT-" + context.job().jobId(),
                context.job().jobId(),
                context.target(),
                decision,
                generatedAt,
                context.stageResults(),
                context.findings(),
                context.failureModes(),
                context.rcaRecords(),
                context.fixtureResults(),
                context.regressionLock(),
                summary);
    }

    private static String stageDecision(ValidationContext context, String stageId) {
        return context.stageResults().stream()
                .filter(value -> stageId.equals(value.stageId()))
                .map(value -> value.decision().name())
                .findFirst().orElse("NOT_RUN");
    }

    private static Decision finalDecision(ValidationContext context, Exception executionFailure) {
        if (executionFailure != null) return Decision.FAIL;
        if (!ValidationCompletionGate.evaluate(context).eligible()) return Decision.FAIL;
        if (context.stageResults().stream().anyMatch(value -> value.decision() == Decision.FAIL)) {
            return Decision.FAIL;
        }
        if (context.stageResults().stream().anyMatch(value -> value.decision() != Decision.PASS)) {
            return Decision.HOLD;
        }
        boolean blocking = context.findings().stream()
                .filter(value -> value.status() == FindingStatus.OPEN)
                .anyMatch(value -> value.severity() == Severity.CRITICAL || value.severity() == Severity.HIGH);
        if (blocking) return Decision.FAIL;
        boolean nonBlocking = context.findings().stream()
                .anyMatch(value -> value.status() == FindingStatus.OPEN);
        return nonBlocking ? Decision.HOLD : Decision.PASS;
    }

    private static long count(List<Finding> findings, Severity severity) {
        return findings.stream()
                .filter(value -> value.status() == FindingStatus.OPEN)
                .filter(value -> value.severity() == severity)
                .count();
    }

    private static String createJobId(String targetId, Instant created) {
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .withZone(ZoneOffset.UTC).format(created);
        return targetId.replaceAll("[^A-Za-z0-9._-]", "-") + "-" + stamp + "-"
                + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String safeMessage(Exception value) {
        String message = value.getMessage();
        return message == null || message.isBlank() ? value.getClass().getSimpleName() : message;
    }

    public static final class ValidationExecutionException extends Exception {
        private final Path runRoot;
        private final ValidationReport report;

        ValidationExecutionException(String message, Throwable cause, Path runRoot, ValidationReport report) {
            super(message, cause);
            this.runRoot = runRoot;
            this.report = report;
        }

        public Path runRoot() { return runRoot; }
        public ValidationReport report() { return report; }
    }
}
