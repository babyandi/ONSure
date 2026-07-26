package io.onsure.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Creates an RCA candidate and promotes it only when causal evidence is present. */
public final class DiagnosisService {
    public static final String CONTRACT = "ONSURE_DIAGNOSIS_V1";
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public record CausalExperiment(
            String experiment,
            String expected,
            String observed,
            String receiptId,
            boolean supportsCause) {
        public CausalExperiment {
            requireText(experiment, "experiment");
            expected = expected == null ? "" : expected;
            observed = observed == null ? "" : observed;
            requireText(receiptId, "receiptId");
        }
    }

    public Map<String, Object> candidate(
            String findingId,
            String sourceDigest,
            String directCause,
            String rootCauseHypothesis,
            List<String> contributingFactors,
            Path output) throws Exception {
        requireText(findingId, "findingId");
        requireDigest(sourceDigest, "sourceDigest");
        requireText(directCause, "directCause");
        requireText(rootCauseHypothesis, "rootCauseHypothesis");
        Map<String, Object> value = base(
                findingId, sourceDigest, List.of(), null, List.of(), directCause,
                rootCauseHypothesis, contributingFactors, 0.35, List.of(), "RCA_CANDIDATE");
        write(output, value);
        return Map.copyOf(value);
    }

    public Map<String, Object> confirm(
            String findingId,
            String sourceDigest,
            List<String> reproductionSteps,
            String firstFailurePoint,
            List<CausalExperiment> causalExperiments,
            String directCause,
            String rootCause,
            List<String> contributingFactors,
            List<String> evidenceRefs,
            Path output) throws Exception {
        requireText(findingId, "findingId");
        requireDigest(sourceDigest, "sourceDigest");
        if (reproductionSteps == null || reproductionSteps.isEmpty()
                || reproductionSteps.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("RCA_REPRODUCTION_REQUIRED");
        }
        requireText(firstFailurePoint, "firstFailurePoint");
        if (causalExperiments == null || causalExperiments.isEmpty()) {
            throw new IllegalArgumentException("RCA_CAUSAL_EXPERIMENT_REQUIRED");
        }
        if (causalExperiments.stream().noneMatch(CausalExperiment::supportsCause)) {
            throw new IllegalArgumentException("RCA_CAUSE_NOT_SUPPORTED");
        }
        requireText(directCause, "directCause");
        requireText(rootCause, "rootCause");
        if (evidenceRefs == null || evidenceRefs.size() < 2
                || evidenceRefs.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("RCA_EVIDENCE_MINIMUM_NOT_MET");
        }
        double confidence = Math.min(0.99,
                0.60 + causalExperiments.stream().filter(CausalExperiment::supportsCause).count() * 0.08
                        + Math.min(0.15, evidenceRefs.size() * 0.03));
        List<Map<String, Object>> experiments = causalExperiments.stream()
                .map(value -> Map.<String, Object>of(
                        "experiment", value.experiment(),
                        "expected", value.expected(),
                        "observed", value.observed(),
                        "receipt_id", value.receiptId(),
                        "supports_cause", value.supportsCause()))
                .toList();
        Map<String, Object> value = base(
                findingId, sourceDigest, reproductionSteps, firstFailurePoint, experiments,
                directCause, rootCause, contributingFactors, confidence,
                evidenceRefs, "RCA_CONFIRMED");
        write(output, value);
        return Map.copyOf(value);
    }

    private Map<String, Object> base(
            String findingId,
            String sourceDigest,
            List<String> reproductionSteps,
            String firstFailurePoint,
            List<Map<String, Object>> causalExperiments,
            String directCause,
            String rootCause,
            List<String> contributingFactors,
            double confidence,
            List<String> evidenceRefs,
            String state) {
        String diagnosisId = "DIAG-" + Hashing.sha256(
                findingId + "|" + sourceDigest + "|" + state + "|" + directCause
                        + "|" + rootCause + "|" + causalExperiments)
                .substring(0, 20);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("contract", CONTRACT);
        value.put("diagnosis_id", diagnosisId);
        value.put("finding_id", findingId);
        value.put("source_digest", sourceDigest);
        value.put("reproduction_steps", reproductionSteps == null ? List.of() : List.copyOf(reproductionSteps));
        value.put("first_failure_point", firstFailurePoint);
        value.put("causal_experiments", causalExperiments);
        value.put("direct_cause", directCause);
        value.put("root_cause", rootCause);
        value.put("contributing_factors", contributingFactors == null
                ? List.of() : List.copyOf(contributingFactors));
        value.put("confidence", confidence);
        value.put("evidence_refs", evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs));
        value.put("state", state);
        value.put("created_at", Instant.now().toString());
        value.put("independent_review", "NOT_RUN");
        value.put("final_claim_allowed", false);
        return value;
    }

    private void write(Path output, Object value) throws Exception {
        Path normalized = output.toAbsolutePath().normalize();
        Files.createDirectories(normalized.getParent());
        Path temporary = normalized.resolveSibling(normalized.getFileName() + ".tmp");
        mapper.writeValue(temporary.toFile(), value);
        try {
            Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field);
    }

    private static void requireDigest(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field);
        }
    }
}
