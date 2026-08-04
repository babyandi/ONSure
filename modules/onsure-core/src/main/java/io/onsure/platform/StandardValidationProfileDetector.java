package io.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.onsure.platform.UniversalValidationProfile.Phase;
import io.onsure.platform.UniversalValidationProfile.Profile;
import io.onsure.platform.UniversalValidationProfile.Step;
import io.onsure.platform.UniversalValidationProfile.StepKind;
import io.onsure.platform.UniversalValidationProfile.EnvironmentRequirement;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Detects a conservative validation profile without requiring target-owned ONSure files. */
public final class StandardValidationProfileDetector {
    private static final long MAX_DETECTION_CONFIG_BYTES = 5L * 1024 * 1024;
    private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(20);
    private static final Duration TEST_TIMEOUT = Duration.ofMinutes(15);
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<ValidationPack> packs;

    public StandardValidationProfileDetector() {
        this(installedPacks());
    }

    public StandardValidationProfileDetector(List<ValidationPack> packs) {
        this.packs = validatePacks(packs);
    }

    public Profile detect(String profileId, Path sourceRoot) throws Exception {
        Path root = requireRoot(sourceRoot);
        Set<String> technologies = new LinkedHashSet<>();
        List<Step> functional = new ArrayList<>();
        List<Step> endToEnd = new ArrayList<>();
        List<Step> operations = new ArrayList<>();
        List<EnvironmentRequirement> environmentRequirements = new ArrayList<>();
        EnumMap<Phase, String> notRun = new EnumMap<>(Phase.class);

        Step preflight = step("environment.preflight", Phase.STRUCTURE_STATIC,
                StepKind.ENVIRONMENT_PREFLIGHT, true, List.of(), Duration.ofMinutes(2), List.of());
        Step inventory = step("structure.inventory", Phase.STRUCTURE_STATIC, StepKind.INVENTORY,
                true, List.of(), Duration.ofMinutes(2), List.of(preflight.stepId()));
        Step meta = step("validator.meta-check", Phase.STRUCTURE_STATIC, StepKind.VALIDATOR_META_CHECK,
                true, List.of(), Duration.ofMinutes(2), List.of(inventory.stepId()));

        if (file(root, "pom.xml")) {
            technologies.add("JAVA");
            technologies.add("MAVEN");
            functional.add(step("maven.clean-verify", Phase.COMPONENT_AND_NEGATIVE, StepKind.BUILD,
                    true, List.of("mvn", "-B", "-ntp", "-o", "clean", "verify"), BUILD_TIMEOUT,
                    List.of(meta.stepId())));
            String pom = readDetectionConfig(root.resolve("pom.xml"));
            if (pom.contains("maven-failsafe-plugin")) {
                endToEnd.add(step("maven.integration", Phase.END_TO_END_LINEAGE, StepKind.INTEGRATION_TEST,
                        true, List.of("mvn", "-B", "-ntp", "-o", "verify"), BUILD_TIMEOUT,
                        List.of("maven.clean-verify")));
            }
        } else if (file(root, "gradlew") && (file(root, "build.gradle") || file(root, "build.gradle.kts"))) {
            technologies.add("JAVA");
            technologies.add("GRADLE");
            functional.add(step("gradle.clean-test", Phase.COMPONENT_AND_NEGATIVE, StepKind.BUILD,
                    true, List.of("bash", "gradlew", "--offline", "clean", "test"), BUILD_TIMEOUT,
                    List.of(meta.stepId())));
        }

        boolean python = file(root, "pyproject.toml") || file(root, "pytest.ini")
                || file(root, "requirements.txt") || directory(root, "tests");
        if (python) {
            technologies.add("PYTHON");
            boolean pytest = file(root, "pytest.ini") || contains(root.resolve("pyproject.toml"), "pytest")
                    || contains(root.resolve("requirements.txt"), "pytest");
            List<String> command = pytest
                    ? List.of("python3", "-m", "pytest", "-q")
                    : List.of("python3", "-m", "unittest", "discover", "-s", "tests");
            functional.add(step("python.tests", Phase.COMPONENT_AND_NEGATIVE, StepKind.UNIT_TEST,
                    true, command, TEST_TIMEOUT, List.of(meta.stepId())));
            if (directory(root, "tests/integration")) {
                List<String> integrationCommand = pytest
                        ? List.of("python3", "-m", "pytest", "-q", "tests/integration")
                        : List.of("python3", "-m", "unittest", "discover", "-s", "tests/integration");
                endToEnd.add(step("python.integration", Phase.END_TO_END_LINEAGE, StepKind.INTEGRATION_TEST,
                        true, integrationCommand,
                        TEST_TIMEOUT, List.of("python.tests")));
            }
        }

        if (file(root, "package.json")) {
            technologies.add("NODE");
            JsonNode packageJson = mapper.readTree(readDetectionConfig(root.resolve("package.json")));
            JsonNode scripts = packageJson.path("scripts");
            boolean dependencies = packageJson.path("dependencies").size() > 0
                    || packageJson.path("devDependencies").size() > 0
                    || packageJson.path("optionalDependencies").size() > 0;
            String nodePreparation = null;
            if (dependencies) {
                environmentRequirements.add(new EnvironmentRequirement(
                        "node.lockfile", UniversalValidationProfile.RequirementKind.SOURCE_FILE,
                        "package-lock.json", true));
                nodePreparation = "node.dependencies";
                functional.add(step(nodePreparation, Phase.COMPONENT_AND_NEGATIVE, StepKind.BUILD,
                        true, List.of("npm", "--offline", "ci", "--ignore-scripts"), BUILD_TIMEOUT,
                        List.of(meta.stepId())));
            }
            if (scripts.hasNonNull("test")) {
                functional.add(step("node.tests", Phase.COMPONENT_AND_NEGATIVE, StepKind.UNIT_TEST,
                        true, List.of("npm", "--offline", "test"), TEST_TIMEOUT,
                        nodePreparation == null ? List.of(meta.stepId()) : List.of(nodePreparation)));
            }
            if (scripts.hasNonNull("test:integration")) {
                endToEnd.add(step("node.integration", Phase.END_TO_END_LINEAGE, StepKind.INTEGRATION_TEST,
                        true, List.of("npm", "--offline", "run", "test:integration"), TEST_TIMEOUT,
                        scripts.hasNonNull("test") ? List.of("node.tests") : List.of(meta.stepId())));
            }
            if (scripts.hasNonNull("build")) {
                functional.add(step("node.build", Phase.COMPONENT_AND_NEGATIVE, StepKind.BUILD,
                        true, List.of("npm", "--offline", "run", "build"), BUILD_TIMEOUT,
                        nodePreparation == null ? List.of(meta.stepId()) : List.of(nodePreparation)));
            }
        }

        if (firstFile(root, "openapi.yaml", "openapi.yml", "openapi.json",
                "contracts/openapi/onsure-local-api.v1.json",
                "contracts/openapi/onsure-llm-gateway.v1.json") != null) {
            technologies.add("OPENAPI");
            functional.add(step("openapi.contract", Phase.COMPONENT_AND_NEGATIVE, StepKind.API_CONTRACT,
                    true, List.of(), Duration.ofMinutes(2), List.of(meta.stepId())));
        }

        if (directory(root, "db/migration") || directory(root, "src/main/resources/db/migration")
                || directory(root, "migrations")) {
            technologies.add("DATABASE_MIGRATIONS");
            functional.add(step("database.migration-static", Phase.COMPONENT_AND_NEGATIVE,
                    StepKind.DATABASE_MIGRATION, true, List.of(), Duration.ofMinutes(2),
                    List.of(meta.stepId())));
            notRun.put(Phase.OPERATIONAL_RESILIENCE,
                    "DATABASE_RUNTIME_AND_APPROVED_SYNTHETIC_CONNECTION_NOT_CONFIGURED");
        }

        for (ValidationPack pack : packs) {
            ValidationPack.Contribution contribution = pack.detect(root);
            if (contribution == null) throw new IllegalArgumentException("VALIDATION_PACK_RESULT_REQUIRED:" + pack.id());
            technologies.addAll(contribution.technologies());
            environmentRequirements.addAll(contribution.environmentRequirements());
            for (Step contributed : contribution.steps()) {
                validateContribution(pack, contributed);
                switch (contributed.kind().group()) {
                    case STAGE_FUNCTIONAL -> functional.add(contributed);
                    case CONNECTED_E2E -> endToEnd.add(contributed);
                    case OPERATIONS_RECOVERY -> operations.add(contributed);
                    default -> throw new IllegalArgumentException(
                            "VALIDATION_PACK_RESERVED_GROUP:" + pack.id() + ":" + contributed.stepId());
                }
            }
        }

        if (endToEnd.isEmpty()) {
            notRun.put(Phase.END_TO_END_LINEAGE, "END_TO_END_ENTRYPOINT_NOT_DISCOVERED");
        }
        if (operations.isEmpty()) {
            notRun.putIfAbsent(Phase.OPERATIONAL_RESILIENCE,
                    "OPERATIONAL_PROFILE_AND_ISOLATED_RUNTIME_NOT_CONFIGURED");
        }
        addMissingFunctionalPathChecks(functional, meta.stepId());
        List<String> functionalGate = functional.stream().filter(Step::required).map(Step::stepId).toList();
        addMissingEndToEndChecks(endToEnd, functionalGate);
        addMissingOperationalChecks(operations, "evidence.verify");
        List<String> evidenceDependencies = new ArrayList<>();
        evidenceDependencies.add(meta.stepId());
        functional.stream().filter(Step::required).map(Step::stepId).forEach(evidenceDependencies::add);
        endToEnd.stream().filter(Step::required).map(Step::stepId).forEach(evidenceDependencies::add);
        Step evidence = step("evidence.verify", Phase.END_TO_END_LINEAGE,
                StepKind.EVIDENCE_VERIFICATION, true, List.of(), Duration.ofMinutes(2), evidenceDependencies);
        List<Step> steps = new ArrayList<>();
        steps.add(preflight);
        steps.add(inventory);
        steps.add(meta);
        steps.addAll(functional);
        steps.addAll(endToEnd);
        steps.add(evidence);
        steps.addAll(operations);
        return new Profile(profileId, root, technologies, environmentRequirements, steps, notRun);
    }

