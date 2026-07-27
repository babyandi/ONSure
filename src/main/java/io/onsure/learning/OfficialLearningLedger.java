package io.onsure.learning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Append-only authority for the ONSure learning-to-application pipeline.
 *
 * <p>No learning or scheduled-validation result is complete merely because a file, queue row,
 * PASS result, PR, or candidate artifact exists. Completion is recognized only after this ledger
 * contains a valid, hash-bound chain from candidate through APPLIED_LOCK.
 */
public final class OfficialLearningLedger {
    public static final String CONTRACT = "ONSURE_OFFICIAL_LEARNING_LEDGER_V1";
    public static final String ANCHOR_CONTRACT = "ONSURE_OFFICIAL_LEARNING_LEDGER_HEAD_V1";
    public static final String GENESIS = "0".repeat(64);

    public enum EntryType {
        LEARNING_CANDIDATE,
        VALIDATION_REQUEST,
        VALIDATION_PACK,
        VALIDATION_RECEIPT,
        PROMOTION,
        APPLIED_LOCK
    }

    public enum CompletionStatus {
        HOLD_NO_CANDIDATE,
        HOLD_VALIDATION_NOT_REQUESTED,
        HOLD_VALIDATION_PACK_MISSING,
        HOLD_TWO_PASS_RECEIPTS_MISSING,
        HOLD_PROMOTION_MISSING,
        HOLD_APPLIED_LOCK_MISSING,
        APPLIED_LOCKED
    }

    public record LearningCandidate(
            String candidateId,
            String candidateType,
            String sourceReceiptSha256,
            String learnerOutputSha256,
            String trainingDatasetVersion,
            boolean hiddenDatasetNonAccessAttestation,
            String learnerIdentity) {}

    public record ValidationRequest(
            String requestId,
            String candidateId,
            String queueItemId,
            String policyVersion,
            String datasetVersionsDigest,
            String validatorVersion,
            String requestedBy) {}

    public record ValidationPack(
            String packId,
            String requestId,
            String candidateId,
            String fixtureDigest,
            String harnessDigest,
            String oracleDigest,
            String expectedEvidenceDigest) {}

    public record ValidationReceipt(
            String receiptId,
            String packId,
            String candidateId,
            String runId,
            String verifierIdentity,
            String decision,
            String projectionDigest,
            String evidenceDigest,
            boolean independentRecalculation,
            boolean copiedLearnerOutput) {}

    public record Promotion(
            String promotionId,
            String candidateId,
            String artifactDigest,
            String applicationClass,
            String reviewerIdentity,
            String approverIdentity,
            String rollbackPlanId) {}

    public record AppliedLock(
            String lockId,
            String candidateId,
            String artifactDigest,
            String activeSelector,
            String activeArtifactDigest,
            String mainOrStableRefSha,
            String immutableEvidenceBundleDigest,
            String postApplyVerificationReceiptId,
            String rollbackPointer,
            String appliedCountIncrementReceiptDigest,
            boolean readOnlyReverificationPass) {}

    public record ChainVerification(boolean valid, List<String> violations, String head) {
        public ChainVerification {
            violations = List.copyOf(violations);
        }
    }

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final Path ledgerFile;
    private final Path lockFile;
    private final Path anchorFile;

    public OfficialLearningLedger(Path ledgerFile) {
        this.ledgerFile = ledgerFile.toAbsolutePath().normalize();
        this.lockFile = this.ledgerFile.resolveSibling(this.ledgerFile.getFileName() + ".lock");
        this.anchorFile = this.ledgerFile.resolveSibling(this.ledgerFile.getFileName() + ".head.json");
    }

