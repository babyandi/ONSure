package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Resolves DD evidence only from a workspace-local digest-bound evidence index. */
public final class FileBackedDdEvidenceResolver implements DdEvidenceResolver {
    public static final String CONTRACT = "ONSURE_DD_EVIDENCE_INDEX_V2";
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path workspaceRoot;
    private final Map<String, Entry> byRef;

    private record Entry(String ref, Set<String> ddIds, Path path, String sha256, boolean current, String authorityRef) {}

    private FileBackedDdEvidenceResolver(Path workspaceRoot, Map<String, Entry> byRef) {
        this.workspaceRoot = workspaceRoot;
        this.byRef = Map.copyOf(byRef);
    }

    public static FileBackedDdEvidenceResolver load(Path workspaceRoot) {
        return load(workspaceRoot, null);
    }

    public static FileBackedDdEvidenceResolver load(Path workspaceRoot, String expectedTreeSha) {
        try {
            Path root = workspaceRoot.toAbsolutePath().normalize();
            Path index = root.resolve(".onsure/dd-runtime/evidence-index.json").normalize();
            if (!Files.isRegularFile(index)) return new FileBackedDdEvidenceResolver(root, Map.of());
            JsonNode doc = JSON.readTree(Files.readAllBytes(index));
            if (!CONTRACT.equals(doc.path("contract").asText())) throw new IllegalStateException("DD_EVIDENCE_INDEX_CONTRACT_MISMATCH");
            String tree = doc.path("source_tree_sha").asText("");
            if (!tree.matches("[0-9a-f]{40}")) throw new IllegalStateException("DD_EVIDENCE_INDEX_TREE_INVALID");
            if (expectedTreeSha != null && !expectedTreeSha.equals(tree)) throw new IllegalStateException("DD_EVIDENCE_INDEX_TREE_MISMATCH");
            Map<String, Entry> entries = new LinkedHashMap<>();
            for (JsonNode row : doc.path("rows")) {
                String ref = row.path("evidence_ref").asText("");
                String rel = row.path("path").asText("");
                String sha = row.path("sha256").asText("");
                String authority = row.path("authority_ref").asText("UNRESOLVED");
                Set<String> ddIds = new LinkedHashSet<>();
                for (JsonNode id : row.path("dd_ids")) {
                    String dd = id.asText("");
                    if (!dd.matches("DD-(00[1-9]|0[1-3][0-9]|040)")) throw new IllegalStateException("DD_EVIDENCE_INDEX_DD_ID_INVALID:" + dd);
                    ddIds.add(dd);
                }
                if (ref.isBlank() || rel.isBlank() || !sha.matches("[0-9a-f]{64}") || ddIds.isEmpty()) throw new IllegalStateException("DD_EVIDENCE_INDEX_ROW_INVALID");
                Path p = root.resolve(rel).normalize();
                if (!p.startsWith(root)) throw new SecurityException("DD_EVIDENCE_PATH_ESCAPE:" + rel);
                Entry prior = entries.putIfAbsent(ref, new Entry(ref, Set.copyOf(ddIds), p, sha, row.path("current").asBoolean(false), authority));
                if (prior != null) throw new IllegalStateException("DD_EVIDENCE_REF_DUPLICATE:" + ref);
            }
            return new FileBackedDdEvidenceResolver(root, entries);
        } catch (Exception e) {
            throw new IllegalStateException("DD_EVIDENCE_INDEX_LOAD_FAILED", e);
        }
    }

    public Set<String> refsForDd(String ddId) {
        Set<String> refs = new LinkedHashSet<>();
        byRef.values().stream().filter(e -> e.ddIds().contains(ddId)).forEach(e -> refs.add(e.ref()));
        return Set.copyOf(refs);
    }

    @Override
    public Optional<ResolvedEvidence> resolve(String evidenceRef) {
        Entry entry = byRef.get(evidenceRef);
        if (entry == null || !Files.isRegularFile(entry.path())) return Optional.empty();
        try {
            byte[] bytes = Files.readAllBytes(entry.path());
            String actual = sha256(bytes);
            JsonNode document = JSON.readTree(bytes);
            return Optional.of(new ResolvedEvidence(entry.ref(), actual, document,
                    actual.equals(entry.sha256()), entry.current(), entry.authorityRef()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        var md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(bytes);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
