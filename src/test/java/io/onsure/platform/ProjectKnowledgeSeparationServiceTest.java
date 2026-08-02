package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProjectKnowledgeSeparationServiceTest {
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
}
