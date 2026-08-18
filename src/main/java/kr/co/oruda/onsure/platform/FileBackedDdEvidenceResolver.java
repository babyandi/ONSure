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

/** Resolves DD evidence only from an explicitly supplied or workspace-local digest-bound evidence index. */
public final class FileBackedDdEvidenceResolver implements DdEvidenceResolver {
    public static final String CONTRACT = "ONSURE_DD_EVIDENCE_INDEX_V2";
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path workspaceRoot;
    private final Path indexPath;
    private final Map<String, Entry> byRef;

    private record Entry(String ref, Set<String> ddIds, Path path, String sha256, boolean current, String authorityRef) {}

    private FileBackedDdEvidenceResolver(Path workspaceRoot, Path indexPath, Map<String, Entry> byRef) {
        this.workspaceRoot = workspaceRoot;
        this.indexPath = indexPath;
        this.byRef = Map.copyOf(byRef);
    }

    public static FileBackedDdEvidenceResolver load(Path workspaceRoot) {
        return load(workspaceRoot, null);
    }

    public static FileBackedDdEvidenceResolver load(Path workspaceRoot, String expectedTreeSha) {
        Path root = workspaceRoot.toAbsolutePath().normalize();
        String supplied = System.getenv("ONSURE_DD_EVIDENCE_INDEX");
        Path index = supplied == null || supplied.isBlank()
                ? root.resolve(".onsure/dd-runtime/evidence-index.json")
                : Path.of(supplied);
        if (!index.isAbsolute()) index = root.resolve(index);
        return load(workspaceRoot, expectedTreeSha, index.normalize());
    }

    public static FileBackedDdEvidenceResolver load(Path workspaceRoot, String expectedTreeSha, Path indexPath) {
        try {
            Path root = workspaceRoot.toAbsolutePath().normalize();
            Path index = indexPath.toAbsolutePath().normalize();
            if (!Files.isRegularFile(index)) return new FileBackedDdEvidenceResolver(root, index, Map.of());
            JsonNode doc = JSON.readTree(Files.readAllBytes(index));
            if (!CONTRACT.equals(doc.path("contract").asText())) throw new IllegalStateException("DD_EVIDENCE_INDEX_CONTRACT_MISMATCH");
            String tree = doc.path("source_tree_sha").asText("");
            if (!tree.matches("[0-9a-f]{40}")) throw new IllegalStateException("DD_EVIDENCE_INDEX_TREE_INVALID");
            if (expectedTreeSha != null && !expectedTreeSha.equals(tree)) throw new IllegalStateException("DD_EVIDENCE_INDEX_TREE_MISMATCH");
            String pathBase = doc.path("path_base").asText("");
            if (!Set.of("WORKSPACE_ROOT","INDEX_DIRECTORY").contains(pathBase)) throw new IllegalStateException("DD_EVIDENCE_INDEX_PATH_BASE_INVALID");
            Path relativeBase = "WORKSPACE_ROOT".equals(pathBase) ? root : index.getParent();
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
                Path declared = Path.of(rel);
                Path p = declared.isAbsolute() ? declared.normalize() : relativeBase.resolve(declared).normalize();
                if (!declared.isAbsolute() && !p.startsWith(relativeBase)) throw new SecurityException("DD_EVIDENCE_PATH_ESCAPE:" + rel);
                Entry prior = entries.putIfAbsent(ref, new Entry(ref, Set.copyOf(ddIds), p, sha, row.path("current").asBoolean(false), authority));
                if (prior != null) throw new IllegalStateException("DD_EVIDENCE_REF_DUPLICATE:" + ref);
            }
            return new FileBackedDdEvidenceResolver(root, index, entries);
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
