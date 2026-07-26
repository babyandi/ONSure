package io.onsure.platform.oruda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.onsure.assurance.Decision;
import io.onsure.platform.OrudaTargetAdapter;
import io.onsure.platform.SourceReferenceBinding;
import io.onsure.platform.ValidationEngine;
import io.onsure.platform.ValidationModel.TargetType;
import io.onsure.platform.ValidationModel.ValidationTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OrudaMvf001E2ETest {
    private static final Path ROOT = Path.of("fixtures/oruda/mvf-001");
    private final ObjectMapper mapper = new ObjectMapper();
    @TempDir Path temp;

    @Test
    void mvfManifestContainsExactlySeventeenExecutableFixturesAcrossRequiredGroupsAndLanes() throws Exception {
        JsonNode root = mapper.readTree(ROOT.resolve("oruda-target.json").toFile());
        JsonNode fixtures = root.path("fixtures");
        assertEquals("ONSURE_ORUDA_TARGET_PROFILE_V1", root.path("contract").asText());
        assertEquals("MVF-001", root.path("fixture_set").path("id").asText());
        assertEquals("NOT_RUN", root.path("fixture_set").path("execution_status").asText());
        assertEquals(17, fixtures.size());

        Map<String, Long> groups = StreamSupport.stream(fixtures.spliterator(), false)
                .map(value -> value.path("group").asText())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        assertEquals(Map.of(
                "POSITIVE", 3L,
                "NEGATIVE", 5L,
                "HANDOFF", 3L,
                "QUALITY", 3L,
                "EVIDENCE", 3L), groups);

        Set<String> lanes = StreamSupport.stream(root.path("lanes").spliterator(), false)
                .map(JsonNode::asText).collect(Collectors.toSet());
        assertTrue(lanes.containsAll(Set.of(
                "OReport", "ODesign", "OAsset", "OUI", "OTester", "OAudit", "EvidenceRegistry")));

        for (JsonNode fixture : fixtures) {
            assertTrue(fixture.path("id").asText().matches("MVF-(POS|NEG|HANDOFF|QUALITY|EVID)-\\d{3}"));
            assertTrue(fixture.path("required_evidence").isArray());
            assertTrue(fixture.path("required_evidence").size() > 0);
            assertEquals("bash", fixture.path("command").get(0).asText());
            assertEquals("mvf-runner.sh", fixture.path("command").get(1).asText());
            assertTrue(Files.isRegularFile(ROOT.resolve(fixture.path("command").get(1).asText())));
        }
    }

    @Test
    void mvfRunsThroughExplicitOrudaAdapterHarnessOracleEvidenceRcaAndRegressionLock() throws Exception {
        ValidationTarget target = new ValidationTarget(
                "ORUDA-MVF-001",
                "ORUDA Minimum Viable Fixture Set",
                TargetType.AI_AGENTIC_PLATFORM,
                ROOT,
                SourceReferenceBinding.treeReference(ROOT),
                OrudaTargetAdapter.ID,
                "ONSURE_ORUDA_MVF_POLICY_V1",
                "LOCAL_MVF_E2E");

        ValidationEngine.RunResult result = ValidationEngine.withOptionalAdapters(
                temp.resolve("runs"), List.of(new OrudaTargetAdapter())).run(target);
        assertEquals(Decision.PASS, result.report().decision());
        assertEquals(17, result.report().fixtureResults().size());
        assertTrue(result.report().fixtureResults().stream()
                .allMatch(value -> value.decision() == Decision.PASS));
        assertTrue(result.report().findings().isEmpty());
        assertTrue(result.report().failureModes().isEmpty());
        assertTrue(result.report().rcaRecords().isEmpty());
        assertTrue(result.report().regressionLock() != null);

        var fixtureStage = result.report().stages().stream()
                .filter(value -> value.stageId().equals("FIXTURE_HARNESS_ORACLE"))
                .findFirst().orElseThrow();
        assertEquals(17, ((Number) fixtureStage.metrics().get("fixtures")).intValue());
        assertEquals(17, ((Number) fixtureStage.metrics().get("commands_executed")).intValue());
        assertEquals(0, ((Number) fixtureStage.metrics().get("failures")).intValue());

        for (String file : Set.of(
                "fixture-registry.json",
                "oracle-registry.json",
                "harness-command-manifest.json",
                "fixture-results.json",
                "evidence.json",
                "oruda-evidence-registry.json",
                "regression-lock.json",
                "internal-verifier-receipt.json",
                "internal-audit-receipt.json",
                "validation-report.json",
                "manifest.sha256")) {
            assertTrue(Files.isRegularFile(result.runRoot().resolve(file)), file);
        }
        assertEquals(Decision.PASS,
                new ReceiptLineageVerifier().verify(result.runRoot(), ROOT).decision());
    }
}
