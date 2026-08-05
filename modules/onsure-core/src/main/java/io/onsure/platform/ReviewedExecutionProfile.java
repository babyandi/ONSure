package io.onsure.platform;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.onsure.platform.UniversalValidationProfile.Phase;
import io.onsure.platform.UniversalValidationProfile.Step;
import io.onsure.platform.UniversalValidationProfile.StepKind;
import io.onsure.platform.UniversalValidationProfile.VerificationGroup;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict, source-bound execution mapping supplied outside a read-only target repository. */
final class ReviewedExecutionProfile {
    static final String CONTRACT = "ONSURE_REVIEWED_EXECUTION_PROFILE_V1";
    static final String REVIEW_STATE = "REVIEWED_LOCAL_NONFINAL";
    private static final long MAX_BYTES = 1024L * 1024L;
    private static final int MAX_STEPS = 128;
    private static final int MAX_COMMAND_ARGUMENTS = 128;
    private static final int MAX_ARGUMENT_LENGTH = 4096;
    private static final Set<String> ROOT_FIELDS = Set.of(
            "contract", "profile_id", "source_sha256", "review_state", "technologies", "steps");
    private static final Set<String> STEP_FIELDS = Set.of(
            "step_id", "phase", "kind", "command", "working_directory",
            "timeout_seconds", "required", "depends_on");
    private static final Set<String> ALLOWED_EXECUTABLES = Set.of("mvn", "npm", "python3", "bash", "node");
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private ReviewedExecutionProfile() {}

    static Loaded load(Path input, Path sourceRoot) throws Exception {
        Path file = requireFile(input);
        Path root = requireSourceRoot(sourceRoot);
        JsonNode document = MAPPER.readTree(Files.readAllBytes(file));
        if (document == null || !document.isObject() || !fieldNames(document).equals(ROOT_FIELDS)) {
            throw new IllegalArgumentException("EXECUTION_PROFILE_STRUCTURE_INVALID");
        }
        if (!CONTRACT.equals(document.path("contract").asText())) {
            throw new IllegalArgumentException("EXECUTION_PROFILE_CONTRACT_INVALID");
        }
        String profileId = document.path("profile_id").asText("");
        if (!profileId.matches("[A-Za-z0-9._-]{1,128}")) {
            throw new IllegalArgumentException("EXECUTION_PROFILE_ID_INVALID");
        }
        if (!REVIEW_STATE.equals(document.path("review_state").asText())) {
            throw new IllegalArgumentException("EXECUTION_PROFILE_REVIEW_STATE_INVALID");
        }
        String sourceSha256 = document.path("source_sha256").asText("");
        if (!sourceSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("EXECUTION_PROFILE_SOURCE_DIGEST_INVALID");
        }
        String actualSourceSha256 = Hashing.tree(root, Hashing.sourceFiles(root));
        if (!sourceSha256.equals(actualSourceSha256)) {
            throw new IllegalArgumentException("EXECUTION_PROFILE_SOURCE_DIGEST_MISMATCH");
        }
        Set<String> technologies = technologies(document.path("technologies"));
        List<Step> steps = steps(document.path("steps"));
        ProfilePack pack = new ProfilePack(root, steps, technologies);
        return new Loaded(profileId, sourceSha256, Hashing.file(file), file, root, pack);
    }

