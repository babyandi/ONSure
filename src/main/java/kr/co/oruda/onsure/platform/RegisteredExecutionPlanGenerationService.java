package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;

/** Generates the immutable unsigned plan that a human/external authority may sign. */
public final class RegisteredExecutionPlanGenerationService {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public Map<String, Object> generate(
            String projectId,
            ValidationModel.ValidationTarget target,
            Path programProfileFile,
            int registeredFixtureCount,
            Path outputFile) throws Exception {
        if (!Files.isRegularFile(programProfileFile, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(programProfileFile)) {
            throw new IllegalArgumentException("PROGRAM_PROFILE_FILE_INVALID");
        }
        JsonNode profile = mapper.readTree(programProfileFile.toFile());
        if (!ProgramLearningService.CONTRACT.equals(profile.path("contract").asText())) {
            throw new IllegalArgumentException("PROGRAM_PROFILE_CONTRACT_INVALID");
        }
        if (!projectId.equals(profile.path("project_id").asText())) {
            throw new IllegalStateException("PROGRAM_PROFILE_PROJECT_MISMATCH");
        }
        if (!target.targetId().equals(profile.path("program_id").asText())) {
            throw new IllegalStateException("PROGRAM_PROFILE_TARGET_MISMATCH");
        }
        String profileSource = profile.path("source_baseline").path("source_tree_sha256").asText();
        String currentSource = Hashing.tree(target.sourceRoot());
        if (!currentSource.equals(profileSource)) {
            throw new IllegalStateException("PROGRAM_PROFILE_SOURCE_DRIFT");
        }
        return new ExecutionPlanService().plan(
                target, programProfileFile, registeredFixtureCount, outputFile);
    }
}
