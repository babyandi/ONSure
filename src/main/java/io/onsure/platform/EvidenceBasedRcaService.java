package io.onsure.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.onsure.platform.ValidationModel.Evidence;
import io.onsure.platform.ValidationModel.Finding;
import io.onsure.platform.ValidationModel.FixtureResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds reproducible RCA records and refuses to call a hypothesis confirmed without causal evidence. */
public final class EvidenceBasedRcaService {
    public static final String CONTRACT = "ONSURE_EVIDENCE_BASED_RCA_SET_V1";
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public Map<String, Object> analyze(ValidationContext context, Path outputFile) throws Exception {
        List<Map<String, Object>> records = new ArrayList<>();
        for (Finding finding : context.findings()) {
            List<Evidence> evidence = context.evidence().stream()
                    .filter(item -> finding.evidenceIds().contains(item.evidenceId()))
                    .toList();
            FixtureResult fixture = fixtureFor(context, finding);
            boolean reproduced = fixture != null;
            boolean causalExperiment = reproduced && fixture.decision() != io.onsure.assurance.Decision.PASS;
            String status = causalExperiment ? "CONFIRMED" : "CANDIDATE";
            double confidence = causalExperiment ? 0.95 : evidence.isEmpty() ? 0.25 : 0.70;
            String firstFailurePoint = fixture != null
                    ? "fixture:" + fixture.fixtureId() + "/oracle:" + fixture.oracleId()
                    : finding.location();
            List<String> reproduction = fixture != null
                    ? List.of(
                            "Execute fixture " + fixture.fixtureId() + " with harness " + fixture.harnessId() + ".",
                            "Compare expected '" + fixture.expected() + "' to observed '" + fixture.observed() + "'.",
                            "Require the same source, policy, environment and fixture digests.")
                    : List.of(
                            "Re-read the exact source location " + finding.location() + ".",
                            "Recalculate the source pattern evidence against the same source tree.",
                            "Run a focused causal fixture before confirmation.");
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("rca_id", "ERCA-" + finding.fingerprint().substring(0, 16));
            record.put("finding_id", finding.findingId());
            record.put("status", status);
            record.put("reproduction", reproduction);
            record.put("first_failure_point", firstFailurePoint);
            record.put("direct_cause", directCause(finding, fixture));
            record.put("root_cause_hypothesis", rootCauseHypothesis(finding.category()));
            record.put("contributing_factors", contributingFactors(finding, context));
            record.put("causal_experiment", causalExperiment
                    ? "ORACLE_MISMATCH_REPRODUCED" : "NOT_RUN");
            record.put("confidence", confidence);
            record.put("evidence_refs", evidence.stream().map(Evidence::evidenceId).sorted().toList());
            record.put("source_tree_sha256", context.attributes().get("source_tree_sha256"));
            record.put("required_confirmation", status.equals("CONFIRMED")
                    ? List.of("Repeat focused fixture", "Run complete regression", "Independent review")
                    : List.of("Create focused fixture", "Vary one causal input", "Observe first divergence"));
            record.put("final_claim_allowed", false);
            records.add(Map.copyOf(record));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contract", CONTRACT);
        result.put("job_id", context.job().jobId());
        result.put("source_tree_sha256", context.attributes().get("source_tree_sha256"));
        result.put("records", List.copyOf(records));
        result.put("confirmed_count", records.stream().filter(item -> "CONFIRMED".equals(item.get("status"))).count());
        result.put("candidate_count", records.stream().filter(item -> "CANDIDATE".equals(item.get("status"))).count());
        result.put("independent_confirmation", "NOT_RUN");
        result.put("generated_at", Instant.now().toString());
        result.put("final_claim_allowed", false);
        result.put("rca_set_sha256", sha256(mapper.writeValueAsBytes(result)));
        writeAtomic(outputFile, result);
        return Map.copyOf(result);
    }

    private static FixtureResult fixtureFor(ValidationContext context, Finding finding) {
        if (!finding.location().startsWith("fixture:")) return null;
        String fixtureId = finding.location().substring("fixture:".length());
        return context.fixtureResults().stream()
                .filter(value -> value.fixtureId().equals(fixtureId)).findFirst().orElse(null);
    }

    private static String directCause(Finding finding, FixtureResult fixture) {
        if (fixture != null) {
            return "Observed output '" + fixture.observed() + "' did not satisfy Oracle '"
                    + fixture.oracleId() + "' expectation '" + fixture.expected() + "'.";
        }
        return "The source-bound evidence at " + finding.location()
                + " contains the condition described by the finding.";
    }

    private static String rootCauseHypothesis(String category) {
        return switch (category) {
            case "AI_TOOL_AUTHORIZATION" -> "Tool authorization is not enforced before execution.";
            case "AI_SELF_APPROVAL" -> "Production and approval authorities are not separated.";
            case "PROMPT_INJECTION" -> "Untrusted instructions can override the intended policy boundary.";
            case "AI_DATA_EXFILTRATION", "SECRET_EXPOSURE" -> "Sensitive data boundary and redaction controls are incomplete.";
            case "COMMAND_EXECUTION", "DYNAMIC_EXECUTION" -> "Execution is not constrained to a typed allowlisted sandbox.";
            case "RUNTIME_BEHAVIOR" -> "Runtime behavior diverges from the registered Oracle contract.";
            default -> "The required control is absent, incomplete, stale, or not enforced at the first failure point.";
        };
    }

    private static List<String> contributingFactors(Finding finding, ValidationContext context) {
        List<String> values = new ArrayList<>();
        if (finding.evidenceIds().isEmpty()) values.add("EVIDENCE_REFERENCE_MISSING");
        if ("NOT_RUN".equals(context.attributes().getOrDefault("independent_verifier", "NOT_RUN"))) {
            values.add("INDEPENDENT_VALIDATION_NOT_RUN");
        }
        if (!Boolean.TRUE.equals(context.attributes().get("immutable_source_verified"))) {
            values.add("SOURCE_IDENTITY_NOT_VERIFIED");
        }
        if (values.isEmpty()) values.add("CONTROL_DESIGN_OR_IMPLEMENTATION_GAP");
        return List.copyOf(values);
    }

    private void writeAtomic(Path outputFile, Object value) throws Exception {
        Path output = outputFile.toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
        mapper.writeValue(temporary.toFile(), value);
        try {
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