    public synchronized String registerCandidate(LearningCandidate value) {
        requireIdentifier(value.candidateId(), "CANDIDATE_ID_INVALID");
        requireText(value.candidateType(), "CANDIDATE_TYPE_MISSING");
        requireDigest(value.sourceReceiptSha256(), "CANDIDATE_SOURCE_RECEIPT_DIGEST_INVALID");
        requireDigest(value.learnerOutputSha256(), "CANDIDATE_OUTPUT_DIGEST_INVALID");
        requireText(value.trainingDatasetVersion(), "TRAINING_DATASET_VERSION_MISSING");
        requireText(value.learnerIdentity(), "LEARNER_IDENTITY_MISSING");
        if (!value.hiddenDatasetNonAccessAttestation()) {
            throw failure("HIDDEN_DATASET_NON_ACCESS_ATTESTATION_REQUIRED");
        }
        requireAbsent(EntryType.LEARNING_CANDIDATE, "candidate_id", value.candidateId(),
                "CANDIDATE_REPLAY");
        return append(EntryType.LEARNING_CANDIDATE, value.candidateId(), value.learnerOutputSha256(),
                Map.of(
                        "candidate_id", value.candidateId(),
                        "candidate_type", value.candidateType(),
                        "source_receipt_sha256", value.sourceReceiptSha256(),
                        "learner_output_sha256", value.learnerOutputSha256(),
                        "training_dataset_version", value.trainingDatasetVersion(),
                        "hidden_dataset_non_access_attestation", true,
                        "learner_identity", value.learnerIdentity()));
    }

    public synchronized String requestValidation(ValidationRequest value) {
        requireIdentifier(value.requestId(), "VALIDATION_REQUEST_ID_INVALID");
        requireIdentifier(value.candidateId(), "CANDIDATE_ID_INVALID");
        requireIdentifier(value.queueItemId(), "QUEUE_ITEM_ID_INVALID");
        requireText(value.policyVersion(), "POLICY_VERSION_MISSING");
        requireDigest(value.datasetVersionsDigest(), "DATASET_VERSIONS_DIGEST_INVALID");
        requireText(value.validatorVersion(), "VALIDATOR_VERSION_MISSING");
        requireText(value.requestedBy(), "REQUESTER_IDENTITY_MISSING");
        JsonNode candidate = requireEntry(EntryType.LEARNING_CANDIDATE, "candidate_id", value.candidateId(),
                "LEARNING_CANDIDATE_MISSING");
        if (payload(candidate, "learner_identity").equals(value.requestedBy())) {
            throw failure("LEARNER_CANNOT_REQUEST_OWN_VALIDATION");
        }
        requireAbsent(EntryType.VALIDATION_REQUEST, "request_id", value.requestId(),
                "VALIDATION_REQUEST_REPLAY");
        return append(EntryType.VALIDATION_REQUEST, value.candidateId(), value.datasetVersionsDigest(),
                Map.of(
                        "request_id", value.requestId(),
                        "candidate_id", value.candidateId(),
                        "queue_item_id", value.queueItemId(),
                        "policy_version", value.policyVersion(),
                        "dataset_versions_digest", value.datasetVersionsDigest(),
                        "validator_version", value.validatorVersion(),
                        "requested_by", value.requestedBy()));
    }

    public synchronized String issueValidationPack(ValidationPack value) {
        requireIdentifier(value.packId(), "VALIDATION_PACK_ID_INVALID");
        requireIdentifier(value.requestId(), "VALIDATION_REQUEST_ID_INVALID");
        requireIdentifier(value.candidateId(), "CANDIDATE_ID_INVALID");
        requireDigest(value.fixtureDigest(), "FIXTURE_DIGEST_INVALID");
        requireDigest(value.harnessDigest(), "HARNESS_DIGEST_INVALID");
        requireDigest(value.oracleDigest(), "ORACLE_DIGEST_INVALID");
        requireDigest(value.expectedEvidenceDigest(), "EXPECTED_EVIDENCE_DIGEST_INVALID");
        JsonNode request = requireEntry(
                EntryType.VALIDATION_REQUEST, "request_id", value.requestId(),
                "VALIDATION_REQUEST_MISSING");
        requireEqual(value.candidateId(), payload(request, "candidate_id"),
                "VALIDATION_PACK_CANDIDATE_MISMATCH");
        JsonNode latestRequest = latest(
                EntryType.VALIDATION_REQUEST, "candidate_id", value.candidateId(),
                "VALIDATION_REQUEST_MISSING");
        requireEqual(value.requestId(), payload(latestRequest, "request_id"),
                "VALIDATION_PACK_REQUEST_STALE");
        requireAbsent(EntryType.VALIDATION_PACK, "pack_id", value.packId(),
                "VALIDATION_PACK_REPLAY");
        return append(EntryType.VALIDATION_PACK, value.candidateId(), value.expectedEvidenceDigest(),
                Map.of(
                        "pack_id", value.packId(),
                        "request_id", value.requestId(),
                        "candidate_id", value.candidateId(),
                        "fixture_digest", value.fixtureDigest(),
                        "harness_digest", value.harnessDigest(),
                        "oracle_digest", value.oracleDigest(),
                        "expected_evidence_digest", value.expectedEvidenceDigest()));
    }

