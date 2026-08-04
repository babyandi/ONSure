package io.onsure.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Target-owned, fail-closed automatic learning state machine.
 *
 * <p>This class prepares and records a learning cycle. It never calls a model or vector database
 * directly. A target-owned executor may consume an APPROVED request, but promotion and application
 * still require independent validation and rollback evidence.
 */
public final class ProgramLearningOrchestrator {
    public static final String REQUEST_CONTRACT = "TARGET_PROGRAM_LEARNING_REQUEST_V1";
    public static final String RECEIPT_CONTRACT = "TARGET_PROGRAM_LEARNING_CYCLE_RECEIPT_V1";

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public Map<String, Object> createApprovedRequest(
            Path targetRoot,
            String candidateId,
            String immutableSourceRef,
            String candidateSha256,
            boolean ownerApproved,
            boolean dataReviewPassed,
            String rollbackPlanSha256) throws Exception {
        Path environment = environment(targetRoot);
        requireEnvironment(environment);
        JsonNode policy = mapper.readTree(environment.resolve("learning/policy.json").toFile());
        if (!policy.path("automatic_learning_enabled").asBoolean(false)) {
            throw new IllegalStateException("AUTOMATIC_LEARNING_DISABLED");
        }
        if (!ownerApproved) throw new IllegalStateException("LEARNING_OWNER_APPROVAL_REQUIRED");
        if (!dataReviewPassed) throw new IllegalStateException("LEARNING_DATA_REVIEW_REQUIRED");
        requireSha("candidate_sha256", candidateSha256);
        requireSha("rollback_plan_sha256", rollbackPlanSha256);
        if (immutableSourceRef == null
                || !immutableSourceRef.matches("[0-9a-f]{40}|[0-9a-f]{64}")) {
            throw new IllegalArgumentException("IMMUTABLE_SOURCE_REFERENCE_REQUIRED");
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("contract", REQUEST_CONTRACT);
        request.put("request_id", "LR-" + candidateId);
        request.put("candidate_id", candidateId);
        request.put("immutable_source_ref", immutableSourceRef);
        request.put("candidate_sha256", candidateSha256);
        request.put("rollback_plan_sha256", rollbackPlanSha256);
        request.put("owner_approved", true);
        request.put("data_review_passed", true);
        request.put("state", "CANDIDATE_APPROVED");
        request.put("created_at", Instant.now().toString());
        request.put("actual_learning_performed", false);
        request.put("request_sha256", sha256(mapper.writeValueAsBytes(request)));
        write(environment.resolve("learning/requests/LR-" + candidateId + ".json"), request);
        return Map.copyOf(request);
    }

    public Map<String, Object> recordValidatedApplication(
            Path targetRoot,
            Map<String, Object> approvedRequest,
            String learningOutputSha256,
            String validationReceiptSha256,
            String appliedArtifactSha256,
            String postValidationReceiptSha256,
            boolean validationPassed,
            boolean postValidationPassed) throws Exception {
        Path environment = environment(targetRoot);
        requireEnvironment(environment);
        if (!REQUEST_CONTRACT.equals(approvedRequest.get("contract"))
                || !"CANDIDATE_APPROVED".equals(approvedRequest.get("state"))
                || !Boolean.TRUE.equals(approvedRequest.get("owner_approved"))
                || !Boolean.TRUE.equals(approvedRequest.get("data_review_passed"))) {
            throw new IllegalArgumentException("LEARNING_REQUEST_NOT_APPROVED");
        }
        requireSha("learning_output_sha256", learningOutputSha256);
        requireSha("validation_receipt_sha256", validationReceiptSha256);
        requireSha("applied_artifact_sha256", appliedArtifactSha256);
        requireSha("post_validation_receipt_sha256", postValidationReceiptSha256);
        if (!validationPassed) throw new IllegalStateException("LEARNING_VALIDATION_FAILED_HOLD");
        if (!postValidationPassed) {
            throw new IllegalStateException("POST_APPLICATION_VALIDATION_FAILED_ROLLBACK_REQUIRED");
        }

        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("contract", RECEIPT_CONTRACT);
        receipt.put("request_id", approvedRequest.get("request_id"));
        receipt.put("candidate_id", approvedRequest.get("candidate_id"));
        receipt.put("immutable_source_ref", approvedRequest.get("immutable_source_ref"));
        receipt.put("candidate_sha256", approvedRequest.get("candidate_sha256"));
        receipt.put("learning_output_sha256", learningOutputSha256);
        receipt.put("validation_receipt_sha256", validationReceiptSha256);
        receipt.put("applied_artifact_sha256", appliedArtifactSha256);
        receipt.put("post_validation_receipt_sha256", postValidationReceiptSha256);
        receipt.put("rollback_plan_sha256", approvedRequest.get("rollback_plan_sha256"));
        receipt.put("state", "POST_VALIDATED");
        receipt.put("actual_learning_performed", true);
        receipt.put("final_lock_allowed", false);
        receipt.put("recorded_at", Instant.now().toString());
        receipt.put("receipt_sha256", sha256(mapper.writeValueAsBytes(receipt)));
        write(environment.resolve("learning/post-validation/"
                + approvedRequest.get("request_id") + ".json"), receipt);
        return Map.copyOf(receipt);
    }

    private void requireEnvironment(Path environment) {
        if (!Files.isRegularFile(environment.resolve("manifest.json"))
                || !Files.isRegularFile(environment.resolve("learning/profile.json"))
                || !Files.isRegularFile(environment.resolve("learning/policy.json"))) {
            throw new IllegalStateException("TARGET_RAG_LEARNING_ENVIRONMENT_NOT_READY");
        }
    }

    private static Path environment(Path targetRoot) {
        if (targetRoot == null) throw new IllegalArgumentException("TARGET_ROOT_MISSING");
        Path root = targetRoot.toAbsolutePath().normalize();
        Path result = root.resolve(RagPreparationService.TARGET_ENVIRONMENT).normalize();
        if (!result.startsWith(root)) throw new IllegalArgumentException("TARGET_RAG_PATH_ESCAPE");
        return result;
    }

    private static void requireSha(String field, String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field.toUpperCase() + "_INVALID");
        }
    }

    private void write(Path file, Object value) throws Exception {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        mapper.writeValue(temporary.toFile(), value);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
