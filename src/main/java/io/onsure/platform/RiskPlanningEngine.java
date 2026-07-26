package io.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.onsure.platform.TargetAdapter.FixtureDefinition;
import io.onsure.platform.ValidationModel.ValidationTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Creates and approves evidence-bound verification plans before any executable scenario runs. */
public final class RiskPlanningEngine {
    public static final String CONTRACT = "ONSURE_EXECUTION_PLAN_V1";
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public Map<String, Object> createPlan(
            ValidationTarget target,
            TargetAdapter adapter,
            Path programProfile,
            Path behaviorProfile,
            Path output) throws Exception {
        JsonNode program = mapper.readTree(programProfile.toFile());
        if (!"ONSURE_PROGRAM_PROFILE_V1".equals(program.path("contract").asText())) {
            throw new IllegalArgumentException("PROGRAM_PROFILE_CONTRACT_INVALID");
        }
        JsonNode behavior = mapper.readTree(behaviorProfile.toFile());
        if (!"ONSURE_BEHAVIOR_PROFILE_V1".equals(behavior.path("contract").asText())) {
            throw new IllegalArgumentException("BEHAVIOR_PROFILE_CONTRACT_INVALID");
        }
        String sourceDigest = program.path("source_baseline")
                .path("source_tree_sha256").asText();
        if (!sourceDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("PLAN_SOURCE_DIGEST_INVALID");
        }
        adapter.validateRegistration(target);
        List<FixtureDefinition> fixtures = adapter.loadFixtures(target);
        if (fixtures.isEmpty()) throw new IllegalStateException("PLAN_FIXTURE_SET_EMPTY");

        int riskScore = riskScore(target, program, behavior);
        List<Map<String, Object>> scenarios = new ArrayList<>();
        Set<String> permissions = new LinkedHashSet<>();
        int totalSeconds = 0;
        for (FixtureDefinition fixture : fixtures) {
            String category = category(fixture.fixtureId());
            String risk = scenarioRisk(category, riskScore);
            List<String> required = FixtureProcessSandbox.STRICT_BWRAP.equals(
                    FixtureProcessSandbox.mapExecutionProfile(target.executionProfile()))
                    ? List.of("PROCESS_EXECUTION", "LOCAL_READ_ONLY_SOURCE", "SANDBOX_NAMESPACE")
                    : List.of("PROCESS_EXECUTION", "REVIEWED_LOCAL_FIXTURE");
            permissions.addAll(required);
            int estimatedSeconds = Math.max(1, fixture.timeoutSeconds());
            totalSeconds += estimatedSeconds;
            Map<String, Object> scenario = new LinkedHashMap<>();
            scenario.put("scenario_id", fixture.fixtureId());
            scenario.put("category", category);
            scenario.put("risk", risk);
            scenario.put("command", fixture.command());
            scenario.put("expected", fixture.expected());
            scenario.put("required_permissions", required);
            scenario.put("estimated_seconds", estimatedSeconds);
            scenario.put("selected", true);
            scenario.put("reason", reason(category, target, program, behavior));
            scenarios.add(Map.copyOf(scenario));
        }

        String planId = "PLAN-" + Hashing.sha256(
                target.targetId() + "|" + sourceDigest + "|" + scenarios)
                .substring(0, 20);
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("contract", CONTRACT);
        plan.put("plan_id", planId);
        plan.put("target_id", target.targetId());
        plan.put("source_digest", sourceDigest);
        plan.put("risk_score", riskScore);
        plan.put("scenarios", scenarios);
        plan.put("required_permissions", permissions.stream().sorted().toList());
        plan.put("estimated_minutes", Math.max(1, (totalSeconds + 59) / 60));
        plan.put("estimated_credit_units", Math.max(1, scenarios.size() + riskScore / 10));
        plan.put("stop_conditions", List.of(
                "SOURCE_OR_POLICY_DIGEST_CHANGES",
                "CRITICAL_FINDING_OBSERVED",
                "SANDBOX_CONTROL_MISSING",
                "TIME_OR_OUTPUT_LIMIT_EXCEEDED",
                "UNAPPROVED_PERMISSION_REQUIRED"));
        plan.put("approval", Map.of(
                "state", "AWAITING_APPROVAL",
                "approved_scenario_ids", List.of(),
                "rejected_scenario_ids", List.of(),
                "approved_by", null,
                "approved_at", null));
        plan.put("created_at", Instant.now().toString());
        plan.put("state", "AWAITING_APPROVAL");
        write(output, plan);
        return Map.copyOf(plan);
    }

