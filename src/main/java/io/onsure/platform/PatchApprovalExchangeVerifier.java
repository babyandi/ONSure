package io.onsure.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Verifies request/receipt/plan scope binding before the existing external-signature gate. */
final class PatchApprovalExchangeVerifier {
    static final String CONTRACT = "ONSURE_PATCH_APPROVAL_EXCHANGE_VERIFICATION_V1";
    private static final Set<String> SIGNER_FIELDS = Set.of(
            "approval_id", "nonce", "actor", "key_id", "signature_algorithm",
            "signature", "approved_at", "expires_at");
    private static final TypeReference<LinkedHashMap<String, Object>> MAP = new TypeReference<>() {};
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    Map<String, Object> verify(Path requestFile, Path receiptFile, Path planFile) throws Exception {
        Path requestPath = regular(requestFile, "REQUEST");
        Path receiptPath = regular(receiptFile, "RECEIPT");
        Path planPath = regular(planFile, "PLAN");
        Map<String, Object> request = mapper.readValue(requestPath.toFile(), MAP);
        Map<String, Object> receipt = mapper.readValue(receiptPath.toFile(), MAP);
        Map<String, Object> plan = mapper.readValue(planPath.toFile(), MAP);

        require("ONSURE_HUNK_APPROVAL_REQUEST_V1".equals(request.get("contract")), "REQUEST_CONTRACT");
        require("AWAITING_EXTERNAL_SIGNATURE".equals(request.get("request_state")), "REQUEST_STATE");
        require("ONSURE_HUNK_APPROVAL_RECEIPT_V1".equals(request.get("receipt_contract")), "REQUEST_RECEIPT_CONTRACT");
        require("ONSURE_HUNK_APPROVAL_RECEIPT_V1".equals(receipt.get("contract")), "RECEIPT_CONTRACT");
        require("ONSURE_PATCH_PLAN_V1".equals(plan.get("contract")), "PLAN_CONTRACT");
        require("PATCH_HUNK_APPROVAL".equals(request.get("approval_purpose"))
                && request.get("approval_purpose").equals(receipt.get("approval_purpose")), "PURPOSE");
        require("HUMAN_OR_EXTERNAL_APPROVER".equals(receipt.get("authority_class")), "AUTHORITY");

        String planId = text(plan, "patch_plan_id");
        require(planId.equals(request.get("patch_plan_id")) && planId.equals(receipt.get("patch_plan_id")), "PLAN_ID");
        String planSha = Hashing.file(planPath);
        require(planSha.equals(request.get("patch_plan_file_sha256"))
                && planSha.equals(receipt.get("patch_plan_file_sha256")), "PLAN_DIGEST");
        require(planPath.equals(Path.of(text(request, "patch_plan_file")).toAbsolutePath().normalize()), "PLAN_PATH");

        List<String> selected = strings(request, "selected_hunk_ids");
        List<String> approved = strings(receipt, "approved_hunk_ids");
        require(new LinkedHashSet<>(selected).size() == selected.size()
                && new LinkedHashSet<>(approved).size() == approved.size()
                && Set.copyOf(selected).equals(Set.copyOf(approved)), "HUNK_SCOPE");
        List<Map<String, Object>> hunks = maps(plan, "hunks");
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> hunk : hunks) {
            String hunkId = text(hunk, "hunk_id");
            require(byId.put(hunkId, hunk) == null, "PLAN_DUPLICATE_HUNK");
        }
        require(selected.stream().allMatch(byId::containsKey), "UNKNOWN_HUNK");
        List<String> expectedFiles = selected.stream().map(byId::get)
                .map(value -> text(value, "relative_path")).distinct().sorted().toList();
        List<String> selectedFiles = strings(request, "selected_files").stream().sorted().toList();
        require(expectedFiles.equals(selectedFiles), "FILE_SCOPE");
        String selectionScope = text(request, "selection_scope");
        require(List.of("HUNK", "FILE").contains(selectionScope), "SELECTION_SCOPE");
        if ("FILE".equals(selectionScope)) {
            Set<String> expectedWholeFileHunks = new LinkedHashSet<>();
            for (Map<String, Object> hunk : hunks) {
                if (expectedFiles.contains(text(hunk, "relative_path"))) {
                    expectedWholeFileHunks.add(text(hunk, "hunk_id"));
                }
            }
            require(Set.copyOf(selected).equals(expectedWholeFileHunks), "WHOLE_FILE_HUNK_SCOPE");
        }

        Map<String, Object> impact = map(request, "impact_scope");
        Set<String> findings = new LinkedHashSet<>();
        selected.forEach(id -> findings.add(text(byId.get(id), "finding_id")));
        require(number(impact, "file_count") == expectedFiles.size()
                && number(impact, "hunk_count") == selected.size()
                && Set.copyOf(strings(impact, "finding_ids")).equals(findings), "IMPACT_SCOPE");
        require(text(request, "branch_name").equals(text(receipt, "branch_name")), "BRANCH");

