package kr.co.oruda.onsure.platform.oruda;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import kr.co.oruda.onsure.assurance.Decision;
import kr.co.oruda.onsure.assurance.ValidationResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Creates Final Lock only after fresh two-run candidate evaluation and external human approval. */
public final class OrudaFinalLockGate {
    public static final String CONTRACT = "ONSURE_ORUDA_FINAL_LOCK_V1";

    public record FinalLock(
            String contract,
            String finalLockId,
            String targetId,
            String candidateId,
            String candidateDigest,
            String run1JobId,
            String run2JobId,
            String approvalId,
            String approvalReceiptSha256,
            Instant lockedAt,
            String decision,
            String lockDigest) {}

    public record Outcome(ValidationResult result, FinalLock finalLock) {}

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public Outcome create(Path run1, Path run2, Path targetRoot, Path approvalReceipt, Path outputFile) {
        List<String> violations = new ArrayList<>();
        try {
            FinalCandidateGate.GateResult candidate = new FinalCandidateGate().evaluate(run1, run2, targetRoot);
            if (!candidate.eligible() || !"PASS".equals(candidate.decision())) {
                for (String reason : candidate.reasons()) violations.add("CANDIDATE_" + reason);
                if (candidate.reasons().isEmpty()) violations.add("ORUDA_FINAL_CANDIDATE_NOT_ELIGIBLE");
                return new Outcome(ValidationResult.fail(violations), null);
            }

            FinalApprovalReceiptVerifier.Verification approval =
                    new FinalApprovalReceiptVerifier().verify(approvalReceipt, candidate);
            if (approval.result().decision() != Decision.PASS || approval.approval() == null) {
                violations.addAll(approval.result().violations());
                return new Outcome(ValidationResult.fail(violations), null);
            }

            Instant lockedAt = Instant.now();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("contract", CONTRACT);
            body.put("target_id", candidate.targetId());
            body.put("candidate_id", candidate.candidateId());
            body.put("candidate_digest", candidate.candidateDigest());
            body.put("run_1_job_id", candidate.run1JobId());
            body.put("run_2_job_id", candidate.run2JobId());
            body.put("approval_id", approval.approval().approvalId());
            body.put("approval_receipt_sha256", approval.approval().receiptSha256());
            body.put("locked_at", lockedAt.toString());
            body.put("decision", "LOCKED");
            String lockDigest = digest(body);
            String finalLockId = "ORUDA-FINAL-LOCK-" + lockDigest.substring(0, 24);

            FinalLock lock = new FinalLock(
                    CONTRACT,
                    finalLockId,
                    candidate.targetId(),
                    candidate.candidateId(),
                    candidate.candidateDigest(),
                    candidate.run1JobId(),
                    candidate.run2JobId(),
                    approval.approval().approvalId(),
                    approval.approval().receiptSha256(),
                    lockedAt,
                    "LOCKED",
                    lockDigest);
            writeAtomic(outputFile, lock);
            ValidationResult verified = verify(run1, run2, targetRoot, approvalReceipt, outputFile);
            if (verified.decision() != Decision.PASS) {
                Files.deleteIfExists(outputFile);
                return new Outcome(verified, null);
            }
            return new Outcome(ValidationResult.pass(), lock);
        } catch (Exception e) {
            try { Files.deleteIfExists(outputFile); } catch (Exception ignored) {}
            violations.add("ORUDA_FINAL_LOCK_CREATION_FAILED:" + e.getClass().getSimpleName());
            return new Outcome(ValidationResult.fail(violations), null);
        }
    }