    public Map<String, Object> approve(
            Path planFile,
            String approver,
            Set<String> approvedScenarioIds,
            Path output) throws Exception {
        if (approver == null || !approver.matches("[A-Za-z0-9@._:-]{3,256}")) {
            throw new IllegalArgumentException("PLAN_APPROVER_INVALID");
        }
        JsonNode root = mapper.readTree(planFile.toFile());
        if (!CONTRACT.equals(root.path("contract").asText())) {
            throw new IllegalArgumentException("EXECUTION_PLAN_CONTRACT_INVALID");
        }
        Set<String> all = new LinkedHashSet<>();
        for (JsonNode scenario : root.path("scenarios")) {
            all.add(scenario.path("scenario_id").asText());
        }
        if (approvedScenarioIds == null || !all.containsAll(approvedScenarioIds)) {
            throw new IllegalArgumentException("PLAN_APPROVAL_SCENARIO_INVALID");
        }
        Set<String> rejected = new LinkedHashSet<>(all);
        rejected.removeAll(approvedScenarioIds);
        String state;
        String planState;
        if (approvedScenarioIds.isEmpty()) {
            state = "REJECTED";
            planState = "HOLD";
        } else if (approvedScenarioIds.size() == all.size()) {
            state = "APPROVED";
            planState = "READY";
        } else {
            state = "PARTIALLY_APPROVED";
            planState = "READY";
        }

        Map<String, Object> approved = mapper.convertValue(root, LinkedHashMap.class);
        approved.put("approval", Map.of(
                "state", state,
                "approved_scenario_ids", approvedScenarioIds.stream().sorted().toList(),
                "rejected_scenario_ids", rejected.stream().sorted().toList(),
                "approved_by", approver,
                "approved_at", Instant.now().toString()));
        approved.put("state", planState);
        approved.put("approval_receipt_sha256", Hashing.sha256(
                root.path("plan_id").asText() + "|" + approver + "|"
                        + approvedScenarioIds.stream().sorted().toList()));
        write(output, approved);
        return Map.copyOf(approved);
    }

    private static int riskScore(
            ValidationTarget target, JsonNode program, JsonNode behavior) {
        int score = switch (target.targetType()) {
            case GENERAL_SOFTWARE -> 30;
            case AI_APPLICATION -> 55;
            case AI_AGENTIC_PLATFORM -> 70;
        };
        score += Math.min(15, program.path("unknowns").size() * 3);
        score += Math.min(10, program.path("conflicts").size() * 5);
        score += Math.min(20, behavior.path("failure_conditions").size() * 2);
        if (!behavior.path("variability").path("stable").asBoolean(false)) score += 10;
        return Math.min(100, score);
    }

    private static String category(String fixtureId) {
        String value = fixtureId.toLowerCase();
        if (value.contains("advers") || value.contains("injection")
                || value.contains("unauthorized") || value.contains("escape")) {
            return "ADVERSARIAL";
        }
        if (value.contains("fail") || value.contains("error") || value.contains("timeout")) {
            return "FAILURE";
        }
        if (value.contains("boundary") || value.contains("empty") || value.contains("max")) {
            return "BOUNDARY";
        }
        if (value.contains("regression")) return "REGRESSION";
        return "NORMAL";
    }

    private static String scenarioRisk(String category, int aggregateRisk) {
        if ("ADVERSARIAL".equals(category) || aggregateRisk >= 85) return "CRITICAL";
        if ("FAILURE".equals(category) || aggregateRisk >= 65) return "HIGH";
        if ("BOUNDARY".equals(category) || aggregateRisk >= 40) return "MEDIUM";
        return "LOW";
    }

    private static String reason(
            String category, ValidationTarget target, JsonNode program, JsonNode behavior) {
        return category + " scenario selected for " + target.targetType()
                + "; program_unknowns=" + program.path("unknowns").size()
                + ", observed_failures=" + behavior.path("failure_conditions").size() + ".";
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
}
