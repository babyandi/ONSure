package kr.co.oruda.onsure.assurance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AdversarialFixtureReportMain {
    public static final String CONTRACT = "ONSURE_ADVERSARIAL_FIXTURE_REPORT_V1";

    private AdversarialFixtureReportMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("usage: AdversarialFixtureReportMain <fixture-json> <output-tsv>");
            System.exit(64);
        }
        ValidationResult result = writeReport(Path.of(args[0]), Path.of(args[1]));
        if (result.decision() != Decision.PASS) {
            System.err.println("ADVERSARIAL_FIXTURE_REPORT_FAIL " + result.violations());
            System.exit(88);
        }
    }

    static ValidationResult writeReport(Path fixtureFile, Path output) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(fixtureFile.toFile());
            List<AdversarialFixtureRunner.FixtureResult> actual;
            try (var input = Files.newInputStream(fixtureFile)) {
                actual = new AdversarialFixtureRunner().run(input);
            }
            Map<String, AdversarialFixtureRunner.FixtureResult> byId = new LinkedHashMap<>();
            for (var result : actual) byId.put(result.id(), result);

            StringBuilder tsv = new StringBuilder();
            tsv.append("contract\tfixture\texpected_decision\texpected_reason\tactual_decision\tactual_reasons\tresult\n");
            boolean allMatch = true;
            for (JsonNode fixture : root.path("fixtures")) {
                String id = fixture.path("id").asText();
                String expectedDecision = fixture.path("expected_decision").asText();
                String expectedReason = fixture.path("expected_reason").asText();
                var result = byId.get(id);
                String actualDecision = result == null ? "NOT_RUN" : result.decision().name();
                String actualReasons = result == null ? "UNKNOWN_FIXTURE" : String.join(",", result.reasons());
                boolean match = expectedDecision.equals(actualDecision)
                        && result != null && result.reasons().contains(expectedReason);
                allMatch &= match;
                tsv.append(CONTRACT).append('\t')
                        .append(clean(id)).append('\t')
                        .append(clean(expectedDecision)).append('\t')
                        .append(clean(expectedReason)).append('\t')
                        .append(clean(actualDecision)).append('\t')
                        .append(clean(actualReasons)).append('\t')
                        .append(match ? "PASS" : "FAIL").append('\n');
            }
            if (byId.size() != root.path("fixtures").size()) allMatch = false;

            Files.createDirectories(output.toAbsolutePath().normalize().getParent());
            Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
            Files.writeString(temporary, tsv.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }
            return allMatch ? ValidationResult.pass()
                    : ValidationResult.fail(List.of("ADVERSARIAL_FIXTURE_CONTRACT_MISMATCH"));
        } catch (Exception e) {
            return ValidationResult.fail(List.of("ADVERSARIAL_FIXTURE_REPORT_WRITE_FAILED"));
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
