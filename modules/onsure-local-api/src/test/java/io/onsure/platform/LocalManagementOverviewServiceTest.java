package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalManagementOverviewServiceTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void projectsRegisteredProgramsValidationAndImprovementWithoutSecrets() throws Exception {
        Path catalog = temp.resolve(".onsure/product-catalog");
        Path run = temp.resolve(".onsure/validation-data/program-one/run-001");
        Files.createDirectories(catalog);
        Files.createDirectories(run);
        mapper.writeValue(catalog.resolve("targets.json").toFile(), List.of(Map.of(
                "projectId", "project-one",
                "target", Map.of(
                        "targetId", "program-one",
                        "targetName", "Program One",
                        "targetType", "GENERAL_SOFTWARE",
                        "immutableSourceReference", "git:0123456789abcdef"))));
        mapper.writeValue(run.resolve("validation-report.json").toFile(), Map.of(
                "reportId", "report-001",
                "decision", "FAIL",
                "generatedAt", "2026-08-03T12:00:00Z",
                "findings", List.of(Map.of("findingId", "finding-one"))));
        mapper.writeValue(run.resolve("evidence.json").toFile(), List.of(
                Map.of("evidenceId", "evidence-one"), Map.of("evidenceId", "evidence-two")));
        mapper.writeValue(run.resolve("remediation-plans.json").toFile(), List.of(
                Map.of("planId", "plan-one")));

        String secret = "sk-test-secret-must-not-appear";
        Map<String, Object> overview = new LocalManagementOverviewService(
                temp, Map.of("ONSURE_LLM_PROVIDER", "openai", "OPENAI_API_KEY", secret),
                HttpClient.newHttpClient()).overview();

        assertEquals(1, overview.get("program_count"));
        assertEquals(1L, overview.get("validated_program_count"));
        assertEquals(1L, overview.get("improvement_candidate_count"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> programs = (List<Map<String, Object>>) overview.get("programs");
        assertEquals("program-one", programs.get(0).get("program_id"));
        assertEquals("AVAILABLE", programs.get(0).get("validation_state"));
        @SuppressWarnings("unchecked")
        Map<String, Object> latest = (Map<String, Object>) programs.get(0).get("latest_validation");
        assertEquals("FAIL", latest.get("decision"));
        assertEquals(1, latest.get("finding_count"));
        assertEquals(2, latest.get("evidence_count"));
        assertEquals(1, latest.get("improvement_candidate_count"));
        assertEquals("NOT_RUN", ((Map<?, ?>) latest.get("scorecard")).get("state"));
        String serialized = mapper.writeValueAsString(overview);
        assertFalse(serialized.contains(secret));
        assertFalse(serialized.contains("OPENAI_API_KEY"));
        assertTrue(serialized.contains("credential_configured"));
        assertTrue(serialized.contains("NOT_RUN"));
    }

    @Test
    void withholdsScoreWhenReceiptDigestDoesNotMatchReport() throws Exception {
        Path run = scoredRun("PASS_NONFINAL");
        Map<String, Object> report = scoredReport("f".repeat(64));
        mapper.writeValue(run.resolve("validation-report.json").toFile(), report);

        Map<String, Object> latest = latestValidation();

        assertEquals("INVALID_EVIDENCE", latest.get("state"));
        assertEquals("HOLD", latest.get("decision"));
        assertEquals("WITHHELD_EVIDENCE_INVALID", ((Map<?, ?>) latest.get("scorecard")).get("state"));
        assertEquals("RECEIPT_SHA256_MISMATCH",
                ((Map<?, ?>) latest.get("evidence_integrity")).get("reason"));
    }

    @Test
    void withholdsScoreWhenFinalReceiptEvidenceIntegrityFailed() throws Exception {
        Path run = scoredRun("FAIL");
        mapper.writeValue(run.resolve("validation-report.json").toFile(),
                scoredReport(Hashing.file(run.resolve(UniversalValidationRunner.RECEIPT_FILE))));

        Map<String, Object> latest = latestValidation();

        assertEquals("INVALID_EVIDENCE", latest.get("state"));
        assertEquals("WITHHELD_EVIDENCE_INVALID", ((Map<?, ?>) latest.get("scorecard")).get("state"));
        assertEquals("FINAL_EVIDENCE_INTEGRITY_NOT_PASS",
                ((Map<?, ?>) latest.get("evidence_integrity")).get("reason"));
    }

    @Test
    void withholdsScoreInsteadOfFailingOverviewWhenReceiptJsonIsMalformed() throws Exception {
        Path run = scoredRun("PASS_NONFINAL");
        Files.writeString(run.resolve(UniversalValidationRunner.RECEIPT_FILE), "{invalid-json\n");
        mapper.writeValue(run.resolve("validation-report.json").toFile(),
                scoredReport(Hashing.file(run.resolve(UniversalValidationRunner.RECEIPT_FILE))));

        Map<String, Object> latest = latestValidation();

        assertEquals("INVALID_EVIDENCE", latest.get("state"));
        assertEquals("HOLD", latest.get("decision"));
        assertEquals("RECEIPT_JSON_INVALID",
                ((Map<?, ?>) latest.get("evidence_integrity")).get("reason"));
    }

    private Path scoredRun(String finalEvidenceOutcome) throws Exception {
        Path catalog = temp.resolve(".onsure/product-catalog");
        Path run = temp.resolve(".onsure/validation-data/program-one/run-001");
        Files.createDirectories(catalog);
        Files.createDirectories(run);
        mapper.writeValue(catalog.resolve("targets.json").toFile(), List.of(Map.of(
                "projectId", "project-one",
                "target", Map.of(
                        "targetId", "program-one", "targetName", "Program One",
                        "targetType", "GENERAL_SOFTWARE",
                        "immutableSourceReference", "sha256:" + "a".repeat(64)))));
        mapper.writeValue(run.resolve(UniversalValidationRunner.RECEIPT_FILE).toFile(), Map.of(
                "contract", UniversalValidationRunner.CONTRACT,
                "source_digest", "a".repeat(64),
                "scorecard", scorecard(),
                "final_evidence_integrity", Map.of("outcome", finalEvidenceOutcome)));
        return run;
    }

    private Map<String, Object> scoredReport(String receiptSha256) {
        return Map.of(
                "reportId", "report-001", "decision", "PASS_NONFINAL",
                "generatedAt", "2026-08-05T12:00:00Z",
                "sourceDigestBefore", "a".repeat(64),
                "universalReceiptSha256", receiptSha256,
                "scorecard", scorecard(), "findings", List.of());
    }

    private Map<String, Object> scorecard() {
        return Map.of(
                "contract", ValidationScorecard.CONTRACT,
                "earned_points", 100, "max_points", 100,
                "validation_outcome", "PASS_NONFINAL");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> latestValidation() throws Exception {
        Map<String, Object> overview = new LocalManagementOverviewService(
                temp, Map.of(), HttpClient.newHttpClient()).overview();
        List<Map<String, Object>> programs = (List<Map<String, Object>>) overview.get("programs");
        return (Map<String, Object>) programs.get(0).get("latest_validation");
    }
}