    public synchronized String recordValidationReceipt(ValidationReceipt value) {
        requireIdentifier(value.receiptId(), "VALIDATION_RECEIPT_ID_INVALID");
        requireIdentifier(value.packId(), "VALIDATION_PACK_ID_INVALID");
        requireIdentifier(value.candidateId(), "CANDIDATE_ID_INVALID");
        requireIdentifier(value.runId(), "VALIDATION_RUN_ID_INVALID");
        requireText(value.verifierIdentity(), "VERIFIER_IDENTITY_MISSING");
        requireText(value.decision(), "VALIDATION_DECISION_MISSING");
        requireDigest(value.projectionDigest(), "PROJECTION_DIGEST_INVALID");
        requireDigest(value.evidenceDigest(), "EVIDENCE_DIGEST_INVALID");
        JsonNode pack = requireEntry(
                EntryType.VALIDATION_PACK, "pack_id", value.packId(), "VALIDATION_PACK_MISSING");
        requireEqual(value.candidateId(), payload(pack, "candidate_id"),
                "VALIDATION_RECEIPT_CANDIDATE_MISMATCH");
        JsonNode latestPack = latest(
                EntryType.VALIDATION_PACK, "candidate_id", value.candidateId(),
                "VALIDATION_PACK_MISSING");
        requireEqual(value.packId(), payload(latestPack, "pack_id"),
                "VALIDATION_RECEIPT_PACK_STALE");
        JsonNode candidate = requireEntry(
                EntryType.LEARNING_CANDIDATE, "candidate_id", value.candidateId(),
                "LEARNING_CANDIDATE_MISSING");
        JsonNode request = requireEntry(
                EntryType.VALIDATION_REQUEST, "request_id", payload(pack, "request_id"),
                "VALIDATION_REQUEST_MISSING");
        if (payload(candidate, "learner_identity").equals(value.verifierIdentity())) {
            throw failure("LEARNER_CANNOT_VALIDATE");
        }
        if (payload(request, "requested_by").equals(value.verifierIdentity())) {
            throw failure("REQUESTER_CANNOT_VALIDATE");
        }
        if ("PASS".equals(value.decision()) && !value.independentRecalculation()) {
            throw failure("INDEPENDENT_RECALCULATION_REQUIRED");
        }
        if ("PASS".equals(value.decision()) && value.copiedLearnerOutput()) {
            throw failure("COPIED_LEARNER_OUTPUT_CANNOT_PASS");
        }
        if (!List.of("PASS", "FAIL", "INCONCLUSIVE").contains(value.decision())) {
            throw failure("VALIDATION_DECISION_INVALID");
        }
        requireAbsent(EntryType.VALIDATION_RECEIPT, "receipt_id", value.receiptId(),
                "VALIDATION_RECEIPT_REPLAY");
        return append(EntryType.VALIDATION_RECEIPT, value.candidateId(), value.evidenceDigest(),
                Map.of(
                        "receipt_id", value.receiptId(),
                        "pack_id", value.packId(),
                        "candidate_id", value.candidateId(),
                        "run_id", value.runId(),
                        "verifier_identity", value.verifierIdentity(),
                        "decision", value.decision(),
                        "projection_digest", value.projectionDigest(),
                        "evidence_digest", value.evidenceDigest(),
                        "independent_recalculation", value.independentRecalculation(),
                        "copied_learner_output", value.copiedLearnerOutput()));
    }

