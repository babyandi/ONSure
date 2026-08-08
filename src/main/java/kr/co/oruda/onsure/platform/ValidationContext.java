package kr.co.oruda.onsure.platform;

import kr.co.oruda.onsure.platform.ValidationModel.Evidence;
import kr.co.oruda.onsure.platform.ValidationModel.FailureMode;
import kr.co.oruda.onsure.platform.ValidationModel.Finding;
import kr.co.oruda.onsure.platform.ValidationModel.FixtureResult;
import kr.co.oruda.onsure.platform.ValidationModel.RcaRecord;
import kr.co.oruda.onsure.platform.ValidationModel.RegressionLock;
import kr.co.oruda.onsure.platform.ValidationModel.StageResult;
import kr.co.oruda.onsure.platform.ValidationModel.ValidationJob;
import kr.co.oruda.onsure.platform.ValidationModel.ValidationTarget;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Mutable in-memory aggregate for one validation job. */
public final class ValidationContext {
    private final ValidationTarget target;
    private ValidationJob job;
    private final TargetAdapter adapter;
    private final Path runRoot;
    private final List<Evidence> evidence = new ArrayList<>();
    private final List<Finding> findings = new ArrayList<>();
    private final List<FailureMode> failureModes = new ArrayList<>();
    private final List<RcaRecord> rcaRecords = new ArrayList<>();
    private final List<RemediationPlan> remediationPlans = new ArrayList<>();
    private final List<FixtureResult> fixtureResults = new ArrayList<>();
    private final List<StageResult> stageResults = new ArrayList<>();
    private final Map<String, Object> attributes = new LinkedHashMap<>();
    private RegressionLock regressionLock;

    public ValidationContext(ValidationTarget target, ValidationJob job, TargetAdapter adapter, Path runRoot) {
        this.target = Objects.requireNonNull(target, "target");
        this.job = Objects.requireNonNull(job, "job");
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.runRoot = Objects.requireNonNull(runRoot, "runRoot").toAbsolutePath().normalize();
    }

    public ValidationTarget target() { return target; }
    public ValidationJob job() { return job; }
    public TargetAdapter adapter() { return adapter; }
    public Path runRoot() { return runRoot; }
    public List<Evidence> evidence() { return List.copyOf(evidence); }
    public List<Finding> findings() { return List.copyOf(findings); }
    public List<FailureMode> failureModes() { return List.copyOf(failureModes); }
    public List<RcaRecord> rcaRecords() { return List.copyOf(rcaRecords); }
    public List<RemediationPlan> remediationPlans() { return List.copyOf(remediationPlans); }
    public List<FixtureResult> fixtureResults() { return List.copyOf(fixtureResults); }
    public List<StageResult> stageResults() { return List.copyOf(stageResults); }
    public Map<String, Object> attributes() { return Map.copyOf(attributes); }
    public RegressionLock regressionLock() { return regressionLock; }

    public void job(ValidationJob value) { job = Objects.requireNonNull(value); }
    public void addEvidence(Evidence value) { evidence.add(Objects.requireNonNull(value)); }
    public void addFinding(Finding value) { findings.add(Objects.requireNonNull(value)); }
    public void addFailureMode(FailureMode value) { failureModes.add(Objects.requireNonNull(value)); }
    public void addRcaRecord(RcaRecord value) { rcaRecords.add(Objects.requireNonNull(value)); }
    public void addRemediationPlan(RemediationPlan value) { remediationPlans.add(Objects.requireNonNull(value)); }
    public void addFixtureResult(FixtureResult value) { fixtureResults.add(Objects.requireNonNull(value)); }
    public void addStageResult(StageResult value) { stageResults.add(Objects.requireNonNull(value)); }
    public void putAttribute(String key, Object value) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("key");
        attributes.put(key, value);
    }
    public void regressionLock(RegressionLock value) { regressionLock = Objects.requireNonNull(value); }
}
