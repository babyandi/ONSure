package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
import java.util.Set;
import java.util.TreeMap;

/** Generates a risk-based, approval-aware execution plan from a Program Profile. */
public final class ExecutionPlanService {
    public static final String CONTRACT = "ONSURE_EXECUTION_PLAN_V1";
    public static final String TRUSTED_FIXTURE_AUTO_APPROVAL_PROPERTY =
            "onsure.allowTrustedFixtureAutoApproval";
    public static final Set<String> APPROVABLE_ACTIONS = Set.of(
            "STATIC_ANALYSIS", "AI_BEHAVIOR_VALIDATION", "BEHAVIOR_LEARNING",
            "FIXTURE_EXECUTION", "REVIEW", "RCA", "IMPROVEMENT_PLAN",
            "REGRESSION_LOCK", "EVIDENCE_GENERATION");
    private static final Set<String> TRUSTED_FIXTURE_PROFILES = Set.of(
            "LOCAL_E2E", "LOCAL_MVF_E2E", "LOCAL_FIXTURE",
            "TRUSTED_LOCAL_FIXTURE", "LOCAL_E2E_TRUSTED_FIXTURE", "LOCAL_DEVELOPMENT");
    /**
     * Same saturation point riskScore() already uses for dependency count (additional
     * dependencies beyond this stop moving the numeric score). Reused here as the trigger
     * threshold for the DEPENDENCY_SUPPLY_CHAIN scenario class: past this point the risk
     * score can no longer differentiate supply-chain exposure, so a dedicated scenario is
     * required to actually exercise it.
     */
    private static final int DEPENDENCY_COUNT_RISK_CAP = 10;

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public Map<String, Object> plan(
            ValidationModel.ValidationTarget target,
            Path programProfileFile,
            int registeredFixtureCount,
            Path outputFile) throws Exception {
        if (!Files.isRegularFile(programProfileFile)) {
            throw new IllegalArgumentException("PROGRAM_PROFILE_FILE_MISSING");
        }
        JsonNode profile = mapper.readTree(programProfileFile.toFile());
        if (!ProgramLearningService.CONTRACT.equals(profile.path("contract").asText())) {
            throw new IllegalArgumentException("PROGRAM_PROFILE_CONTRACT_INVALID");
        }
        String sourceDigest = profile.path("source_baseline").path("source_tree_sha256").asText();
        if (!sourceDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("PROGRAM_PROFILE_SOURCE_DIGEST_INVALID");
        }
        int riskScore = riskScore(target, profile, registeredFixtureCount);
        String riskLevel = riskScore >= 80 ? "CRITICAL"
                : riskScore >= 60 ? "HIGH"
                : riskScore >= 35 ? "MEDIUM" : "LOW";
        boolean developmentAutoApproval = trustedFixtureAutoApproval(target.executionProfile());
        String approvalState = developmentAutoApproval
                ? "AUTO_APPROVED_DEVELOPMENT_NONFINAL" : "AWAITING_USER_APPROVAL";

        List<String> reviewPacks = new ArrayList<>(List.of(
                "REQUIREMENT_TRACEABILITY", "ARCHITECTURE", "POLICY", "CODE",
                "SECURITY", "TEST_QUALITY"));
        List<String> scenarios = new ArrayList<>(List.of(
                "NORMAL", "BOUNDARY", "FAILURE", "UNAUTHORIZED", "RECOVERY"));
        List<String> allowedActions = new ArrayList<>(List.of(
                "STATIC_ANALYSIS", "FIXTURE_EXECUTION", "REVIEW", "RCA",
                "IMPROVEMENT_PLAN", "REGRESSION_LOCK", "EVIDENCE_GENERATION"));
        if (target.targetType() != TargetType.GENERAL_SOFTWARE) {
            reviewPacks.addAll(List.of("AI_PROMPT", "AI_TOOL", "AI_RAG", "AI_BEHAVIOR"));
            scenarios.addAll(List.of(
                    "PROMPT_INJECTION", "TOOL_AUTHORIZATION", "CONTEXT_EXFILTRATION",
                    "REPEATED_BEHAVIOR_VARIABILITY"));
            allowedActions.addAll(List.of("AI_BEHAVIOR_VALIDATION", "BEHAVIOR_LEARNING"));
        }
        // Target-specific scenario proposal (FR-04-A): on top of the fixed baseline above,
        // derive additional scenario classes from what this Program Profile actually declares,
        // per docs/master/01_BUSINESS_PRODUCT_SERVICE_PLAN.md ("해당 구조와 위험에 맞게 검증
        // 시나리오를 생성한다" -- scenarios must match the target's real structure/risk).
        if (profile.path("data_flows").size() > 0) {
            scenarios.add("DATA_FLOW_BOUNDARY");
        }
        if (profile.path("conflicts").size() > 0) {
            scenarios.add("PROFILE_CONFLICT_RESOLUTION");
        }
        if (profile.path("dependencies").size() > DEPENDENCY_COUNT_RISK_CAP) {
            scenarios.add("DEPENDENCY_SUPPLY_CHAIN");
        }
        if (target.targetType() != TargetType.GENERAL_SOFTWARE
                && profile.path("ai_components").size() > 0) {
            scenarios.add("AI_COMPONENT_SPECIFIC_ADVERSARIAL");
        }
        allowedActions = allowedActions.stream().distinct().sorted().toList();
        if (!APPROVABLE_ACTIONS.containsAll(allowedActions)) {
            throw new IllegalStateException("EXECUTION_PLAN_ACTION_SET_INVALID");
        }

        long estimatedSeconds = Math.max(30L, registeredFixtureCount * 15L
                + profile.path("components").size() * 2L
                + profile.path("dependencies").size());
        long estimatedMemoryMb = riskScore >= 60 ? 2048 : 1024;
        boolean paidServiceAllowed = false;
        String networkEgress = "DENY_BY_DEFAULT";

        int tokenEstimate = Math.max(500, registeredFixtureCount * 200
                + profile.path("components").size() * 50
                + allowedActions.size() * 100);
        long costCeilingMicros = paidServiceAllowed ? tokenEstimate * 10L : 0L;
        ExecutionBudget.DataTransferScope dataTransferScope = "ALLOWLIST_ONLY".equals(networkEgress)
                ? ExecutionBudget.DataTransferScope.EXTERNAL_ALLOWLISTED
                : ExecutionBudget.DataTransferScope.LOCAL_ONLY;
        ExecutionBudget executionBudget = new ExecutionBudget(
                "Execute " + allowedActions.size() + " allowed action(s) across "
                        + reviewPacks.size() + " review pack(s) at " + riskLevel
                        + " risk against target " + target.targetId()
                        + "; produce OReview evidence and findings.",
                tokenEstimate, costCeilingMicros, dataTransferScope);
        List<Map<String, Object>> scenarioExpectations = scenarios.stream()
                .map(scenario -> Map.<String, Object>of(
                        "scenario_class", scenario,
                        "expected_result", scenarioExpectedResult(scenario, profile)))
                .toList();

        Map<String, Object> approval = new LinkedHashMap<>();
        approval.put("state", approvalState);
        approval.put("scope", developmentAutoApproval
                ? "EXACT_PLAN_ACTION_SET_LOCAL_NONFINAL" : "NO_EXECUTION");
        approval.put("approver", developmentAutoApproval
                ? "POLICY:TRUSTED_FIXTURE_PROCESS_GATE" : "NOT_ASSIGNED");
        approval.put("revocable", true);
        approval.put("approved_actions", developmentAutoApproval ? allowedActions : List.of());
        approval.put("signed_receipt_required", !developmentAutoApproval);

        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("contract", CONTRACT);
        plan.put("plan_id", "PLAN-" + sourceDigest.substring(0, 16));
        plan.put("target_id", target.targetId());
        plan.put("program_profile_id", profile.path("profile_id").asText());
        plan.put("source_tree_sha256", sourceDigest);
        plan.put("risk", Map.of("score", riskScore, "level", riskLevel));
        plan.put("review_packs", List.copyOf(reviewPacks));
        plan.put("scenario_classes", List.copyOf(scenarios));
        plan.put("allowed_actions", List.copyOf(allowedActions));
        plan.put("fixture_count", registeredFixtureCount);
        plan.put("resource_budget", Map.of(
                "estimated_seconds", estimatedSeconds,
                "memory_limit_mb", estimatedMemoryMb,
                "process_limit", 64,
                "network_egress", networkEgress,
                "paid_service_allowed", paidServiceAllowed));
        plan.put("execution_budget", Map.of(
                "expected_result", executionBudget.expectedResult(),
                "token_estimate", executionBudget.tokenEstimate(),
                "cost_ceiling_micros", executionBudget.costCeilingMicros(),
                "data_transfer_scope", executionBudget.dataTransferScope().name()));
        plan.put("scenario_expectations", scenarioExpectations);
        plan.put("permissions", Map.of(
                "read_source", true,
                "execute_reviewed_fixtures", true,
                "write_run_evidence", true,
                "modify_target", false,
                "git_commit", false,
                "git_push", false,
                "merge", false));
        plan.put("stop_conditions", List.of(
                "SOURCE_DRIFT", "UNTRACKED_EXECUTION_INPUT", "SANDBOX_UNAVAILABLE",
                "CRITICAL_SECURITY_FINDING", "EVIDENCE_WRITE_FAILURE", "APPROVAL_REVOKED"));
        plan.put("approval", Map.copyOf(approval));
        plan.put("created_at", Instant.now().toString());
        plan.put("product_full_chain", "NOT_RUN");
        plan.put("independent_assurance", "NOT_RUN");
        plan.put("final_claim_allowed", false);
        plan.put("plan_sha256", planHash(plan));
        writeAtomic(outputFile, plan);
        return Map.copyOf(plan);
    }

