package io.onsure.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Produces digest-bound, non-scoring comparisons between consecutive inferred E2E executions. */
final class InferredE2ERunComparisonService {
    static final String CONTRACT = "ONSURE_INFERRED_E2E_RUN_COMPARISON_V1";
    private static final long MAX_FILE_BYTES = 2_097_152L;
    private final Path workspaceRoot;
    private final Clock clock;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    InferredE2ERunComparisonService(Path workspaceRoot) {
        this(workspaceRoot, Clock.systemUTC());
    }

    InferredE2ERunComparisonService(Path workspaceRoot, Clock clock) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.clock = clock;
    }

    Map<String, Object> record(Map<String, Object> currentPlan, Map<String, Object> currentReceipt,
            String currentReceiptSha, LocalProgramUnderstandingApprovalService approvals) throws Exception {
        requireCurrent(currentPlan, currentReceipt, currentReceiptSha);
        String currentAuthorization = currentPlan.get("execution_authorization_id").toString();
        String requestId = currentPlan.get("approval_request_id").toString();
        Baseline baseline = baseline(currentPlan, currentAuthorization, approvals);
        Map<String, Object> comparison = compare(currentPlan, currentReceipt, currentReceiptSha, baseline);
        Path file = authorizationDirectory(currentAuthorization).resolve("runtime-comparison.json");
        write(file, comparison);
        String sha = Hashing.file(file);
        String relative = workspaceRoot.relativize(file).toString().replace('\\', '/');
        approvals.attachExecutionComparison(requestId, currentReceipt.get("run_id").toString(), relative, sha);
        Map<String, Object> result = new LinkedHashMap<>(comparison);
        result.put("runtime_comparison_sha256", sha);
        result.put("runtime_comparison_file", relative);
        return Map.copyOf(result);
    }

    Map<String, Object> history(String targetId, int limit) throws Exception {
        List<Map<String, Object>> values = new ArrayList<>();
        LocalProgramUnderstandingApprovalService approvals =
                new LocalProgramUnderstandingApprovalService(workspaceRoot);
        @SuppressWarnings("unchecked") List<Map<String, Object>> requests =
                (List<Map<String, Object>>) approvals.list(100).get("requests");
        for (Map<String, Object> request : requests) {
            if (values.size() >= Math.max(1, Math.min(limit, 50))) break;
            if (!targetId.equals(request.get("target_id"))
                    || !"EXECUTION_COMPLETED".equals(request.get("state"))) continue;
            Map<String, Object> value = new LinkedHashMap<>();
            for (String key : List.of("request_id", "execution_authorization_id", "execution_run_id",
                    "execution_state", "execution_started_at", "execution_completed_at",
                    "runtime_receipt_sha256", "recovery_count", "last_recovery_action")) {
                if (request.containsKey(key)) value.put(key, request.get(key));
            }
            if (request.containsKey("runtime_comparison_file")
                    && request.containsKey("runtime_comparison_sha256")) {
                Path file = workspaceRoot.resolve(request.get("runtime_comparison_file").toString()).normalize();
                if (safeFile(file) && Hashing.file(file).equals(request.get("runtime_comparison_sha256"))) {
                    Map<String, Object> comparison = mapper.readValue(file.toFile(), new TypeReference<>() {});
                    if (CONTRACT.equals(comparison.get("contract"))) value.put("comparison", comparison);
                    else value.put("comparison", Map.of("state", "INVALID_CONTRACT"));
                } else value.put("comparison", Map.of("state", "STALE_OR_TAMPERED"));
            } else value.put("comparison", Map.of("state", "NOT_RUN"));
            value.put("final_claim_allowed", false);
            values.add(Map.copyOf(value));
        }
        return Map.of("contract", "ONSURE_INFERRED_E2E_RUN_HISTORY_V1", "runs", List.copyOf(values),
                "run_count", values.size(), "final_claim_allowed", false);
    }

    private Baseline baseline(Map<String, Object> currentPlan, String currentAuthorization,
            LocalProgramUnderstandingApprovalService approvals) throws Exception {
        @SuppressWarnings("unchecked") List<Map<String, Object>> requests =
                (List<Map<String, Object>>) approvals.list(100).get("requests");
        List<Map<String, Object>> candidates = requests.stream().filter(value ->
                "EXECUTION_COMPLETED".equals(value.get("state"))
                        && currentPlan.get("target_id").equals(value.get("target_id"))
                        && currentPlan.get("source_sha256").equals(value.get("source_sha256"))
                        && currentPlan.get("profile_file_sha256").equals(value.get("profile_file_sha256"))
                        && !currentAuthorization.equals(value.get("execution_authorization_id")))
                .sorted(Comparator.comparing(value -> Instant.parse(
                        value.get("execution_completed_at").toString()), Comparator.reverseOrder()))
                .toList();
        for (Map<String, Object> candidate : candidates) {
            Path planFile = workspaceRoot.resolve(candidate.get("execution_plan_file").toString()).normalize();
            Path receiptFile = planFile.resolveSibling("runtime-receipt.json");
            if (!safeFile(planFile) || !safeFile(receiptFile)
                    || !Hashing.file(planFile).equals(candidate.get("execution_plan_sha256"))
                    || !Hashing.file(receiptFile).equals(candidate.get("runtime_receipt_sha256"))) continue;
            Map<String, Object> receipt = mapper.readValue(receiptFile.toFile(), new TypeReference<>() {});
            Map<String, Object> baselinePlan = mapper.readValue(planFile.toFile(), new TypeReference<>() {});
            if (!LocalInferredE2EHttpRunner.CONTRACT.equals(receipt.get("contract"))
                    || !"ONSURE_INFERRED_E2E_EXECUTION_AUTHORIZATION_V1".equals(baselinePlan.get("contract"))
                    || !candidate.get("execution_run_id").equals(receipt.get("run_id"))
                    || !candidate.get("execution_authorization_id").equals(receipt.get("execution_authorization_id"))
                    || !candidate.get("execution_plan_sha256").equals(receipt.get("execution_plan_sha256"))
                    || !candidate.get("target_id").equals(baselinePlan.get("target_id"))
                    || !candidate.get("source_sha256").equals(baselinePlan.get("source_sha256"))) continue;
            return new Baseline(candidate, receipt, Hashing.file(receiptFile));
        }
        return null;
    }

    private Map<String, Object> compare(Map<String, Object> plan, Map<String, Object> current,
            String currentSha, Baseline baseline) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contract", CONTRACT);
        result.put("target_id", plan.get("target_id"));
        result.put("source_sha256", plan.get("source_sha256"));
        result.put("profile_file_sha256", plan.get("profile_file_sha256"));
        result.put("current_run_id", current.get("run_id"));
        result.put("current_execution_authorization_id", plan.get("execution_authorization_id"));
        result.put("current_runtime_receipt_sha256", currentSha);
        result.put("current_outcome", current.get("outcome"));
        result.put("compared_at", clock.instant().toString());
        if (baseline == null) {
            result.put("state", "NOT_RUN_NO_COMPARABLE_BASELINE");
            result.put("baseline_run_id", "NOT_RUN");
            result.put("step_comparisons", List.of());
            result.put("improved_step_count", 0); result.put("regressed_step_count", 0);
            result.put("unchanged_step_count", 0); result.put("changed_inconclusive_step_count", 0);
            result.put("added_step_count", stepMap(current).size()); result.put("removed_step_count", 0);
            result.put("diagnosis", "동일 source/profile의 이전 실제 E2E Receipt가 없어 비교하지 않았습니다.");
            result.put("improvement_guide", "동일한 source/profile과 격리 조건으로 후속 실행해 비교 기준선을 만드십시오.");
        } else {
            result.put("state", "COMPARISON_AVAILABLE_NONFINAL");
            result.put("baseline_run_id", baseline.receipt().get("run_id"));
            result.put("baseline_execution_authorization_id", baseline.approval().get("execution_authorization_id"));
            result.put("baseline_runtime_receipt_sha256", baseline.receiptSha());
            result.put("baseline_outcome", baseline.receipt().get("outcome"));
            List<Map<String, Object>> steps = compareSteps(baseline.receipt(), current);
            result.put("step_comparisons", steps);
            for (String state : List.of("IMPROVED", "REGRESSED", "UNCHANGED", "CHANGED_INCONCLUSIVE",
                    "ADDED", "REMOVED")) {
                long count = steps.stream().filter(step -> state.equals(step.get("change"))).count();
                result.put(state.toLowerCase() + "_step_count", count);
            }
            long regressed = ((Number) result.get("regressed_step_count")).longValue();
            long improved = ((Number) result.get("improved_step_count")).longValue();
            result.put("overall_change", regressed > 0 ? "REGRESSED" : improved > 0 ? "IMPROVED"
                    : "NO_PROVEN_CHANGE");
            result.put("diagnosis", regressed > 0
                    ? "이전 PASS_NONFINAL 단계 중 현재 비통과 단계가 발견됐습니다."
                    : improved > 0 ? "이전 비통과 단계가 실제 Receipt 기준으로 개선됐습니다."
                    : "실행 증적 기준으로 입증된 개선 또는 퇴행이 없습니다.");
            result.put("improvement_guide", regressed > 0
                    ? "REGRESSED 단계의 status/schema 오류와 대상 로그를 확인하고 동일 조건으로 재검증하십시오."
                    : "환경·fixture·source digest를 유지해 비교 가능성을 보존하십시오.");
        }
        result.put("score_eligible", false);
        result.put("comparison_is_final_assurance", false);
        result.put("final_claim_allowed", false);
        return Map.copyOf(result);
    }

    private static List<Map<String, Object>> compareSteps(Map<String, Object> baseline,
            Map<String, Object> current) {
        Map<String, Map<String, Object>> before = stepMap(baseline);
        Map<String, Map<String, Object>> after = stepMap(current);
        Set<String> ids = new java.util.TreeSet<>(); ids.addAll(before.keySet()); ids.addAll(after.keySet());
        List<Map<String, Object>> result = new ArrayList<>();
        for (String id : ids) {
            Map<String, Object> old = before.get(id); Map<String, Object> now = after.get(id);
            String oldOutcome = old == null ? "NOT_RUN" : old.getOrDefault("oracle_outcome", "NOT_RUN").toString();
            String nowOutcome = now == null ? "NOT_RUN" : now.getOrDefault("oracle_outcome", "NOT_RUN").toString();
            String change = old == null ? "ADDED" : now == null ? "REMOVED"
                    : "PASS_NONFINAL".equals(nowOutcome) && !"PASS_NONFINAL".equals(oldOutcome) ? "IMPROVED"
                    : "PASS_NONFINAL".equals(oldOutcome) && !"PASS_NONFINAL".equals(nowOutcome) ? "REGRESSED"
                    : oldOutcome.equals(nowOutcome) ? "UNCHANGED" : "CHANGED_INCONCLUSIVE";
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("plan_id", id); value.put("baseline_outcome", oldOutcome);
            value.put("current_outcome", nowOutcome); value.put("change", change);
            if (old != null && old.containsKey("response_status")) value.put("baseline_response_status", old.get("response_status"));
            if (now != null && now.containsKey("response_status")) value.put("current_response_status", now.get("response_status"));
            if (old != null && old.containsKey("response_body_sha256") && now != null
                    && now.containsKey("response_body_sha256"))
                value.put("response_digest_changed", !old.get("response_body_sha256").equals(now.get("response_body_sha256")));
            value.put("diagnosis", diagnosis(change)); value.put("improvement_guide", guide(change));
            value.put("final_claim_allowed", false);
            result.add(Map.copyOf(value));
        }
        return List.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> stepMap(Map<String, Object> receipt) {
        Map<String, Map<String, Object>> result = new TreeMap<>();
        Object raw = receipt.get("steps");
        if (!(raw instanceof List<?> steps)) return result;
        for (Object item : steps) if (item instanceof Map<?, ?> step && step.get("plan_id") != null)
            result.put(step.get("plan_id").toString(), (Map<String, Object>) step);
        return result;
    }

    private static String diagnosis(String change) {
        return switch (change) {
            case "IMPROVED" -> "이전 비통과가 현재 실제 실행에서 PASS_NONFINAL로 변경됐습니다.";
            case "REGRESSED" -> "이전 PASS_NONFINAL이 현재 비통과로 변경됐습니다.";
            case "UNCHANGED" -> "판정 상태가 이전 실행과 동일합니다.";
            case "ADDED" -> "현재 실행에 새 단계가 추가됐으며 이전 비교값은 없습니다.";
            case "REMOVED" -> "이전 실행 단계가 현재 Receipt에서 사라졌습니다.";
            default -> "비통과 상태가 달라졌으나 개선 또는 퇴행으로 단정할 수 없습니다.";
        };
    }

    private static String guide(String change) {
        return switch (change) {
            case "REGRESSED" -> "현재 status, schema 오류 및 실행환경 digest를 기준선과 대조하십시오.";
            case "IMPROVED" -> "개선 원인과 변경 SHA를 보존하고 전체 회귀에서도 재현되는지 확인하십시오.";
            case "REMOVED" -> "계약 또는 추론 계획에서 단계가 제거된 이유를 검토하십시오.";
            case "ADDED" -> "후속 동일 조건 실행으로 새 단계의 안정성을 비교하십시오.";
            default -> "동일 source/profile과 합성 조건을 유지해 추세를 누적하십시오.";
        };
    }

    private void requireCurrent(Map<String, Object> plan, Map<String, Object> receipt, String sha) throws Exception {
        if (!"ONSURE_INFERRED_E2E_EXECUTION_AUTHORIZATION_V1".equals(plan.get("contract"))
                || !LocalInferredE2EHttpRunner.CONTRACT.equals(receipt.get("contract"))
                || !plan.get("execution_authorization_id").equals(receipt.get("execution_authorization_id"))
                || !sha.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("INFERRED_E2E_COMPARISON_CURRENT_BINDING_INVALID");
        Path file = authorizationDirectory(plan.get("execution_authorization_id").toString())
                .resolve("runtime-receipt.json");
        Path planFile = file.resolveSibling("execution-plan.json");
        if (!safeFile(file) || !safeFile(planFile) || !Hashing.file(file).equals(sha)
                || !Hashing.file(planFile).equals(receipt.get("execution_plan_sha256")))
            throw new IllegalArgumentException("INFERRED_E2E_COMPARISON_CURRENT_BINDING_INVALID");
    }

    private Path authorizationDirectory(String authorizationId) {
        if (!authorizationId.matches("inferred-e2e-auth-[0-9a-f-]{36}"))
            throw new IllegalArgumentException("INFERRED_E2E_COMPARISON_AUTHORIZATION_INVALID");
        return workspaceRoot.resolve(".onsure/inferred-e2e-authorizations").resolve(authorizationId).normalize();
    }

    private boolean safeFile(Path file) {
        return file.startsWith(workspaceRoot) && noSymlinkComponents(file)
                && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(file) && size(file) <= MAX_FILE_BYTES;
    }

    private boolean noSymlinkComponents(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(workspaceRoot)) return false;
        Path current = workspaceRoot;
        for (Path part : workspaceRoot.relativize(normalized)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) return false;
        }
        return true;
    }

    private static long size(Path file) {
        try { return Files.size(file); } catch (Exception ignored) { return Long.MAX_VALUE; }
    }

    private void write(Path file, Object value) throws Exception {
        if (!file.startsWith(workspaceRoot.resolve(".onsure/inferred-e2e-authorizations").normalize()))
            throw new IllegalArgumentException("INFERRED_E2E_COMPARISON_PATH_INVALID");
        if (!Files.isDirectory(file.getParent(), LinkOption.NOFOLLOW_LINKS)
                || !noSymlinkComponents(file.getParent()))
            throw new IllegalArgumentException("INFERRED_E2E_COMPARISON_PATH_INVALID");
        Path temporary = file.resolveSibling(file.getFileName() + "." + UUID.randomUUID() + ".tmp");
        mapper.writeValue(temporary.toFile(), value);
        try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }

    private record Baseline(Map<String, Object> approval, Map<String, Object> receipt, String receiptSha) { }
}
