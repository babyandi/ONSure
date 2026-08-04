package io.onsure.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Writes product-neutral, ONSure-owned RAG preparation candidates without target mutation. */
public final class RagCandidatePreparer {
    public static final String CANDIDATE_CONTRACT = "ONSURE_RAG_CANDIDATE_V1";

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public Map<String, Object> prepare(RagCandidateRequest request, Path onsureManagedRoot)
            throws Exception {
        Path root = normalized(onsureManagedRoot);
        Files.createDirectories(root);

        String valueDecision = valueDecision(request);
        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("contract", CANDIDATE_CONTRACT);
        candidate.put("candidate_id", "RAG-" + request.jobId());
        candidate.put("owner", "ONSURE");
        candidate.put("source_type", "VALIDATION");
        candidate.put("target_id", request.targetId());
        candidate.put("target_source_ref", request.targetSourceReference());
        candidate.put("validation_report_id", request.reportId());
        candidate.put("validation_decision", request.validationDecision());
        candidate.put("value_decision", valueDecision);
        candidate.put("rag_environment_required", !"LOCAL_ONLY".equals(valueDecision));
        candidate.put("reason_codes", reasonCodes(request));
        candidate.put("prepared_at", Instant.now().toString());
        candidate.put("embedding_status", "NOT_RUN");
        candidate.put("rag_index_status", "NOT_CREATED");
        candidate.put("training_status", "NOT_RUN");
        candidate.put("application_status", "NOT_RUN");
        candidate.put("source_report_sha256", request.sourceReportSha256());

        writeJson(root.resolve("candidates").resolve(request.jobId() + ".json"), candidate);
        writeJson(root.resolve("receipts").resolve(request.jobId() + ".json"), Map.of(
                "contract", "ONSURE_RAG_PREPARATION_RECEIPT_V1",
                "candidate_id", candidate.get("candidate_id"),
                "owner", "ONSURE",
                "value_decision", valueDecision,
                "candidate_sha256", Sha256.digest(mapper.writeValueAsBytes(candidate)),
                "actual_ingestion_performed", false));
        return Map.copyOf(candidate);
    }

    public static String valueDecision(RagCandidateRequest request) {
        if (request.rcaCount() > 0 || request.failureModeCount() > 0) return "RAG_READY";
        if (request.findingCount() > 0 || request.nonPassingFixture()) {
            return "RAG_REVIEW_REQUIRED";
        }
        return "LOCAL_ONLY";
    }

    private static List<String> reasonCodes(RagCandidateRequest request) {
        List<String> reasons = new ArrayList<>();
        if (request.failureModeCount() > 0) reasons.add("REUSABLE_FAILURE_MODE");
        if (request.rcaCount() > 0) reasons.add("RCA_AND_REMEDIATION");
        if (request.findingCount() > 0) reasons.add("VALIDATION_FINDING");
        if (reasons.isEmpty()) reasons.add("NO_REUSABLE_KNOWLEDGE_DETECTED");
        return List.copyOf(reasons);
    }

    private void writeJson(Path file, Object value) throws Exception {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        mapper.writeValue(temporary.toFile(), value);
        move(temporary, file);
    }

    private static void move(Path source, Path target) throws Exception {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path normalized(Path path) {
        if (path == null) throw new IllegalArgumentException("RAG_ROOT_MISSING");
        return path.toAbsolutePath().normalize();
    }
}
