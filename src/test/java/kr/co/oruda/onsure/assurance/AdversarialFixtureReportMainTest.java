package kr.co.oruda.onsure.assurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdversarialFixtureReportMainTest {
    private static final Path FIXTURES =
            Path.of("fixtures/design/adversarial-transition-fixtures.v1.json");

    @TempDir Path temp;

    @Test
    void writesCompleteDeterministicReportForAllDeclaredFixtures() throws Exception {
        Path first = temp.resolve("first.tsv");
        Path second = temp.resolve("second.tsv");

        assertEquals(Decision.PASS, AdversarialFixtureReportMain.writeReport(FIXTURES, first).decision());
        assertEquals(Decision.PASS, AdversarialFixtureReportMain.writeReport(FIXTURES, second).decision());
        assertEquals(Files.readString(first), Files.readString(second));

        List<String> lines = Files.readAllLines(first);
        assertEquals(21, lines.size());
        assertTrue(lines.get(0).startsWith("contract\tfixture\texpected_decision"));
        assertTrue(lines.stream().skip(1).allMatch(line ->
                line.startsWith(AdversarialFixtureReportMain.CONTRACT + "\t")
                        && line.endsWith("\tPASS")));
    }

    @Test
    void mutatedExpectationFailsClosedAndStillWritesDiagnosticReport() throws Exception {
        JsonNode root = new ObjectMapper().readTree(FIXTURES.toFile());
        ((com.fasterxml.jackson.databind.node.ObjectNode) root.path("fixtures").get(0))
                .put("expected_reason", "NON_EXISTENT_REASON");
        Path mutated = temp.resolve("mutated.json");
        Path report = temp.resolve("mutated.tsv");
        new ObjectMapper().writeValue(mutated.toFile(), root);

        ValidationResult result = AdversarialFixtureReportMain.writeReport(mutated, report);

        assertEquals(Decision.FAIL, result.decision());
        assertTrue(result.violations().contains("ADVERSARIAL_FIXTURE_CONTRACT_MISMATCH"));
        assertTrue(Files.readString(report).contains("\tFAIL\n"));
    }

    @Test
    void unreadableFixtureInputFailsClosed() {
        ValidationResult result = AdversarialFixtureReportMain.writeReport(
                temp.resolve("missing.json"), temp.resolve("missing.tsv"));

        assertEquals(Decision.FAIL, result.decision());
        assertTrue(result.violations().contains("ADVERSARIAL_FIXTURE_REPORT_WRITE_FAILED"));
    }
}
