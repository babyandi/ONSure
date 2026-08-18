package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DdQualifiedRuntimeFactoryTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String QUALIFIED_TREE = "1".repeat(40);
    private static final String EXECUTION_TREE = "2".repeat(40);
    @TempDir Path tmp;

    @Test void absentActivationKeepsBuiltInsUnqualified() throws Exception {
        var runtime=DdQualifiedRuntimeFactory.loadOrUnqualified(tmp);
        var request=JSON.readTree("{\"dd_id\":\"DD-001\",\"evidence_refs\":[\"e:test\"]}");
        var result=runtime.execute("assurance.visibility-evidence.evaluate",request);
        assertEquals("HOLD",result.get("decision")); assertEquals("IMPLEMENTED_UNQUALIFIED",result.get("semantic_evaluator_state"));
    }
    @Test void partialActivationFailsClosedInsteadOfDowngradingSilently() throws Exception {writeActivation(39,false,false); assertThrows(IllegalStateException.class,()->DdQualifiedRuntimeFactory.loadOrUnqualified(tmp));}
    @Test void exactReceiptBackedActivationAndDigestBoundEvidenceCanExecuteQualifiedPredicate() throws Exception {
        writeActivation(40,false,false); Path evidence=tmp.resolve("evidence/dd001.json"); Files.createDirectories(evidence.getParent()); Files.writeString(evidence,"{\"facts\":{\"mandatory_dimensions\":[\"a\",\"b\"],\"observed_dimensions\":[\"a\",\"b\",\"c\"]}}",StandardCharsets.UTF_8); writeEvidenceIndex(evidence,sha256(Files.readAllBytes(evidence)),EXECUTION_TREE);
        var runtime=DdQualifiedRuntimeFactory.loadOrUnqualified(tmp); var request=JSON.readTree("{\"dd_id\":\"DD-001\",\"evidence_refs\":[\"e:dd001\"]}"); var result=runtime.execute("assurance.visibility-evidence.evaluate",request); assertEquals("PASS_NONFINAL",result.get("decision")); assertEquals(Boolean.FALSE,result.get("final_claim_allowed"));
    }
    @Test void forgedActivationDigestIsRejected() throws Exception {writeActivation(40,true,false); assertThrows(IllegalStateException.class,()->DdQualifiedRuntimeFactory.loadOrUnqualified(tmp));}
    @Test void wrongEvaluatorArtifactDigestIsRejected() throws Exception {writeActivation(40,false,true); assertThrows(IllegalStateException.class,()->DdQualifiedRuntimeFactory.loadOrUnqualified(tmp));}
    @Test void evidenceIndexFromDifferentExecutionTreeIsRejected() throws Exception {
        writeActivation(40,false,false); Path evidence=tmp.resolve("evidence/dd001.json"); Files.createDirectories(evidence.getParent()); Files.writeString(evidence,"{\"facts\":{\"mandatory_dimensions\":[\"a\"],\"observed_dimensions\":[\"a\"]}}",StandardCharsets.UTF_8); writeEvidenceIndex(evidence,sha256(Files.readAllBytes(evidence)),"9".repeat(40)); assertThrows(IllegalStateException.class,()->DdQualifiedRuntimeFactory.loadOrUnqualified(tmp));
    }
    @Test void qualificationSubjectTreeMayDifferFromExecutionTree() throws Exception {
        writeActivation(40,false,false); Path evidence=tmp.resolve("evidence/dd001.json"); Files.createDirectories(evidence.getParent()); Files.writeString(evidence,"{\"facts\":{\"mandatory_dimensions\":[\"a\"],\"observed_dimensions\":[\"a\"]}}",StandardCharsets.UTF_8); writeEvidenceIndex(evidence,sha256(Files.readAllBytes(evidence)),EXECUTION_TREE); var runtime=DdQualifiedRuntimeFactory.loadOrUnqualified(tmp); var request=JSON.readTree("{\"dd_id\":\"DD-001\",\"evidence_refs\":[\"e:dd001\"]}"); assertEquals("PASS_NONFINAL",runtime.execute("assurance.visibility-evidence.evaluate",request).get("decision"));
    }
    @Test void wrongEvidenceDigestCannotDrivePositiveClaim() throws Exception {
        writeActivation(40,false,false); Path evidence=tmp.resolve("evidence/dd001.json"); Files.createDirectories(evidence.getParent()); Files.writeString(evidence,"{\"facts\":{\"mandatory_dimensions\":[\"a\"],\"observed_dimensions\":[\"a\"]}}",StandardCharsets.UTF_8); writeEvidenceIndex(evidence,"0".repeat(64),EXECUTION_TREE); var runtime=DdQualifiedRuntimeFactory.loadOrUnqualified(tmp); var request=JSON.readTree("{\"dd_id\":\"DD-001\",\"evidence_refs\":[\"e:dd001\"]}"); assertEquals("HOLD",runtime.execute("assurance.visibility-evidence.evaluate",request).get("decision"));
    }

    private void writeActivation(int count,boolean forgeFirstDigest,boolean forgeFirstArtifact)throws Exception{
        Path dir=tmp.resolve(".onsure/dd-runtime"); Files.createDirectories(dir); Path receipts=tmp.resolve("receipts/dd-semantic-evaluator-qualification"); Files.createDirectories(receipts); List<Map<String,Object>> rows=new ArrayList<>(); String artifactSha=classArtifactSha256(BuiltInDdSemanticEvaluators.class); Instant qualifiedAt=Instant.now().minus(1,ChronoUnit.MINUTES); Instant expiresAt=Instant.now().plus(1,ChronoUnit.DAYS);
        for(int n=1;n<=count;n++){String dd=String.format("DD-%03d",n); String evaluatorId="builtin-"+dd.toLowerCase(); Map<String,Object> receipt=new LinkedHashMap<>(); receipt.put("contract","ONSURE_DD_SEMANTIC_EVALUATOR_QUALIFICATION_V1"); receipt.put("dd_id",dd); receipt.put("evaluator_id",evaluatorId); receipt.put("evaluator_version",BuiltInDdSemanticEvaluators.VERSION); receipt.put("evaluator_artifact_sha256",forgeFirstArtifact&&n==1?"e".repeat(64):artifactSha); receipt.put("source_tree_sha",QUALIFIED_TREE); receipt.put("obligation_registry_sha256","b".repeat(64)); receipt.put("policy_authority_digests",List.of("c".repeat(64))); receipt.put("qualification_principal","independent:test-reviewer-"+n); receipt.put("qualification_process_lineage","independent:test-process-"+n); receipt.put("independence_attestation",Map.of("independent_from_evaluator_authoring",true,"independent_from_target_claim_author",true,"common_control_disclosed",true)); receipt.put("fixture_results",fixtureResults(dd)); receipt.put("positive_oracle_refs",List.of("oracle:"+dd)); receipt.put("qualified_at",qualifiedAt.toString()); receipt.put("expires_at",expiresAt.toString()); receipt.put("decision","QUALIFIED_NONFINAL"); receipt.put("final_claim_allowed",false); String digest=canonicalDigest(receipt); receipt.put("receipt_digest",digest); Path rp=receipts.resolve(dd+".json"); Files.writeString(rp,JSON.writeValueAsString(receipt),StandardCharsets.UTF_8); Map<String,Object> row=new LinkedHashMap<>(); row.put("dd_id",dd); row.put("evaluator_id",evaluatorId); row.put("evaluator_version",BuiltInDdSemanticEvaluators.VERSION); row.put("qualification_receipt_ref",tmp.relativize(rp).toString().replace('\\','/')); row.put("qualification_receipt_digest",forgeFirstDigest&&n==1?"f".repeat(64):digest); row.put("qualification_current",true); row.put("independent_qualification",true); rows.add(row);}
        Map<String,Object> doc=new LinkedHashMap<>(); doc.put("contract",DdQualifiedRuntimeFactory.CONTRACT); doc.put("execution_commit_sha","3".repeat(40)); doc.put("execution_tree_sha",EXECUTION_TREE); doc.put("qualified_subject_tree_sha",QUALIFIED_TREE); doc.put("qualified_count",count); doc.put("rows",rows); doc.put("generated_at",Instant.now().toString()); doc.put("github_actions_authority",false); doc.put("final_claim_allowed",false); Files.writeString(dir.resolve("activation.json"),JSON.writeValueAsString(doc),StandardCharsets.UTF_8);
    }
    private static Map<String,Object> fixtureResults(String dd){Map<String,Object> result=new LinkedHashMap<>(); for(String klass:List.of("positive","negative","recovery","adversarial")){result.put(klass,Map.of("executed_count",1,"passed_count",1,"failed_count",0,"fixture_ids",List.of(dd+"-"+klass.toUpperCase()),"evidence_refs",List.of("evidence:"+dd+":"+klass)));} return result;}
    private void writeEvidenceIndex(Path evidence,String digest,String tree)throws Exception{Path dir=tmp.resolve(".onsure/dd-runtime"); Files.createDirectories(dir); Map<String,Object> row=new LinkedHashMap<>(); row.put("evidence_ref","e:dd001"); row.put("dd_ids",List.of("DD-001")); row.put("path_base","WORKSPACE_ROOT"); row.put("path",tmp.relativize(evidence).toString().replace('\\','/')); row.put("sha256",digest); row.put("current",true); row.put("authority_ref","authority:test"); Map<String,Object> doc=new LinkedHashMap<>(); doc.put("contract",FileBackedDdEvidenceResolver.CONTRACT); doc.put("source_commit_sha","3".repeat(40)); doc.put("source_tree_sha",tree); doc.put("path_base","WORKSPACE_ROOT"); doc.put("rows",List.of(row)); doc.put("final_claim_allowed",false); Files.writeString(dir.resolve("evidence-index.json"),JSON.writeValueAsString(doc),StandardCharsets.UTF_8);}
    private static String classArtifactSha256(Class<?> type)throws Exception{try(InputStream in=type.getResourceAsStream(type.getSimpleName()+".class")){if(in==null)throw new IllegalStateException("TEST_CLASS_ARTIFACT_UNAVAILABLE"); return sha256(in.readAllBytes());}}
    private static String canonicalDigest(Map<String,Object> source)throws Exception{JsonNode node=JSON.valueToTree(source); return sha256(JSON.writeValueAsBytes(canonical(node)));}
    private static Object canonical(JsonNode node){if(node.isObject()){Map<String,Object> map=new TreeMap<>(); node.fields().forEachRemaining(e->map.put(e.getKey(),canonical(e.getValue()))); return map;} if(node.isArray()){List<Object> out=new ArrayList<>(); node.forEach(v->out.add(canonical(v))); return out;} if(node.isBoolean())return node.booleanValue(); if(node.isIntegralNumber())return node.longValue(); if(node.isFloatingPointNumber())return node.decimalValue(); if(node.isNull())return null; return node.asText();}
    private static String sha256(byte[] bytes)throws Exception{byte[] d=MessageDigest.getInstance("SHA-256").digest(bytes); StringBuilder sb=new StringBuilder(); for(byte b:d)sb.append(String.format("%02x",b)); return sb.toString();}
}
