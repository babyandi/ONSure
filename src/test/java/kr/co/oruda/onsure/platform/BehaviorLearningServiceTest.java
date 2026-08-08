package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BehaviorLearningServiceTest {
    @TempDir Path temp;

    @Test
    void repeatedExecutableFixturesCreateBoundObservationReceipts() throws Exception {
        Path source = Path.of("fixtures/e2e/ai-program").toAbsolutePath().normalize();
        ValidationModel.ValidationTarget target = new ValidationModel.ValidationTarget(
                "sample-ai-program", "Sample AI", ValidationModel.TargetType.AI_APPLICATION,
                source, SourceReferenceBinding.treeReference(source),
                GenericManifestTargetAdapter.ID, "ONSURE_DEFAULT_POLICY_V1", "LOCAL_FIXTURE");
        Path output = temp.resolve("behavior-profile.json");
        Map<String, Object> profile = new BehaviorLearningService().learn(
                new BehaviorLearningService.ValidationTargetBundle(
                        target, new GenericManifestTargetAdapter()),
                "profile-sample-ai", 2, output);
        assertEquals("EXECUTABLE_FIXTURE_PROCESS_PROXY", profile.get("coverage_class"));
        assertEquals(false, profile.get("direct_behavior_telemetry"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> observations =
                (List<Map<String, Object>>) profile.get("observations");
        assertEquals(4, observations.size());
        @SuppressWarnings("unchecked")
        List<String> refs = (List<String>) profile.get("evidence_refs");
        assertEquals(4, refs.size());
        for (Map<String, Object> observation : observations) {
            Path receipt = Path.of(observation.get("run_receipt_path").toString());
            assertTrue(Files.isRegularFile(receipt));
            assertEquals(Hashing.file(receipt), observation.get("run_receipt_file_sha256"));
            assertTrue(refs.contains("receipt-file:sha256:" + Hashing.file(receipt)));
            assertEquals(BehaviorLearningService.OBSERVATION_RECEIPT_CONTRACT,
                    new ObjectMapper().readTree(receipt.toFile()).path("contract").asText());
        }
        assertTrue(Files.isRegularFile(output));
        assertEquals("NOT_RUN", profile.get("independent_validation"));
    }
}