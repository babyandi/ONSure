package io.onsure.platform;

import io.onsure.platform.UniversalValidationProfile.Outcome;
import io.onsure.platform.UniversalValidationProfile.Phase;
import io.onsure.platform.UniversalValidationProfile.StepKind;
import io.onsure.platform.UniversalValidationProfile.VerificationGroup;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Evidence-coverage scorecard that never replaces the fail-closed validation outcome. */
final class ValidationScorecard {
    static final String CONTRACT = "ONSURE_VALIDATION_SCORECARD_V1";
    private static final Map<VerificationGroup, Integer> WEIGHT_BASIS_POINTS = Map.of(
            VerificationGroup.ENVIRONMENT_DEPENDENCY, 1_000,
            VerificationGroup.STRUCTURE, 1_000,
            VerificationGroup.VALIDATOR_META, 1_000,
            VerificationGroup.STAGE_FUNCTIONAL, 2_000,
            VerificationGroup.CONNECTED_E2E, 2_500,
            VerificationGroup.EVIDENCE_DECISION, 1_500,
            VerificationGroup.OPERATIONS_RECOVERY, 1_000);

    private ValidationScorecard() {}

    static Map<String, Object> calculate(
            List<UniversalValidationRunner.StepResult> steps,
            Map<Phase, Outcome> phaseOutcomes,
            Map<VerificationGroup, Outcome> groupOutcomes,
            Outcome overallOutcome) {
        Map<String, Integer> allocations = allocations(steps);
        List<Map<String, Object>> stepScores = new ArrayList<>();
        List<Map<String, Object>> unearned = new ArrayList<>();
        int earnedTotal = 0;
        int requiredCount = 0;
        int passedRequiredCount = 0;
        for (var step : steps) {
            int possible = step.required() ? allocations.getOrDefault(step.stepId(), 0) : 0;
            int earned = step.outcome() == Outcome.PASS_NONFINAL ? possible : 0;
            earnedTotal += earned;
            if (step.required()) {
                requiredCount++;
                if (step.outcome() == Outcome.PASS_NONFINAL) passedRequiredCount++;
                else unearned.add(Map.of(
                        "step_id", step.stepId(), "outcome", step.outcome().name(),
                        "reason", step.reason(), "unearned_points", points(possible)));
            }
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("step_id", step.stepId());
            value.put("phase", step.phase().name());
            value.put("group", step.kind().group().name());
            value.put("kind", step.kind().name());
            value.put("required", step.required());
            value.put("possible_points", points(possible));
            value.put("earned_points", points(earned));
            value.put("outcome", step.outcome().name());
            value.put("reason", step.reason());
            value.put("diagnosis", diagnosis(step.outcome(), step.stepId(), true));
            value.put("improvement_guide", improvement(step.outcome(), step.reason()));
            value.put("output_sha256", step.outputSha256());
            value.put("environment_sha256", step.environmentSha256());
            value.put("duration_millis", Math.max(0L,
                    Duration.between(step.startedAt(), step.completedAt()).toMillis()));
            stepScores.add(Map.copyOf(value));
        }

        List<Map<String, Object>> groups = new ArrayList<>();
        for (VerificationGroup group : VerificationGroup.values()) {
            List<UniversalValidationRunner.StepResult> values = steps.stream()
                    .filter(step -> step.kind().group() == group).toList();
            int possible = values.stream().filter(UniversalValidationRunner.StepResult::required)
                    .mapToInt(step -> allocations.getOrDefault(step.stepId(), 0)).sum();
            int earned = values.stream().filter(UniversalValidationRunner.StepResult::required)
                    .filter(step -> step.outcome() == Outcome.PASS_NONFINAL)
                    .mapToInt(step -> allocations.getOrDefault(step.stepId(), 0)).sum();
            long required = values.stream().filter(UniversalValidationRunner.StepResult::required).count();
            long passed = values.stream().filter(UniversalValidationRunner.StepResult::required)
                    .filter(step -> step.outcome() == Outcome.PASS_NONFINAL).count();
            groups.add(Map.ofEntries(
                    Map.entry("group", group.name()),
                    Map.entry("order", group.order()),
                    Map.entry("weight_points", points(WEIGHT_BASIS_POINTS.get(group))),
                    Map.entry("possible_points", points(possible)),
                    Map.entry("earned_points", points(earned)),
                    Map.entry("unearned_points", points(possible - earned)),
                    Map.entry("required_step_count", required),
                    Map.entry("passed_required_step_count", passed),
                    Map.entry("outcome", groupOutcomes.getOrDefault(group, Outcome.NOT_RUN).name()),
                    Map.entry("diagnosis", diagnosis(
                            groupOutcomes.getOrDefault(group, Outcome.NOT_RUN), group.name(), !values.isEmpty())),
                    Map.entry("improvement_guide", improvement(
                            groupOutcomes.getOrDefault(group, Outcome.NOT_RUN), firstReason(values))),
                    Map.entry("step_ids", values.stream().map(
                            UniversalValidationRunner.StepResult::stepId).toList())));
        }

        List<Map<String, Object>> phases = new ArrayList<>();
        for (Phase phase : Phase.values()) {
            List<UniversalValidationRunner.StepResult> values = steps.stream()
                    .filter(step -> step.phase() == phase).toList();
            int possible = values.stream().filter(UniversalValidationRunner.StepResult::required)
                    .mapToInt(step -> allocations.getOrDefault(step.stepId(), 0)).sum();
            int earned = values.stream().filter(UniversalValidationRunner.StepResult::required)
                    .filter(step -> step.outcome() == Outcome.PASS_NONFINAL)
                    .mapToInt(step -> allocations.getOrDefault(step.stepId(), 0)).sum();
            phases.add(Map.of(
                    "phase", phase.name(), "level", phase.level(),
                    "possible_points", points(possible), "earned_points", points(earned),
                    "unearned_points", points(possible - earned),
                    "outcome", phaseOutcomes.getOrDefault(phase, Outcome.NOT_RUN).name(),
                    "diagnosis", diagnosis(
                            phaseOutcomes.getOrDefault(phase, Outcome.NOT_RUN), phase.name(), !values.isEmpty()),
                    "improvement_guide", improvement(
                            phaseOutcomes.getOrDefault(phase, Outcome.NOT_RUN), firstReason(values)),
                    "step_ids", values.stream().map(UniversalValidationRunner.StepResult::stepId).toList()));
        }

        long failed = steps.stream().filter(UniversalValidationRunner.StepResult::required)
                .filter(step -> step.outcome() == Outcome.FAIL).count();
        long blocked = steps.stream().filter(UniversalValidationRunner.StepResult::required)
                .filter(step -> step.outcome() == Outcome.BLOCKED).count();
        long notRun = steps.stream().filter(UniversalValidationRunner.StepResult::required)
                .filter(step -> step.outcome() == Outcome.NOT_RUN).count();
        long inconclusive = steps.stream().filter(UniversalValidationRunner.StepResult::required)
                .filter(step -> step.outcome() == Outcome.INCONCLUSIVE).count();
        Map<String, Object> scorecard = new LinkedHashMap<>();
        scorecard.put("contract", CONTRACT);
        scorecard.put("score_type", "SELF_VALIDATION_EVIDENCE_COVERAGE_NONFINAL");
        scorecard.put("scoring_model_id", "SEVEN_GROUP_WEIGHTED_REQUIRED_STEP_V1");
        scorecard.put("max_points", points(10_000));
        scorecard.put("earned_points", points(earnedTotal));
        scorecard.put("unearned_points", points(10_000 - earnedTotal));
        scorecard.put("evidence_coverage_percent", points(earnedTotal));
        scorecard.put("validation_outcome", overallOutcome.name());
        scorecard.put("nonfinal_gate_satisfied", overallOutcome == Outcome.PASS_NONFINAL);
        scorecard.put("diagnosis_summary", diagnosis(overallOutcome, "전체 검증", !steps.isEmpty()));
        scorecard.put("improvement_summary", overallOutcome == Outcome.PASS_NONFINAL
                ? "현재 source/environment digest에 결속된 비최종 증적은 충족했습니다. 변경 후 전체 재실행하고 독립 검증 상태를 별도로 확인하십시오."
                : "미획득 점수가 큰 순서보다 FAIL, BLOCKED, NOT_RUN 순으로 원인을 해소하고 동일 source 또는 승인된 변경 source에서 전체 회귀를 재실행하십시오.");
        scorecard.put("trust_gate", Map.of(
                "self_validation", overallOutcome.name(),
                "independent_otester", "NOT_RUN",
                "independent_oaudit", "NOT_RUN",
                "source_mutation_allowed", false,
                "final_claim_allowed", false));
        scorecard.put("required_step_count", requiredCount);
        scorecard.put("passed_required_step_count", passedRequiredCount);
        scorecard.put("failed_required_step_count", failed);
        scorecard.put("blocked_required_step_count", blocked);
        scorecard.put("not_run_required_step_count", notRun);
        scorecard.put("inconclusive_required_step_count", inconclusive);
        scorecard.put("groups", List.copyOf(groups));
        scorecard.put("phases", List.copyOf(phases));
        List<Map<String, Object>> assessmentAreas = assessmentAreas(steps);
        scorecard.put("assessment_domains", assessmentDomains(steps));
        scorecard.put("assessment_areas", assessmentAreas);
        scorecard.put("steps", List.copyOf(stepScores));
        scorecard.put("unearned_required_steps", List.copyOf(unearned));
        scorecard.put("interpretation", "Points measure executed digest-bound evidence coverage; they do not override FAIL, BLOCKED, NOT_RUN or independent assurance requirements.");
        scorecard.put("final_claim_allowed", false);
        return Map.copyOf(scorecard);
    }

