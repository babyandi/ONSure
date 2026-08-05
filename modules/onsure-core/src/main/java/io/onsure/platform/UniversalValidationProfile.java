package io.onsure.platform;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Target-neutral validation plan used by language and runtime packs.
 *
 * <p>The four assurance phases and seven ordered verification groups are
 * deliberately explicit. A component test cannot be promoted to an
 * end-to-end or operational result merely because it passed.
 */
public final class UniversalValidationProfile {
    public static final String CONTRACT = "ONSURE_UNIVERSAL_VALIDATION_PROFILE_V1";

    public enum Phase {
        STRUCTURE_STATIC(1),
        COMPONENT_AND_NEGATIVE(2),
        END_TO_END_LINEAGE(3),
        OPERATIONAL_RESILIENCE(4);

        private final int level;

        Phase(int level) { this.level = level; }
        public int level() { return level; }
    }

    public enum Outcome {
        PASS_NONFINAL,
        FAIL,
        BLOCKED,
        NOT_RUN,
        INCONCLUSIVE
    }

    public enum RequirementKind {
        EXECUTABLE,
        SOURCE_FILE,
        SOURCE_DIRECTORY,
        EXECUTABLE_SOURCE_FILE,
        FONT_FAMILY
    }

    public record EnvironmentRequirement(
            String requirementId,
            RequirementKind kind,
            String value,
            boolean required) {
        public EnvironmentRequirement {
            if (requirementId == null || !requirementId.matches("[a-z0-9][a-z0-9._-]{0,127}")) {
                throw new IllegalArgumentException("ENVIRONMENT_REQUIREMENT_ID_INVALID");
            }
            Objects.requireNonNull(kind, "kind");
            if (value == null || value.isBlank() || value.length() > 512
                    || value.chars().anyMatch(character -> Character.isISOControl(character))) {
                throw new IllegalArgumentException("ENVIRONMENT_REQUIREMENT_VALUE_INVALID:" + requirementId);
            }
            if (kind == RequirementKind.EXECUTABLE && !value.matches("[A-Za-z0-9._+-]{1,128}")) {
                throw new IllegalArgumentException("ENVIRONMENT_EXECUTABLE_INVALID:" + requirementId);
            }
            if (kind != RequirementKind.EXECUTABLE && kind != RequirementKind.FONT_FAMILY) {
                Path relative = Path.of(value).normalize();
                if (relative.isAbsolute() || relative.startsWith("..")) {
                    throw new IllegalArgumentException("ENVIRONMENT_REQUIREMENT_PATH_ESCAPE:" + requirementId);
                }
            }
        }
    }

    public enum VerificationGroup {
        ENVIRONMENT_DEPENDENCY(1),
        STRUCTURE(2),
        VALIDATOR_META(3),
        STAGE_FUNCTIONAL(4),
        CONNECTED_E2E(5),
        EVIDENCE_DECISION(6),
        OPERATIONS_RECOVERY(7);

        private final int order;

        VerificationGroup(int order) { this.order = order; }
        public int order() { return order; }
    }

    public enum StepKind {
        ENVIRONMENT_PREFLIGHT(VerificationGroup.ENVIRONMENT_DEPENDENCY),
        VALIDATOR_META_CHECK(VerificationGroup.VALIDATOR_META),
        INVENTORY(VerificationGroup.STRUCTURE),
        STATIC_ANALYSIS(VerificationGroup.STRUCTURE),
        BUILD(VerificationGroup.STAGE_FUNCTIONAL),
        UNIT_TEST(VerificationGroup.STAGE_FUNCTIONAL),
        NEGATIVE_TEST(VerificationGroup.STAGE_FUNCTIONAL),
        RETRY_TEST(VerificationGroup.STAGE_FUNCTIONAL),
        BLOCKING_TEST(VerificationGroup.STAGE_FUNCTIONAL),
        INTEGRATION_TEST(VerificationGroup.CONNECTED_E2E),
        E2E_REQUEST_FLOW(VerificationGroup.CONNECTED_E2E),
        E2E_RENDER_OR_PRODUCE(VerificationGroup.CONNECTED_E2E),
        E2E_ARTIFACT_READBACK(VerificationGroup.CONNECTED_E2E),
        E2E_TESTER_CHECK(VerificationGroup.CONNECTED_E2E),
        E2E_AUDIT_CHECK(VerificationGroup.CONNECTED_E2E),
        E2E_EXPOSURE_DECISION(VerificationGroup.CONNECTED_E2E),
        API_CONTRACT(VerificationGroup.STAGE_FUNCTIONAL),
        WORKFLOW_LINEAGE(VerificationGroup.CONNECTED_E2E),
        DATABASE_MIGRATION(VerificationGroup.STAGE_FUNCTIONAL),
        PACKAGE(VerificationGroup.STAGE_FUNCTIONAL),
        PERFORMANCE(VerificationGroup.OPERATIONS_RECOVERY),
        RECOVERY(VerificationGroup.OPERATIONS_RECOVERY),
        INTERRUPTION_TEST(VerificationGroup.OPERATIONS_RECOVERY),
        RESUME_TEST(VerificationGroup.OPERATIONS_RECOVERY),
        ROLLBACK_TEST(VerificationGroup.OPERATIONS_RECOVERY),
        RERUN_TEST(VerificationGroup.OPERATIONS_RECOVERY),
        EVIDENCE_VERIFICATION(VerificationGroup.EVIDENCE_DECISION);

