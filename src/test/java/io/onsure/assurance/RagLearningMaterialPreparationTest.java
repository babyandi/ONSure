package io.onsure.assurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RagLearningMaterialPreparationTest {
    private static final Path FIXTURES =
            Path.of("fixtures/design/adversarial-transition-fixtures.v1.json");
    private static final Path MATERIALS =
            Path.of("rag/preparation/adversarial-learning-materials.v1.json");
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void everyAdversarialFixtureHasOneSourceBoundRagPreparationRecord() throws Exception {
        JsonNode fixtures = mapper.readTree(FIXTURES.toFile());
        JsonNode materials = mapper.readTree(MATERIALS.toFile());

        assertEquals("PREPARED_NOT_INGESTED", materials.path("preparation_status").asText());
        assertEquals("NOT_CREATED", materials.path("rag_index_status").asText());
        assertEquals("NOT_RUN", materials.path("embedding_status").asText());
        assertEquals("NOT_RUN", materials.path("learning_application_status").asText());
        assertEquals(0, materials.path("applied_count").asInt(-1));
        assertFalse(materials.path("hidden_answer_accessed").asBoolean(true));
        assertEquals(sha256(FIXTURES), materials.path("source_fixture_catalog_sha256").asText());

        Map<String, JsonNode> byFixture = new HashMap<>();
        Set<String> materialIds = new HashSet<>();
        Set<String> semanticKeys = new HashSet<>();
        for (JsonNode material : materials.path("materials")) {
            assertTrue(materialIds.add(material.path("material_id").asText()),
                    "duplicate material id");
            assertTrue(semanticKeys.add(material.path("semantic_key").asText()),
                    "duplicate semantic key");
            assertEquals("PARTIAL", material.path("availability").asText());
            assertEquals("HOLD", material.path("rag_eligibility").asText());
            assertTrue(material.path("missing_components").isArray());
            assertFalse(material.path("missing_components").isEmpty());
            assertEquals(null, byFixture.put(material.path("fixture_id").asText(), material),
                    "duplicate fixture material");
        }

        assertEquals(fixtures.path("fixtures").size(), byFixture.size());
        for (JsonNode fixture : fixtures.path("fixtures")) {
            String fixtureId = fixture.path("id").asText();
            assertTrue(byFixture.containsKey(fixtureId), "missing RAG material: " + fixtureId);
            String reason = fixture.path("expected_reason").asText();
            assertTrue(byFixture.get(fixtureId).path("semantic_key").asText().endsWith(reason),
                    "semantic key not bound to expected reason: " + fixtureId);
        }
    }

    @Test
    void preparationCannotClaimIngestionOrLearningApplication() throws Exception {
        JsonNode materials = mapper.readTree(MATERIALS.toFile());
        assertFalse(materials.toString().contains("\"INGESTED\""));
        assertFalse(materials.toString().contains("\"APPLIED_LOCKED\""));
        assertTrue(materials.path("materials").size() > 0);
        for (JsonNode material : materials.path("materials")) {
            assertEquals("HOLD", material.path("rag_eligibility").asText());
        }
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