    private static Map<String, Integer> allocations(
            List<UniversalValidationRunner.StepResult> steps) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (VerificationGroup group : VerificationGroup.values()) {
            List<UniversalValidationRunner.StepResult> required = steps.stream()
                    .filter(UniversalValidationRunner.StepResult::required)
                    .filter(step -> step.kind().group() == group).toList();
            if (required.isEmpty()) continue;
            int weight = WEIGHT_BASIS_POINTS.get(group);
            int base = weight / required.size();
            int remainder = weight % required.size();
            for (int index = 0; index < required.size(); index++) {
                result.put(required.get(index).stepId(), base + (index < remainder ? 1 : 0));
            }
        }
        return Map.copyOf(result);
    }

    private static List<Map<String, Object>> assessmentAreas(
            List<UniversalValidationRunner.StepResult> steps) {
        List<AssessmentArea> areas = List.of(
                new AssessmentArea("ENVIRONMENT_DEPENDENCY", "SECURITY_ENVIRONMENT",
                        Set.of(StepKind.ENVIRONMENT_PREFLIGHT)),
                new AssessmentArea("REQUIREMENTS_DESIGN_TRACEABILITY", "DESIGN",
                        Set.of(StepKind.INVENTORY)),
                new AssessmentArea("VALIDATOR_COVERAGE_DESIGN", "DESIGN",
                        Set.of(StepKind.VALIDATOR_META_CHECK)),
                new AssessmentArea("ARCHITECTURE_STATIC_STRUCTURE", "DESIGN",
                        Set.of(StepKind.STATIC_ANALYSIS)),
                new AssessmentArea("API_CONTRACT_DESIGN", "DESIGN",
                        Set.of(StepKind.API_CONTRACT)),
                new AssessmentArea("DATABASE_MIGRATION_DESIGN", "DESIGN",
                        Set.of(StepKind.DATABASE_MIGRATION)),
                new AssessmentArea("BUILD_REPRODUCIBILITY", "CODING",
                        Set.of(StepKind.BUILD)),
                new AssessmentArea("UNIT_CODE_QUALITY", "CODING",
                        Set.of(StepKind.UNIT_TEST)),
                new AssessmentArea("PACKAGE_INTEGRITY", "CODING",
                        Set.of(StepKind.PACKAGE)),
                new AssessmentArea("FAILURE_AND_BLOCKING_CONTROL", "FUNCTIONAL_PROCESS",
                        Set.of(StepKind.NEGATIVE_TEST, StepKind.BLOCKING_TEST)),
                new AssessmentArea("RETRY_IDEMPOTENCY_CONTROL", "FUNCTIONAL_PROCESS",
                        Set.of(StepKind.RETRY_TEST)),
                new AssessmentArea("INTEGRATION_EXECUTION", "WORKFLOW_PROCESS",
                        Set.of(StepKind.INTEGRATION_TEST)),
                new AssessmentArea("REQUEST_AND_PRODUCTION_FLOW", "WORKFLOW_PROCESS",
                        Set.of(StepKind.E2E_REQUEST_FLOW, StepKind.E2E_RENDER_OR_PRODUCE)),
                new AssessmentArea("ARTIFACT_CONSUMER_READBACK", "WORKFLOW_PROCESS",
                        Set.of(StepKind.E2E_ARTIFACT_READBACK)),
                new AssessmentArea("TEST_AUDIT_EXPOSURE_GATES", "EVIDENCE_GOVERNANCE",
                        Set.of(StepKind.E2E_TESTER_CHECK, StepKind.E2E_AUDIT_CHECK,
                                StepKind.E2E_EXPOSURE_DECISION)),
                new AssessmentArea("DIGEST_SCHEMA_PERMIT_LINEAGE", "EVIDENCE_GOVERNANCE",
                        Set.of(StepKind.WORKFLOW_LINEAGE, StepKind.EVIDENCE_VERIFICATION)),
                new AssessmentArea("PERFORMANCE_AND_RECOVERY", "OPERATIONS",
                        Set.of(StepKind.PERFORMANCE, StepKind.RECOVERY)),
                new AssessmentArea("INTERRUPTION_AND_RESUME", "OPERATIONS",
                        Set.of(StepKind.INTERRUPTION_TEST, StepKind.RESUME_TEST)),
                new AssessmentArea("ROLLBACK_AND_RERUN", "OPERATIONS",
                        Set.of(StepKind.ROLLBACK_TEST, StepKind.RERUN_TEST)));
        return areas.stream().map(area -> areaScore(area.areaId(), area.domain(), area.kinds(), steps)).toList();
    }

    private static List<Map<String, Object>> assessmentDomains(
            List<UniversalValidationRunner.StepResult> steps) {
        return List.of(
                areaScore("SECURITY_ENVIRONMENT", "SECURITY_ENVIRONMENT",
                        Set.of(StepKind.ENVIRONMENT_PREFLIGHT), steps),
                areaScore("DESIGN", "DESIGN", Set.of(
                        StepKind.INVENTORY, StepKind.VALIDATOR_META_CHECK, StepKind.STATIC_ANALYSIS,
                        StepKind.API_CONTRACT, StepKind.DATABASE_MIGRATION), steps),
                areaScore("CODING", "CODING", Set.of(
                        StepKind.BUILD, StepKind.UNIT_TEST, StepKind.PACKAGE), steps),
                areaScore("FUNCTIONAL_PROCESS", "FUNCTIONAL_PROCESS", Set.of(
                        StepKind.NEGATIVE_TEST, StepKind.RETRY_TEST, StepKind.BLOCKING_TEST), steps),
                areaScore("WORKFLOW_PROCESS", "WORKFLOW_PROCESS", Set.of(
                        StepKind.INTEGRATION_TEST, StepKind.E2E_REQUEST_FLOW,
                        StepKind.E2E_RENDER_OR_PRODUCE, StepKind.E2E_ARTIFACT_READBACK), steps),
                areaScore("EVIDENCE_GOVERNANCE", "EVIDENCE_GOVERNANCE", Set.of(
                        StepKind.E2E_TESTER_CHECK, StepKind.E2E_AUDIT_CHECK,
                        StepKind.E2E_EXPOSURE_DECISION, StepKind.WORKFLOW_LINEAGE,
                        StepKind.EVIDENCE_VERIFICATION), steps),
                areaScore("OPERATIONS", "OPERATIONS", Set.of(
                        StepKind.PERFORMANCE, StepKind.RECOVERY, StepKind.INTERRUPTION_TEST,
                        StepKind.RESUME_TEST, StepKind.ROLLBACK_TEST, StepKind.RERUN_TEST), steps));
    }

    private static Map<String, Object> areaScore(
            String areaId, String domain, Set<StepKind> kinds,
            List<UniversalValidationRunner.StepResult> steps) {
        List<UniversalValidationRunner.StepResult> applicable = steps.stream()
                .filter(UniversalValidationRunner.StepResult::required)
                .filter(step -> kinds.contains(step.kind())).toList();
        long passed = applicable.stream().filter(step -> step.outcome() == Outcome.PASS_NONFINAL).count();
        Outcome outcome = UniversalValidationProfile.aggregate(
                applicable.stream().map(UniversalValidationRunner.StepResult::outcome).toList());
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("area_id", areaId);
        value.put("domain", domain);
        value.put("basis_step_kinds", kinds.stream().map(Enum::name).sorted().toList());
        value.put("applicability", applicable.isEmpty() ? "NOT_DISCOVERED" : "APPLICABLE");
        value.put("outcome", outcome.name());
        value.put("diagnosis", diagnosis(outcome, areaId, !applicable.isEmpty()));
        value.put("improvement_guide", improvement(outcome, firstReason(applicable)));
        value.put("required_step_count", applicable.size());
        value.put("passed_required_step_count", passed);
        value.put("possible_points", applicable.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(100));
        value.put("earned_points", applicable.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(passed * 10_000L / applicable.size(), 2));
        value.put("step_ids", applicable.stream().map(
                UniversalValidationRunner.StepResult::stepId).toList());
        value.put("limitations", applicable.isEmpty()
                ? List.of("APPLICABLE_EXECUTION_STEP_NOT_DISCOVERED_OR_NOT_CONFIGURED") : List.of());
        return Map.copyOf(value);
    }

    private static BigDecimal points(int basisPoints) {
        return BigDecimal.valueOf(basisPoints, 2);
    }

    private static String firstReason(List<UniversalValidationRunner.StepResult> steps) {
        return steps.stream().filter(step -> step.outcome() != Outcome.PASS_NONFINAL)
                .map(UniversalValidationRunner.StepResult::reason).findFirst().orElse("ALL_REQUIRED_STEPS_PASS");
    }

    private static String diagnosis(Outcome outcome, String scope, boolean applicable) {
        if (!applicable) return scope + "에 적용 가능한 실행 단계가 발견되거나 승인되지 않아 평가하지 못했습니다.";
        return switch (outcome) {
            case PASS_NONFINAL -> scope + "의 필수 실행과 digest 결속 증적이 현재 비최종 검증 범위에서 충족됐습니다.";
            case FAIL -> scope + "에서 재현 가능한 실패가 발생하여 하위 또는 후속 판정을 신뢰할 수 없습니다.";
            case BLOCKED -> scope + " 실행에 필요한 환경·권한·의존성이 충족되지 않아 검증이 차단됐습니다.";
            case NOT_RUN -> scope + "의 필수 단계가 실행되지 않았거나 선행 단계 미통과로 실행되지 못했습니다.";
            case INCONCLUSIVE -> scope + "의 증적이 판정에 충분하지 않아 결론을 확정할 수 없습니다.";
        };
    }

    private static String improvement(Outcome outcome, String reason) {
        String safeReason = reason == null || reason.isBlank() ? "UNSPECIFIED" : reason;
        if (outcome == Outcome.PASS_NONFINAL) {
            return "source 또는 환경이 바뀌면 동일 단계를 재실행하고 Receipt·output·environment digest를 새 실행에 다시 결속하십시오.";
        }
        if (safeReason.contains("DEPENDENCY_NOT_PASS")) {
            return "표시된 선행 단계부터 해결한 뒤 이 단계와 모든 후속 단계를 다시 실행하십시오. 원인 코드: " + safeReason;
        }
        if (safeReason.contains("ENVIRONMENT") || safeReason.contains("SANDBOX")
                || safeReason.contains("CACHE") || outcome == Outcome.BLOCKED) {
            return "격리 이미지의 도구·offline cache·권한 요구사항을 보완하고 환경 preflight부터 재실행하십시오. 원인 코드: " + safeReason;
        }
        if (safeReason.contains("PACK_NOT_INSTALLED") || outcome == Outcome.NOT_RUN) {
            return "자동 탐지된 Workflow를 검토하고 exact-source-bound 실행 Profile 또는 표준 Validation Pack을 승인해 연결하십시오. 원인 코드: " + safeReason;
        }
        if (outcome == Outcome.FAIL) {
            return "해당 Step 로그와 output digest로 실패를 재현하고 근본원인을 수정한 뒤 Before/After와 전체 회귀·rollback 증적을 생성하십시오. 원인 코드: " + safeReason;
        }
        return "추가 증적과 명확한 oracle을 확보한 뒤 같은 source digest에서 다시 판정하십시오. 원인 코드: " + safeReason;
    }

    private record AssessmentArea(String areaId, String domain, Set<StepKind> kinds) {}
}
