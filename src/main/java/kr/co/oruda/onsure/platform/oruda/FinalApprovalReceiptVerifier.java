package kr.co.oruda.onsure.platform.oruda;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import kr.co.oruda.onsure.assurance.ValidationResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Verifies an externally issued human approval. ONSURE never self-issues this receipt. */
public final class FinalApprovalReceiptVerifier {
    public static final String CONTRACT = "ONSURE_ORUDA_FINAL_APPROVAL_RECEIPT_V1";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public record Approval(String approvalId, String approverId, String receiptSha256) {}
    public record Verification(ValidationResult result, Approval approval) {}

    public Verification verify(Path file, FinalCandidateGate.GateResult candidate) {
        List<String> violations = new ArrayList<>();
        Approval approval = null;
        try {
            if (!Files.isRegularFile(file)) {
                return new Verification(ValidationResult.fail(List.of("ORUDA_FINAL_APPROVAL_RECEIPT_MISSING")), null);
            }
            Map<String, Object> body = MAPPER.readValue(file.toFile(), new TypeReference<>() {});
            Object storedDigest = body.remove("receipt_sha256");
            if (!CONTRACT.equals(body.get("contract"))) violations.add("ORUDA_FINAL_APPROVAL_CONTRACT_MISMATCH");
            if (!Objects.equals(candidate.targetId(), body.get("target_id"))) violations.add("ORUDA_FINAL_APPROVAL_TARGET_MISMATCH");
            if (!Objects.equals(candidate.candidateId(), body.get("candidate_id"))) violations.add("ORUDA_FINAL_APPROVAL_CANDIDATE_ID_MISMATCH");
            if (!Objects.equals(candidate.candidateDigest(), body.get("candidate_digest"))) {
                violations.add("ORUDA_FINAL_APPROVAL_CANDIDATE_DIGEST_MISMATCH");
            }
            if (!"HUMAN_FINAL_AUTHORITY".equals(body.get("approver_authority"))) {
                violations.add("ORUDA_FINAL_APPROVAL_AUTHORITY_INVALID");
            }
            if (!"APPROVE".equals(body.get("decision"))) violations.add("ORUDA_FINAL_APPROVAL_NON_APPROVE");
            try { Instant.parse(String.valueOf(body.get("approved_at"))); }
            catch (Exception e) { violations.add("ORUDA_FINAL_APPROVAL_TIME_INVALID"); }

            String approvalId = text(body.get("approval_id"));
            String approverId = text(body.get("approver_id"));
            if (approvalId.isBlank()) violations.add("ORUDA_FINAL_APPROVAL_ID_MISSING");
            if (approverId.isBlank()) violations.add("ORUDA_FINAL_APPROVER_ID_MISSING");
            String expectedDigest = digestBody(body);
            if (!(storedDigest instanceof String digest) || !digest.matches("[0-9a-f]{64}")
                    || !digest.equals(expectedDigest)) {
                violations.add("ORUDA_FINAL_APPROVAL_RECEIPT_HASH_MISMATCH");
            } else if (violations.isEmpty()) {
                approval = new Approval(approvalId, approverId, digest);
            }
        } catch (Exception e) {
            violations.add("ORUDA_FINAL_APPROVAL_RECEIPT_UNREADABLE:" + e.getClass().getSimpleName());
        }
        return new Verification(
                violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations), approval);
    }

    public static String digestBody(Map<String, Object> bodyWithoutDigest) throws Exception {
        byte[] canonical = MAPPER.writeValueAsBytes(new TreeMap<>(bodyWithoutDigest));
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
    }

    private static String text(Object value) { return value == null ? "" : value.toString(); }
}