        requireFalse(request, "allow_direct_main_write", "allow_force_push", "allow_merge", "final_claim_allowed");
        requireFalse(receipt, "allow_direct_main_write", "allow_force_push", "allow_merge");
        requireFalse(plan, "direct_main_write_allowed", "force_push_allowed", "merge_allowed", "final_claim_allowed");
        Map<String, Object> rollback = map(request, "rollback_preview");
        require("PATCH_APPLY_RECEIPT_BACKUP_AND_ISOLATED_GIT_WORKTREE_REMOVAL".equals(rollback.get("method"))
                && Boolean.FALSE.equals(rollback.get("source_workspace_write_allowed"))
                && Boolean.FALSE.equals(rollback.get("automatic_rollback_executed")), "ROLLBACK_PREVIEW");
        Map<String, Object> risk = map(request, "risk_preview");
        require("BOUNDED_CHANGE_SURFACE_CANDIDATE".equals(risk.get("classification"))
                && List.of("LOW", "MEDIUM", "HIGH").contains(risk.get("level"))
                && "NOT_RUN".equals(risk.get("independent_risk_review")), "RISK_PREVIEW");
        require(Set.copyOf(strings(request, "signer_must_supply")).equals(SIGNER_FIELDS), "SIGNER_FIELDS");
        require("Ed25519".equals(receipt.get("signature_algorithm")), "SIGNATURE_ALGORITHM");
        require(!"ONSURE_AUTOMATION".equals(receipt.get("actor")), "ACTOR");
        require(text(receipt, "approval_id").length() <= 160, "APPROVAL_ID");
        require(text(receipt, "nonce").length() >= 16 && text(receipt, "nonce").length() <= 160, "NONCE");
        require(text(receipt, "key_id").length() <= 160, "KEY_ID");
        require(text(receipt, "signature").length() >= 32, "SIGNATURE_PRESENT");

        Instant created = Instant.parse(text(request, "created_at"));
        Instant approvedAt = Instant.parse(text(receipt, "approved_at"));
        Instant expiresAt = Instant.parse(text(receipt, "expires_at"));
        require(!approvedAt.isBefore(created) && expiresAt.isAfter(approvedAt), "TIME_WINDOW");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contract", CONTRACT);
        result.put("request_id", text(request, "request_id"));
        result.put("approval_id", text(receipt, "approval_id"));
        result.put("patch_plan_id", planId);
        result.put("patch_plan_file_sha256", planSha);
        result.put("bound_hunk_ids", List.copyOf(selected));
        result.put("bound_files", expectedFiles);
        result.put("state", "BOUND_PENDING_CRYPTOGRAPHIC_RECEIPT_VERIFICATION");
        result.put("external_signer_verification_included", false);
        result.put("source_workspace_write_allowed", false);
        result.put("merge_allowed", false);
        result.put("final_claim_allowed", false);
        return Map.copyOf(result);
    }

    private static Path regular(Path value, String label) {
        Path path = value.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IllegalArgumentException("PATCH_APPROVAL_" + label + "_FILE_INVALID");
        }
        return path;
    }

    private static void requireFalse(Map<String, Object> value, String... fields) {
        for (String field : fields) require(Boolean.FALSE.equals(value.get(field)), "UNSAFE_" + field.toUpperCase());
    }

    private static String text(Map<String, Object> value, String field) {
        Object item = value.get(field);
        if (!(item instanceof String text) || text.isBlank()) throw new IllegalArgumentException("PATCH_APPROVAL_" + field.toUpperCase() + "_INVALID");
        return text;
    }

    private static int number(Map<String, Object> value, String field) {
        Object item = value.get(field);
        if (!(item instanceof Number number)) throw new IllegalArgumentException("PATCH_APPROVAL_" + field.toUpperCase() + "_INVALID");
        return number.intValue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> value, String field) {
        Object item = value.get(field);
        if (!(item instanceof Map<?, ?> raw)) throw new IllegalArgumentException("PATCH_APPROVAL_" + field.toUpperCase() + "_INVALID");
        Map<String, Object> copy = new LinkedHashMap<>();
        raw.forEach((key, entry) -> copy.put(String.valueOf(key), entry));
        return copy;
    }

    private static List<Map<String, Object>> maps(Map<String, Object> value, String field) {
        Object item = value.get(field);
        if (!(item instanceof List<?> raw)) throw new IllegalArgumentException("PATCH_APPROVAL_" + field.toUpperCase() + "_INVALID");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object entry : raw) {
            if (!(entry instanceof Map<?, ?> map)) throw new IllegalArgumentException("PATCH_APPROVAL_" + field.toUpperCase() + "_INVALID");
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, nested) -> copy.put(String.valueOf(key), nested));
            result.add(copy);
        }
        return result;
    }

    private static List<String> strings(Map<String, Object> value, String field) {
        Object item = value.get(field);
        if (!(item instanceof List<?> raw) || raw.isEmpty()) throw new IllegalArgumentException("PATCH_APPROVAL_" + field.toUpperCase() + "_INVALID");
        return raw.stream().map(String::valueOf).toList();
    }

    private static void require(boolean condition, String code) {
        if (!condition) throw new IllegalArgumentException("PATCH_APPROVAL_EXCHANGE_" + code + "_INVALID");
    }
}
