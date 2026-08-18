package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Builds a DD runtime only from receipt-backed, independently-qualified activation rows. */
public final class DdQualifiedRuntimeFactory {
    public static final String CONTRACT = "ONSURE_DD_QUALIFIED_RUNTIME_ACTIVATION_V1";
    private static final String QUALIFICATION_CONTRACT = "ONSURE_DD_SEMANTIC_EVALUATOR_QUALIFICATION_V1";
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
            String activationTree = doc.path("source_tree_sha").asText("");
            if (!activationTree.matches("[0-9a-f]{40}")) throw new IllegalStateException("DD_RUNTIME_ACTIVATION_TREE_INVALID");

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
                if (!current || !independent) throw new IllegalStateException("DD_RUNTIME_ACTIVATION_QUALIFICATION_FLAGS_INVALID:" + dd);

                Path receiptPath = resolveUnderRoot(root, row.path("qualification_receipt_ref").asText(""));
                JsonNode receipt = JSON.readTree(Files.readAllBytes(receiptPath));
                validateQualificationReceipt(dd, evaluator, row, receipt, activationTree);
                registrations.add(new DdSemanticEvaluatorRegistry.Registration(
                        evaluator,
                        receipt.path("evaluator_id").asText(""),
                        receipt.path("evaluator_version").asText(""),
                        receipt.path("receipt_digest").asText(""),
                        true,
                        true));
            }
            if (seen.size()!=40 || !seen.equals(builtins.keySet())) throw new IllegalStateException("DD_RUNTIME_ACTIVATION_DENOMINATOR_MISMATCH");
            var registry = new DdSemanticEvaluatorRegistry(registrations);
            if (registry.qualifiedCount()!=40) throw new IllegalStateException("DD_RUNTIME_ACTIVATION_QUALIFIED_COUNT_MISMATCH");
            return new DdAssuranceOperationRuntime(registry, FileBackedDdEvidenceResolver.load(root));
        } catch (Exception e) {
            throw new IllegalStateException("DD_RUNTIME_ACTIVATION_LOAD_FAILED", e);
        }
    }

    private static Path resolveUnderRoot(Path root, String value) {
        if (value == null || value.isBlank()) throw new IllegalStateException("DD_QUALIFICATION_RECEIPT_REF_REQUIRED");
        Path p = Path.of(value);
        p = (p.isAbsolute() ? p : root.resolve(p)).normalize();
        if (!p.startsWith(root) || !Files.isRegularFile(p)) throw new SecurityException("DD_QUALIFICATION_RECEIPT_PATH_INVALID:" + value);
        return p;
    }

    private static void validateQualificationReceipt(
            String dd, DdSemanticEvaluator evaluator, JsonNode activationRow, JsonNode receipt, String activationTree) throws Exception {
        if (!QUALIFICATION_CONTRACT.equals(receipt.path("contract").asText())) throw new IllegalStateException("DD_QUALIFICATION_RECEIPT_CONTRACT_MISMATCH:" + dd);
        if (!dd.equals(receipt.path("dd_id").asText())) throw new IllegalStateException("DD_QUALIFICATION_RECEIPT_ID_MISMATCH:" + dd);
        if (!"QUALIFIED_NONFINAL".equals(receipt.path("decision").asText())) throw new IllegalStateException("DD_QUALIFICATION_RECEIPT_NOT_QUALIFIED:" + dd);
        if (receipt.path("final_claim_allowed").asBoolean(true)) throw new IllegalStateException("DD_QUALIFICATION_RECEIPT_FINAL_CLAIM_INVALID:" + dd);
        if (!activationTree.equals(receipt.path("source_tree_sha").asText())) throw new IllegalStateException("DD_QUALIFICATION_RECEIPT_TREE_MISMATCH:" + dd);
        String evaluatorId = receipt.path("evaluator_id").asText("");
        String evaluatorVersion = receipt.path("evaluator_version").asText("");
        if (!evaluatorId.equals(activationRow.path("evaluator_id").asText(""))) throw new IllegalStateException("DD_QUALIFICATION_EVALUATOR_ID_MISMATCH:" + dd);
        if (!evaluatorVersion.equals(activationRow.path("evaluator_version").asText(""))) throw new IllegalStateException("DD_QUALIFICATION_EVALUATOR_VERSION_MISMATCH:" + dd);
        if (!BuiltInDdSemanticEvaluators.VERSION.equals(evaluatorVersion)) throw new IllegalStateException("DD_QUALIFICATION_EVALUATOR_VERSION_NOT_CURRENT:" + dd);
        String digest = receipt.path("receipt_digest").asText("");
        if (!digest.equals(activationRow.path("qualification_receipt_digest").asText(""))) throw new IllegalStateException("DD_QUALIFICATION_RECEIPT_ACTIVATION_DIGEST_MISMATCH:" + dd);
        if (!digest.equals(canonicalReceiptDigest(receipt))) throw new IllegalStateException("DD_QUALIFICATION_RECEIPT_DIGEST_INVALID:" + dd);
        JsonNode att = receipt.path("independence_attestation");
        if (!att.path("independent_from_evaluator_authoring").asBoolean(false)
                || !att.path("independent_from_target_claim_author").asBoolean(false)
                || !att.path("common_control_disclosed").asBoolean(false)) {
            throw new IllegalStateException("DD_QUALIFICATION_RECEIPT_INDEPENDENCE_INVALID:" + dd);
        }
        Instant expires = Instant.parse(receipt.path("expires_at").asText(""));
        if (!expires.isAfter(Instant.now())) throw new IllegalStateException("DD_QUALIFICATION_RECEIPT_EXPIRED:" + dd);
    }

    private static String canonicalReceiptDigest(JsonNode receipt) throws Exception {
        Object canonical = canonical(receipt, true);
        byte[] bytes = JSON.writeValueAsBytes(canonical);
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static Object canonical(JsonNode node, boolean root) {
        if (node.isObject()) {
            Map<String,Object> map = new TreeMap<>();
            node.fields().forEachRemaining(e -> {
                if (!(root && "receipt_digest".equals(e.getKey()))) map.put(e.getKey(), canonical(e.getValue(), false));
            });
            return map;
        }
        if (node.isArray()) {
            List<Object> values = new ArrayList<>(); node.forEach(v -> values.add(canonical(v, false))); return values;
        }
        if (node.isBoolean()) return node.booleanValue();
        if (node.isIntegralNumber()) return node.longValue();
        if (node.isFloatingPointNumber()) return node.decimalValue();
        if (node.isNull()) return null;
        return node.asText();
    }
}