    public synchronized String approvePromotion(Promotion value) {
        requireIdentifier(value.promotionId(), "PROMOTION_ID_INVALID");
        requireIdentifier(value.candidateId(), "CANDIDATE_ID_INVALID");
        requireDigest(value.artifactDigest(), "PROMOTED_ARTIFACT_DIGEST_INVALID");
        requireText(value.applicationClass(), "APPLICATION_CLASS_MISSING");
        requireText(value.reviewerIdentity(), "REVIEWER_IDENTITY_MISSING");
        requireText(value.approverIdentity(), "APPROVER_IDENTITY_MISSING");
        requireIdentifier(value.rollbackPlanId(), "ROLLBACK_PLAN_ID_INVALID");
        if (value.reviewerIdentity().equals(value.approverIdentity())) {
            throw failure("REVIEWER_APPROVER_SEPARATION_REQUIRED");
        }
        JsonNode candidate = requireEntry(
                EntryType.LEARNING_CANDIDATE, "candidate_id", value.candidateId(),
                "LEARNING_CANDIDATE_MISSING");
        JsonNode latestRequest = latest(
                EntryType.VALIDATION_REQUEST, "candidate_id", value.candidateId(),
                "VALIDATION_REQUEST_MISSING");
        JsonNode latestPack = latest(
                EntryType.VALIDATION_PACK, "candidate_id", value.candidateId(),
                "VALIDATION_PACK_MISSING");
        List<JsonNode> passes = passReceiptsForPack(value.candidateId(), payload(latestPack, "pack_id"));
        if (passes.size() < 2) throw failure("TWO_PASS_RECEIPTS_REQUIRED");
        List<JsonNode> selected = passes.stream()
                .sorted(Comparator.comparing(node -> payload(node, "run_id")))
                .limit(2)
                .toList();
        String runA = payload(selected.get(0), "run_id");
        String runB = payload(selected.get(1), "run_id");
        String verifierA = payload(selected.get(0), "verifier_identity");
        String verifierB = payload(selected.get(1), "verifier_identity");
        if (runA.equals(runB)) throw failure("TWO_DISTINCT_RUNS_REQUIRED");
        if (verifierA.equals(verifierB)) throw failure("TWO_DISTINCT_VERIFIERS_REQUIRED");
        if (!payload(selected.get(0), "projection_digest")
                .equals(payload(selected.get(1), "projection_digest"))) {
            throw failure("TWO_RUN_PROJECTION_MISMATCH");
        }
        Set<String> priorActors = Set.of(
                payload(candidate, "learner_identity"),
                payload(latestRequest, "requested_by"),
                verifierA,
                verifierB);
        if (priorActors.contains(value.reviewerIdentity())) {
            throw failure("REVIEWER_ROLE_SEPARATION_REQUIRED");
        }
        if (priorActors.contains(value.approverIdentity())) {
            throw failure("APPROVER_ROLE_SEPARATION_REQUIRED");
        }
        requireAbsent(EntryType.PROMOTION, "promotion_id", value.promotionId(),
                "PROMOTION_REPLAY");
        return append(EntryType.PROMOTION, value.candidateId(), value.artifactDigest(),
                Map.of(
                        "promotion_id", value.promotionId(),
                        "candidate_id", value.candidateId(),
                        "pack_id", payload(latestPack, "pack_id"),
                        "artifact_digest", value.artifactDigest(),
                        "application_class", value.applicationClass(),
                        "reviewer_identity", value.reviewerIdentity(),
                        "approver_identity", value.approverIdentity(),
                        "rollback_plan_id", value.rollbackPlanId(),
                        "validation_receipt_ids", selected.stream()
                                .map(node -> payload(node, "receipt_id")).sorted().toList()));
    }

