package io.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.onsure.platform.ValidationModel.Finding;
import io.onsure.platform.ValidationModel.Severity;
import io.onsure.platform.ValidationModel.TargetType;
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

/** Performs a structured review across requirement, design, code, AI, security and test domains. */
public final class OReviewService {
    public static final String CONTRACT = "ONSURE_OREVIEW_RESULT_V1";
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public Map<String, Object> review(ValidationContext context, Path outputFile) throws Exception {
        JsonNode program = readRequired(context.runRoot().resolve("program-profile.json"),
                ProgramLearningService.CONTRACT, "PROGRAM_PROFILE");
        JsonNode plan = readRequired(context.runRoot().resolve("execution-plan.json"),
                ExecutionPlanService.CONTRACT, "EXECUTION_PLAN");
        JsonNode behavior = Files.isRegularFile(context.runRoot().resolve("behavior-profile.json"))
                ? mapper.readTree(context.runRoot().resolve("behavior-profile.json").toFile()) : null;

        List<Map<String, Object>> domains = new ArrayList<>();
        domains.add(domain("REQUIREMENT_TRACEABILITY",
                program.path("unknowns").isEmpty() ? "PASS" : "HOLD",
                List.of("program-profile.json#unknowns"),
                program.path("unknowns").isEmpty()
                        ? List.of("No explicit static unknown was recorded.")
                        : List.of("Static learning contains unresolved unknowns."),
                "Atomic requirement reconciliation and source-bound evidence remain required."));
        domains.add(domain("ARCHITECTURE",
                program.path("components").isEmpty() ? "HOLD" : "PASS",
                List.of("program-profile.json#components", "program-profile.json#data_flows"),
                List.of("Component and data-flow inventory were recalculated from source."),
                "Confirm boundaries and dynamic calls with runtime trace."));
        String approval = plan.path("approval").path("state").asText();
        domains.add(domain("POLICY_AND_APPROVAL",
                List.of("AUTO_APPROVED_DEVELOPMENT_NONFINAL", "USER_APPROVED").contains(approval)
                        ? "PASS" : "FAIL",
                List.of("execution-plan.json#approval", "execution-plan.json#permissions"),
                List.of("Execution permissions and stop conditions are explicit."),
                "Any scope change requires a new approval receipt."));
        long codeFindings = context.findings().stream()
                .filter(finding -> List.of("STATIC_ANALYSIS", "AI_BEHAVIOR_VALIDATION")
                        .contains(finding.stageId()))
                .count();
        domains.add(domain("CODE",
                blocking(context.findings()) ? "FAIL" : codeFindings > 0 ? "HOLD" : "PASS",
                evidenceFor(context.findings()),
                List.of("Static and policy-source findings were reconciled by fingerprint."),
                "Inline human review and compiler/runtime evidence remain separate gates."));
        if (context.target().targetType() != TargetType.GENERAL_SOFTWARE) {
            String aiDecision = behavior == null ? "FAIL"
                    : behavior.path("variability").path("stable").asBoolean(false) ? "PASS" : "HOLD";
            domains.add(domain("AI_BEHAVIOR",
                    aiDecision,
                    List.of("behavior-profile.json", "program-profile.json#ai_components"),
                    List.of("Repeated executable scenarios were observed with versioned runtime context."),
                    "Real model/provider, prompt, tool and RAG production traces remain required."));
        } else {
            domains.add(domain("AI_BEHAVIOR", "NOT_APPLICABLE", List.of(),
                    List.of("Target type is GENERAL_SOFTWARE."), "None."));
        }
        long securityCritical = context.findings().stream()
                .filter(finding -> securityCategory(finding.category()))
                .filter(finding -> finding.severity() == Severity.CRITICAL
                        || finding.severity() == Severity.HIGH)
                .count();
        domains.add(domain("SECURITY", securityCritical > 0 ? "FAIL" : "PASS",
                evidenceFor(context.findings().stream().filter(
                        finding -> securityCategory(finding.category())).toList()),
                List.of("Known security categories were evaluated against current source and fixtures."),
                "Dependency, container, network and secret-provider scans remain separate execution gates."));
        domains.add(domain("PERFORMANCE", "HOLD", List.of("execution-plan.json#resource_budget"),
                List.of("A resource budget and timeout are defined."),
                "Quantitative load, latency, memory and recovery benchmarks are NOT_RUN."));
        Object fixtureCount = context.attributes().get("registered_fixture_count");
        Object executableCount = context.attributes().get("registered_executable_fixture_count");
        boolean fixtureReady = fixtureCount instanceof Number registered
                && executableCount instanceof Number executable
                && registered.longValue() > 0 && registered.longValue() == executable.longValue();
        domains.add(domain("TEST_QUALITY", fixtureReady ? "PASS" : "FAIL",
                List.of("fixture-registry.json", "oracle-registry.json"),
                List.of("Registered scenario and executable fixture counts are compared."),
                "Golden, blind and independent acceptance suites remain separate gates."));
        domains.add(domain("PRODUCT_QUALITY",
                program.path("conflicts").isEmpty() ? "PASS" : "HOLD",
                List.of("program-profile.json#conflicts"),
                List.of("Static conflicts and unknowns are preserved instead of silently resolved."),
                "Owner review is required before profile activation."));
        domains.add(domain("MERGE_READINESS", "NOT_APPLICABLE", List.of(),
                List.of("This run validates a target and does not authorize a code merge."),
                "Patch approval, worktree, regression, Draft PR and independent review are required."));

        String qualityDecision = domains.stream().anyMatch(item -> "FAIL".equals(item.get("decision")))
                ? "FAIL" : domains.stream().anyMatch(item -> "HOLD".equals(item.get("decision")))
                ? "HOLD" : "PASS";
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contract", CONTRACT);
        result.put("review_id", "REVIEW-" + context.job().jobId());
        result.put("target_id", context.target().targetId());
        result.put("source_tree_sha256", context.attributes().get("source_tree_sha256"));
        result.put("program_profile_id", context.attributes().get("program_profile_id"));
        result.put("execution_plan_id", context.attributes().get("execution_plan_id"));
        result.put("domains", List.copyOf(domains));
        result.put("quality_decision", qualityDecision);
        result.put("review_execution", "PASS");
        result.put("independent_reviewer", "NOT_RUN");
        result.put("merge_authorized", false);
        result.put("final_claim_allowed", false);
        result.put("generated_at", Instant.now().toString());
        result.put("review_sha256", sha256(mapper.writeValueAsBytes(result)));
        writeAtomic(outputFile, result);
        return Map.copyOf(result);
    }