    public ValidationResult verify(Path run1, Path run2, Path targetRoot,
            Path approvalReceipt, Path finalLockFile) {
        List<String> violations = new ArrayList<>();
        try {
            if (!Files.isRegularFile(finalLockFile)) {
                return ValidationResult.fail(List.of("ORUDA_FINAL_LOCK_MISSING"));
            }
            FinalCandidateGate.GateResult candidate = new FinalCandidateGate().evaluate(run1, run2, targetRoot);
            if (!candidate.eligible() || !"PASS".equals(candidate.decision())) {
                for (String reason : candidate.reasons()) violations.add("CANDIDATE_" + reason);
                if (candidate.reasons().isEmpty()) violations.add("ORUDA_FINAL_CANDIDATE_NOT_ELIGIBLE");
            }
            FinalApprovalReceiptVerifier.Verification approval =
                    new FinalApprovalReceiptVerifier().verify(approvalReceipt, candidate);
            if (approval.result().decision() != Decision.PASS || approval.approval() == null) {
                violations.addAll(approval.result().violations());
                return ValidationResult.fail(violations);
            }

            FinalLock lock = mapper.readValue(finalLockFile.toFile(), FinalLock.class);
            if (!CONTRACT.equals(lock.contract())) violations.add("ORUDA_FINAL_LOCK_CONTRACT_MISMATCH");
            if (!"LOCKED".equals(lock.decision())) violations.add("ORUDA_FINAL_LOCK_DECISION_INVALID");
            if (!Objects.equals(candidate.targetId(), lock.targetId())) violations.add("ORUDA_FINAL_LOCK_TARGET_MISMATCH");
            if (!Objects.equals(candidate.candidateId(), lock.candidateId())) violations.add("ORUDA_FINAL_LOCK_CANDIDATE_ID_MISMATCH");
            if (!Objects.equals(candidate.candidateDigest(), lock.candidateDigest())) {
                violations.add("ORUDA_FINAL_LOCK_CANDIDATE_DIGEST_MISMATCH");
            }
            if (!Objects.equals(candidate.run1JobId(), lock.run1JobId())) violations.add("ORUDA_FINAL_LOCK_RUN1_MISMATCH");
            if (!Objects.equals(candidate.run2JobId(), lock.run2JobId())) violations.add("ORUDA_FINAL_LOCK_RUN2_MISMATCH");
            if (!Objects.equals(approval.approval().approvalId(), lock.approvalId())) {
                violations.add("ORUDA_FINAL_LOCK_APPROVAL_ID_MISMATCH");
            }
            if (!Objects.equals(approval.approval().receiptSha256(), lock.approvalReceiptSha256())) {
                violations.add("ORUDA_FINAL_LOCK_APPROVAL_HASH_MISMATCH");
            }
            if (lock.finalLockId() == null || !lock.finalLockId().equals(
                    "ORUDA-FINAL-LOCK-" + lock.lockDigest().substring(0, 24))) {
                violations.add("ORUDA_FINAL_LOCK_ID_MISMATCH");
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("contract", lock.contract());
            body.put("target_id", lock.targetId());
            body.put("candidate_id", lock.candidateId());
            body.put("candidate_digest", lock.candidateDigest());
            body.put("run_1_job_id", lock.run1JobId());
            body.put("run_2_job_id", lock.run2JobId());
            body.put("approval_id", lock.approvalId());
            body.put("approval_receipt_sha256", lock.approvalReceiptSha256());
            body.put("locked_at", lock.lockedAt().toString());
            body.put("decision", lock.decision());
            if (!Objects.equals(digest(body), lock.lockDigest())) violations.add("ORUDA_FINAL_LOCK_DIGEST_MISMATCH");
        } catch (Exception e) {
            violations.add("ORUDA_FINAL_LOCK_UNREADABLE:" + e.getClass().getSimpleName());
        }
        return violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations);
    }

    private void writeAtomic(Path file, FinalLock lock) throws Exception {
        Path normalized = file.toAbsolutePath().normalize();
        if (normalized.getParent() == null) throw new IllegalArgumentException("ORUDA_FINAL_LOCK_PARENT_MISSING");
        Files.createDirectories(normalized.getParent());
        Path temporary = normalized.resolveSibling(normalized.getFileName() + ".tmp");
        mapper.writeValue(temporary.toFile(), lock);
        try {
            Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String digest(Map<String, Object> body) throws Exception {
        byte[] canonical = mapper.writeValueAsBytes(new TreeMap<>(body));
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
    }
}
