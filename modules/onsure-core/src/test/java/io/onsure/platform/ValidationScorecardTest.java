package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.onsure.platform.UniversalValidationProfile.Outcome;
import io.onsure.platform.UniversalValidationProfile.Phase;
import io.onsure.platform.UniversalValidationProfile.StepKind;
import io.onsure.platform.UniversalValidationProfile.VerificationGroup;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ValidationScorecardTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void weightsAllSevenGroupsAndNeverTurnsCoverageIntoFinalClaim() {
        List<UniversalValidationRunner.StepResult> steps = List.of(
                step("environment", StepKind.ENVIRONMENT_PREFLIGHT, Phase.STRUCTURE_STATIC, Outcome.PASS_NONFINAL),
                step("structure", StepKind.INVENTORY, Phase.STRUCTURE_STATIC, Outcome.PASS_NONFINAL),
                step("validator", StepKind.VALIDATOR_META_CHECK, Phase.STRUCTURE_STATIC, Outcome.PASS_NONFINAL),
                step("functional", StepKind.BUILD, Phase.COMPONENT_AND_NEGATIVE, Outcome.PASS_NONFINAL),
                step("e2e", StepKind.E2E_REQUEST_FLOW, Phase.END_TO_END_LINEAGE, Outcome.FAIL),
                step("evidence", StepKind.EVIDENCE_VERIFICATION, Phase.END_TO_END_LINEAGE, Outcome.PASS_NONFINAL),
                step("operations", StepKind.RECOVERY, Phase.OPERATIONAL_RESILIENCE, Outcome.PASS_NONFINAL));
        Map<VerificationGroup, Outcome> groups = outcomes(VerificationGroup.class, Outcome.PASS_NONFINAL);
        groups.put(VerificationGroup.CONNECTED_E2E, Outcome.FAIL);
        Map<Phase, Outcome> phases = outcomes(Phase.class, Outcome.PASS_NONFINAL);
        phases.put(Phase.END_TO_END_LINEAGE, Outcome.FAIL);

        Map<String, Object> score = ValidationScorecard.calculate(steps, phases, groups, Outcome.FAIL);

        assertEquals(new BigDecimal("75.00"), score.get("earned_points"));
        assertEquals(new BigDecimal("25.00"), score.get("unearned_points"));
        assertEquals(7, score.get("required_step_count"));
        assertFalse((Boolean) score.get("nonfinal_gate_satisfied"));
        assertFalse((Boolean) score.get("final_claim_allowed"));
        @SuppressWarnings("unchecked")
        Map<String, Object> trust = (Map<String, Object>) score.get("trust_gate");
        assertEquals("NOT_RUN", trust.get("independent_otester"));
    }

    @Test
    void comparesEveryLevelAndReportsRegressionWithoutClaimingCausality() {
        JsonNode baseline = mapper.valueToTree(score(Outcome.FAIL));
        JsonNode current = mapper.valueToTree(score(Outcome.PASS_NONFINAL));

        Map<String, Object> comparison = ValidationScorecardComparison.compare(
                "run-before", baseline, "run-after", current);

        assertEquals("IMPROVED", comparison.get("state"));
        assertEquals(0, new BigDecimal("25.00").compareTo(
                (BigDecimal) comparison.get("total_delta_points")));
        assertFalse((Boolean) comparison.get("final_claim_allowed"));
    }

    @Test
    void scoreStoreRejectsRemotePostgresqlEndpoints() {
        PostgresqlValidationScoreStore.requireLoopback("jdbc:postgresql://127.0.0.1:5432/onsure");
        assertThrows(SecurityException.class, () -> PostgresqlValidationScoreStore.requireLoopback(
                "jdbc:postgresql://database.example.com:5432/onsure"));
        assertThrows(IllegalArgumentException.class, () -> PostgresqlValidationScoreStore.requireLoopback(
                "jdbc:mysql://127.0.0.1/onsure"));
    }

    private Map<String, Object> score(Outcome e2e) {
        List<UniversalValidationRunner.StepResult> steps = List.of(
                step("environment", StepKind.ENVIRONMENT_PREFLIGHT, Phase.STRUCTURE_STATIC, Outcome.PASS_NONFINAL),
                step("structure", StepKind.INVENTORY, Phase.STRUCTURE_STATIC, Outcome.PASS_NONFINAL),
                step("validator", StepKind.VALIDATOR_META_CHECK, Phase.STRUCTURE_STATIC, Outcome.PASS_NONFINAL),
                step("functional", StepKind.BUILD, Phase.COMPONENT_AND_NEGATIVE, Outcome.PASS_NONFINAL),
                step("e2e", StepKind.E2E_REQUEST_FLOW, Phase.END_TO_END_LINEAGE, e2e),
                step("evidence", StepKind.EVIDENCE_VERIFICATION, Phase.END_TO_END_LINEAGE, Outcome.PASS_NONFINAL),
                step("operations", StepKind.RECOVERY, Phase.OPERATIONAL_RESILIENCE, Outcome.PASS_NONFINAL));
        Map<VerificationGroup, Outcome> groups = outcomes(VerificationGroup.class, Outcome.PASS_NONFINAL);
        groups.put(VerificationGroup.CONNECTED_E2E, e2e);
        Map<Phase, Outcome> phases = outcomes(Phase.class, Outcome.PASS_NONFINAL);
        phases.put(Phase.END_TO_END_LINEAGE, e2e);
        return ValidationScorecard.calculate(steps, phases, groups, e2e);
    }

    private UniversalValidationRunner.StepResult step(
            String id, StepKind kind, Phase phase, Outcome outcome) {
        Instant now = Instant.parse("2026-08-05T00:00:00Z");
        return new UniversalValidationRunner.StepResult(
                id, phase, kind, true, outcome, outcome == Outcome.PASS_NONFINAL ? 0 : 1,
                "a".repeat(64), "b".repeat(64), "/run/" + id + ".log", false,
                outcome == Outcome.PASS_NONFINAL ? "PASS" : "TEST_FAILURE", now, now.plusMillis(5));
    }

    private static <E extends Enum<E>> Map<E, Outcome> outcomes(Class<E> type, Outcome outcome) {
        Map<E, Outcome> result = new EnumMap<>(type);
        for (E value : type.getEnumConstants()) result.put(value, outcome);
        return result;
    }
}
