package kr.co.oruda.onsure.platform;

import kr.co.oruda.onsure.assurance.Decision;
import kr.co.oruda.onsure.platform.ValidationModel.StageResult;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Seals Fixture definitions and available Oracles/Commands before Harness execution. */
public final class FixtureRegistryStage implements ValidatorStage {
    public static final String TRUSTED_LOCAL_PROFILE = "TRUSTED_LOCAL_FIXTURE";
    public static final String LEGACY_TRUSTED_LOCAL_PROFILE = "LOCAL_E2E_TRUSTED_FIXTURE";
    private static final Set<String> TRUSTED_PROFILES = Set.of(
            TRUSTED_LOCAL_PROFILE, LEGACY_TRUSTED_LOCAL_PROFILE,
            "LOCAL_E2E", "LOCAL_MVF_E2E", "LOCAL_FIXTURE", "LOCAL_DEVELOPMENT");

    @Override public String stageId() { return "FIXTURE_ORACLE_REGISTRY"; }
    @Override public boolean supports(ValidationContext context) { return true; }

    @Override
    public StageResult execute(ValidationContext context) throws Exception {
        Instant start = Instant.now();
        List<TargetAdapter.FixtureDefinition> fixtures = context.adapter().loadFixtures(context.target());
        if (fixtures.isEmpty()) {
            throw new IllegalArgumentException("FIXTURE_SET_EMPTY");
        }
        Set<String> fixtureIds = new HashSet<>();
        for (TargetAdapter.FixtureDefinition fixture : fixtures) {
            if (!fixtureIds.add(fixture.fixtureId())) {
                throw new IllegalArgumentException("DUPLICATE_FIXTURE_ID:" + fixture.fixtureId());
            }
        }
        long executable = fixtures.stream().filter(TargetAdapter.FixtureDefinition::executable).count();
        if (executable != fixtures.size()) {
            throw new IllegalArgumentException("ALL_FIXTURES_MUST_BE_EXECUTABLE");
        }
        boolean processGatedTrustedFixture =
                ExecutionPlanService.trustedFixtureAutoApproval(context.target().executionProfile());
        boolean signedFixtureApproval =
                "USER_APPROVED".equals(context.attributes().get("execution_plan_approval"))
                && ExecutionPlanActionPolicy.isApproved(context, "FIXTURE_EXECUTION");
        if (!processGatedTrustedFixture && !signedFixtureApproval) {
            throw new IllegalArgumentException(
                    "EXECUTABLE_FIXTURE_REQUIRES_PROCESS_GATE_OR_SIGNED_PLAN_APPROVAL");
        }
        context.putAttribute("registered_fixture_count", fixtures.size());
        context.putAttribute("registered_executable_fixture_count", executable);
        context.putAttribute("fixture_execution_authority",
                signedFixtureApproval ? "SIGNED_EXECUTION_PLAN" : "TRUSTED_FIXTURE_PROCESS_GATE");
        FixtureHarness harness = new FixtureHarness("ONSURE_BUILTIN_HARNESS_V1");
        new FixtureRegistry().persist(
                context.runRoot(), context.target().targetId(), context.target().sourceRoot(), fixtures,
                harness.harnessId(), harness.oracleIds());
        return new StageResult(stageId(), Decision.PASS, start, Instant.now(), List.of(), Map.of(
                "fixtures", fixtures.size(), "executable", executable,
                "oracles", harness.oracleIds().size(), "commands", fixtures.size(),
                "fixture_execution_authority", context.attributes().get("fixture_execution_authority")));
    }
}