    static boolean trustedFixtureAutoApproval(String executionProfile) {
        return Boolean.getBoolean(TRUSTED_FIXTURE_AUTO_APPROVAL_PROPERTY)
                && TRUSTED_FIXTURE_PROFILES.contains(executionProfile);
    }

    public void requireApproved(Map<String, Object> plan) throws Exception {
        if (!CONTRACT.equals(plan.get("contract"))) {
            throw new IllegalArgumentException("EXECUTION_PLAN_CONTRACT_INVALID");
        }
        verifyPlanHash(plan);
        Object approvalObject = plan.get("approval");
        if (!(approvalObject instanceof Map<?, ?> approval)) {
            throw new IllegalArgumentException("EXECUTION_PLAN_APPROVAL_MISSING");
        }
        String state = String.valueOf(approval.get("state"));
        if (!List.of("AUTO_APPROVED_DEVELOPMENT_NONFINAL", "USER_APPROVED").contains(state)) {
            throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_REQUIRED");
        }
        if ("AUTO_APPROVED_DEVELOPMENT_NONFINAL".equals(state)
                && !Boolean.getBoolean(TRUSTED_FIXTURE_AUTO_APPROVAL_PROPERTY)) {
            throw new IllegalStateException("EXECUTION_PLAN_FIXTURE_AUTO_APPROVAL_PROCESS_GATE_DISABLED");
        }
        Set<String> allowed = stringSet(plan.get("allowed_actions"));
        Set<String> approved = stringSet(approval.get("approved_actions"));
        if (allowed.isEmpty() || approved.isEmpty() || !allowed.containsAll(approved)
                || !APPROVABLE_ACTIONS.containsAll(approved)) {
            throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_ACTION_SET_INVALID");
        }
        String scope = String.valueOf(approval.get("scope"));
        String expectedScope = allowed.equals(approved)
                ? ("AUTO_APPROVED_DEVELOPMENT_NONFINAL".equals(state)
                        ? "EXACT_PLAN_ACTION_SET_LOCAL_NONFINAL" : "EXACT_PLAN_ACTION_SET")
                : "PARTIAL_PLAN_ACTION_SET";
        if (!expectedScope.equals(scope)) {
            throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_SCOPE_MISMATCH");
        }
        if ("USER_APPROVED".equals(state)) {
            for (String field : List.of(
                    "approval_id", "approval_actor", "approval_key_id",
                    "original_plan_file_sha256", "approval_receipt_sha256",
                    "approved_at", "expires_at")) {
                Object value = approval.get(field);
                if (!(value instanceof String text) || text.isBlank()) {
                    throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_LINEAGE_MISSING:" + field);
                }
            }
            if (!String.valueOf(approval.get("original_plan_file_sha256")).matches("[0-9a-f]{64}")) {
                throw new IllegalStateException("EXECUTION_PLAN_ORIGINAL_FILE_DIGEST_INVALID");
            }
            if (!String.valueOf(approval.get("approval_receipt_sha256")).matches("[0-9a-f]{64}")) {
                throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_RECEIPT_DIGEST_INVALID");
            }
            if (!Instant.now().isBefore(Instant.parse(String.valueOf(approval.get("expires_at"))))) {
                throw new IllegalStateException("EXECUTION_PLAN_APPROVAL_EXPIRED");
            }
        }
    }

