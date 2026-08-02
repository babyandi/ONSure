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

/** Generic Validator Engine: learn -> plan -> review/verify -> diagnose -> improve-plan -> prove nonfinal. */
public final class ValidationEngine {
    public static final String REPORT_CONTRACT = "ONSURE_VALIDATION_REPORT_V1";
    public record RunResult(Path runRoot, ValidationReport report) {}
    public record ApprovedExecutionPlanBundle(
            Path approvedPlanFile,
            Path originalPlanFile,
            Path signedApprovalReceipt,
            Path trustedKeyRegistry,
            Path approvalReplayLedger) {
        public ApprovedExecutionPlanBundle {
            approvedPlanFile = requirePath(approvedPlanFile, "approvedPlanFile");
            originalPlanFile = requirePath(originalPlanFile, "originalPlanFile");
            signedApprovalReceipt = requirePath(signedApprovalReceipt, "signedApprovalReceipt");
            trustedKeyRegistry = requirePath(trustedKeyRegistry, "trustedKeyRegistry");
            approvalReplayLedger = requirePath(approvalReplayLedger, "approvalReplayLedger");
        }
        private static Path requirePath(Path value, String name) {
            if (value == null) throw new IllegalArgumentException(name + " required");
            return value.toAbsolutePath().normalize();
        }
    }

    private final TargetAdapterRegistry adapterRegistry;
    private final List<ValidatorStage> stages;
    private final FileValidationStore store;

    public ValidationEngine(List<TargetAdapter> adapters, List<ValidatorStage> stages, FileValidationStore store) {
        this.adapterRegistry = new TargetAdapterRegistry(adapters);
        this.stages = List.copyOf(stages);
        this.store = Objects.requireNonNull(store, "store");
        if (this.stages.isEmpty()) throw new IllegalArgumentException("at least one stage is required");
    }

    public static ValidationEngine defaultEngine(Path storeRoot) {
        return withOptionalAdapters(storeRoot, List.of());
    }

    public static ValidationEngine withOptionalAdapters(
            Path storeRoot, List<TargetAdapter> optionalAdapters) {
        List<TargetAdapter> adapters = new ArrayList<>();
        adapters.add(new GenericManifestTargetAdapter());
        if (optionalAdapters != null) adapters.addAll(optionalAdapters);
        return new ValidationEngine(adapters, defaultStages(), new FileValidationStore(storeRoot));
    }

    private static List<ValidatorStage> defaultStages() {
        List<ValidatorStage> values = new ArrayList<>(BuiltInStages.defaults());
        int afterInventory = indexOf(values, "SOURCE_INVENTORY") + 1;
        values.add(afterInventory, new ProgramLearningStage());
        values.add(afterInventory + 1, new RiskPlanningStage());
        int runtimeIndex = indexOf(values, "FIXTURE_HARNESS_ORACLE");
        values.add(runtimeIndex, new FixtureRegistryStage());
        runtimeIndex = indexOf(values, "FIXTURE_HARNESS_ORACLE");
        values.add(runtimeIndex, new BehaviorLearningStage());
        runtimeIndex = indexOf(values, "FIXTURE_HARNESS_ORACLE");
        values.add(runtimeIndex, new OReviewStage());
        int afterLegacyRca = indexOf(values, "FAILURE_MODE_AND_RCA") + 1;
        values.add(afterLegacyRca, new EvidenceBasedRcaStage());
        values.add(afterLegacyRca + 1, new PatchPlanningStage());
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
        return runInternal(target, null);
    }

    /** Legacy approved-plan-only entry is intentionally prohibited. */
    @Deprecated
    public RunResult run(ValidationTarget target, Path approvedExecutionPlanFile) {
        throw new IllegalArgumentException("APPROVED_EXECUTION_PLAN_BUNDLE_REQUIRED");
    }

    public RunResult run(
            ValidationTarget target, ApprovedExecutionPlanBundle approvalBundle) throws Exception {
        return runInternal(target, Objects.requireNonNull(approvalBundle, "approvalBundle"));
    }