    public synchronized String lockApplied(AppliedLock value) {
        requireIdentifier(value.lockId(), "APPLIED_LOCK_ID_INVALID");
        requireIdentifier(value.candidateId(), "CANDIDATE_ID_INVALID");
        requireDigest(value.artifactDigest(), "APPLIED_ARTIFACT_DIGEST_INVALID");
        requireText(value.activeSelector(), "ACTIVE_SELECTOR_MISSING");
        requireDigest(value.activeArtifactDigest(), "ACTIVE_ARTIFACT_DIGEST_INVALID");
        requireGitObjectId(value.mainOrStableRefSha(), "MAIN_OR_STABLE_REF_SHA_INVALID");
        requireDigest(value.immutableEvidenceBundleDigest(), "EVIDENCE_BUNDLE_DIGEST_INVALID");
        requireIdentifier(value.postApplyVerificationReceiptId(),
                "POST_APPLY_RECEIPT_ID_INVALID");
        requireText(value.rollbackPointer(), "ROLLBACK_POINTER_MISSING");
        requireDigest(value.appliedCountIncrementReceiptDigest(),
                "APPLIED_COUNT_INCREMENT_RECEIPT_DIGEST_INVALID");
        JsonNode promotion = latest(
                EntryType.PROMOTION, "candidate_id", value.candidateId(), "PROMOTION_MISSING");
        requireEqual(value.artifactDigest(), payload(promotion, "artifact_digest"),
                "APPLIED_ARTIFACT_PROMOTION_MISMATCH");
        requireEqual(value.artifactDigest(), value.activeArtifactDigest(),
                "ACTIVE_SELECTOR_NOT_PROMOTED_ARTIFACT");
        if (!value.readOnlyReverificationPass()) {
            throw failure("READ_ONLY_REVERIFICATION_REQUIRED");
        }
        JsonNode postApply = requireEntry(
                EntryType.VALIDATION_RECEIPT, "receipt_id", value.postApplyVerificationReceiptId(),
                "POST_APPLY_RECEIPT_MISSING");
        requireEqual(value.candidateId(), payload(postApply, "candidate_id"),
                "POST_APPLY_RECEIPT_CANDIDATE_MISMATCH");
        requireEqual("PASS", payload(postApply, "decision"), "POST_APPLY_RECEIPT_NOT_PASS");
        requireEqual(payload(promotion, "pack_id"), payload(postApply, "pack_id"),
                "POST_APPLY_RECEIPT_PACK_MISMATCH");
        requireAbsent(EntryType.APPLIED_LOCK, "lock_id", value.lockId(), "APPLIED_LOCK_REPLAY");
        return append(EntryType.APPLIED_LOCK, value.candidateId(), value.immutableEvidenceBundleDigest(),
                Map.ofEntries(
                        Map.entry("lock_id", value.lockId()),
                        Map.entry("candidate_id", value.candidateId()),
                        Map.entry("artifact_digest", value.artifactDigest()),
                        Map.entry("active_selector", value.activeSelector()),
                        Map.entry("active_artifact_digest", value.activeArtifactDigest()),
                        Map.entry("main_or_stable_ref_sha", value.mainOrStableRefSha()),
                        Map.entry("immutable_evidence_bundle_digest",
                                value.immutableEvidenceBundleDigest()),
                        Map.entry("post_apply_verification_receipt_id",
                                value.postApplyVerificationReceiptId()),
                        Map.entry("rollback_pointer", value.rollbackPointer()),
                        Map.entry("applied_count_increment_receipt_digest",
                                value.appliedCountIncrementReceiptDigest()),
                        Map.entry("read_only_reverification_pass", true)));
    }