    private static Step step(String id, Phase phase, StepKind kind, boolean required,
            List<String> command, Duration timeout, List<String> dependencies) {
        return new Step(id, phase, kind, required, command, Path.of(""), timeout, dependencies);
    }

    private static Path requireRoot(Path sourceRoot) {
        if (sourceRoot == null) throw new IllegalArgumentException("VALIDATION_SOURCE_ROOT_REQUIRED");
        Path root = sourceRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
            throw new IllegalArgumentException("VALIDATION_SOURCE_ROOT_INVALID");
        }
        return root;
    }

    private static boolean file(Path root, String relative) {
        Path value = root.resolve(relative).normalize();
        return value.startsWith(root) && Files.isRegularFile(value, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(value);
    }

    private static boolean directory(Path root, String relative) {
        Path value = root.resolve(relative).normalize();
        return value.startsWith(root) && Files.isDirectory(value, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(value);
    }

    private static boolean contains(Path file, String token) {
        try { return Files.isRegularFile(file) && readDetectionConfig(file).contains(token); }
        catch (Exception ignored) { return false; }
    }

    private static String readDetectionConfig(Path file) throws Exception {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)
                || Files.size(file) > MAX_DETECTION_CONFIG_BYTES) {
            throw new IllegalArgumentException("VALIDATION_CONFIG_INVALID_OR_TOO_LARGE:" + file.getFileName());
        }
        return Files.readString(file);
    }

    private static Path firstFile(Path root, String... candidates) {
        for (String candidate : candidates) if (file(root, candidate)) return root.resolve(candidate);
        return null;
    }

    private static List<ValidationPack> validatePacks(List<ValidationPack> values) {
        if (values == null) return List.of();
        List<ValidationPack> result = List.copyOf(values);
        Set<String> ids = new LinkedHashSet<>();
        for (ValidationPack pack : result) {
            if (pack == null || pack.id() == null || !pack.id().matches("[a-z][a-z0-9-]{0,63}")) {
                throw new IllegalArgumentException("VALIDATION_PACK_ID_INVALID");
            }
            if (!ids.add(pack.id())) throw new IllegalArgumentException("VALIDATION_PACK_ID_DUPLICATED:" + pack.id());
        }
        return result;
    }

    private static List<ValidationPack> installedPacks() {
        List<ValidationPack> discovered = new ArrayList<>();
        java.util.ServiceLoader.load(ValidationPack.class).forEach(discovered::add);
        discovered.sort(java.util.Comparator.comparing(ValidationPack::id));
        return List.copyOf(discovered);
    }

    private static void validateContribution(ValidationPack pack, Step step) {
        if (step == null || !step.stepId().startsWith(pack.id() + ".")) {
            throw new IllegalArgumentException("VALIDATION_PACK_STEP_PREFIX_INVALID:" + pack.id());
        }
    }

    private static void addMissingFunctionalPathChecks(List<Step> functional, String metaStepId) {
        addMissingKind(functional, metaStepId, StepKind.NEGATIVE_TEST,
                "functional.negative-paths");
        addMissingKind(functional, metaStepId, StepKind.RETRY_TEST,
                "functional.retry-paths");
        addMissingKind(functional, metaStepId, StepKind.BLOCKING_TEST,
                "functional.blocking-paths");
    }

    private static void addMissingEndToEndChecks(List<Step> endToEnd, List<String> functionalGate) {
        addMissingKind(endToEnd, functionalGate, StepKind.E2E_REQUEST_FLOW, "e2e.request-flow");
        addMissingKind(endToEnd, functionalGate, StepKind.E2E_RENDER_OR_PRODUCE, "e2e.render-or-produce");
        addMissingKind(endToEnd, functionalGate, StepKind.E2E_ARTIFACT_READBACK, "e2e.artifact-readback");
        addMissingKind(endToEnd, functionalGate, StepKind.E2E_TESTER_CHECK, "e2e.tester-check");
        addMissingKind(endToEnd, functionalGate, StepKind.E2E_AUDIT_CHECK, "e2e.audit-check");
        addMissingKind(endToEnd, functionalGate, StepKind.E2E_EXPOSURE_DECISION, "e2e.exposure-decision");
    }

    private static void addMissingOperationalChecks(List<Step> operations, String evidenceStepId) {
        addMissingKind(operations, evidenceStepId, StepKind.INTERRUPTION_TEST, "operations.interruption");
        addMissingKind(operations, evidenceStepId, StepKind.RESUME_TEST, "operations.resume");
        addMissingKind(operations, evidenceStepId, StepKind.ROLLBACK_TEST, "operations.rollback");
        addMissingKind(operations, evidenceStepId, StepKind.RERUN_TEST, "operations.rerun");
    }

    private static void addMissingKind(List<Step> functional, String metaStepId, StepKind kind,
            String id) {
        addMissingKind(functional, List.of(metaStepId), kind, id);
    }

    private static void addMissingKind(List<Step> functional, List<String> dependencies, StepKind kind,
            String id) {
        if (functional.stream().noneMatch(step -> step.kind() == kind)) {
            Phase phase = switch (kind.group()) {
                case STAGE_FUNCTIONAL -> Phase.COMPONENT_AND_NEGATIVE;
                case CONNECTED_E2E, EVIDENCE_DECISION -> Phase.END_TO_END_LINEAGE;
                case OPERATIONS_RECOVERY -> Phase.OPERATIONAL_RESILIENCE;
                default -> Phase.STRUCTURE_STATIC;
            };
            functional.add(step(id, phase, kind, true, List.of(),
                    Duration.ofMinutes(2), dependencies));
        }
    }
}
