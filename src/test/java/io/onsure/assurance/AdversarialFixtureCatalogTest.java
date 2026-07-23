package io.onsure.assurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AdversarialFixtureCatalogTest {
    private static final Path FIXTURE_PATH = Path.of(
            "fixtures/design/adversarial-transition-fixtures.v1.json");

    @Test
    void catalogContainsUniqueA01ThroughA20AndRemainsNotRun() throws IOException {
        JsonNode root = new ObjectMapper().readTree(Files.readString(FIXTURE_PATH));

        assertEquals("NOT_RUN", root.path("execution_status").asText());
        JsonNode fixtures = root.path("fixtures");
        assertTrue(fixtures.isArray());
        assertEquals(20, fixtures.size());

        Set<String> ids = new HashSet<>();
        for (JsonNode fixture : fixtures) {
            String id = fixture.path("id").asText();
            assertFalse(id.isBlank());
            assertTrue(ids.add(id), "duplicate fixture id: " + id);
            assertFalse(fixture.path("mutation").asText().isBlank());
            assertFalse(fixture.path("expected_decision").asText().isBlank());
            assertFalse(fixture.path("expected_reason").asText().isBlank());
        }

        for (int number = 1; number <= 20; number++) {
            String prefix = "A%02d_".formatted(number);
            assertTrue(ids.stream().anyMatch(id -> id.startsWith(prefix)),
                    "missing fixture prefix: " + prefix);
        }
    }
}
