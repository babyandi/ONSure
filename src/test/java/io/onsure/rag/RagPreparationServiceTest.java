package io.onsure.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.onsure.assurance.Decision;
import io.onsure.common.Sha256;
import io.onsure.platform.ValidationModel;
import io.onsure.platform.ValidationModel.FailureMode;
import io.onsure.platform.ValidationModel.JobStatus;
import io.onsure.platform.ValidationModel.TargetType;
import io.onsure.platform.ValidationModel.ValidationJob;
import io.onsure.platform.ValidationModel.ValidationReport;
import io.onsure.platform.ValidationModel.ValidationTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RagPreparationServiceTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Test
    void platformNeutralRequestMatchesLegacyReportAdapter() throws Exception {
        Path target = Files.createDirectories(temp.resolve("target"));
        ValidationReport report = report(target, true);
        RagPreparationRequest request = request(report);
        RagPreparationService service = new RagPreparationService();

        Map<String, Object> legacy = service.prepareOwnCandidate(
                report, temp.resolve("legacy-store"));
        Map<String, Object> neutral = service.prepareOwnCandidate(
                request, temp.resolve("neutral-store"));

        assertEquals(withoutPreparedAt(legacy), withoutPreparedAt(neutral));
        assertEquals(request.sourceReportSha256(), neutral.get("source_report_sha256"));
        assertEquals(service.learningProfile(report), service.learningProfile(request));
        Map<String, Object> bootstrap = service.bootstrapTargetEnvironment(request, target, true);
        assertEquals(request.targetId(), bootstrap.get("owner"));
        assertEquals(request.targetSourceReference(), bootstrap.get("target_source_ref"));
    }

    @Test
    void platformNeutralRequestRejectsInvalidEvidenceMetadata() throws Exception {
        ValidationReport report = report(temp.resolve("target"), false);
        RagPreparationRequest valid = request(report);

        assertThrows(IllegalArgumentException.class, () -> new RagPreparationRequest(
                valid.jobId(), valid.reportId(), valid.targetId(), valid.targetName(),
                valid.targetType(), valid.targetSourceRoot(), valid.targetSourceReference(),
                valid.validationDecision(), -1, valid.failureModeCount(), valid.rcaCount(),
                valid.nonPassingFixture(), valid.sourceReportSha256()));
        assertThrows(IllegalArgumentException.class, () -> new RagPreparationRequest(
                valid.jobId(), valid.reportId(), valid.targetId(), valid.targetName(),
                valid.targetType(), valid.targetSourceRoot(), valid.targetSourceReference(),
                valid.validationDecision(), valid.findingCount(), valid.failureModeCount(),
                valid.rcaCount(), valid.nonPassingFixture(), "not-a-digest"));
        assertThrows(IllegalArgumentException.class, () -> new RagPreparationRequest(
                valid.jobId(), valid.reportId(), valid.targetId(), valid.targetName(),
                "UNKNOWN_TARGET", valid.targetSourceRoot(), valid.targetSourceReference(),
                valid.validationDecision(), valid.findingCount(), valid.failureModeCount(),
                valid.rcaCount(), valid.nonPassingFixture(), valid.sourceReportSha256()));
        assertThrows(IllegalArgumentException.class, () -> new RagPreparationRequest(
                valid.jobId(), valid.reportId(), valid.targetId(), valid.targetName(),
                valid.targetType(), valid.targetSourceRoot(), valid.targetSourceReference(),
                "UNKNOWN_DECISION", valid.findingCount(), valid.failureModeCount(),
                valid.rcaCount(), valid.nonPassingFixture(), valid.sourceReportSha256()));
    }

    @Test
    void onsureKeepsItsOwnCandidateAndTargetOwnsExplicitlyBootstrappedEnvironment() throws Exception {
        Path target = Files.createDirectories(temp.resolve("target"));
        ValidationReport report = report(target, true);
        RagPreparationService service = new RagPreparationService();

        Map<String, Object> candidate =
                service.prepareOwnCandidate(report, temp.resolve("onsure-store"));
        assertEquals("ONSURE", candidate.get("owner"));
        assertEquals("RAG_READY", candidate.get("value_decision"));
        assertTrue(Files.isRegularFile(
                temp.resolve("onsure-store/candidates/job-1.json")));
        assertFalse(Files.exists(target.resolve(RagPreparationService.TARGET_ENVIRONMENT)));

        Map<String, Object> receipt =
                service.bootstrapTargetEnvironment(report, target, true);
        Path environment = target.resolve(RagPreparationService.TARGET_ENVIRONMENT);
        assertEquals("target-1", receipt.get("owner"));
        assertTrue(Files.isRegularFile(environment.resolve("source/source_pack.md")));
        assertTrue(Files.isRegularFile(environment.resolve("chunks/chunks.jsonl")));
        assertTrue(Files.isRegularFile(environment.resolve("manifest.json")));
        assertTrue(Files.isRegularFile(environment.resolve("ingest_guide.md")));
        assertEquals("NOT_RUN",
                mapper.readTree(environment.resolve("manifest.json").toFile())
                        .path("training_status").asText());
        assertTrue(mapper.readTree(environment.resolve("learning/profile.json").toFile())
                .path("learning_required").asBoolean());
        assertFalse(mapper.readTree(environment.resolve("learning/policy.json").toFile())
                .path("automatic_learning_enabled").asBoolean());
    }

    @Test
    void validationCannotMutateTargetWithoutSeparateAuthorization() throws Exception {
        Path target = Files.createDirectories(temp.resolve("target"));
        RagPreparationService service = new RagPreparationService();
        assertThrows(IllegalStateException.class,
                () -> service.bootstrapTargetEnvironment(report(target, true), target, false));
        assertFalse(Files.exists(target.resolve(RagPreparationService.TARGET_ENVIRONMENT)));
    }

    @Test
    void cleanLocalOnlyRunCannotCreateUnneededTargetEnvironment() throws Exception {
        Path target = Files.createDirectories(temp.resolve("target"));
        RagPreparationService service = new RagPreparationService();
        assertThrows(IllegalStateException.class,
                () -> service.bootstrapTargetEnvironment(report(target, false), target, true));
        assertFalse(Files.exists(target.resolve(RagPreparationService.TARGET_ENVIRONMENT)));
    }

    @Test
    void bootstrapRejectsAProgramRootThatDoesNotOwnTheValidationReport() throws Exception {
        Path target = Files.createDirectories(temp.resolve("target"));
        Path other = Files.createDirectories(temp.resolve("other"));
        assertThrows(IllegalArgumentException.class,
                () -> new RagPreparationService()
                        .bootstrapTargetEnvironment(report(target, true), other, true));
    }

    @Test
    void oDesignIsClassifiedAsLearningProgramEvenWhenItsRunIsClean() {
        ValidationReport original = report(temp.resolve("odesign"), false);
        ValidationTarget odesign = new ValidationTarget(
                "ODesign", "ODesign", TargetType.GENERAL_SOFTWARE,
                original.target().sourceRoot(), original.target().immutableSourceReference(),
                original.target().adapterId(), original.target().policyProfile(),
                original.target().executionProfile());
        ValidationReport report = new ValidationReport(
                original.contract(), original.reportId(), original.jobId(), odesign,
                original.decision(), original.generatedAt(), original.stages(),
                original.findings(), original.failureModes(), original.rcaRecords(),
                original.fixtureResults(), original.regressionLock(), original.summary());

        Map<String, Object> profile = new RagPreparationService().learningProfile(report);

        assertEquals(true, profile.get("learning_required"));
        assertEquals(true, profile.get("rag_preparation_required"));
        assertEquals(false, profile.get("automatic_learning_enabled"));
    }

    private static ValidationReport report(Path target, boolean reusableFailure) {
        Instant now = Instant.parse("2026-07-25T00:00:00Z");
        ValidationTarget validationTarget = new ValidationTarget(
                "target-1", "Target", TargetType.AI_APPLICATION, target,
                "0123456789abcdef0123456789abcdef01234567",
                "ONSURE_GENERIC_MANIFEST_V1", "default", "isolated");
        ValidationJob job = new ValidationJob(
                "job-1", "target-1", JobStatus.COMPLETED, now, now, now, null);
        List<FailureMode> modes = reusableFailure
                ? List.of(new FailureMode("fm-1", "RAG_POISONING", "Poisoning",
                        "untrusted source", "wrong retrieval", List.of()))
                : List.of();
        return new ValidationReport(
                "ONSURE_VALIDATION_REPORT_V1", "report-1", job.jobId(),
                validationTarget, Decision.PASS, now, List.of(), List.of(), modes,
                List.of(), List.of(), null, Map.of());
    }

    private RagPreparationRequest request(ValidationReport report) throws Exception {
        return new RagPreparationRequest(
                report.jobId(), report.reportId(), report.target().targetId(),
                report.target().targetName(), report.target().targetType().name(),
                report.target().sourceRoot(), report.target().immutableSourceReference(),
                report.decision().name(), report.findings().size(), report.failureModes().size(),
                report.rcaRecords().size(), report.fixtureResults().stream()
                        .anyMatch(value -> value.decision() != Decision.PASS),
                Sha256.digest(mapper.writeValueAsBytes(report)));
    }

    private static Map<String, Object> withoutPreparedAt(Map<String, Object> candidate) {
        Map<String, Object> copy = new LinkedHashMap<>(candidate);
        copy.remove("prepared_at");
        return copy;
    }
}
