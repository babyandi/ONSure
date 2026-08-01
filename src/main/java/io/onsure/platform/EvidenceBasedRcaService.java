package io.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.onsure.platform.ValidationModel.Evidence;
import io.onsure.platform.ValidationModel.Finding;
import io.onsure.platform.ValidationModel.FixtureResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds reproducible RCA hypotheses and confirms them only with bound causal experiments. */
public final class EvidenceBasedRcaService {
    public static final String CONTRACT = "ONSURE_EVIDENCE_BASED_RCA_SET_V1";
    public static final String EXPERIMENT_CONTRACT = "ONSURE_RCA_CAUSAL_EXPERIMENT_SET_V1";
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public Map<String, Object> analyze(ValidationContext context, Path outputFile) throws Exception {
        Map<String, JsonNode> experiments = loadExperiments(
                context.runRoot().resolve("rca-causal-experiments.json"), context);
        List<Map<String, Object>> records = new ArrayList<>();
        for (Finding finding : context.findings()) {
            List<Evidence> evidence = context.evidence().stream()
                    .filter(item -> finding.evidenceIds().contains(item.evidenceId()))
                    .toList();
            FixtureResult fixture = fixtureFor(context, finding);
            JsonNode experiment = experiments.get(finding.findingId());
            boolean reproduced = fixture != null
                    && fixture.decision() != io.onsure.assurance.Decision.PASS;
            boolean causalConfirmed = validCausalExperiment(experiment, finding, context);
            String status = causalConfirmed ? "CONFIRMED" : reproduced ? "REPRODUCED" : "CANDIDATE";
            double confidence = causalConfirmed ? 0.95 : reproduced ? 0.75 : evidence.isEmpty() ? 0.25 : 0.55;
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
            List<String> evidenceRefs = new ArrayList<>(
                    evidence.stream().map(Evidence::evidenceId).sorted().toList());
            if (causalConfirmed) evidenceRefs.add("rca-experiment:" + experiment.path("experiment_id").asText());

            Map<String, Object> record = new LinkedHashMap<>();
            record.put("rca_id", "ERCA-" + finding.fingerprint().substring(0, 16));
            record.put("finding_id", finding.findingId());
            record.put("status", status);
            record.put("reproduction", reproduction);
            record.put("first_failure_point", firstFailurePoint);
            record.put("direct_cause", directCause(finding, fixture));
            record.put("root_cause_hypothesis", rootCauseHypothesis(finding.category()));
            record.put("contributing_factors", contributingFactors(finding, context));
            Map<String, Object> impactScope = impactScope(finding, fixture, experiment, causalConfirmed);
            record.put("impact_scope", impactScope);
            record.put("unknown_items", unknownItems(causalConfirmed, impactScope));
            record.put("causal_experiment", causalConfirmed
                    ? experimentSummary(experiment) : reproduced ? "FAILURE_REPRODUCED_CAUSAL_ISOLATION_NOT_RUN" : "NOT_RUN");
            record.put("causal_experiment_receipt_id", causalConfirmed
                    ? experiment.path("experiment_id").asText() : "NOT_RUN");
            record.put("confidence", confidence);
            record.put("evidence_refs", List.copyOf(evidenceRefs));
            record.put("source_tree_sha256", context.attributes().get("source_tree_sha256"));
            record.put("required_confirmation", causalConfirmed
                    ? List.of("Repeat the causal experiment", "Run complete regression", "Independent review")
                    : List.of("Create control and treatment cases", "Vary exactly one causal factor",
                            "Bind both outputs to the same source/environment", "Run independent confirmation"));
            record.put("final_claim_allowed", false);
            records.add(Map.copyOf(record));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contract", CONTRACT);
        result.put("job_id", context.job().jobId());
        result.put("source_tree_sha256", context.attributes().get("source_tree_sha256"));
        result.put("records", List.copyOf(records));
        result.put("confirmed_count", count(records, "CONFIRMED"));
        result.put("reproduced_count", count(records, "REPRODUCED"));
        result.put("candidate_count", count(records, "CANDIDATE"));
        result.put("independent_confirmation", "NOT_RUN");
        result.put("generated_at", Instant.now().toString());
        result.put("final_claim_allowed", false);
        result.put("rca_set_sha256", sha256(mapper.writeValueAsBytes(result)));
        writeAtomic(outputFile, result);
        return Map.copyOf(result);
    }

    private Map<String, JsonNode> loadExperiments(Path file, ValidationContext context) throws Exception {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return Map.of();
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
            throw new IllegalStateException("RCA_CAUSAL_EXPERIMENT_FILE_INVALID");
        }
        JsonNode root = mapper.readTree(file.toFile());
        if (!EXPERIMENT_CONTRACT.equals(root.path("contract").asText())) {
            throw new IllegalStateException("RCA_CAUSAL_EXPERIMENT_CONTRACT_INVALID");
        }
        if (!context.job().jobId().equals(root.path("job_id").asText())
                || !String.valueOf(context.attributes().get("source_tree_sha256"))
                        .equals(root.path("source_tree_sha256").asText())) {
            throw new IllegalStateException("RCA_CAUSAL_EXPERIMENT_CONTEXT_MISMATCH");
        }
        Map<String, JsonNode> result = new LinkedHashMap<>();
        for (JsonNode experiment : root.path("experiments")) {
            String findingId = experiment.path("finding_id").asText();
            if (findingId.isBlank() || result.putIfAbsent(findingId, experiment) != null) {
                throw new IllegalStateException("RCA_CAUSAL_EXPERIMENT_DUPLICATE_OR_INVALID");
            }
        }
        return Map.copyOf(result);
    }

