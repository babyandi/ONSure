package io.onsure.platform;

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
    private final List<ValidationPack> packs;

    public StandardValidationProfileDetector() {
        this(installedPacks());
    }

    public StandardValidationProfileDetector(List<ValidationPack> packs) {
        List<ValidationPack> combined = new ArrayList<>(standardPacks());
        if (packs != null) combined.addAll(packs);
        this.packs = validatePacks(combined);
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
                    technologies.contains("DATABASE_MIGRATIONS")
                            ? "DATABASE_RUNTIME_AND_APPROVED_SYNTHETIC_CONNECTION_NOT_CONFIGURED"
                            : "OPERATIONAL_PROFILE_AND_ISOLATED_RUNTIME_NOT_CONFIGURED");
        }
        addMissingFunctionalPathChecks(functional, meta.stepId());
        List<String> functionalGate = functional.stream().filter(Step::required).map(Step::stepId).toList();
        addMissingEndToEndChecks(endToEnd, functionalGate);
        bindLineageToConnectedFacets(endToEnd);
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

    private static List<ValidationPack> standardPacks() {
        return List.of(
                new GradleValidationPack(), new MavenValidationPack(), new NodeValidationPack(),
                new OpenApiValidationPack(), new PostgresqlValidationPack(), new PythonValidationPack());
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
        List<String> connectedFacets = endToEnd.stream()
                .filter(step -> Set.of(
                        StepKind.E2E_REQUEST_FLOW, StepKind.E2E_RENDER_OR_PRODUCE,
                        StepKind.E2E_ARTIFACT_READBACK, StepKind.E2E_TESTER_CHECK,
                        StepKind.E2E_AUDIT_CHECK, StepKind.E2E_EXPOSURE_DECISION).contains(step.kind()))
                .map(Step::stepId).toList();
        addMissingKind(endToEnd, connectedFacets, StepKind.WORKFLOW_LINEAGE, "e2e.workflow-lineage");
    }

    private static void addMissingOperationalChecks(List<Step> operations, String evidenceStepId) {
        addMissingKind(operations, evidenceStepId, StepKind.INTERRUPTION_TEST, "operations.interruption");
        addMissingKind(operations, evidenceStepId, StepKind.RESUME_TEST, "operations.resume");
        addMissingKind(operations, evidenceStepId, StepKind.ROLLBACK_TEST, "operations.rollback");
        addMissingKind(operations, evidenceStepId, StepKind.RERUN_TEST, "operations.rerun");
    }

    private static void bindLineageToConnectedFacets(List<Step> endToEnd) {
        Set<StepKind> facets = Set.of(
                StepKind.E2E_REQUEST_FLOW, StepKind.E2E_RENDER_OR_PRODUCE,
                StepKind.E2E_ARTIFACT_READBACK, StepKind.E2E_TESTER_CHECK,
                StepKind.E2E_AUDIT_CHECK, StepKind.E2E_EXPOSURE_DECISION);
        List<String> facetIds = endToEnd.stream().filter(step -> facets.contains(step.kind()))
                .filter(Step::required).map(Step::stepId).toList();
        for (int index = 0; index < endToEnd.size(); index++) {
            Step step = endToEnd.get(index);
            if (step.kind() != StepKind.WORKFLOW_LINEAGE) continue;
            LinkedHashSet<String> dependencies = new LinkedHashSet<>(step.dependsOn());
            dependencies.addAll(facetIds);
            endToEnd.set(index, new Step(step.stepId(), step.phase(), step.kind(), step.required(),
                    step.command(), step.workingDirectory(), step.timeout(), List.copyOf(dependencies)));
        }
        endToEnd.sort(java.util.Comparator.comparingInt(
                step -> step.kind() == StepKind.WORKFLOW_LINEAGE ? 1 : 0));
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
