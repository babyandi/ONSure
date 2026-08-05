package io.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic before/after comparison of evidence-coverage scorecards. */
final class ValidationScorecardComparison {
    static final String CONTRACT = "ONSURE_VALIDATION_SCORECARD_COMPARISON_V1";
    private static final List<Level> LEVELS = List.of(
            new Level("DOMAIN", "assessment_domains", "area_id"),
            new Level("PHASE", "phases", "phase"),
            new Level("GROUP", "groups", "group"),
            new Level("AREA", "assessment_areas", "area_id"),
            new Level("STEP", "steps", "step_id"));

    private ValidationScorecardComparison() {}

    static Map<String, Object> compare(
            String baselineRunId, JsonNode baseline,
            String currentRunId, JsonNode current) {
        requireScorecard(baseline);
        requireScorecard(current);
        List<Map<String, Object>> changes = new ArrayList<>();
        int improved = 0;
        int regressed = 0;
        int unchanged = 0;
        for (Level level : LEVELS) {
            Map<String, JsonNode> before = index(baseline.path(level.arrayField()), level.idField());
            Map<String, JsonNode> after = index(current.path(level.arrayField()), level.idField());
            var ids = new java.util.TreeSet<String>();
            ids.addAll(before.keySet());
            ids.addAll(after.keySet());
            for (String id : ids) {
                JsonNode left = before.get(id);
                JsonNode right = after.get(id);
                BigDecimal beforePoints = decimal(left, "earned_points");
                BigDecimal afterPoints = decimal(right, "earned_points");
                BigDecimal delta = afterPoints.subtract(beforePoints);
                String state = delta.signum() > 0 ? "IMPROVED" : delta.signum() < 0 ? "REGRESSED" : "UNCHANGED";
                if ("IMPROVED".equals(state)) improved++;
                else if ("REGRESSED".equals(state)) regressed++;
                else unchanged++;
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("level", level.name());
                value.put("node_id", id);
                value.put("state", state);
                value.put("baseline_earned_points", beforePoints);
                value.put("current_earned_points", afterPoints);
                value.put("delta_points", delta);
                value.put("baseline_outcome", text(left, "outcome", "NOT_DISCOVERED"));
                value.put("current_outcome", text(right, "outcome", "NOT_DISCOVERED"));
                value.put("diagnosis", diagnosis(state, id, delta));
                value.put("improvement_guide", right == null
                        ? "현재 검증 Profile에서 항목이 사라진 원인을 검토하고 의도된 범위 변경인지 승인하십시오."
                        : text(right, "improvement_guide", "동일 조건의 재검증 증적을 검토하십시오."));
                changes.add(Map.copyOf(value));
            }
        }
        changes.sort(Comparator.comparing(value -> value.get("level") + ":" + value.get("node_id")));
        BigDecimal baselineTotal = decimal(baseline, "earned_points");
        BigDecimal currentTotal = decimal(current, "earned_points");
        BigDecimal totalDelta = currentTotal.subtract(baselineTotal);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contract", CONTRACT);
        result.put("comparison_type", "SAME_TARGET_NONFINAL_EVIDENCE_COVERAGE");
        result.put("baseline_run_id", baselineRunId);
        result.put("current_run_id", currentRunId);
        result.put("baseline_earned_points", baselineTotal);
        result.put("current_earned_points", currentTotal);
        result.put("total_delta_points", totalDelta);
        result.put("state", totalDelta.signum() > 0 ? "IMPROVED"
                : totalDelta.signum() < 0 ? "REGRESSED" : "UNCHANGED");
        result.put("improved_node_count", improved);
        result.put("regressed_node_count", regressed);
        result.put("unchanged_node_count", unchanged);
        result.put("changes", List.copyOf(changes));
        result.put("limitations", List.of(
                "A score increase is not a final assurance decision.",
                "Changed source or environment digests require separate causal review.",
                "Independent OTester and OAudit remain separate gates."));
        result.put("final_claim_allowed", false);
        return Map.copyOf(result);
    }

    private static void requireScorecard(JsonNode value) {
        if (value == null || !value.isObject()
                || !ValidationScorecard.CONTRACT.equals(value.path("contract").asText())) {
            throw new IllegalArgumentException("VALIDATION_SCORECARD_INVALID");
        }
    }

    private static Map<String, JsonNode> index(JsonNode values, String idField) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        if (!values.isArray()) return result;
        for (JsonNode value : values) {
            String id = value.path(idField).asText("");
            if (!id.isBlank()) result.put(id, value);
        }
        return result;
    }

    private static BigDecimal decimal(JsonNode value, String field) {
        return value == null ? BigDecimal.ZERO : value.path(field).decimalValue();
    }

    private static String text(JsonNode value, String field, String fallback) {
        if (value == null) return fallback;
        String result = value.path(field).asText("");
        return result.isBlank() ? fallback : result;
    }

    private static String diagnosis(String state, String id, BigDecimal delta) {
        return switch (state) {
            case "IMPROVED" -> id + " 증적 커버리지가 " + delta + "점 개선됐습니다. source·환경 변경과 증적을 함께 검토해야 개선 원인을 확정할 수 있습니다.";
            case "REGRESSED" -> id + " 증적 커버리지가 " + delta.abs() + "점 감소했습니다. 실패·차단·미실행 전환과 환경 차이를 우선 확인하십시오.";
            default -> id + " 점수는 동일합니다. 동일 점수라도 outcome·digest·실행시간 변화가 있는지 증적을 확인하십시오.";
        };
    }

    private record Level(String name, String arrayField, String idField) {}
}
