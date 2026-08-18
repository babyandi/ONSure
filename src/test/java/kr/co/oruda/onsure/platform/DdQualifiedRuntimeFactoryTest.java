package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DdQualifiedRuntimeFactoryTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path tmp;

    @Test
    void absentActivationKeepsBuiltInsUnqualified() throws Exception {
        var runtime = DdQualifiedRuntimeFactory.loadOrUnqualified(tmp);
        var request = JSON.readTree("{\"dd_id\":\"DD-001\",\"evidence_refs\":[\"e:test\"]}");
        var result = runtime.execute("assurance.visibility-evidence.evaluate", request);
        assertEquals("HOLD", result.get("decision"));
        assertEquals("IMPLEMENTED_UNQUALIFIED", result.get("semantic_evaluator_state"));
    }

    @Test
    void partialActivationFailsClosedInsteadOfDowngradingSilently() throws Exception {
        writeActivation(39);
        assertThrows(IllegalStateException.class, () -> DdQualifiedRuntimeFactory.loadOrUnqualified(tmp));
    }

    @Test
    void exactActivationAndDigestBoundEvidenceCanExecuteQualifiedPredicate() throws Exception {
        writeActivation(40);
        Path evidence = tmp.resolve("evidence/dd001.json");
        Files.createDirectories(evidence.getParent());
        Files.writeString(evidence, "{\"facts\":{\"mandatory_dimensions\":[\"a\",\"b\"],\"observed_dimensions\":[\"a\",\"b\",\"c\"]}}", StandardCharsets.UTF_8);
        writeEvidenceIndex(evidence, sha256(Files.readAllBytes(evidence)));
        var runtime = DdQualifiedRuntimeFactory.loadOrUnqualified(tmp);
        var request = JSON.readTree("{\"dd_id\":\"DD-001\",\"evidence_refs\":[\"e:dd001\"]}");
        var result = runtime.execute("assurance.visibility-evidence.evaluate", request);
        assertEquals("PASS_NONFINAL", result.get("decision"));
        assertEquals(Boolean.FALSE, result.get("final_claim_allowed"));
    }

    @Test
    void wrongEvidenceDigestCannotDrivePositiveClaim() throws Exception {
        writeActivation(40);
        Path evidence = tmp.resolve("evidence/dd001.json");
        Files.createDirectories(evidence.getParent());
        Files.writeString(evidence, "{\"facts\":{\"mandatory_dimensions\":[\"a\"],\"observed_dimensions\":[\"a\"]}}", StandardCharsets.UTF_8);
        writeEvidenceIndex(evidence, "0".repeat(64));
        var runtime = DdQualifiedRuntimeFactory.loadOrUnqualified(tmp);
        var request = JSON.readTree("{\"dd_id\":\"DD-001\",\"evidence_refs\":[\"e:dd001\"]}");
        var result = runtime.execute("assurance.visibility-evidence.evaluate", request);
        assertEquals("HOLD", result.get("decision"));
    }

    private void writeActivation(int count) throws Exception {
        Path dir = tmp.resolve(".onsure/dd-runtime"); Files.createDirectories(dir);
        List<Map<String,Object>> rows = new ArrayList<>();
        for (int n=1;n<=count;n++) {
            String dd=String.format("DD-%03d",n);
            Map<String,Object> row=new LinkedHashMap<>();
            row.put("dd_id",dd); row.put("evaluator_id","builtin-"+dd.toLowerCase());
            row.put("evaluator_version",BuiltInDdSemanticEvaluators.VERSION);
            row.put("qualification_receipt_digest","a".repeat(64));
            row.put("qualification_current",true); row.put("independent_qualification",true); rows.add(row);
        }
        Map<String,Object> doc=new LinkedHashMap<>();
        doc.put("contract",DdQualifiedRuntimeFactory.CONTRACT); doc.put("qualified_count",count);
        doc.put("rows",rows); doc.put("final_claim_allowed",false);
        Files.writeString(dir.resolve("activation.json"),JSON.writeValueAsString(doc),StandardCharsets.UTF_8);
    }

    private void writeEvidenceIndex(Path evidence, String digest) throws Exception {
        Path dir=tmp.resolve(".onsure/dd-runtime"); Files.createDirectories(dir);
        Map<String,Object> row=new LinkedHashMap<>(); row.put("evidence_ref","e:dd001");
        row.put("path",tmp.relativize(evidence).toString().replace('\\','/')); row.put("sha256",digest);
        row.put("current",true); row.put("authority_ref","authority:test");
        Map<String,Object> doc=new LinkedHashMap<>(); doc.put("contract",FileBackedDdEvidenceResolver.CONTRACT);
        doc.put("source_commit_sha","0".repeat(40)); doc.put("rows",List.of(row)); doc.put("final_claim_allowed",false);
        Files.writeString(dir.resolve("evidence-index.json"),JSON.writeValueAsString(doc),StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] d=MessageDigest.getInstance("SHA-256").digest(bytes); StringBuilder sb=new StringBuilder();
        for(byte b:d) sb.append(String.format("%02x",b)); return sb.toString();
    }
}