    public synchronized CompletionStatus completionStatus(String candidateId) {
        requireIdentifier(candidateId, "CANDIDATE_ID_INVALID");
        ChainVerification chain = verifyChain();
        if (!chain.valid()) throw failure("OFFICIAL_LEDGER_CHAIN_INVALID");
        if (find(EntryType.LEARNING_CANDIDATE, "candidate_id", candidateId).isEmpty()) {
            return CompletionStatus.HOLD_NO_CANDIDATE;
        }
        if (find(EntryType.VALIDATION_REQUEST, "candidate_id", candidateId).isEmpty()) {
            return CompletionStatus.HOLD_VALIDATION_NOT_REQUESTED;
        }
        if (find(EntryType.VALIDATION_PACK, "candidate_id", candidateId).isEmpty()) {
            return CompletionStatus.HOLD_VALIDATION_PACK_MISSING;
        }
        if (passReceipts(candidateId).size() < 2) {
            return CompletionStatus.HOLD_TWO_PASS_RECEIPTS_MISSING;
        }
        if (find(EntryType.PROMOTION, "candidate_id", candidateId).isEmpty()) {
            return CompletionStatus.HOLD_PROMOTION_MISSING;
        }
        if (find(EntryType.APPLIED_LOCK, "candidate_id", candidateId).isEmpty()) {
            return CompletionStatus.HOLD_APPLIED_LOCK_MISSING;
        }
        return CompletionStatus.APPLIED_LOCKED;
    }

    public synchronized void requireAppliedLocked(String candidateId) {
        CompletionStatus status = completionStatus(candidateId);
        if (status != CompletionStatus.APPLIED_LOCKED) {
            throw failure("SCHEDULED_OR_VALIDATION_RESULT_NOT_COMPLETE:" + status);
        }
    }

    public synchronized ChainVerification verifyChain() {
        return verifyChain(true);
    }

    public synchronized long appliedCount() {
        ChainVerification chain = verifyChain();
        if (!chain.valid()) throw failure("OFFICIAL_LEDGER_CHAIN_INVALID");
        return find(EntryType.APPLIED_LOCK, null, null).stream()
                .map(node -> payload(node, "candidate_id"))
                .distinct()
                .count();
    }

    private ChainVerification verifyChain(boolean requireAnchor) {
        List<String> violations = new ArrayList<>();
        String previous = GENESIS;
        long expectedSequence = 1;
        try {
            for (JsonNode node : readEntries()) {
                if (!CONTRACT.equals(node.path("contract").asText())) {
                    violations.add("LEDGER_CONTRACT_MISMATCH");
                }
                if (node.path("sequence").asLong() != expectedSequence) {
                    violations.add("LEDGER_SEQUENCE_BROKEN");
                }
                if (!previous.equals(node.path("previous_hash").asText())) {
                    violations.add("LEDGER_PREVIOUS_HASH_BROKEN");
                }
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("contract", node.path("contract").asText());
                body.put("sequence", node.path("sequence").asLong());
                body.put("entry_type", node.path("entry_type").asText());
                body.put("subject_id", node.path("subject_id").asText());
                body.put("artifact_digest", node.path("artifact_digest").asText());
                body.put("payload", mapper.convertValue(node.path("payload"), TreeMap.class));
                body.put("recorded_at", node.path("recorded_at").asText());
                body.put("previous_hash", node.path("previous_hash").asText());
                String calculated = sha256(mapper.writeValueAsBytes(body));
                if (!calculated.equals(node.path("entry_hash").asText())) {
                    violations.add("LEDGER_ENTRY_TAMPERED");
                }
                previous = node.path("entry_hash").asText();
                expectedSequence++;
            }
            if (requireAnchor) verifyAnchor(previous, expectedSequence - 1, violations);
        } catch (Exception exception) {
            violations.add("LEDGER_UNREADABLE");
        }
        return new ChainVerification(violations.isEmpty(), violations, previous);
    }

