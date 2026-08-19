package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Executes all 42 independently-qualified DD semantic evaluators against the current digest-bound evidence index. */
public final class DdSemanticRuntimeEvidenceMain {
    public static final String CONTRACT = "ONSURE_DD_TARGET_RUNTIME_EXECUTION_RAW_V2";
    private static final ObjectMapper JSON = new ObjectMapper();

    private DdSemanticRuntimeEvidenceMain() {}

    public static void main(String[] args) throws Exception {
        Path root = args.length > 0 ? Path.of(args[0]) : Path.of(".");
        root = root.toAbsolutePath().normalize();
        Path outputPath = args.length > 1 ? Path.of(args[1]) : root.resolve(".onsure/dd-runtime/raw-execution.json");
        if (!outputPath.isAbsolute()) outputPath = root.resolve(outputPath);
        outputPath = outputPath.normalize();
        if (!outputPath.startsWith(root)) throw new SecurityException("DD_RUNTIME_RAW_OUTPUT_PATH_ESCAPE");

        String suppliedIndex = System.getenv("ONSURE_DD_EVIDENCE_INDEX");
        Path indexPath = suppliedIndex == null || suppliedIndex.isBlank()
                ? root.resolve(".onsure/dd-runtime/evidence-index.json")
                : Path.of(suppliedIndex);
        if (!indexPath.isAbsolute()) indexPath = root.resolve(indexPath);
        indexPath = indexPath.normalize();
        if (!Files.isRegularFile(indexPath)) throw new IllegalStateException("DD_RUNTIME_EVIDENCE_INDEX_MISSING");
        JsonNode index = JSON.readTree(Files.readAllBytes(indexPath));
        if (!FileBackedDdEvidenceResolver.CONTRACT.equals(index.path("contract").asText())) {
            throw new IllegalStateException("DD_RUNTIME_EVIDENCE_INDEX_CONTRACT_MISMATCH");
        }

        Map<String,List<String>> refsByDd = new LinkedHashMap<>();
        for (int n=1;n<=42;n++) refsByDd.put(String.format("DD-%03d",n), new ArrayList<>());
        for (JsonNode row : index.path("rows")) {
            String ref = row.path("evidence_ref").asText("");
            for (JsonNode id : row.path("dd_ids")) {
                List<String> refs = refsByDd.get(id.asText());
                if (refs != null && !ref.isBlank()) refs.add(ref);
            }
        }

        DdAssuranceOperationRuntime runtime = DdQualifiedRuntimeFactory.loadOrUnqualified(root);
        List<String> operations = runtime.operations().stream().sorted(Comparator.naturalOrder()).toList();
        if (operations.size()!=42) throw new IllegalStateException("DD_RUNTIME_OPERATION_DENOMINATOR_NOT_42");
        ArrayNode rows = JSON.createArrayNode();
        for (String operation : operations) {
            String dd = runtime.ddIdFor(operation);
            List<String> refs = refsByDd.getOrDefault(dd,List.of()).stream().distinct().sorted().toList();
            ObjectNode request = JSON.createObjectNode();
            request.put("dd_id",dd);
            ArrayNode evidenceRefs = request.putArray("evidence_refs");
            refs.forEach(evidenceRefs::add);
            Map<String,Object> result = runtime.execute(operation,request);
            ObjectNode row = rows.addObject();
            row.put("dd_id",dd);
            row.put("operation",operation);
            row.set("evidence_refs",evidenceRefs.deepCopy());
            row.set("result",JSON.valueToTree(result));
        }
        ObjectNode output=JSON.createObjectNode();
        output.put("contract",CONTRACT);
        output.put("source_tree_sha",index.path("source_tree_sha").asText(""));
        output.put("evidence_index_path",indexPath.toString());
        output.put("dd_count",42);
        output.set("rows",rows);
        output.put("final_claim_allowed",false);
        Files.createDirectories(outputPath.getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(),output);
    }
}
