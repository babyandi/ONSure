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
        String serialized = mapper.writeValueAsString(overview);
        assertFalse(serialized.contains(secret));
        assertFalse(serialized.contains("OPENAI_API_KEY"));
        assertTrue(serialized.contains("credential_configured"));
        assertTrue(serialized.contains("NOT_RUN"));
    }
}
