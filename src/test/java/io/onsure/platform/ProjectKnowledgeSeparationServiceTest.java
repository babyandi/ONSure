package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.LinkedHashMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectKnowledgeSeparationServiceTest {
    @TempDir Path temp;
    @Test
    void anonymizesIdentifiersDeterministicallyAndSeparatesOnlyExplicitCommonCandidates() throws Exception {
        byte[] salt = "workspace-scoped-test-salt-value-0001".getBytes(StandardCharsets.UTF_8);
        Map<String, String> source = Map.of(
                "private.context", "Acme-X owner@example.com /srv/Acme-X/data token=top-secret 10.0.0.7",
                "common.pattern", "Acme-X retries use exponential backoff");
        ProjectKnowledgeSeparationService service = new ProjectKnowledgeSeparationService();
        var first = service.separate("Acme-X", source, salt);
        var second = service.separate("Acme-X", source, salt);

        assertEquals(first, second);
        String combined = first.anonymizedProjectKnowledge().toString();
        for (String secret : new String[]{"Acme-X", "owner@example.com", "/srv/", "top-secret", "10.0.0.7"}) {
            assertFalse(combined.contains(secret), secret);
        }
        assertTrue(first.commonKnowledgeCandidates().containsKey("pattern"));
        assertFalse(first.commonKnowledgeCandidates().containsKey("context"));
        assertEquals(false, first.automatedCommonPromotionAllowed());
        assertEquals(true, first.humanReviewRequired());
        assertEquals(false, first.finalClaimAllowed());
        assertTrue(first.redactionCategories().containsAll(
                java.util.List.of("PROJECT", "EMAIL", "PATH", "SECRET", "IP")));
    }

    @Test
    void scansMaximumBoundedCorpusWithoutLeakingKnownIdentifierClasses() throws Exception {
        byte[] salt = "workspace-scoped-corpus-salt-value-0001".getBytes(StandardCharsets.UTF_8);
        Map<String, String> corpus = new LinkedHashMap<>();
        for (int index = 0; index < 1000; index++) {
            String prefix = index % 10 == 0 ? "common.pattern-" : "private.sample-";
            corpus.put(prefix + index, "Project-Z user" + index + "@example.org "
                    + "/srv/Project-Z/tenant-" + index + " 10.20.30." + (index % 255)
                    + " api_key=secret-" + index);
        }

        var result = new ProjectKnowledgeSeparationService().separate("Project-Z", corpus, salt);

        assertEquals(1000, result.anonymizedProjectKnowledge().size());
        assertEquals(100, result.commonKnowledgeCandidates().size());
        String combined = String.join("\n", result.anonymizedProjectKnowledge().values());
        for (String forbidden : new String[]{"Project-Z", "@example.org", "/srv/", "10.20.30.", "secret-"}) {
            assertFalse(combined.contains(forbidden), forbidden);
        }
        assertTrue(result.redactionCategories().containsAll(
                java.util.List.of("PROJECT", "EMAIL", "PATH", "SECRET", "IP")));
    }

    @Test
    void exposesAnonymizationThroughTheSharedLocalApiDispatcherWithoutReturningSalt() throws Exception {
        Path salt = temp.resolve("workspace-salt.bin");
        Files.write(salt, "dispatcher-workspace-salt-value-00001".getBytes(StandardCharsets.UTF_8));
        var result = new LocalWorkflowDispatcher(temp).dispatch("knowledge.anonymize",
                new ObjectMapper().valueToTree(Map.of(
                        "project_id", "Project-A",
                        "workspace_salt_file", salt.toString(),
                        "knowledge", Map.of("common.pattern", "Project-A owner@example.org"))));
        String serialized = new ObjectMapper().writeValueAsString(result);
        assertTrue(serialized.contains(ProjectKnowledgeSeparationService.CONTRACT));
        assertFalse(serialized.contains("Project-A owner@example.org"));
        assertFalse(serialized.contains("dispatcher-workspace-salt-value"));
    }
}
