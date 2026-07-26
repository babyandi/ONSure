package io.onsure.platform;

import io.onsure.platform.ValidationModel.TargetType;
import io.onsure.platform.ValidationModel.ValidationTarget;
import java.util.List;
import java.util.Map;

/** Target-specific collection only. It cannot issue ONSURE final decisions. */
public interface TargetAdapter {
    record FixtureDefinition(
            String fixtureId,
            String input,
            String expected,
            String declaredObserved,
            String oracleId,
            List<String> command,
            int timeoutSeconds,
            Map<String, String> environment) {
        public FixtureDefinition {
            if (fixtureId == null || fixtureId.isBlank()) throw new IllegalArgumentException("fixtureId");
            input = input == null ? "" : input;
            expected = expected == null ? "" : expected;
            declaredObserved = declaredObserved == null ? "" : declaredObserved;
            oracleId = oracleId == null || oracleId.isBlank() ? "EQUALS" : oracleId;
            command = command == null ? List.of() : List.copyOf(command);
            if (command.stream().anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException("fixture command contains blank argument");
            }
            if (timeoutSeconds < 1 || timeoutSeconds > 300) timeoutSeconds = 30;
            environment = environment == null ? Map.of() : Map.copyOf(environment);
        }

        public FixtureDefinition(String fixtureId, String input, String expected,
                String declaredObserved, String oracleId) {
            this(fixtureId, input, expected, declaredObserved, oracleId, List.of(), 30, Map.of());
        }

        public boolean executable() { return !command.isEmpty(); }
    }

    String adapterId();

    boolean supports(TargetType targetType);

    void validateRegistration(ValidationTarget target) throws Exception;

    Map<String, Object> collectTargetMetadata(ValidationTarget target) throws Exception;

    List<FixtureDefinition> loadFixtures(ValidationTarget target) throws Exception;

    /** Optional adapter-owned evidence extension. Core does not import target-specific classes. */
    default void persistAdditionalEvidence(ValidationContext context) throws Exception {
        // Generic adapters have no additional evidence registry.
    }
}
