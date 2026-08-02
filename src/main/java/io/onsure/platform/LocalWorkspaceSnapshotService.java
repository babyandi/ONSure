package io.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Package-private read model for the loopback VS Code surface. */
final class LocalWorkspaceSnapshotService {
    static final String CONTRACT = "ONSURE_LOCAL_WORKSPACE_SNAPSHOT_V1";
    private static final int MAX_RUNS = 50;
    private static final int MAX_ITEMS = 200;
    private static final long MAX_JSON_BYTES = 10_485_760L;
    private static final Set<String> RUN_ARTIFACTS = Set.of(
            "job.json", "target.json", "target-metadata.json", "storage-context.json",
            "validation-report.json", "execution-plan.json", "behavior-profile.json",
            "review-result.json", "findings.json", "evidence.json", "evidence-based-rca.json",
            "failure-modes.json", "rca.json", "patch-plan.json", "remediation-plans.json",
            "fixture-results.json", "stage-results.json", "regression-lock.json",
            "improvement-proof.json", "rollback-receipt.json", "rag-preparation-candidate.json");

    private final Path workspaceRoot;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    LocalWorkspaceSnapshotService(Path workspaceRoot) {
        this.workspaceRoot = requireDirectory(workspaceRoot, "WORKSPACE_ROOT_INVALID");
    }

    Map<String, Object> snapshot(String projectId, String targetId) throws Exception {
        requireId(projectId, "PROJECT_ID_INVALID");
        requireId(targetId, "TARGET_ID_INVALID");
        @SuppressWarnings("unchecked")
        Map<String, Object> targetEnvelope = new LocalWorkflowDispatcher(workspaceRoot).dispatch(
                "project.read-target", mapper.valueToTree(Map.of(
                        "project_id", projectId, "target_id", targetId)));
        @SuppressWarnings("unchecked")
        Map<String, Object> targetResult = (Map<String, Object>) targetEnvelope.get("result");

        Path profile = workspaceRoot.resolve(
                ".onsure/profiles/" + targetId + "/program-profile.json").normalize();
        Path plan = workspaceRoot.resolve(
                ".onsure/plans/" + targetId + "-execution-plan.json").normalize();
        Path approvedPlan = workspaceRoot.resolve(
                ".onsure/plans/approved-execution-plan.json").normalize();
        Path validationRoot = workspaceRoot.resolve(".onsure/validation-data").normalize();
        List<Map<String, Object>> runs = runs(validationRoot.resolve(targetId).normalize());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contract", CONTRACT);
        result.put("project_id", projectId);
        result.put("target_id", targetId);
        result.put("registered_target", targetResult.get("registered_target"));
        result.put("catalog_revision", targetResult.get("catalog_revision"));
        result.put("profile", document(profile, "PROGRAM_PROFILE"));
        result.put("plan", document(plan, "EXECUTION_PLAN"));
        result.put("approved_plan", document(approvedPlan, "APPROVED_EXECUTION_PLAN"));
        result.put("delivery", Map.of(
                "patch_apply_receipt", document(
                        workspaceRoot.resolve(".onsure/improvement-evidence/patch-apply-receipt.json"),
                        "PATCH_APPLY_RECEIPT"),
                "improvement_proof", document(
                        workspaceRoot.resolve(".onsure/improvement-evidence/improvement-proof.json"),
                        "IMPROVEMENT_PROOF"),
                "change_set", document(
                        workspaceRoot.resolve(".onsure/git/change-set.json"), "GIT_CHANGE_SET"),
                "draft_pr_receipt", document(
                        workspaceRoot.resolve(".onsure/git/draft-pr-receipt.json"),
                        "DRAFT_PR_RECEIPT")));
        result.put("validation_store", document(
                validationRoot.resolve("store-revision.json"), "VALIDATION_STORE_REVISION"));
        result.put("runs", List.copyOf(runs));
        result.put("run_count", runs.size());
        result.put("latest_run", runs.isEmpty()
                ? Map.of("state", "NOT_PRESENT") : runs.get(0));
        result.put("assurance_class", "SELF_VALIDATION_NONFINAL");
        result.put("independent_otester", "NOT_RUN");
        result.put("independent_oaudit", "NOT_RUN");
        result.put("final_claim_allowed", false);
        return Map.copyOf(result);
    }

