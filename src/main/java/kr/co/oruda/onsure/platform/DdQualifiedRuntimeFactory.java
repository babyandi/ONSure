package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
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
    public static final String CONTRACT = "ONSURE_DD_QUALIFIED_RUNTIME_ACTIVATION_V2";
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
            String executionTree = doc.path("execution_tree_sha").asText("");
            String qualifiedTree = doc.path("qualified_subject_tree_sha").asText("");
            if (!executionTree.matches("[0-9a-f]{40}")) throw new IllegalStateException("DD_RUNTIME_EXECUTION_TREE_INVALID");
            if (!qualifiedTree.matches("[0-9a-f]{40}")) throw new IllegalStateException("DD_RUNTIME_QUALIFIED_SUBJECT_TREE_INVALID");

            Map<String,DdSemanticEvaluator> builtins = new LinkedHashMap<>();
            for (DdSemanticEvaluator e : BuiltInDdSemanticEvaluators.all()) builtins.put(e.ddId(), e);
            List<DdSemanticEvaluatorRegistry.Registration> registrations = new ArrayList<>();
            java.util.Set<String> seen = new java.util.LinkedHashSet<>();
            String evaluatorArtifactSha = classArtifactSha256(BuiltInDdSemanticEvaluators.class);
            for (JsonNode row : doc.path("rows")) {
                String dd = row.path("dd_id").asText("");
                if (!seen.add(dd)) throw new IllegalStateException("DD_RUNTIME_ACTIVATION_DUPLICATE:" + dd);
                DdSemanticEvaluator evaluator = builtins.get(dd);
                if (evaluator == null) throw new IllegalStateException("DD_RUNTIME_ACTIVATION_UNKNOWN_DD:" + dd);
                if (!row.path("qualification_current").asBoolean(false) || !row.path("independent_qualification").asBoolean(false)) {
                    throw new IllegalStateException("DD_RUNTIME_ACTIVATION_QUALIFICATION_FLAGS_INVALID:" + dd);
                }
                Path receiptPath = resolveUnderRoot(root,row.path("qualification_receipt_ref").asText(""));
                JsonNode receipt = JSON.readTree(Files.readAllBytes(receiptPath));
                validateQualificationReceipt(dd,evaluator,row,receipt,qualifiedTree,evaluatorArtifactSha);
                registrations.add(new DdSemanticEvaluatorRegistry.Registration(evaluator,receipt.path("evaluator_id").asText(""),receipt.path("evaluator_version").asText(""),receipt.path("receipt_digest").asText(""),true,true));
            }
            if (seen.size()!=40 || !seen.equals(builtins.keySet())) throw new IllegalStateException("DD_RUNTIME_ACTIVATION_DENOMINATOR_MISMATCH");
            var registry = new DdSemanticEvaluatorRegistry(registrations);
            if (registry.qualifiedCount()!=40) throw new IllegalStateException("DD_RUNTIME_ACTIVATION_QUALIFIED_COUNT_MISMATCH");
            return new DdAssuranceOperationRuntime(registry, FileBackedDdEvidenceResolver.load(root, executionTree));
        } catch (Exception e) {
            throw new IllegalStateException("DD_RUNTIME_ACTIVATION_LOAD_FAILED", e);
        }
    }

    private static Path resolveUnderRoot(Path root,String value){
        if(value==null||value.isBlank()) throw new IllegalStateException("DD_QUALIFICATION_RECEIPT_REF_REQUIRED");
        Path p=Path.of(value); p=(p.isAbsolute()?p:root.resolve(p)).normalize();
        if(!p.startsWith(root)||!Files.isRegularFile(p)) throw new SecurityException("DD_QUALIFICATION_RECEIPT_PATH_INVALID:"+value);
        return p;
    }

    private static void validateQualificationReceipt(String dd,DdSemanticEvaluator evaluator,JsonNode activationRow,JsonNode receipt,String qualifiedTree,String evaluatorArtifactSha) throws Exception {
        if(!QUALIFICATION_CONTRACT.equals(receipt.path("contract").asText())) throw new IllegalStateException("DD_QUALIFICATION_RECEIPT_CONTRACT_MISMATCH:"+dd);
        if(!dd.equals(receipt.path("dd_id").asText())) throw new IllegalStateException("DD_QUALIFICATION_RECEIPT_ID_MISMATCH:"+dd);
        if(!"QUALIFIED_NONFINAL".equals(receipt.path("decision").asText())) throw new IllegalStateException("DD_QUALIFICATION_RECEIPT_NOT_QUALIFIED:"+dd);
        if(receipt.path("final_claim_allowed").asBoolean(true)) throw new IllegalStateException("DD_QUALIFICATION_RECEIPT_FINAL_CLAIM_INVALID:"+dd);
        if(!qualifiedTree.equals(receipt.path("source_tree_sha").asText())) throw new IllegalStateException("DD_QUALIFICATION_RECEIPT_SUBJECT_TREE_MISMATCH:"+dd);
        String evaluatorId=receipt.path("evaluator_id").asText(""); String evaluatorVersion=receipt.path("evaluator_version").asText("");
        if(!evaluatorId.equals(activationRow.path("evaluator_id").asText(""))) throw new IllegalStateException("DD_QUALIFICATION_EVALUATOR_ID_MISMATCH:"+dd);
        if(!evaluatorVersion.equals(activationRow.path("evaluator_version").asText(""))) throw new IllegalStateException("DD_QUALIFICATION_EVALUATOR_VERSION_MISMATCH:"+dd);
        if(!BuiltInDdSemanticEvaluators.VERSION.equals(evaluatorVersion)) throw new IllegalStateException("DD_QUALIFICATION_EVALUATOR_VERSION_NOT_CURRENT:"+dd);
        if(!evaluatorArtifactSha.equals(receipt.path("evaluator_artifact_sha256").asText(""))) throw new IllegalStateException("DD_QUALIFICATION_EVALUATOR_ARTIFACT_MISMATCH:"+dd);
        requireSha256(receipt,"obligation_registry_sha256",dd); requireNonEmptyArray(receipt,"policy_authority_digests",dd); requireText(receipt,"qualification_principal",dd); requireText(receipt,"qualification_process_lineage",dd); requireNonEmptyArray(receipt,"positive_oracle_refs",dd);
        JsonNode att=receipt.path("independence_attestation");
        if(!att.path("independent_from_evaluator_authoring").asBoolean(false)||!att.path("independent_from_target_claim_author").asBoolean(false)||!att.path("common_control_disclosed").asBoolean(false)) throw new IllegalStateException("DD_QUALIFICATION_RECEIPT_INDEPENDENCE_INVALID:"+dd);
        JsonNode fixtures=receipt.path("fixture_results");
        for(String klass:List.of("positive","negative","recovery","adversarial")){
            JsonNode fr=fixtures.path(klass); int executed=fr.path("executed_count").asInt(0); int passed=fr.path("passed_count").asInt(-1); int failed=fr.path("failed_count").asInt(-1);
            if(executed<1||passed!=executed||failed!=0) throw new IllegalStateException("DD_QUALIFICATION_FIXTURE_RESULT_INVALID:"+dd+":"+klass);
            requireNonEmptyArray(fr,"fixture_ids",dd+":"+klass); requireNonEmptyArray(fr,"evidence_refs",dd+":"+klass);
        }
        Instant qualifiedAt=Instant.parse(receipt.path("qualified_at").asText("")); Instant expires=Instant.parse(receipt.path("expires_at").asText(""));
        if(!expires.isAfter(qualifiedAt)||!expires.isAfter(Instant.now())) throw new IllegalStateException("DD_QUALIFICATION_RECEIPT_EXPIRED_OR_INTERVAL_INVALID:"+dd);
        String digest=receipt.path("receipt_digest").asText("");
        if(!digest.equals(activationRow.path("qualification_receipt_digest").asText(""))) throw new IllegalStateException("DD_QUALIFICATION_RECEIPT_ACTIVATION_DIGEST_MISMATCH:"+dd);
        if(!digest.equals(canonicalReceiptDigest(receipt))) throw new IllegalStateException("DD_QUALIFICATION_RECEIPT_DIGEST_INVALID:"+dd);
    }

    private static void requireText(JsonNode node,String field,String subject){if(node.path(field).asText("").isBlank()) throw new IllegalStateException("DD_QUALIFICATION_FIELD_REQUIRED:"+subject+":"+field);}
    private static void requireSha256(JsonNode node,String field,String subject){if(!node.path(field).asText("").matches("[0-9a-f]{64}")) throw new IllegalStateException("DD_QUALIFICATION_SHA256_REQUIRED:"+subject+":"+field);}
    private static void requireNonEmptyArray(JsonNode node,String field,String subject){JsonNode value=node.path(field); if(!value.isArray()||value.isEmpty()) throw new IllegalStateException("DD_QUALIFICATION_ARRAY_REQUIRED:"+subject+":"+field);}
    private static String classArtifactSha256(Class<?> type)throws Exception{String resource=type.getSimpleName()+".class"; try(InputStream in=type.getResourceAsStream(resource)){if(in==null) throw new IllegalStateException("DD_EVALUATOR_CLASS_ARTIFACT_UNAVAILABLE"); return hex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));}}
    private static String canonicalReceiptDigest(JsonNode receipt)throws Exception{return hex(MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(canonical(receipt,true))));}
    private static String hex(byte[] hash){StringBuilder sb=new StringBuilder(hash.length*2); for(byte b:hash) sb.append(String.format("%02x",b)); return sb.toString();}
    private static Object canonical(JsonNode node,boolean root){if(node.isObject()){Map<String,Object> map=new TreeMap<>(); node.fields().forEachRemaining(e->{if(!(root&&"receipt_digest".equals(e.getKey()))) map.put(e.getKey(),canonical(e.getValue(),false));}); return map;} if(node.isArray()){List<Object> values=new ArrayList<>(); node.forEach(v->values.add(canonical(v,false))); return values;} if(node.isBoolean())return node.booleanValue(); if(node.isIntegralNumber())return node.longValue(); if(node.isFloatingPointNumber())return node.decimalValue(); if(node.isNull())return null; return node.asText();}
}
