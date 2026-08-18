package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds a DD runtime from a separately validated qualification activation manifest. */
public final class DdQualifiedRuntimeFactory {
    public static final String CONTRACT = "ONSURE_DD_QUALIFIED_RUNTIME_ACTIVATION_V1";
    private static final ObjectMapper JSON = new ObjectMapper();

    private DdQualifiedRuntimeFactory() {}

    public static DdAssuranceOperationRuntime loadOrUnqualified(Path workspaceRoot) {
        Path root = workspaceRoot.toAbsolutePath().normalize();
        Path activation = root.resolve(".onsure/dd-runtime/activation.json");
        if (!Files.isRegularFile(activation)) return new DdAssuranceOperationRuntime();
        try {
            JsonNode doc = JSON.readTree(Files.readAllBytes(activation));
            if (!CONTRACT.equals(doc.path("contract").asText())) throw new IllegalStateException("DD_RUNTIME_ACTIVATION_CONTRACT_MISMATCH");
            if (doc.path("qualified_count").asInt(-1) != 40) throw new IllegalStateException("DD_RUNTIME_ACTIVATION_NOT_40_OF_40");
            if (doc.path("final_claim_allowed").asBoolean(true)) throw new IllegalStateException("DD_RUNTIME_ACTIVATION_FINAL_CLAIM_INVALID");
            Map<String,DdSemanticEvaluator> builtins = new LinkedHashMap<>();
            for (DdSemanticEvaluator e : BuiltInDdSemanticEvaluators.all()) builtins.put(e.ddId(), e);
            List<DdSemanticEvaluatorRegistry.Registration> registrations = new ArrayList<>();
            java.util.Set<String> seen = new java.util.LinkedHashSet<>();
            for (JsonNode row : doc.path("rows")) {
                String dd = row.path("dd_id").asText("");
                if (!seen.add(dd)) throw new IllegalStateException("DD_RUNTIME_ACTIVATION_DUPLICATE:" + dd);
                DdSemanticEvaluator evaluator = builtins.get(dd);
                if (evaluator == null) throw new IllegalStateException("DD_RUNTIME_ACTIVATION_UNKNOWN_DD:" + dd);
                boolean current = row.path("qualification_current").asBoolean(false);
                boolean independent = row.path("independent_qualification").asBoolean(false);
                registrations.add(new DdSemanticEvaluatorRegistry.Registration(
                        evaluator,
                        row.path("evaluator_id").asText(""),
                        row.path("evaluator_version").asText(""),
                        row.path("qualification_receipt_digest").asText(""),
                        current,
                        independent));
            }
            if (seen.size()!=40 || !seen.equals(builtins.keySet())) throw new IllegalStateException("DD_RUNTIME_ACTIVATION_DENOMINATOR_MISMATCH");
            var registry = new DdSemanticEvaluatorRegistry(registrations);
            if (registry.qualifiedCount()!=40) throw new IllegalStateException("DD_RUNTIME_ACTIVATION_QUALIFIED_COUNT_MISMATCH");
            return new DdAssuranceOperationRuntime(registry, FileBackedDdEvidenceResolver.load(root));
        } catch (Exception e) {
            throw new IllegalStateException("DD_RUNTIME_ACTIVATION_LOAD_FAILED", e);
        }
    }
}
