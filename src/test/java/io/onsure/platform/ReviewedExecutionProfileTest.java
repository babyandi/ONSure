package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReviewedExecutionProfileTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void bindsReviewedStepsAndReceiptToExactExternalSourceAndProfile() throws Exception {
        Path source = Files.createDirectory(temp.resolve("source"));
        Files.createDirectories(source.resolve("tests"));
        Files.writeString(source.resolve("tests/negative.py"), "print('negative pass')\n");
        Path profileFile = writeProfile(source, List.of(step(
                "reviewed.negative", "COMPONENT_AND_NEGATIVE", "NEGATIVE_TEST",
                List.of("python3", "tests/negative.py"), List.of("validator.meta-check"))));

        ReviewedExecutionProfile.Loaded loaded = ReviewedExecutionProfile.load(profileFile, source);
        var profile = new StandardValidationProfileDetector(List.of(loaded.pack()))
                .detect("external", source);
        var runner = new UniversalValidationRunner((step, root) ->
                new UniversalValidationRunner.StepExecution(
                        UniversalValidationProfile.Outcome.PASS_NONFINAL, 0,
                        "reviewed pass", false, "TEST_EXECUTOR"));

        var result = runner.run(profile, temp.resolve("run"), null, loaded);

        assertTrue(profile.steps().stream().anyMatch(step -> step.stepId().equals("reviewed.negative")));
        var receipt = mapper.readTree(result.receiptFile().toFile());
        assertEquals(ReviewedExecutionProfile.CONTRACT,
                receipt.at("/external_execution_profile/contract").asText());
        assertEquals(ReviewedExecutionProfile.REVIEW_STATE,
                receipt.at("/external_execution_profile/review_state").asText());
        assertEquals(loaded.sourceSha256(),
                receipt.at("/external_execution_profile/source_sha256").asText());
        assertEquals(loaded.sourceFileSha256(),
                receipt.at("/external_execution_profile/source_file_sha256").asText());
        assertTrue(Files.notExists(source.resolve(".onsure")));
    }

    @Test
    void rejectsTargetDigestDriftAndUnsafeInterpreterEvaluation() throws Exception {
        Path source = Files.createDirectory(temp.resolve("unsafe-source"));
        Files.writeString(source.resolve("runner.py"), "print('safe')\n");
        Path profileFile = writeProfile(source, List.of(step(
                "reviewed.negative", "COMPONENT_AND_NEGATIVE", "NEGATIVE_TEST",
                List.of("python3", "-c", "print('unsafe')"), List.of("validator.meta-check"))));

        IllegalArgumentException unsafe = assertThrows(IllegalArgumentException.class,
                () -> ReviewedExecutionProfile.load(profileFile, source));
        assertTrue(unsafe.getMessage().startsWith("EXECUTION_PROFILE_COMMAND_UNSAFE"));

        Path safeProfile = writeProfile(source, List.of(step(
                "reviewed.retry", "COMPONENT_AND_NEGATIVE", "RETRY_TEST",
                List.of("python3", "runner.py"), List.of("validator.meta-check"))));
        Files.writeString(source.resolve("drift.txt"), "changed\n");
        IllegalArgumentException drift = assertThrows(IllegalArgumentException.class,
                () -> ReviewedExecutionProfile.load(safeProfile, source));
        assertEquals("EXECUTION_PROFILE_SOURCE_DIGEST_MISMATCH", drift.getMessage());
    }

    @Test
    void refusesChangedProfileAfterReviewBeforeExecution() throws Exception {
        Path source = Files.createDirectory(temp.resolve("changed-profile-source"));
        Files.writeString(source.resolve("runner.js"), "console.log('pass');\n");
        Path profileFile = writeProfile(source, List.of(step(
                "reviewed.blocking", "COMPONENT_AND_NEGATIVE", "BLOCKING_TEST",
                List.of("node", "runner.js"), List.of("validator.meta-check"))));
        ReviewedExecutionProfile.Loaded loaded = ReviewedExecutionProfile.load(profileFile, source);
        var profile = new StandardValidationProfileDetector(List.of(loaded.pack()))
                .detect("changed-profile", source);
        Files.writeString(profileFile, Files.readString(profileFile) + "\n");

        IllegalStateException changed = assertThrows(IllegalStateException.class,
                () -> new UniversalValidationRunner((step, root) ->
                        new UniversalValidationRunner.StepExecution(
                                UniversalValidationProfile.Outcome.PASS_NONFINAL, 0,
                                "pass", false, "TEST_EXECUTOR"))
                        .run(profile, temp.resolve("changed-profile-run"), null, loaded));
        assertEquals("EXECUTION_PROFILE_SOURCE_FILE_CHANGED", changed.getMessage());
    }

    @Test
    void rejectsOptionalStepsAndForwardReviewedDependencies() throws Exception {
        Path source = Files.createDirectory(temp.resolve("required-source"));
        Files.writeString(source.resolve("runner.py"), "print('pass')\n");
        Map<String, Object> optional = new java.util.LinkedHashMap<>(step(
                "reviewed.optional", "COMPONENT_AND_NEGATIVE", "NEGATIVE_TEST",
                List.of("python3", "runner.py"), List.of("validator.meta-check")));
        optional.put("required", false);
        Path optionalProfile = writeProfile(source, List.of(optional));
        assertEquals("EXECUTION_PROFILE_STEP_STRUCTURE_INVALID", assertThrows(
                IllegalArgumentException.class,
                () -> ReviewedExecutionProfile.load(optionalProfile, source)).getMessage());

        Path forwardProfile = writeProfile(source, List.of(
                step("reviewed.first", "COMPONENT_AND_NEGATIVE", "NEGATIVE_TEST",
                        List.of("python3", "runner.py"), List.of("reviewed.second")),
                step("reviewed.second", "COMPONENT_AND_NEGATIVE", "RETRY_TEST",
                        List.of("python3", "runner.py"), List.of("validator.meta-check"))));
        assertEquals("EXECUTION_PROFILE_REVIEWED_DEPENDENCY_NOT_PRIOR:reviewed.first", assertThrows(
                IllegalArgumentException.class,
                () -> ReviewedExecutionProfile.load(forwardProfile, source)).getMessage());
    }

    private Path writeProfile(Path source, List<Map<String, Object>> steps) throws Exception {
        Path file = temp.resolve("profile-" + Files.list(temp).count() + ".json");
        Map<String, Object> profile = Map.of(
                "contract", ReviewedExecutionProfile.CONTRACT,
                "profile_id", "reviewed-external",
                "source_sha256", Hashing.tree(source, Hashing.sourceFiles(source)),
                "review_state", ReviewedExecutionProfile.REVIEW_STATE,
                "technologies", List.of("REVIEWED_EXTERNAL_WORKFLOW"),
                "steps", steps);
        mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), profile);
        return file;
    }

    private static Map<String, Object> step(
            String id, String phase, String kind, List<String> command, List<String> dependencies) {
        return Map.of(
                "step_id", id,
                "phase", phase,
                "kind", kind,
                "command", command,
                "working_directory", "",
                "timeout_seconds", 120,
                "required", true,
                "depends_on", dependencies);
    }
}