    private String append(
            EntryType type, String subjectId, String artifactDigest, Map<String, ?> payload) {
        try {
            Files.createDirectories(ledgerFile.getParent());
            try (FileChannel channel = FileChannel.open(lockFile,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 var ignored = channel.lock()) {
                ChainVerification chain = verifyChain(false);
                if (!chain.valid()) throw failure("OFFICIAL_LEDGER_CHAIN_INVALID");
                validateAppendUniqueness(type, payload);
                validateCurrentBinding(type, payload);
                List<String> lines = Files.exists(ledgerFile)
                        ? new ArrayList<>(Files.readAllLines(ledgerFile, StandardCharsets.UTF_8))
                        : new ArrayList<>();
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("contract", CONTRACT);
                body.put("sequence", lines.size() + 1L);
                body.put("entry_type", type.name());
                body.put("subject_id", subjectId);
                body.put("artifact_digest", artifactDigest);
                body.put("payload", new TreeMap<>(payload));
                body.put("recorded_at", Instant.now().toString());
                body.put("previous_hash", chain.head());
                String entryHash = sha256(mapper.writeValueAsBytes(body));
                body.put("entry_hash", entryHash);
                lines.add(mapper.writeValueAsString(body));
                Path temporary = ledgerFile.resolveSibling(ledgerFile.getFileName() + ".tmp");
                Files.write(temporary, lines, StandardCharsets.UTF_8);
                moveReplacing(temporary, ledgerFile);
                writeAnchor(entryHash, lines.size());
                ChainVerification written = verifyChain(true);
                if (!written.valid()) throw failure("OFFICIAL_LEDGER_WRITE_VERIFY_FAILED");
                return entryHash;
            }
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("OFFICIAL_LEDGER_WRITE_FAILED", exception);
        }
    }

    private void validateAppendUniqueness(EntryType type, Map<String, ?> payload) throws Exception {
        String field = switch (type) {
            case LEARNING_CANDIDATE -> "candidate_id";
            case VALIDATION_REQUEST -> "request_id";
            case VALIDATION_PACK -> "pack_id";
            case VALIDATION_RECEIPT -> "receipt_id";
            case PROMOTION -> "promotion_id";
            case APPLIED_LOCK -> "lock_id";
        };
        Object value = payload.get(field);
        if (value != null && !findWithoutVerification(type, field, value.toString()).isEmpty()) {
            throw failure(type.name() + "_REPLAY");
        }
    }

    private void validateCurrentBinding(EntryType type, Map<String, ?> payload) throws Exception {
        if (type == EntryType.VALIDATION_RECEIPT) {
            JsonNode latestPack = latestWithoutVerification(
                    EntryType.VALIDATION_PACK, "candidate_id", payload.get("candidate_id").toString(),
                    "VALIDATION_PACK_MISSING");
            requireEqual(payload.get("pack_id").toString(), payload(latestPack, "pack_id"),
                    "VALIDATION_RECEIPT_PACK_STALE");
        }
        if (type == EntryType.PROMOTION) {
            JsonNode latestPack = latestWithoutVerification(
                    EntryType.VALIDATION_PACK, "candidate_id", payload.get("candidate_id").toString(),
                    "VALIDATION_PACK_MISSING");
            requireEqual(payload.get("pack_id").toString(), payload(latestPack, "pack_id"),
                    "PROMOTION_PACK_STALE");
        }
    }

    private List<JsonNode> passReceipts(String candidateId) {
        JsonNode latestPack = latest(
                EntryType.VALIDATION_PACK, "candidate_id", candidateId, "VALIDATION_PACK_MISSING");
        return passReceiptsForPack(candidateId, payload(latestPack, "pack_id"));
    }

    private List<JsonNode> passReceiptsForPack(String candidateId, String packId) {
        return find(EntryType.VALIDATION_RECEIPT, "candidate_id", candidateId).stream()
                .filter(node -> packId.equals(payload(node, "pack_id")))
                .filter(node -> "PASS".equals(payload(node, "decision")))
                .filter(node -> node.path("payload").path("independent_recalculation").asBoolean())
                .filter(node -> !node.path("payload").path("copied_learner_output").asBoolean())
                .toList();
    }

    private JsonNode requireEntry(
            EntryType type, String field, String value, String error) {
        return find(type, field, value).stream().findFirst().orElseThrow(() -> failure(error));
    }

    private JsonNode latest(
            EntryType type, String field, String value, String error) {
        List<JsonNode> entries = find(type, field, value);
        if (entries.isEmpty()) throw failure(error);
        return entries.get(entries.size() - 1);
    }

    private JsonNode latestWithoutVerification(
            EntryType type, String field, String value, String error) throws Exception {
        List<JsonNode> entries = findWithoutVerification(type, field, value);
        if (entries.isEmpty()) throw failure(error);
        return entries.get(entries.size() - 1);
    }

    private void requireAbsent(EntryType type, String field, String value, String error) {
        if (!find(type, field, value).isEmpty()) throw failure(error);
    }

    private List<JsonNode> find(EntryType type, String field, String value) {
        ChainVerification chain = verifyChain();
        if (!chain.valid()) throw failure("OFFICIAL_LEDGER_CHAIN_INVALID");
        try {
            return findWithoutVerification(type, field, value);
        } catch (Exception exception) {
            throw new IllegalStateException("OFFICIAL_LEDGER_READ_FAILED", exception);
        }
    }

    private List<JsonNode> findWithoutVerification(EntryType type, String field, String value)
            throws Exception {
        return readEntries().stream()
                .filter(node -> type.name().equals(node.path("entry_type").asText()))
                .filter(node -> field == null || value.equals(payload(node, field)))
                .toList();
    }

    private List<JsonNode> readEntries() throws Exception {
        if (!Files.exists(ledgerFile)) return List.of();
        List<JsonNode> entries = new ArrayList<>();
        for (String line : Files.readAllLines(ledgerFile, StandardCharsets.UTF_8)) {
            if (line.isBlank()) throw failure("OFFICIAL_LEDGER_BLANK_ENTRY");
            entries.add(mapper.readTree(line));
        }
        return List.copyOf(entries);
    }

    private void writeAnchor(String head, long sequence) throws Exception {
        Map<String, Object> anchor = new LinkedHashMap<>();
        anchor.put("contract", ANCHOR_CONTRACT);
        anchor.put("ledger", ledgerFile.getFileName().toString());
        anchor.put("sequence", sequence);
        anchor.put("head_sha256", head);
        anchor.put("anchored_at", Instant.now().toString());
        Path temporary = anchorFile.resolveSibling(anchorFile.getFileName() + ".tmp");
        mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), anchor);
        moveReplacing(temporary, anchorFile);
    }

    private void verifyAnchor(String head, long sequence, List<String> violations) throws Exception {
        if (sequence == 0 && !Files.exists(anchorFile)) return;
        if (!Files.isRegularFile(anchorFile)) {
            violations.add("LEDGER_HEAD_ANCHOR_MISSING");
            return;
        }
        JsonNode anchor = mapper.readTree(anchorFile.toFile());
        if (!ANCHOR_CONTRACT.equals(anchor.path("contract").asText())) {
            violations.add("LEDGER_HEAD_ANCHOR_CONTRACT_MISMATCH");
        }
        if (anchor.path("sequence").asLong(-1) != sequence) {
            violations.add("LEDGER_HEAD_ANCHOR_SEQUENCE_MISMATCH");
        }
        if (!head.equals(anchor.path("head_sha256").asText())) {
            violations.add("LEDGER_HEAD_ANCHOR_MISMATCH");
        }
    }

    private static String payload(JsonNode node, String field) {
        return node.path("payload").path(field).asText();
    }

    private static void requireIdentifier(String value, String error) {
        if (value == null || !value.matches("[A-Za-z0-9._:-]{3,160}")) throw failure(error);
    }

    private static void requireText(String value, String error) {
        if (value == null || value.isBlank()) throw failure(error);
    }

    private static void requireDigest(String value, String error) {
        if (value == null || !value.matches("[0-9a-f]{64}")) throw failure(error);
    }

    private static void requireGitObjectId(String value, String error) {
        if (value == null || !value.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")) throw failure(error);
    }

    private static void requireEqual(String expected, String actual, String error) {
        if (!expected.equals(actual)) throw failure(error);
    }

    private static IllegalStateException failure(String code) {
        return new IllegalStateException(code);
    }

    private static void moveReplacing(Path source, Path target) throws Exception {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
