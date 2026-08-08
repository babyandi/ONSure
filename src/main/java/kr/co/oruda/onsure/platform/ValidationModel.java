package kr.co.oruda.onsure.platform;

import kr.co.oruda.onsure.assurance.Decision;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Core commercial-product domain model for ONSURE validation runs. */
public final class ValidationModel {
    private ValidationModel() {}

    public enum TargetType {
        GENERAL_SOFTWARE,
        AI_APPLICATION,
        AI_AGENTIC_PLATFORM
    }

    public enum JobStatus {
        CREATED,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public enum Severity {
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW,
        INFO
    }

    public enum FindingStatus {
        OPEN,
        RESOLVED,
        ACCEPTED_RISK
    }

    public record ValidationTarget(
            String targetId,
            String targetName,
            TargetType targetType,
            Path sourceRoot,
            String immutableSourceReference,
            String adapterId,
            String policyProfile,
            String executionProfile) {
        public ValidationTarget {
            requireText(targetId, "targetId");
            requireText(targetName, "targetName");
            Objects.requireNonNull(targetType, "targetType");
            Objects.requireNonNull(sourceRoot, "sourceRoot");
            requireText(immutableSourceReference, "immutableSourceReference");
            requireText(adapterId, "adapterId");
            requireText(policyProfile, "policyProfile");
            requireText(executionProfile, "executionProfile");
            sourceRoot = sourceRoot.toAbsolutePath().normalize();
        }
    }

    public record ValidationJob(
            String jobId,
            String targetId,
            JobStatus status,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            String baselineJobId) {
        public ValidationJob {
            requireText(jobId, "jobId");
            requireText(targetId, "targetId");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    public record Evidence(
            String evidenceId,
            String evidenceType,
            String source,
            String sha256,
            Instant collectedAt,
            Map<String, Object> attributes) {
        public Evidence {
            requireText(evidenceId, "evidenceId");
            requireText(evidenceType, "evidenceType");
            requireText(source, "source");
            requireDigest(sha256, "sha256");
            Objects.requireNonNull(collectedAt, "collectedAt");
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }
    }

    public record Finding(
            String findingId,
            String fingerprint,
            String category,
            Severity severity,
            FindingStatus status,
            String title,
            String description,
            String location,
            List<String> evidenceIds,
            String stageId) {
        public Finding {
            requireText(findingId, "findingId");
            requireDigest(fingerprint, "fingerprint");
            requireText(category, "category");
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(status, "status");
            requireText(title, "title");
            requireText(description, "description");
            requireText(location, "location");
            requireText(stageId, "stageId");
            evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        }
    }

    public record FailureMode(
            String failureModeId,
            String code,
            String title,
            String trigger,
            String impact,
            List<String> findingIds) {
        public FailureMode {
            requireText(failureModeId, "failureModeId");
            requireText(code, "code");
            requireText(title, "title");
            requireText(trigger, "trigger");
            requireText(impact, "impact");
            findingIds = findingIds == null ? List.of() : List.copyOf(findingIds);
        }
    }

    public record RcaRecord(
            String rcaId,
            String findingId,
            String directCause,
            String rootCause,
            String contributingFactor,
            String remediation,
            String revalidationRequirement) {
        public RcaRecord {
            requireText(rcaId, "rcaId");
            requireText(findingId, "findingId");
            requireText(directCause, "directCause");
            requireText(rootCause, "rootCause");
            requireText(contributingFactor, "contributingFactor");
            requireText(remediation, "remediation");
            requireText(revalidationRequirement, "revalidationRequirement");
        }
    }

    public record FixtureResult(
            String fixtureId,
            String harnessId,
            String oracleId,
            String expected,
            String observed,
            Decision decision,
            Instant executedAt) {
        public FixtureResult {
            requireText(fixtureId, "fixtureId");
            requireText(harnessId, "harnessId");
            requireText(oracleId, "oracleId");
            expected = expected == null ? "" : expected;
            observed = observed == null ? "" : observed;
            Objects.requireNonNull(decision, "decision");
            Objects.requireNonNull(executedAt, "executedAt");
        }
    }

    public record StageResult(
            String stageId,
            Decision decision,
            Instant startedAt,
            Instant completedAt,
            List<String> findingIds,
            Map<String, Object> metrics) {
        public StageResult {
            requireText(stageId, "stageId");
            Objects.requireNonNull(decision, "decision");
            Objects.requireNonNull(startedAt, "startedAt");
            Objects.requireNonNull(completedAt, "completedAt");
            findingIds = findingIds == null ? List.of() : List.copyOf(findingIds);
            metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
        }
    }

    public record RegressionLock(
            String contract,
            String targetId,
            String jobId,
            String sourceDigest,
            String resultDigest,
            String lockDigest,
            Instant lockedAt) {
        public RegressionLock {
            requireText(contract, "contract");
            requireText(targetId, "targetId");
            requireText(jobId, "jobId");
            requireDigest(sourceDigest, "sourceDigest");
            requireDigest(resultDigest, "resultDigest");
            requireDigest(lockDigest, "lockDigest");
            Objects.requireNonNull(lockedAt, "lockedAt");
        }
    }

    public record ValidationReport(
            String contract,
            String reportId,
            String jobId,
            ValidationTarget target,
            Decision decision,
            Instant generatedAt,
            List<StageResult> stages,
            List<Finding> findings,
            List<FailureMode> failureModes,
            List<RcaRecord> rcaRecords,
            List<FixtureResult> fixtureResults,
            RegressionLock regressionLock,
            Map<String, Object> summary) {
        public ValidationReport {
            requireText(contract, "contract");
            requireText(reportId, "reportId");
            requireText(jobId, "jobId");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(decision, "decision");
            Objects.requireNonNull(generatedAt, "generatedAt");
            stages = stages == null ? List.of() : List.copyOf(stages);
            findings = findings == null ? List.of() : List.copyOf(findings);
            failureModes = failureModes == null ? List.of() : List.copyOf(failureModes);
            rcaRecords = rcaRecords == null ? List.of() : List.copyOf(rcaRecords);
            fixtureResults = fixtureResults == null ? List.of() : List.copyOf(fixtureResults);
            summary = summary == null ? Map.of() : Map.copyOf(summary);
        }
    }

    public record RevalidationDelta(
            String baselineJobId,
            String currentJobId,
            List<String> resolvedFindingFingerprints,
            List<String> newFindingFingerprints,
            List<String> unchangedFindingFingerprints,
            boolean sourceChanged,
            boolean regressionResultChanged) {
        public RevalidationDelta {
            requireText(baselineJobId, "baselineJobId");
            requireText(currentJobId, "currentJobId");
            resolvedFindingFingerprints = List.copyOf(resolvedFindingFingerprints);
            newFindingFingerprints = List.copyOf(newFindingFingerprints);
            unchangedFindingFingerprints = List.copyOf(unchangedFindingFingerprints);
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }

    private static void requireDigest(String value, String name) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be SHA-256");
        }
    }
}