    private List<Map<String, Object>> runs(Path targetRoot) throws Exception {
        if (!safeDirectory(targetRoot)) return List.of();
        List<Path> roots;
        try (var stream = Files.list(targetRoot)) {
            roots = stream.filter(this::safeDirectory)
                    .sorted(Comparator.comparing(this::modified).reversed()
                            .thenComparing(path -> path.getFileName().toString(), Comparator.reverseOrder()))
                    .limit(MAX_RUNS)
                    .toList();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Path run : roots) result.add(run(run));
        return result;
    }

    private Map<String, Object> run(Path runRoot) throws Exception {
        Map<String, Object> value = new LinkedHashMap<>();
        JsonNode job = json(runRoot.resolve("job.json"));
        JsonNode report = json(runRoot.resolve("validation-report.json"));
        JsonNode findings = json(runRoot.resolve("findings.json"));
        JsonNode evidence = json(runRoot.resolve("evidence.json"));
        value.put("run_root", runRoot.toString());
        value.put("run_id", runRoot.getFileName().toString());
        value.put("job_id", text(job, "jobId", runRoot.getFileName().toString()));
        value.put("job_status", text(job, "status", "UNKNOWN"));
        value.put("decision", text(report, "decision", "NOT_AVAILABLE"));
        value.put("generated_at", text(report, "generatedAt", "NOT_AVAILABLE"));
        value.put("report_id", text(report, "reportId", "NOT_AVAILABLE"));
        value.put("findings", limitedArray(findings));
        value.put("finding_count", findings != null && findings.isArray() ? findings.size() : 0);
        value.put("evidence", limitedArray(evidence));
        value.put("evidence_count", evidence != null && evidence.isArray() ? evidence.size() : 0);
        value.put("artifacts", artifacts(runRoot));
        value.put("final_claim_allowed", false);
        return Map.copyOf(value);
    }

    private List<Map<String, Object>> artifacts(Path runRoot) throws Exception {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String name : RUN_ARTIFACTS.stream().sorted().toList()) {
            Path file = runRoot.resolve(name).normalize();
            if (!file.startsWith(runRoot) || !safeFile(file)) continue;
            long size = Files.size(file);
            if (size > MAX_JSON_BYTES) continue;
            result.add(Map.of(
                    "name", name,
                    "size_bytes", size,
                    "sha256", sha256(file),
                    "openable", true));
        }
        return List.copyOf(result);
    }

    private Map<String, Object> document(Path file, String kind) throws Exception {
        if (!file.startsWith(workspaceRoot) || !safeFile(file)) {
            return Map.of("kind", kind, "state", "NOT_PRESENT");
        }
        long size = Files.size(file);
        if (size > MAX_JSON_BYTES) {
            return Map.of("kind", kind, "state", "TOO_LARGE", "size_bytes", size);
        }
        return Map.of(
                "kind", kind,
                "state", "AVAILABLE",
                "path", file.toString(),
                "size_bytes", size,
                "sha256", sha256(file),
                "body", mapper.readTree(file.toFile()));
    }

    private JsonNode json(Path file) throws Exception {
        if (!safeFile(file) || Files.size(file) > MAX_JSON_BYTES) return null;
        return mapper.readTree(file.toFile());
    }

    private static List<JsonNode> limitedArray(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<JsonNode> result = new ArrayList<>();
        for (JsonNode value : node) {
            if (result.size() >= MAX_ITEMS) break;
            result.add(value);
        }
        return List.copyOf(result);
    }

    private static String text(JsonNode node, String field, String fallback) {
        return node == null ? fallback : node.path(field).asText(fallback);
    }

    private boolean safeDirectory(Path path) {
        return path.startsWith(workspaceRoot)
                && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(path);
    }

    private static boolean safeFile(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path);
    }

    private long modified(Path path) {
        try { return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis(); }
        catch (Exception ignored) { return 0L; }
    }

    private static Path requireDirectory(Path value, String code) {
        if (value == null) throw new IllegalArgumentException(code);
        Path path = value.toAbsolutePath().normalize();
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IllegalArgumentException(code);
        }
        return path;
    }

    private static void requireId(String value, String code) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,128}")) {
            throw new IllegalArgumentException(code);
        }
    }

    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(file)));
    }
}