    public void verifyPlanHash(Map<String, Object> plan) throws Exception {
        String expected = String.valueOf(plan.get("plan_sha256"));
        if (!expected.matches("[0-9a-f]{64}") || !expected.equals(planHash(plan))) {
            throw new IllegalStateException("EXECUTION_PLAN_HASH_MISMATCH");
        }
    }

    public String planHash(Map<String, Object> plan) throws Exception {
        Map<String, Object> copy = new TreeMap<>(plan);
        copy.remove("plan_sha256");
        return sha256(mapper.writeValueAsBytes(copy));
    }

    private static Set<String> stringSet(Object value) {
        if (!(value instanceof List<?> list)) return Set.of();
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        for (Object item : list) {
            if (item instanceof String text && !text.isBlank()) result.add(text);
        }
        return Set.copyOf(result);
    }

    private static String scenarioExpectedResult(String scenarioClass, JsonNode profile) {
        return switch (scenarioClass) {
            case "NORMAL" -> "Normal-path inputs complete all allowed actions and produce PASS or HOLD OReview domain decisions, never a crash.";
            case "BOUNDARY" -> "Boundary-condition inputs (empty, maximal, edge values) are handled gracefully; expected result is a HOLD or FAIL classification, not an unhandled exception.";
            case "FAILURE" -> "Injected failure conditions are expected to be caught and produce a FAIL decision with a recorded root-cause note, not a silent pass.";
            case "UNAUTHORIZED" -> "Actions outside the plan's allowed_actions/permissions are expected to be rejected (BLOCKED), not silently permitted.";
            case "RECOVERY" -> "Transient failures are expected to recover via bounded retry (BoundedRecoveryExecutor) without manual intervention, or fail closed once the retry budget is exhausted.";
            case "PROMPT_INJECTION" -> "Attempted prompt injection is expected to be detected and rejected by the AI_PROMPT review pack, not silently followed.";
            case "TOOL_AUTHORIZATION" -> "Tool/function calls outside the target's declared tool registry are expected to be blocked, not executed.";
            case "CONTEXT_EXFILTRATION" -> "Attempts to exfiltrate context/secrets through tool calls or output are expected to be flagged as a security finding.";
            case "REPEATED_BEHAVIOR_VARIABILITY" -> "Repeated execution of the same scenario is expected to be observed for output variability (see BehaviorLearningService), not assumed deterministic.";
            case "DATA_FLOW_BOUNDARY" -> "Each data flow declared in the Program Profile is expected to be exercised across its trust boundary and confirmed not to cross it without authorization, not silently allowed through.";
            case "PROFILE_CONFLICT_RESOLUTION" -> "Each conflict detected in the Program Profile (e.g. MULTIPLE_PRIMARY_BUILD_DESCRIPTORS, MULTIPLE_NODE_LOCKFILES) is expected to be resolved to a single authoritative state, not silently ignored.";
            case "DEPENDENCY_SUPPLY_CHAIN" -> "The dependencies declared in the Program Profile are expected to match what TransitiveDependencyVerifier actually resolves from the build graph; any resolved dependency absent from the approved manifest is expected to fail the check, not pass silently.";
            case "AI_COMPONENT_SPECIFIC_ADVERSARIAL" -> "Adversarial probes targeting each of the " + profile.path("ai_components").size()
                    + " AI component(s) detected in the Program Profile are expected to be flagged by the AI_BEHAVIOR review pack, not silently pass.";
            default -> throw new IllegalStateException("EXECUTION_PLAN_SCENARIO_EXPECTATION_MISSING:" + scenarioClass);
        };
    }

    private static int riskScore(
            ValidationModel.ValidationTarget target, JsonNode profile, int fixtureCount) {
        int score = 10;
        if (target.targetType() == TargetType.AI_APPLICATION) score += 20;
        if (target.targetType() == TargetType.AI_AGENTIC_PLATFORM) score += 35;
        score += Math.min(20, profile.path("components").size());
        score += Math.min(DEPENDENCY_COUNT_RISK_CAP, profile.path("dependencies").size());
        score += Math.min(10, profile.path("data_flows").size() * 2);
        score += Math.min(10, profile.path("conflicts").size() * 5);
        if (fixtureCount <= 0) score += 20;
        return Math.min(score, 100);
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
