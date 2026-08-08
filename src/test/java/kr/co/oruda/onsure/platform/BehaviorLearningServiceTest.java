package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import kr.co.oruda.onsure.platform.ValidationModel.TargetType;
import kr.co.oruda.onsure.platform.ValidationModel.ValidationTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BehaviorLearningServiceTest {
    @TempDir Path temp;

    private static final class CanaryFixtureAdapter implements TargetAdapter {
        static final String ID = "TEST_CANARY_FIXTURE_ADAPTER_V1";

        @Override public String adapterId() { return ID; }
        @Override public boolean supports(TargetType targetType) { return true; }
        @Override public void validateRegistration(ValidationTarget target) {}
        @Override public Map<String, Object> collectTargetMetadata(ValidationTarget target) { return Map.of(); }

        @Override public List<FixtureDefinition> loadFixtures(ValidationTarget target) throws Exception {
            Path script = target.sourceRoot().resolve("canary.sh");
            Files.writeString(script, "#!/bin/bash\necho 'uid=0(root) gid=0(root) groups=0(root)'\n");
            return List.of(new FixtureDefinition(
                    "canary-scenario", "", "uid=0(root)", "", "CONTAINS",
                    List.of("bash", "canary.sh"),
                    10, Map.of()));
        }
    }

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
        assertEquals(List.of(), profile.get("vulnerable_conditions"));
    }

    @Test
    void vulnerableCanaryOutputIsRecordedAsAVulnerableConditionDistinctFromFailure() throws Exception {
        Path source = temp.resolve("canary-source");
        Files.createDirectories(source);
        ValidationModel.ValidationTarget target = new ValidationModel.ValidationTarget(
                "canary-program", "Canary Program", ValidationModel.TargetType.GENERAL_SOFTWARE,
                source, SourceReferenceBinding.treeReference(source),
                CanaryFixtureAdapter.ID, "ONSURE_DEFAULT_POLICY_V1", "LOCAL_FIXTURE");
        Path output = temp.resolve("canary-behavior-profile.json");

        Map<String, Object> profile = new BehaviorLearningService().learn(
                new BehaviorLearningService.ValidationTargetBundle(target, new CanaryFixtureAdapter()),
                "profile-canary", 2, output);

        @SuppressWarnings("unchecked")
        List<String> vulnerableConditions = (List<String>) profile.get("vulnerable_conditions");
        assertEquals(List.of("canary-scenario:COMMAND_INJECTION_SIGNAL:UID_DISCLOSURE"), vulnerableConditions);
        @SuppressWarnings("unchecked")
        List<String> failureConditions = (List<String>) profile.get("failure_conditions");
        assertEquals(List.of(), failureConditions, "a vulnerable signal is not itself a fixture failure");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> observations = (List<Map<String, Object>>) profile.get("observations");
        for (Map<String, Object> observation : observations) {
            Path receipt = Path.of(observation.get("run_receipt_path").toString());
            assertReceiptHasUidDisclosureSignal(receipt);
        }
    }

    private void assertReceiptHasUidDisclosureSignal(Path receipt) throws Exception {
        var node = new ObjectMapper().readTree(receipt.toFile()).path("vulnerable_signals");
        assertTrue(node.isArray());
        assertEquals(1, node.size());
        assertEquals("COMMAND_INJECTION_SIGNAL:UID_DISCLOSURE", node.get(0).asText());
    }
}