        private final VerificationGroup group;

        StepKind(VerificationGroup group) { this.group = group; }
        public VerificationGroup group() { return group; }
    }

    public record Step(
            String stepId,
            Phase phase,
            StepKind kind,
            boolean required,
            List<String> command,
            Path workingDirectory,
            Duration timeout,
            List<String> dependsOn) {
        public Step {
            if (stepId == null || !stepId.matches("[a-z0-9][a-z0-9._-]{0,127}")) {
                throw new IllegalArgumentException("VALIDATION_STEP_ID_INVALID");
            }
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(kind, "kind");
            command = command == null ? List.of() : List.copyOf(command);
            if (command.stream().anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException("VALIDATION_STEP_COMMAND_INVALID:" + stepId);
            }
            workingDirectory = workingDirectory == null ? Path.of("") : workingDirectory.normalize();
            if (workingDirectory.isAbsolute() || workingDirectory.startsWith("..")) {
                throw new IllegalArgumentException("VALIDATION_STEP_WORKDIR_ESCAPE:" + stepId);
            }
            timeout = timeout == null ? Duration.ofMinutes(10) : timeout;
            if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofHours(2)) > 0) {
                throw new IllegalArgumentException("VALIDATION_STEP_TIMEOUT_INVALID:" + stepId);
            }
            dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        }

        public boolean executable() { return !command.isEmpty(); }
    }

    public record Profile(
            String profileId,
            Path sourceRoot,
            Set<String> technologies,
            List<EnvironmentRequirement> environmentRequirements,
            List<Step> steps,
            Map<Phase, String> notRunReasons) {
        public Profile {
            if (profileId == null || !profileId.matches("[A-Za-z0-9._-]{1,128}")) {
                throw new IllegalArgumentException("VALIDATION_PROFILE_ID_INVALID");
            }
            sourceRoot = Objects.requireNonNull(sourceRoot, "sourceRoot").toAbsolutePath().normalize();
            technologies = technologies == null
                    ? Set.of() : Set.copyOf(new LinkedHashSet<>(technologies));
            environmentRequirements = environmentRequirements == null
                    ? List.of() : List.copyOf(environmentRequirements);
            Set<String> requirementIds = new LinkedHashSet<>();
            for (EnvironmentRequirement requirement : environmentRequirements) {
                if (!requirementIds.add(requirement.requirementId())) {
                    throw new IllegalArgumentException(
                            "ENVIRONMENT_REQUIREMENT_ID_DUPLICATED:" + requirement.requirementId());
                }
            }
            steps = steps == null ? List.of() : List.copyOf(steps);
            Set<String> ids = new LinkedHashSet<>();
            Map<String, Step> byId = new LinkedHashMap<>();
            for (Step step : steps) {
                if (!ids.add(step.stepId())) {
                    throw new IllegalArgumentException("VALIDATION_STEP_ID_DUPLICATED:" + step.stepId());
                }
                byId.put(step.stepId(), step);
            }
            for (Step step : steps) {
                for (String dependency : step.dependsOn()) {
                    if (!ids.contains(dependency)) {
                        throw new IllegalArgumentException(
                                "VALIDATION_STEP_DEPENDENCY_UNKNOWN:" + step.stepId() + ":" + dependency);
                    }
                    Step dependencyStep = byId.get(dependency);
                    if (dependencyStep != null
                            && dependencyStep.kind().group().order() > step.kind().group().order()) {
                        throw new IllegalArgumentException(
                                "VALIDATION_STEP_FORWARD_DEPENDENCY:" + step.stepId() + ":" + dependency);
                    }
                }
                int group = step.kind().group().order();
                // Repository structure and static source integrity are intentionally runnable even
                // when external tools are unavailable. Later groups remain strictly gated.
                if (step.required() && group > VerificationGroup.STRUCTURE.order()
                        && !hasDependencyInGroup(step, group - 1, byId, new LinkedHashSet<>())) {
                    throw new IllegalArgumentException(
                            "VALIDATION_STEP_PREVIOUS_GROUP_GATE_MISSING:" + step.stepId());
                }
            }
            int previousGroup = 0;
            for (Step step : steps) {
                int group = step.kind().group().order();
                if (group < previousGroup) {
                    throw new IllegalArgumentException("VALIDATION_STEP_GROUP_ORDER_INVALID:" + step.stepId());
                }
                previousGroup = group;
            }
            EnumMap<Phase, String> reasons = new EnumMap<>(Phase.class);
            if (notRunReasons != null) reasons.putAll(notRunReasons);
            for (Phase phase : Phase.values()) {
                boolean planned = steps.stream().anyMatch(step -> step.phase() == phase);
                if (!planned) reasons.putIfAbsent(phase, "NO_APPLICABLE_STEP_DISCOVERED");
            }
            notRunReasons = Map.copyOf(reasons);
        }

        public List<Step> steps(Phase phase) {
            return steps.stream().filter(step -> step.phase() == phase)
                    .filter(step -> step.kind() != StepKind.EVIDENCE_VERIFICATION).toList();
        }

        /** Returns all four phase outcomes without manufacturing a PASS for missing evidence. */
        public Map<Phase, Outcome> phaseOutcomes(Map<String, Outcome> executed) {
            Map<String, Outcome> results = executed == null ? Map.of() : Map.copyOf(executed);
            EnumMap<Phase, Outcome> outcomes = new EnumMap<>(Phase.class);
            for (Phase phase : Phase.values()) {
                List<Step> phaseSteps = steps(phase);
                if (phaseSteps.isEmpty()) {
                    outcomes.put(phase, Outcome.NOT_RUN);
                    continue;
                }
                List<Outcome> observed = observedOutcomes(phaseSteps, results);
                outcomes.put(phase, aggregate(observed));
            }
            return Map.copyOf(outcomes);
        }

        /** Returns every ordered group, retaining NOT_RUN for missing execution evidence. */
        public Map<VerificationGroup, Outcome> groupOutcomes(Map<String, Outcome> executed) {
            Map<String, Outcome> results = executed == null ? Map.of() : Map.copyOf(executed);
            EnumMap<VerificationGroup, Outcome> outcomes = new EnumMap<>(VerificationGroup.class);
            for (VerificationGroup group : VerificationGroup.values()) {
                List<Step> groupSteps = steps.stream()
                        .filter(step -> step.kind().group() == group).toList();
                if (groupSteps.isEmpty()) {
                    outcomes.put(group, Outcome.NOT_RUN);
                    continue;
                }
                List<Outcome> observed = observedOutcomes(groupSteps, results);
                outcomes.put(group, aggregate(observed));
            }
            return Map.copyOf(outcomes);
        }

        private static List<Outcome> observedOutcomes(List<Step> steps, Map<String, Outcome> results) {
            List<Outcome> observed = new ArrayList<>();
            for (Step step : steps) {
                Outcome outcome = results.getOrDefault(step.stepId(), Outcome.NOT_RUN);
                if (step.required() || outcome != Outcome.NOT_RUN) observed.add(outcome);
            }
            return observed;
        }

        private static boolean hasDependencyInGroup(
                Step step, int requiredGroup, Map<String, Step> byId, Set<String> visited) {
            if (!visited.add(step.stepId())) return false;
            for (String dependencyId : step.dependsOn()) {
                Step dependency = byId.get(dependencyId);
                if (dependency == null) continue;
                if (dependency.kind().group().order() == requiredGroup) return true;
                if (hasDependencyInGroup(
                        dependency, requiredGroup, byId, new LinkedHashSet<>(visited))) return true;
            }
            return false;
        }
    }

    private UniversalValidationProfile() {}

    static Outcome aggregate(List<Outcome> values) {
        if (values.stream().anyMatch(value -> value == Outcome.FAIL)) return Outcome.FAIL;
        if (values.stream().anyMatch(value -> value == Outcome.BLOCKED)) return Outcome.BLOCKED;
        if (values.stream().anyMatch(value -> value == Outcome.NOT_RUN)) return Outcome.NOT_RUN;
        if (values.stream().anyMatch(value -> value == Outcome.INCONCLUSIVE)) return Outcome.INCONCLUSIVE;
        return values.isEmpty() ? Outcome.NOT_RUN : Outcome.PASS_NONFINAL;
    }
}
