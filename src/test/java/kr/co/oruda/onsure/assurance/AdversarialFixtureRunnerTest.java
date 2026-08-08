package kr.co.oruda.onsure.assurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AdversarialFixtureRunnerTest {
    @Test
    void executesAllTwentyFixturesAndMatchesDeclaredContract() throws Exception {
        Path fixturePath = Path.of("fixtures/design/adversarial-transition-fixtures.v1.json");
        List<AdversarialFixtureRunner.FixtureResult> results;
        try (var in = Files.newInputStream(fixturePath)) {
            results = new AdversarialFixtureRunner().run(in);
        }
        Map<String, AdversarialFixtureRunner.FixtureResult> byId = results.stream()
                .collect(Collectors.toMap(AdversarialFixtureRunner.FixtureResult::id, r -> r));
        JsonNode root = new ObjectMapper().readTree(fixturePath.toFile());
        for (JsonNode fixture : root.path("fixtures")) {
            String id = fixture.path("id").asText();
            AdversarialFixtureRunner.FixtureResult actual = byId.get(id);
            assertEquals(Decision.valueOf(fixture.path("expected_decision").asText()), actual.decision(), id);
            assertTrue(actual.reasons().contains(fixture.path("expected_reason").asText()), id + " reasons=" + actual.reasons());
        }
        assertEquals(20, results.size());
        assertEquals(18, results.stream().filter(r -> r.decision() == Decision.FAIL).count());
        assertEquals(1, results.stream().filter(r -> r.decision() == Decision.HOLD).count());
        assertEquals(1, results.stream().filter(r -> r.decision() == Decision.INCONCLUSIVE).count());
        assertTrue(results.stream().noneMatch(r -> r.decision() == Decision.NOT_RUN));
    }
}