    private RunResult runInternal(
            ValidationTarget target, ApprovedExecutionPlanBundle approvalBundle) throws Exception {
        TargetAdapter adapter = adapterRegistry.require(target);
        Instant created = Instant.now();
        String jobId = createJobId(target.targetId(), created);
        Path runRoot = store.createRunRoot(target.targetId(), jobId);
        ValidationJob running = new ValidationJob(
                jobId, target.targetId(), JobStatus.RUNNING, created, created, null, null);
        ValidationContext context = new ValidationContext(target, running, adapter, runRoot);
        List<String> plannedStageIds = stages.stream().map(ValidatorStage::stageId).toList();
        ValidationStageCheckpointJournal checkpoint = new ValidationStageCheckpointJournal(
                runRoot, jobId, target.targetId(), plannedStageIds);
        ValidationContextSnapshotStore contextSnapshots = new ValidationContextSnapshotStore(runRoot);
        context.putAttribute("registered_adapter_ids", adapterRegistry.adapterIds());
        if (approvalBundle != null) {
            context.putAttribute("approved_execution_plan_file", approvalBundle.approvedPlanFile().toString());
            context.putAttribute("original_execution_plan_file", approvalBundle.originalPlanFile().toString());
            context.putAttribute("signed_plan_approval_receipt", approvalBundle.signedApprovalReceipt().toString());
            context.putAttribute("plan_approval_key_registry", approvalBundle.trustedKeyRegistry().toString());
            context.putAttribute("plan_approval_replay_ledger", approvalBundle.approvalReplayLedger().toString());
        }
        contextSnapshots.save(context, -1, "INITIALIZED");

        Exception executionFailure = null;
        int checkpointIndex = 0;
        for (ValidatorStage stage : stages) {
            checkpoint.stageStarted(stage.stageId(), checkpointIndex);
            if (!stage.supports(context)) {
                checkpoint.stageCompleted(stage.stageId(), "NOT_SUPPORTED");
                checkpointIndex++;
                contextSnapshots.save(context, checkpointIndex - 1, "STAGE_COMPLETED");
                continue;
            }
            String requiredAction = ExecutionPlanActionPolicy.requiredAction(stage.stageId());
            if (requiredAction != null
                    && context.attributes().containsKey("execution_plan_approved_actions")
                    && !ExecutionPlanActionPolicy.isApproved(context, requiredAction)) {
                StageResult result = ExecutionPlanActionPolicy.notApproved(stage.stageId(), requiredAction);
                context.addStageResult(result);
                checkpoint.stageCompleted(stage.stageId(), result.decision().name());
                checkpointIndex++;
                contextSnapshots.save(context, checkpointIndex - 1, "STAGE_COMPLETED");
                continue;
            }
            try {
                StageResult result = stage.execute(context);
                context.addStageResult(result);
                checkpoint.stageCompleted(stage.stageId(), result.decision().name());
                checkpointIndex++;
            } catch (Exception e) {
                executionFailure = e;
                Instant now = Instant.now();
                context.addStageResult(new StageResult(
                        stage.stageId(), Decision.FAIL, now, now, List.of(),
                        Map.of("exception", e.getClass().getName(), "message", safeMessage(e))));
                context.putAttribute("execution_failure_stage", stage.stageId());
                context.putAttribute("execution_failure", safeMessage(e));
                checkpoint.stageFailed(stage.stageId(), e);
                contextSnapshots.save(context, checkpointIndex - 1, "STAGE_FAILED");
                break;
            }
            contextSnapshots.save(context, checkpointIndex - 1, "STAGE_COMPLETED");
        }

        Instant completed = Instant.now();
        context.job(new ValidationJob(
                jobId, target.targetId(), executionFailure == null ? JobStatus.COMPLETED : JobStatus.FAILED,
                created, created, completed, null));
        contextSnapshots.save(context, checkpointIndex - 1, "STAGES_FINISHED");
        Decision decision = finalDecision(context, executionFailure);
        checkpoint.stagesFinished(decision.name());
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
        summary.put("legacy_rca_count", context.rcaRecords().size());
        summary.put("evidence_based_rca_confirmed_count",
                context.attributes().getOrDefault("evidence_based_rca_confirmed_count", "NOT_RUN"));
        summary.put("evidence_based_rca_candidate_count",
                context.attributes().getOrDefault("evidence_based_rca_candidate_count", "NOT_RUN"));
        summary.put("remediation_plan_count", context.remediationPlans().size());
        summary.put("approval_required_count", context.remediationPlans().stream()
                .filter(value -> value.changeClass() == ChangeClass.APPROVAL_REQUIRED).count());
        summary.put("remediation_plans", context.remediationPlans());
        summary.put("patch_plan_id", context.attributes().getOrDefault("patch_plan_id", "NOT_RUN"));
        summary.put("patch_plan_state", context.attributes().getOrDefault("patch_plan_state", "NOT_RUN"));
        summary.put("patch_plan_hunk_count", context.attributes().getOrDefault("patch_plan_hunk_count", "NOT_RUN"));
        summary.put("fixture_count", context.fixtureResults().size());
        summary.put("fixture_failures", context.fixtureResults().stream()
                .filter(value -> value.decision() != Decision.PASS).count());
        summary.put("source_tree_sha256", context.attributes().getOrDefault("source_tree_sha256", "NOT_AVAILABLE"));
        summary.put("program_profile_id", context.attributes().getOrDefault("program_profile_id", "NOT_RUN"));
        summary.put("program_profile_state", context.attributes().getOrDefault("program_profile_state", "NOT_RUN"));
        summary.put("execution_plan_id", context.attributes().getOrDefault("execution_plan_id", "NOT_RUN"));
        summary.put("execution_plan_approval", context.attributes().getOrDefault("execution_plan_approval", "NOT_RUN"));
        summary.put("execution_plan_approval_scope", context.attributes().getOrDefault("execution_plan_approval_scope", "NOT_RUN"));
        summary.put("execution_plan_approved_actions", context.attributes().getOrDefault("execution_plan_approved_actions", List.of()));
        summary.put("execution_plan_unapproved_actions", context.attributes().getOrDefault("execution_plan_unapproved_actions", List.of()));
        summary.put("execution_plan_partial", context.attributes().getOrDefault("execution_plan_partial", false));
        summary.put("execution_plan_approval_sha256", context.attributes().getOrDefault("execution_plan_approval_sha256", "NOT_RUN"));
        summary.put("behavior_profile_id", context.attributes().getOrDefault("behavior_profile_id", "NOT_RUN"));
        summary.put("behavior_profile_state", context.attributes().getOrDefault("behavior_profile_state", "NOT_RUN"));
        summary.put("behavior_profile_stable", context.attributes().getOrDefault("behavior_profile_stable", "NOT_RUN"));
        summary.put("behavior_profile_coverage_class", context.attributes().getOrDefault("behavior_profile_coverage_class", "NOT_RUN"));
        summary.put("review_id", context.attributes().getOrDefault("review_id", "NOT_RUN"));
        summary.put("review_quality_decision", context.attributes().getOrDefault("review_quality_decision", "NOT_RUN"));
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
                REPORT_CONTRACT, "REPORT-" + context.job().jobId(), context.job().jobId(),
                context.target(), decision, generatedAt, context.stageResults(), context.findings(),
                context.failureModes(), context.rcaRecords(), context.fixtureResults(),
                context.regressionLock(), summary);
    }

    private static String stageDecision(ValidationContext context, String stageId) {
        return context.stageResults().stream().filter(value -> stageId.equals(value.stageId()))
                .map(value -> value.decision().name()).findFirst().orElse("NOT_RUN");
    }

    private static Decision finalDecision(ValidationContext context, Exception executionFailure) {
        if (executionFailure != null) return Decision.FAIL;
        if (!ValidationCompletionGate.evaluate(context).eligible()) return Decision.FAIL;
        if (context.stageResults().stream().anyMatch(value -> value.decision() == Decision.FAIL)) return Decision.FAIL;
        if (context.stageResults().stream().anyMatch(value -> value.decision() != Decision.PASS)) return Decision.HOLD;
        boolean blocking = context.findings().stream()
                .filter(value -> value.status() == FindingStatus.OPEN)
                .anyMatch(value -> value.severity() == Severity.CRITICAL || value.severity() == Severity.HIGH);
        if (blocking) return Decision.FAIL;
        return context.findings().stream().anyMatch(value -> value.status() == FindingStatus.OPEN)
                ? Decision.HOLD : Decision.PASS;
    }

    private static long count(List<Finding> findings, Severity severity) {
        return findings.stream().filter(value -> value.status() == FindingStatus.OPEN)
                .filter(value -> value.severity() == severity).count();
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
