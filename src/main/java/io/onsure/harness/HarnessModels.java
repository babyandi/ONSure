package io.onsure.harness;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class HarnessModels {
    private HarnessModels() {}

    public enum Decision { PASS, FAIL, BLOCKED, NOT_RUN }
    public enum Severity { CRITICAL, MAJOR, MINOR, INFO }

    public record FinalCandidateRules(
            boolean notRunMustBeZero,
            boolean blockedMustBeZero,
            int criticalZeroConsecutiveRuns,
            int majorZeroConsecutiveRuns,
            int independentRunsRequired,
            boolean automaticFinalLock) {}

    public record Axis(String id, String code, String name, boolean required, boolean blockingOnNotRun) {}
    public record AxisSet(String contract, String version, FinalCandidateRules finalCandidateRules, List<Axis> axes) {
        public AxisSet { axes = List.copyOf(axes); }
    }

    public record Fixture(
            String fixtureId,
            String kind,
            Severity severity,
            List<String> axisIds,
            List<String> command,
            String cwd,
            int timeoutSec,
            String oracleId,
            JsonNode expected,
            List<String> requiredEvidence,
            List<String> tags) {
        public Fixture {
            axisIds = List.copyOf(axisIds);
            command = List.copyOf(command);
            requiredEvidence = List.copyOf(requiredEvidence);
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }

    public record FixtureSet(String contract, String targetId, List<Fixture> fixtures) {
        public FixtureSet { fixtures = List.copyOf(fixtures); }
    }

    public record OracleSpec(
            String oracleId,
            String type,
            String description,
            boolean blockedOnMissingEvidence,
            Map<String, Object> parameters) {
        public OracleSpec { parameters = parameters == null ? Map.of() : Map.copyOf(parameters); }
    }

    public record OracleSet(String contract, String version, List<OracleSpec> oracles) {
        public OracleSet { oracles = List.copyOf(oracles); }
    }

    public record Evidence(
            String contract,
            String evidenceId,
            String runId,
            String fixtureId,
            List<String> axisIds,
            List<String> command,
            String cwd,
            Instant startedAt,
            Instant completedAt,
            Integer exitCode,
            boolean timedOut,
            String stdoutSha256,
            String stderrSha256,
            String environmentSha256,
            Decision decision,
            String reason) {
        public Evidence {
            axisIds = List.copyOf(axisIds);
            command = List.copyOf(command);
        }
    }

    public record Receipt(
            String contract,
            String receiptId,
            String runId,
            String fixtureId,
            String oracleId,
            String evidenceSha256,
            Decision decision,
            String reason,
            Severity severity,
            boolean rcaRequired,
            Instant createdAt,
            String receiptSha256) {}

    public record AxisResult(String axisId, Decision decision, List<String> fixtureIds, String reason) {
        public AxisResult { fixtureIds = List.copyOf(fixtureIds); }
    }

    public record FixtureResult(
            String fixtureId,
            String kind,
            Severity severity,
            Decision decision,
            String reason,
            String evidencePath,
            String receiptPath,
            String evidenceSha256,
            String receiptSha256) {}

    public record RunSummary(
            String contract,
            String runId,
            String targetId,
            String operatorId,
            String environmentDigest,
            Instant startedAt,
            Instant completedAt,
            Decision decision,
            int criticalDefects,
            int majorDefects,
            int minorDefects,
            int notRunCount,
            int blockedCount,
            List<AxisResult> axisResults,
            List<FixtureResult> fixtureResults,
            String normalizedResultDigest) {
        public RunSummary {
            axisResults = List.copyOf(axisResults);
            fixtureResults = List.copyOf(fixtureResults);
        }
    }

    public record RcaRecord(
            String contract,
            String rcaId,
            String runId,
            String fixtureId,
            Severity severity,
            String failureReason,
            String rootCause,
            String fixReference,
            String regressionRun1,
            String regressionRun2,
            String status) {}

    public record RunReceipt(
            String contract,
            String runId,
            String runSummarySha256,
            String evidenceManifestSha256,
            Decision decision,
            Instant createdAt,
            String receiptSha256) {}

    public record FinalCandidate(
            String contract,
            String candidateId,
            String run1Id,
            String run2Id,
            boolean eligible,
            Decision decision,
            List<String> reasons,
            String candidateDigest,
            boolean finalLockAllowed,
            Instant evaluatedAt) {
        public FinalCandidate { reasons = List.copyOf(reasons); }
    }

    public record RegressionReceipt(
            String contract,
            String baselineRunId,
            String regressionRun1Id,
            String regressionRun2Id,
            boolean eligible,
            Decision decision,
            List<String> resolvedFixtures,
            List<String> remainingFixtures,
            List<String> reasons,
            String regressionDigest,
            Instant evaluatedAt) {
        public RegressionReceipt {
            resolvedFixtures = List.copyOf(resolvedFixtures);
            remainingFixtures = List.copyOf(remainingFixtures);
            reasons = List.copyOf(reasons);
        }
    }
}