    private static boolean validCausalExperiment(
            JsonNode experiment, Finding finding, ValidationContext context) {
        if (experiment == null) return false;
        return finding.findingId().equals(experiment.path("finding_id").asText())
                && "PASS".equals(experiment.path("decision").asText())
                && experiment.path("single_factor_varied").asBoolean(false)
                && experiment.path("same_source_context").asBoolean(false)
                && experiment.path("same_environment_context").asBoolean(false)
                && experiment.path("control_output_sha256").asText().matches("[0-9a-f]{64}")
                && experiment.path("treatment_output_sha256").asText().matches("[0-9a-f]{64}")
                && !experiment.path("control_output_sha256").asText()
                        .equals(experiment.path("treatment_output_sha256").asText())
                && experiment.path("experiment_receipt_sha256").asText().matches("[0-9a-f]{64}")
                && String.valueOf(context.attributes().get("source_tree_sha256"))
                        .equals(experiment.path("source_tree_sha256").asText());
    }

    private static String experimentSummary(JsonNode experiment) {
        return "CONTROL_TREATMENT_SINGLE_FACTOR_PASS:"
                + experiment.path("experiment_id").asText();
    }

    private static long count(List<Map<String, Object>> records, String status) {
        return records.stream().filter(item -> status.equals(item.get("status"))).count();
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
            case "AI_TOOL_AUTHORIZATION" -> "Tool authorization may not be enforced before execution.";
            case "AI_SELF_APPROVAL" -> "Production and approval authorities may not be separated.";
            case "PROMPT_INJECTION" -> "Untrusted instructions may override the intended policy boundary.";
            case "AI_DATA_EXFILTRATION", "SECRET_EXPOSURE" -> "Sensitive data boundary and redaction controls may be incomplete.";
            case "COMMAND_EXECUTION", "DYNAMIC_EXECUTION" -> "Execution may not be constrained to a typed allowlisted sandbox.";
            case "RUNTIME_BEHAVIOR" -> "Runtime behavior diverges from the registered Oracle contract.";
            default -> "The required control may be absent, incomplete, stale, or not enforced at the first failure point.";
        };
    }

    private static List<String> contributingFactors(Finding finding, ValidationContext context) {
        List<String> values = new ArrayList<>();
        if (finding.evidenceIds().isEmpty()) values.add("EVIDENCE_REFERENCE_MISSING");
        values.add("INDEPENDENT_VALIDATION_NOT_RUN");
        if (!Boolean.TRUE.equals(context.attributes().get("immutable_source_verified"))) {
            values.add("SOURCE_IDENTITY_NOT_VERIFIED");
        }
        if (values.size() == 1) values.add("CONTROL_DESIGN_OR_IMPLEMENTATION_GAP");
        return List.copyOf(values);
    }

    private static Map<String, Object> impactScope(
            Finding finding, FixtureResult fixture, JsonNode experiment, boolean causalConfirmed) {
        List<String> verifiedAssets = experimentAssets(experiment);
        boolean verified = causalConfirmed
                && experiment != null
                && experiment.path("impact_scope_verified").asBoolean(false)
                && !verifiedAssets.isEmpty();
        Map<String, Object> impact = new LinkedHashMap<>();
        impact.put("classification", verified ? "EXPERIMENT_VERIFIED"
                : fixture != null ? "FIXTURE_OBSERVED" : "SOURCE_LOCATION_OBSERVED");
        impact.put("affected_assets", verified ? verifiedAssets : List.of(
                fixture != null ? "fixture:" + fixture.fixtureId() : "source:" + finding.location()));
        impact.put("assessment_basis", verified
                ? "BOUND_CAUSAL_EXPERIMENT:" + experiment.path("experiment_id").asText()
                : fixture != null
                        ? "OBSERVED_FIXTURE_FAILURE_NO_IMPACT_ISOLATION"
                        : "SOURCE_FINDING_NO_RUNTIME_IMPACT_ISOLATION");
        impact.put("verified", verified);
        return Map.copyOf(impact);
    }

    private static List<String> experimentAssets(JsonNode experiment) {
        if (experiment == null || !experiment.path("affected_assets").isArray()) return List.of();
        List<String> assets = new ArrayList<>();
        for (JsonNode value : experiment.path("affected_assets")) {
            String asset = value.asText();
            if (asset.isBlank() || assets.contains(asset)) return List.of();
            assets.add(asset);
        }
        return List.copyOf(assets);
    }

    private static List<String> unknownItems(
            boolean causalConfirmed, Map<String, Object> impactScope) {
        List<String> unknowns = new ArrayList<>();
        if (!causalConfirmed) unknowns.add("CAUSAL_FACTOR_NOT_CONFIRMED");
        if (!Boolean.TRUE.equals(impactScope.get("verified"))) {
            unknowns.add("IMPACT_BOUNDARY_NOT_CAUSALLY_VERIFIED");
        }
        unknowns.add("INDEPENDENT_CONFIRMATION_NOT_RUN");
        return List.copyOf(unknowns);
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
