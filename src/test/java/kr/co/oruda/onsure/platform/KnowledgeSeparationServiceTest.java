package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.oruda.onsure.platform.KnowledgeSeparationService.IndependentReproduction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KnowledgeSeparationServiceTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void rawProjectMemoryNeverEntersAllowlistedReusablePattern() throws Exception {
        Path source = temp.resolve("failure-memory.json");
        Files.writeString(source, """
                {
                  "contract":"ONSURE_FAILURE_MEMORY_V1",
                  "memory_id":"customer-alpha-memory-001",
                  "program_id":"customer-alpha-private-project",
                  "finding_id":"finding-secret-001",
                  "reproduction":["Contact alice@example.com and use token sk-secret-123"],
                  "first_failure_point":"fixture:customer-alpha-auth",
                  "direct_cause":"alice@example.com received unauthorized access",
                  "root_cause":"Authorization deny rule missing for /private/customer-alpha",
                  "contributing_factors":[],
                  "confidence":0.9,
                  "evidence_refs":["receipt-customer-alpha-secret"],
                  "scope":"PROJECT_ONLY",
                  "state":"CANDIDATE"
                }
                """);
        KnowledgeSeparationService service = new KnowledgeSeparationService(temp.resolve("memory"));
        var result = service.separate(source, List.of(
                new IndependentReproduction("independent-project-a", "receipt-independent-a", true),
                new IndependentReproduction("independent-project-b", "receipt-independent-b", true)),
                true, true);

        assertEquals("REUSABLE_CANDIDATE", result.decision());
        assertTrue(Files.isRegularFile(result.projectRecordFile()));
        assertTrue(Files.isRegularFile(result.reusablePatternFile()));
        assertFalse(result.projectRecordFile().startsWith(result.reusablePatternFile().getParent()));
        String projectText = Files.readString(result.projectRecordFile());
        assertTrue(projectText.contains("alice@example.com"));
        String reusableText = Files.readString(result.reusablePatternFile());
        for (String forbidden : List.of(
                "alice@example.com", "sk-secret-123", "customer-alpha",
                "finding-secret-001", "receipt-customer-alpha-secret", "/private/")) {
            assertFalse(reusableText.contains(forbidden), forbidden);
        }
        JsonNode reusable = mapper.readTree(result.reusablePatternFile().toFile());
        assertEquals("AUTHORIZATION_POLICY_GAP", reusable.path("pattern_class").asText());
        assertEquals("ALLOWLISTED_TAXONOMY_ONLY",
                reusable.path("deidentification").path("strategy").asText());
        assertFalse(reusable.path("activation_allowed").asBoolean(true));
    }

    @Test
    void insufficientOrUnreviewedEvidenceStaysProjectOnlyOnHold() throws Exception {
        Path source = Path.of("fixtures/contracts/failure-memory.valid.json")
                .toAbsolutePath().normalize();
        KnowledgeSeparationService service = new KnowledgeSeparationService(temp.resolve("memory"));
        var result = service.separate(source, List.of(
                new IndependentReproduction("one-project", "one-receipt", true)), false, false);

        assertEquals("HOLD", result.decision());
        assertNull(result.reusablePatternFile());
        assertTrue(result.violations().contains(
                "REUSABLE_PATTERN_INDEPENDENT_REPRODUCTIONS_INSUFFICIENT"));
        assertTrue(result.violations().contains("REUSABLE_PATTERN_RIGHTS_REVIEW_REQUIRED"));
        assertTrue(result.violations().contains("REUSABLE_PATTERN_PRIVACY_REVIEW_REQUIRED"));
        assertTrue(Files.isRegularFile(result.projectRecordFile()));
    }
}
