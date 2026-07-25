package io.onsure.platform;

import io.onsure.assurance.Decision;
import io.onsure.platform.ValidationModel.StageResult;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Seals Fixture definitions and available Oracles/Commands before Harness execution. */
public final class FixtureRegistryStage implements ValidatorStage {
    public static final String TRUSTED_LOCAL_PROFILE = "LOCAL_E2E_TRUSTED_FIXTURE";
    private static final Set<String> TRUSTED_PROFILES = Set.of(
            TRUSTED_LOCAL_PROFILE, "LOCAL_E2E", "LOCAL_MVF_E2E");

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
        if (!TRUSTED_PROFILES.contains(context.target().executionProfile())) {
            throw new IllegalArgumentException("EXECUTABLE_FIXTURE_REQUIRES_TRUSTED_LOCAL_PROFILE");
        }
        context.putAttribute("registered_fixture_count", fixtures.size());
        context.putAttribute("registered_executable_fixture_count", executable);
        FixtureHarness harness = new FixtureHarness("ONSURE_BUILTIN_HARNESS_V1");
        new FixtureRegistry().persist(
                context.runRoot(), context.target().targetId(), context.target().sourceRoot(), fixtures,
                harness.harnessId(), harness.oracleIds());
        return new StageResult(stageId(), Decision.PASS, start, Instant.now(), List.of(),
                Map.of("fixtures", fixtures.size(), "oracles", harness.oracleIds().size(),
                        "executable_fixtures", executable,
                        "harness_id", harness.harnessId(),
                        "execution_profile", context.target().executionProfile()));
    }
}
