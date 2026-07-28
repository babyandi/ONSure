package io.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
        allowedActions = allowedActions.stream().distinct().sorted().toList();
        if (!APPROVABLE_ACTIONS.containsAll(allowedActions)) {
            throw new IllegalStateException("EXECUTION_PLAN_ACTION_SET_INVALID");
        }

        long estimatedSeconds = Math.max(30L, registeredFixtureCount * 15L
                + profile.path("components").size() * 2L
                + profile.path("dependencies").size());
        long estimatedMemoryMb = riskScore >= 60 ? 2048 : 1024;

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
                "network_egress", "DENY_BY_DEFAULT",
                "paid_service_allowed", false));
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

    private static int riskScore(
            ValidationModel.ValidationTarget target, JsonNode profile, int fixtureCount) {
        int score = 10;
        if (target.targetType() == TargetType.AI_APPLICATION) score += 20;
        if (target.targetType() == TargetType.AI_AGENTIC_PLATFORM) score += 35;
        score += Math.min(20, profile.path("components").size());
        score += Math.min(10, profile.path("dependencies").size());
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
