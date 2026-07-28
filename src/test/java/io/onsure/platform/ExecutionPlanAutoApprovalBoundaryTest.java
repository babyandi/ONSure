package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.onsure.platform.ValidationModel.TargetType;
import io.onsure.platform.ValidationModel.ValidationTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExecutionPlanAutoApprovalBoundaryTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void autoApprovalRequiresProcessGateAndTrustedFixtureProfile() throws Exception {
        Path source = temp.resolve("target");
        Files.createDirectories(source);
        Files.writeString(source.resolve("sample.txt"), "sample\n");
        Path profile = temp.resolve("program-profile.json");
        mapper.writeValue(profile.toFile(), Map.of(
                "contract", ProgramLearningService.CONTRACT,
                "profile_id", "profile-001",
                "source_baseline", Map.of("source_tree_sha256", "a".repeat(64)),
                "components", List.of(),
                "dependencies", List.of(),
                "data_flows", List.of(),
                "conflicts", List.of()));
        ExecutionPlanService service = new ExecutionPlanService();
        String previous = System.getProperty(ExecutionPlanService.TRUSTED_FIXTURE_AUTO_APPROVAL_PROPERTY);
        try {
            System.clearProperty(ExecutionPlanService.TRUSTED_FIXTURE_AUTO_APPROVAL_PROPERTY);
            Map<String, Object> disabled = service.plan(
                    target(source, FixtureRegistryStage.TRUSTED_LOCAL_PROFILE),
                    profile, 1, temp.resolve("disabled.json"));
            assertEquals("AWAITING_USER_APPROVAL", approval(disabled).get("state"));
            assertThrows(IllegalStateException.class, () -> service.requireApproved(disabled));

            System.setProperty(ExecutionPlanService.TRUSTED_FIXTURE_AUTO_APPROVAL_PROPERTY, "true");
            Map<String, Object> enabled = service.plan(
                    target(source, FixtureRegistryStage.TRUSTED_LOCAL_PROFILE),
                    profile, 1, temp.resolve("enabled.json"));
            assertEquals("AUTO_APPROVED_DEVELOPMENT_NONFINAL", approval(enabled).get("state"));
            service.requireApproved(enabled);

            Map<String, Object> reviewed = service.plan(
                    target(source, "LOCAL_REVIEWED"),
                    profile, 1, temp.resolve("reviewed.json"));
            assertEquals("AWAITING_USER_APPROVAL", approval(reviewed).get("state"));
        } finally {
            if (previous == null) {
                System.clearProperty(ExecutionPlanService.TRUSTED_FIXTURE_AUTO_APPROVAL_PROPERTY);
            } else {
                System.setProperty(ExecutionPlanService.TRUSTED_FIXTURE_AUTO_APPROVAL_PROPERTY, previous);
            }
        }
    }

    private static ValidationTarget target(Path source, String executionProfile) {
        return new ValidationTarget(
                "target-001", "Target", TargetType.GENERAL_SOFTWARE, source,
                "sha256:" + "a".repeat(64), GenericManifestTargetAdapter.ID,
                "ONSURE_DEFAULT_POLICY_V1", executionProfile);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> approval(Map<String, Object> plan) {
        return (Map<String, Object>) plan.get("approval");
    }
}
