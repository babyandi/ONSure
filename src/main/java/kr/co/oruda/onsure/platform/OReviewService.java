package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import kr.co.oruda.onsure.platform.ValidationModel.Finding;
import kr.co.oruda.onsure.platform.ValidationModel.Severity;
import kr.co.oruda.onsure.platform.ValidationModel.TargetType;
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

/** Performs a structured review without converting missing evidence into PASS. */
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
        JsonNode planApproval = readRequired(
                context.runRoot().resolve("execution-plan-approval.json"),
                RiskPlanningStage.APPROVAL_EVIDENCE_CONTRACT,
                "EXECUTION_PLAN_APPROVAL_EVIDENCE");
        JsonNode behavior = Files.isRegularFile(context.runRoot().resolve("behavior-profile.json"))
                ? mapper.readTree(context.runRoot().resolve("behavior-profile.json").toFile()) : null;

        List<Map<String, Object>> domains = new ArrayList<>();
        boolean atomicTraceabilityPass = "PASS".equals(
                context.attributes().getOrDefault("atomic_requirement_traceability", "NOT_RUN"));
        domains.add(domain("REQUIREMENT_TRACEABILITY",
                atomicTraceabilityPass ? "PASS" : "HOLD",
                List.of("program-profile.json", "status/design-capability-coverage.v2.json"),
                atomicTraceabilityPass
                        ? List.of("Atomic requirements are source-bound to code, tests and evidence.")
                        : List.of("Capability coverage exists, but atomic requirement authority reconciliation is NOT_RUN."),
                "Reconcile every normative requirement and acceptance criterion before PASS."));

        boolean dynamicTracePass = "PASS".equals(program.path("dynamic_trace").asText());
        domains.add(domain("ARCHITECTURE",
                program.path("components").isEmpty() || !dynamicTracePass ? "HOLD" : "PASS",
                List.of("program-profile.json#components", "program-profile.json#data_flows"),
                List.of(dynamicTracePass
                        ? "Static boundaries and dynamic calls are evidence-bound."
                        : "Static component inventory exists, but dynamic call/data-flow trace is NOT_RUN."),
                "Capture runtime component, API, event and data-flow traces."));

        String approvalState = plan.path("approval").path("state").asText();
        boolean planApproved = List.of("AUTO_APPROVED_DEVELOPMENT_NONFINAL", "USER_APPROVED")
                .contains(approvalState)
                && planApproval.path("plan_file_sha256").asText().matches("[0-9a-f]{64}")
                && planApproval.path("approved_actions").size() == plan.path("allowed_actions").size();
        domains.add(domain("POLICY_AND_APPROVAL", planApproved ? "PASS" : "FAIL",
                List.of("execution-plan.json#approval", "execution-plan-approval.json"),
                List.of(planApproved
                        ? "Exact execution action set is covered by policy or signed approval evidence."
                        : "Execution approval evidence is missing, stale or incomplete."),
                "Any scope change requires a new source-bound approval receipt."));

        long codeFindings = context.findings().stream()
                .filter(finding -> List.of("STATIC_ANALYSIS", "AI_BEHAVIOR_VALIDATION")
                        .contains(finding.stageId()))
                .count();
        boolean compilerEvidence = "PASS".equals(
                context.attributes().getOrDefault("build_verification", "NOT_RUN"));
        String codeDecision = blocking(context.findings()) ? "FAIL"
                : codeFindings > 0 || !compilerEvidence ? "HOLD" : "PASS";
        domains.add(domain("CODE", codeDecision, evidenceFor(context.findings()),
                List.of(compilerEvidence
                        ? "Static review and compiler/build evidence are present."
                        : "Static review executed; compiler, build and focused human review evidence are NOT_RUN."),
                "Run compiler, unit, integration and focused human review gates."));

        if (context.target().targetType() != TargetType.GENERAL_SOFTWARE) {
            String coverage = behavior == null ? "MISSING"
                    : behavior.path("coverage_class").asText("PROCESS_COMMAND_PROXY");
            boolean directTelemetry = "DIRECT_MODEL_TELEMETRY".equals(coverage);
            boolean stable = behavior != null
                    && behavior.path("variability").path("stable").asBoolean(false);
            String aiDecision = behavior == null ? "FAIL"
                    : directTelemetry && stable ? "PASS" : "HOLD";
            domains.add(domain("AI_BEHAVIOR", aiDecision,
                    List.of("behavior-profile.json", "program-profile.json#ai_components"),
                    List.of("Coverage class is " + coverage
                            + "; direct provider/model/prompt/tool/RAG telemetry is required for PASS."),
                    "Run repeated direct model telemetry with tool, prompt, RAG and memory lineage."));
        } else {
            domains.add(domain("AI_BEHAVIOR", "NOT_APPLICABLE", List.of(),
                    List.of("Target type is GENERAL_SOFTWARE."), "None."));
        }

        long securityCritical = context.findings().stream()
                .filter(finding -> securityCategory(finding.category()))
                .filter(finding -> finding.severity() == Severity.CRITICAL
                        || finding.severity() == Severity.HIGH)
                .count();
        boolean dependencyScan = "PASS".equals(
                context.attributes().getOrDefault("dependency_security_scan", "NOT_RUN"));
        boolean privacyReview = "PASS".equals(
                context.attributes().getOrDefault("privacy_data_flow_review", "NOT_RUN"));
        String securityDecision = securityCritical > 0 ? "FAIL"
                : dependencyScan && privacyReview ? "PASS" : "HOLD";
        domains.add(domain("SECURITY_AND_PRIVACY", securityDecision,
                evidenceFor(context.findings().stream().filter(
                        finding -> securityCategory(finding.category())).toList()),
                List.of("Source security patterns were checked; dependency/SBOM and privacy-flow gates are "
                        + (dependencyScan && privacyReview ? "PASS." : "NOT_RUN or incomplete.")),
                "Run dependency, SBOM, container, secret-provider, authorization and privacy-flow tests."));

        boolean performancePass = "PASS".equals(
                context.attributes().getOrDefault("performance_recovery_verification", "NOT_RUN"));
        domains.add(domain("PERFORMANCE_AND_RECOVERY", performancePass ? "PASS" : "HOLD",
                List.of("execution-plan.json#resource_budget"),
                List.of(performancePass
                        ? "Quantitative load, resource and recovery gates passed."
                        : "Only resource budgets exist; load, latency, recovery and failure injection are NOT_RUN."),
                "Run quantitative performance, outage and recovery benchmarks."));

        Object fixtureCount = context.attributes().get("registered_fixture_count");
        Object executableCount = context.attributes().get("registered_executable_fixture_count");
        boolean fixtureReady = fixtureCount instanceof Number registered
                && executableCount instanceof Number executable
                && registered.longValue() > 0 && registered.longValue() == executable.longValue();
        boolean blindIndependent = "PASS".equals(
                context.attributes().getOrDefault("blind_independent_test", "NOT_RUN"));
        domains.add(domain("TEST_QUALITY",
                !fixtureReady ? "FAIL" : blindIndependent ? "PASS" : "HOLD",
                List.of("fixture-registry.json", "oracle-registry.json"),
                List.of(fixtureReady
                        ? "Registered executable fixtures are complete; blind and independent suites remain separate."
                        : "Executable fixture registry is incomplete."),
                "Run golden, adversarial, blind and independent acceptance suites."));

        boolean ownerAcceptance = "PASS".equals(
                context.attributes().getOrDefault("product_owner_acceptance", "NOT_RUN"));
        domains.add(domain("PRODUCT_QUALITY",
                !program.path("conflicts").isEmpty() ? "HOLD"
                        : ownerAcceptance ? "PASS" : "HOLD",
                List.of("program-profile.json#conflicts"),
                List.of(ownerAcceptance
                        ? "Owner acceptance is source-bound."
                        : "Static conflicts are preserved; product owner acceptance is NOT_RUN."),
                "Obtain owner acceptance against the exact profile, plan and evidence bundle."));

        boolean independentPass = "PASS".equals(
                context.attributes().getOrDefault("independent_otester", "NOT_RUN"))
                && "PASS".equals(context.attributes().getOrDefault("independent_oaudit", "NOT_RUN"));
        domains.add(domain("MERGE_READINESS", independentPass ? "PASS" : "HOLD", List.of(),
                List.of(independentPass
                        ? "Independent OTester and OAudit receipts passed."
                        : "Independent OTester/OAudit and human merge acceptance are NOT_RUN."),
                "Require Draft PR, independent receipts and human merge approval."));

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
        result.put("execution_plan_approval_sha256",
                context.attributes().get("execution_plan_approval_sha256"));
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
        return findings.stream().flatMap(finding -> finding.evidenceIds().stream())
                .distinct().sorted().toList();
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