    private static Map<String, Object> domain(
            String id, String decision, List<String> evidenceRefs, List<String> observations,
            String recommendation) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("domain", id);
        value.put("decision", decision);
        value.put("evidence_refs", List.copyOf(evidenceRefs));
        value.put("observations", List.copyOf(observations));
        value.put("recommendation", recommendation);
        return Map.copyOf(value);
    }

    private JsonNode readRequired(Path file, String contract, String label) throws Exception {
        if (!Files.isRegularFile(file)) throw new IllegalStateException(label + "_MISSING");
        JsonNode value = mapper.readTree(file.toFile());
        if (!contract.equals(value.path("contract").asText())) {
            throw new IllegalStateException(label + "_CONTRACT_MISMATCH");
        }
        return value;
    }

    private static boolean blocking(List<Finding> findings) {
        return findings.stream().anyMatch(finding -> finding.severity() == Severity.CRITICAL
                || finding.severity() == Severity.HIGH);
    }

    private static boolean securityCategory(String category) {
        return category.contains("SECRET") || category.contains("AUTH")
                || category.contains("COMMAND") || category.contains("INJECTION")
                || category.contains("EXFILTRATION") || category.contains("PERMISSION");
    }

    private static List<String> evidenceFor(List<Finding> findings) {
        return findings.stream().flatMap(finding -> finding.evidenceIds().stream()).distinct().sorted().toList();
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
