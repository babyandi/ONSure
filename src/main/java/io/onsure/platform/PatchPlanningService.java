package io.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
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

/** Parses a unified diff into a bounded Patch Plan and records file/hunk approval. */
public final class PatchPlanningService {
    public static final String CONTRACT = "ONSURE_PATCH_PLAN_V1";
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public Map<String, Object> create(
            Path diagnosisFile,
            Path patchFile,
            String sourceRef,
            Path output) throws Exception {
        JsonNode diagnosis = mapper.readTree(diagnosisFile.toFile());
        if (!"ONSURE_DIAGNOSIS_V1".equals(diagnosis.path("contract").asText())
                || !"RCA_CONFIRMED".equals(diagnosis.path("state").asText())) {
            throw new IllegalArgumentException("PATCH_REQUIRES_CONFIRMED_RCA");
        }
        requireGitRef(sourceRef);
        if (!Files.isRegularFile(patchFile)) throw new IllegalArgumentException("PATCH_FILE_MISSING");
        String patch = Files.readString(patchFile, StandardCharsets.UTF_8);
        if (patch.isBlank() || !patch.contains("diff --git ")) {
            throw new IllegalArgumentException("UNIFIED_PATCH_INVALID");
        }
        String patchDigest = Hashing.sha256(patch.getBytes(StandardCharsets.UTF_8));
        ParseResult parsed = parse(patch);
        if (parsed.hunks().isEmpty() || parsed.files().isEmpty()) {
            throw new IllegalArgumentException("PATCH_HAS_NO_FILE_OR_HUNK");
        }
        String risk = risk(parsed.files(), patch);
        String planId = "PATCH-PLAN-" + Hashing.sha256(
                diagnosis.path("diagnosis_id").asText() + "|" + sourceRef + "|" + patchDigest)
                .substring(0, 20);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("contract", CONTRACT);
        value.put("patch_plan_id", planId);
        value.put("diagnosis_id", diagnosis.path("diagnosis_id").asText());
        value.put("source_ref", sourceRef);
        value.put("patch_sha256", patchDigest);
        value.put("changed_files", parsed.files().stream().sorted().toList());
        value.put("hunks", parsed.hunks());
        value.put("risk", risk);
        value.put("rollback_ref", sourceRef);
        value.put("approval", approval("AWAITING_APPROVAL", Set.of(), null, null));
        value.put("state", "AWAITING_PATCH_APPROVAL");
        value.put("created_at", Instant.now().toString());
        value.put("final_claim_allowed", false);
        write(output, value);
        return Map.copyOf(value);
    }

    public Map<String, Object> approve(
            Path patchPlanFile,
            String approver,
            Set<String> approvedHunkIds,
            Path output) throws Exception {
        if (approver == null || !approver.matches("[A-Za-z0-9@._:-]{3,256}")) {
            throw new IllegalArgumentException("PATCH_APPROVER_INVALID");
        }
        JsonNode root = mapper.readTree(patchPlanFile.toFile());
        if (!CONTRACT.equals(root.path("contract").asText())) {
            throw new IllegalArgumentException("PATCH_PLAN_CONTRACT_INVALID");
        }
        Set<String> all = new LinkedHashSet<>();
        List<Map<String, Object>> hunks = new ArrayList<>();
        for (JsonNode hunk : root.path("hunks")) {
            String id = hunk.path("hunk_id").asText();
            all.add(id);
            Map<String, Object> mapped = mapper.convertValue(hunk, LinkedHashMap.class);
            mapped.put("approved", approvedHunkIds != null && approvedHunkIds.contains(id));
            hunks.add(Map.copyOf(mapped));
        }
        if (approvedHunkIds == null || !all.containsAll(approvedHunkIds)) {
            throw new IllegalArgumentException("PATCH_APPROVAL_HUNK_INVALID");
        }
        String state = approvedHunkIds.isEmpty() ? "REJECTED"
                : approvedHunkIds.size() == all.size() ? "APPROVED" : "PARTIALLY_APPROVED";
        String planState = approvedHunkIds.isEmpty() ? "HOLD" : "PATCH_APPROVED";
        String receipt = approvedHunkIds.isEmpty() ? null : Hashing.sha256(
                root.path("patch_plan_id").asText() + "|" + approver + "|"
                        + approvedHunkIds.stream().sorted().toList());
        Map<String, Object> approved = mapper.convertValue(root, LinkedHashMap.class);
        approved.put("hunks", hunks);
        approved.put("approval", approval(state, approvedHunkIds, approver, receipt));
        approved.put("state", planState);
        write(output, approved);
        return Map.copyOf(approved);
    }

    private static Map<String, Object> approval(
            String state, Set<String> approved, String approver, String receipt) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("state", state);
        value.put("approved_hunk_ids", approved.stream().sorted().toList());
        value.put("approved_by", approver);
        value.put("approved_at", approver == null ? null : Instant.now().toString());
        value.put("approval_receipt_sha256", receipt);
        return value;
    }

    private static ParseResult parse(String patch) {
        Set<String> files = new LinkedHashSet<>();
        List<Map<String, Object>> hunks = new ArrayList<>();
        String currentFile = null;
        String currentHeader = null;
        StringBuilder currentBody = new StringBuilder();
        for (String line : patch.split("\\R", -1)) {
            if (line.startsWith("diff --git a/")) {
                flushHunk(hunks, currentFile, currentHeader, currentBody);
                currentHeader = null;
                currentBody = new StringBuilder();
                String[] parts = line.split(" ");
                if (parts.length >= 4) {
                    currentFile = normalizeFile(parts[3].startsWith("b/")
                            ? parts[3].substring(2) : parts[3]);
                    files.add(currentFile);
                }
            } else if (line.startsWith("@@ ")) {
                flushHunk(hunks, currentFile, currentHeader, currentBody);
                currentHeader = line;
                currentBody = new StringBuilder();
                currentBody.append(line).append('\n');
            } else if (currentHeader != null) {
                currentBody.append(line).append('\n');
            }
        }
        flushHunk(hunks, currentFile, currentHeader, currentBody);
        return new ParseResult(Set.copyOf(files), List.copyOf(hunks));
    }

    private static void flushHunk(
            List<Map<String, Object>> hunks,
            String file,
            String header,
            StringBuilder body) {
        if (file == null || header == null || body.length() == 0) return;
        String digest = Hashing.sha256(file + "|" + body);
        hunks.add(Map.of(
                "hunk_id", "HUNK-" + digest.substring(0, 16),
                "file", file,
                "header", header,
                "digest", digest,
                "approved", false));
    }

    private static String normalizeFile(String value) {
        Path path = Path.of(value).normalize();
        if (path.isAbsolute() || value.contains("..") || value.startsWith("/")) {
            throw new IllegalArgumentException("PATCH_PATH_ESCAPE:" + value);
        }
        return path.toString().replace('\\', '/');
    }

    private static String risk(Set<String> files, String patch) {
        String joined = String.join("\n", files).toLowerCase();
        if (joined.matches("(?s).*(auth|security|crypto|migration|schema|permission).*")) {
            return "CRITICAL";
        }
        if (patch.lines().filter(line -> line.startsWith("+") || line.startsWith("-"))
                .count() > 300) return "HIGH";
        if (files.size() > 5) return "MEDIUM";
        return "LOW";
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

    private static void requireGitRef(String value) {
        if (value == null || !value.matches("[0-9a-f]{40}|[0-9a-f]{64}")) {
            throw new IllegalArgumentException("PATCH_SOURCE_REF_INVALID");
        }
    }

    private record ParseResult(Set<String> files, List<Map<String, Object>> hunks) {}
}