    private static Path requireFile(Path input) throws Exception {
        if (input == null) throw new IllegalArgumentException("EXECUTION_PROFILE_FILE_REQUIRED");
        Path file = input.toAbsolutePath().normalize();
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)
                || Files.size(file) > MAX_BYTES) {
            throw new IllegalArgumentException("EXECUTION_PROFILE_FILE_INVALID");
        }
        return file;
    }

    private static Path requireSourceRoot(Path sourceRoot) {
        if (sourceRoot == null) throw new IllegalArgumentException("EXECUTION_PROFILE_SOURCE_ROOT_REQUIRED");
        Path root = sourceRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
            throw new IllegalArgumentException("EXECUTION_PROFILE_SOURCE_ROOT_INVALID");
        }
        return root;
    }

    private static Set<String> technologies(JsonNode node) {
        if (!node.isArray() || node.isEmpty() || node.size() > 64) {
            throw new IllegalArgumentException("EXECUTION_PROFILE_TECHNOLOGIES_INVALID");
        }
        Set<String> values = new LinkedHashSet<>();
        for (JsonNode value : node) {
            String technology = value.asText("");
            if (!technology.matches("[A-Z][A-Z0-9_]{0,63}") || !values.add(technology)) {
                throw new IllegalArgumentException("EXECUTION_PROFILE_TECHNOLOGY_INVALID:" + technology);
            }
        }
        return Set.copyOf(values);
    }

    private static List<Step> steps(JsonNode node) {
        if (!node.isArray() || node.isEmpty() || node.size() > MAX_STEPS) {
            throw new IllegalArgumentException("EXECUTION_PROFILE_STEPS_INVALID");
        }
        List<Step> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode value : node) {
            if (!value.isObject() || !fieldNames(value).equals(STEP_FIELDS)
                    || !value.path("required").isBoolean() || !value.path("required").asBoolean()
                    || !value.path("timeout_seconds").canConvertToInt()) {
                throw new IllegalArgumentException("EXECUTION_PROFILE_STEP_STRUCTURE_INVALID");
            }
            String id = value.path("step_id").asText("");
            if (!id.matches("reviewed\\.[a-z0-9][a-z0-9._-]{0,118}") || ids.contains(id)) {
                throw new IllegalArgumentException("EXECUTION_PROFILE_STEP_ID_INVALID:" + id);
            }
            Phase phase = enumValue(Phase.class, value.path("phase").asText(""), "PHASE", id);
            StepKind kind = enumValue(StepKind.class, value.path("kind").asText(""), "KIND", id);
            validateKindAndPhase(kind, phase, id);
            List<String> command = stringArray(value.path("command"), 1, MAX_COMMAND_ARGUMENTS,
                    "EXECUTION_PROFILE_COMMAND_INVALID:" + id);
            validateCommand(command, id);
            String workingDirectory = value.path("working_directory").asText("");
            Path workdir = workingDirectory.isBlank() ? Path.of("") : Path.of(workingDirectory).normalize();
            if (workdir.isAbsolute() || workdir.startsWith("..")) {
                throw new IllegalArgumentException("EXECUTION_PROFILE_WORKDIR_ESCAPE:" + id);
            }
            int timeoutSeconds = value.path("timeout_seconds").asInt();
            if (timeoutSeconds < 1 || timeoutSeconds > 7200) {
                throw new IllegalArgumentException("EXECUTION_PROFILE_TIMEOUT_INVALID:" + id);
            }
            List<String> dependencies = stringArray(value.path("depends_on"), 0, MAX_STEPS,
                    "EXECUTION_PROFILE_DEPENDENCIES_INVALID:" + id);
            if (dependencies.stream().anyMatch(dependency ->
                    !dependency.matches("[a-z0-9][a-z0-9._-]{0,127}"))) {
                throw new IllegalArgumentException("EXECUTION_PROFILE_DEPENDENCY_ID_INVALID:" + id);
            }
            if (dependencies.stream().filter(dependency -> dependency.startsWith("reviewed."))
                    .anyMatch(dependency -> !ids.contains(dependency))) {
                throw new IllegalArgumentException("EXECUTION_PROFILE_REVIEWED_DEPENDENCY_NOT_PRIOR:" + id);
            }
            if (new HashSet<>(dependencies).size() != dependencies.size()) {
                throw new IllegalArgumentException("EXECUTION_PROFILE_DEPENDENCY_DUPLICATED:" + id);
            }
            ids.add(id);
            result.add(new Step(id, phase, kind, true, command,
                    workdir, Duration.ofSeconds(timeoutSeconds), dependencies));
        }
        return List.copyOf(result);
    }

    private static void validateKindAndPhase(StepKind kind, Phase phase, String id) {
        VerificationGroup group = kind.group();
        Phase expected = switch (group) {
            case STAGE_FUNCTIONAL -> Phase.COMPONENT_AND_NEGATIVE;
            case CONNECTED_E2E -> Phase.END_TO_END_LINEAGE;
            case OPERATIONS_RECOVERY -> Phase.OPERATIONAL_RESILIENCE;
            default -> null;
        };
        if (expected == null || phase != expected || kind == StepKind.EVIDENCE_VERIFICATION) {
            throw new IllegalArgumentException("EXECUTION_PROFILE_KIND_PHASE_INVALID:" + id);
        }
    }

    private static void validateCommand(List<String> command, String id) {
        String executable = command.get(0);
        if (!ALLOWED_EXECUTABLES.contains(executable)) {
            throw new IllegalArgumentException("EXECUTION_PROFILE_EXECUTABLE_DENIED:" + id);
        }
        if (command.stream().anyMatch(value -> value.length() > MAX_ARGUMENT_LENGTH
                || value.chars().anyMatch(Character::isISOControl)
                || value.equals("..") || value.startsWith("../") || value.contains("/../"))) {
            throw new IllegalArgumentException("EXECUTION_PROFILE_ARGUMENT_INVALID:" + id);
        }
        boolean safe = switch (executable) {
            case "mvn" -> command.contains("-o");
            case "npm" -> command.contains("--offline");
            case "python3" -> pythonCommand(command);
            case "bash" -> relativeScript(command, ".sh")
                    || command.size() >= 3 && "gradlew".equals(command.get(1)) && command.contains("--offline");
            case "node" -> relativeScript(command, ".js");
            default -> false;
        };
        if (!safe) throw new IllegalArgumentException("EXECUTION_PROFILE_COMMAND_UNSAFE:" + id);
    }

    private static boolean pythonCommand(List<String> command) {
        if (command.size() >= 3 && "-m".equals(command.get(1))) {
            return Set.of("pytest", "unittest").contains(command.get(2));
        }
        return relativeScript(command, ".py");
    }

    private static boolean relativeScript(List<String> command, String suffix) {
        if (command.size() < 2) return false;
        String value = command.get(1).replace('\\', '/');
        Path path = Path.of(value).normalize();
        return value.endsWith(suffix) && !path.isAbsolute() && !path.startsWith("..")
                && !value.startsWith("-");
    }

    private static List<String> stringArray(
            JsonNode node, int minimum, int maximum, String reason) {
        if (!node.isArray() || node.size() < minimum || node.size() > maximum) {
            throw new IllegalArgumentException(reason);
        }
        List<String> result = new ArrayList<>();
        for (JsonNode value : node) {
            if (!value.isTextual() || value.asText().isBlank()) throw new IllegalArgumentException(reason);
            result.add(value.asText());
        }
        return List.copyOf(result);
    }

    private static <T extends Enum<T>> T enumValue(
            Class<T> type, String value, String label, String id) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("EXECUTION_PROFILE_" + label + "_INVALID:" + id, invalid);
        }
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> result = new HashSet<>();
        node.fieldNames().forEachRemaining(result::add);
        return Set.copyOf(result);
    }

    record Loaded(
            String profileId,
            String sourceSha256,
            String sourceFileSha256,
            Path sourceFile,
            Path sourceRoot,
            ValidationPack pack) {}

    private record ProfilePack(Path sourceRoot, List<Step> steps, Set<String> technologies)
            implements ValidationPack {
        @Override public String id() { return "reviewed"; }

        @Override
        public Contribution detect(Path sourceRoot) throws Exception {
            Path normalized = sourceRoot.toAbsolutePath().normalize();
            if (!this.sourceRoot.equals(normalized)) {
                throw new IllegalArgumentException("EXECUTION_PROFILE_PACK_SOURCE_MISMATCH");
            }
            return new Contribution(technologies, List.of(), steps);
        }
    }
}
