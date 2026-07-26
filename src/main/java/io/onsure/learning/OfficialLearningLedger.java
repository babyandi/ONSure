package io.onsure.learning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Fail-closed authority for the ONSure learning-to-application pipeline.
 *
 * <p>Ledger entries are hash chained, writes use a cross-process file lock, every mutation updates
 * a separately stored head anchor, promotion is bound to the latest validation pack, and the two
 * required PASS receipts must use different runs, identities and signing keys. The default file
 * anchor is a local nonfinal integrity control; production requires an external WORM/KMS anchor.
 */
public final class OfficialLearningLedger {
    public static final String CONTRACT = "ONSURE_OFFICIAL_LEARNING_LEDGER_V2";
    public static final String ANCHOR_CONTRACT = "ONSURE_LEARNING_LEDGER_HEAD_ANCHOR_V1";
    public static final String GENESIS = "0".repeat(64);

    public enum EntryType {
        LEARNING_CANDIDATE,
        VALIDATION_REQUEST,
        VALIDATION_PACK,
        VALIDATION_RECEIPT,
        PROMOTION,
        APPLIED_LOCK
    }

    public enum ReceiptPurpose {
        CANDIDATE_VALIDATION,
        POST_APPLY_REVERIFICATION
    }

    public enum CompletionStatus {
        HOLD_NO_CANDIDATE,
        HOLD_VALIDATION_NOT_REQUESTED,
        HOLD_VALIDATION_PACK_MISSING,
        HOLD_TWO_PASS_RECEIPTS_MISSING,
        HOLD_PROMOTION_MISSING,
        HOLD_POST_APPLY_RECEIPT_MISSING,
        HOLD_APPLIED_LOCK_MISSING,
        APPLIED_LOCKED_NONFINAL
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
            String sourceBaselineRef,
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
            String verifierKeyId,
            ReceiptPurpose purpose,
            String decision,
            String projectionDigest,
            String evidenceDigest,
            String recalculationReceiptSha256,
            boolean independentRecalculation,
            boolean copiedLearnerOutput) {}

    public record Promotion(
            String promotionId,
            String candidateId,
            String packId,
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

    public record ChainVerification(
            boolean valid, List<String> violations, String head, long sequence,
            String anchorMode) {
        public ChainVerification {
            violations = List.copyOf(violations);
        }
    }

    /** Pluggable head anchor. A production implementation should use WORM/KMS storage. */
    public interface LedgerAnchorStore {
        JsonNode read() throws Exception;
        void write(Map<String, Object> anchor) throws Exception;
        String mode();
    }

    /** Local-file anchor used only as a nonfinal development integrity control. */
    public static final class FileLedgerAnchorStore implements LedgerAnchorStore {
        private final ObjectMapper mapper = new ObjectMapper()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        private final Path anchorFile;

        public FileLedgerAnchorStore(Path anchorFile) {
            this.anchorFile = anchorFile.toAbsolutePath().normalize();
        }

        @Override
        public JsonNode read() throws Exception {
            return Files.isRegularFile(anchorFile) ? mapper.readTree(anchorFile.toFile()) : null;
        }

        @Override
        public void write(Map<String, Object> anchor) throws Exception {
            Files.createDirectories(anchorFile.getParent());
            Path temporary = anchorFile.resolveSibling(anchorFile.getFileName() + ".tmp");
            byte[] content = mapper.writeValueAsBytes(new TreeMap<>(anchor));
            try (FileChannel channel = FileChannel.open(temporary,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                channel.write(ByteBuffer.wrap(content));
                channel.force(true);
            }
            moveReplacing(temporary, anchorFile);
        }

        @Override
        public String mode() {
            return "LOCAL_FILE_NONFINAL";
        }
    }

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final Path ledgerFile;
    private final Path lockFile;
    private final LedgerAnchorStore anchorStore;

    public OfficialLearningLedger(Path ledgerFile) {
        this(ledgerFile, new FileLedgerAnchorStore(ledgerFile.toAbsolutePath().normalize()
                .resolveSibling(ledgerFile.getFileName() + ".head.json")));
    }

    public OfficialLearningLedger(Path ledgerFile, LedgerAnchorStore anchorStore) {
        this.ledgerFile = ledgerFile.toAbsolutePath().normalize();
        this.lockFile = this.ledgerFile.resolveSibling(this.ledgerFile.getFileName() + ".lock");
        this.anchorStore = java.util.Objects.requireNonNull(anchorStore, "anchorStore");
    }

    public String registerCandidate(LearningCandidate value) {
        return withLock(() -> {
            requireIdentifier(value.candidateId(), "CANDIDATE_ID_INVALID");
            requireText(value.candidateType(), "CANDIDATE_TYPE_MISSING");
            requireDigest(value.sourceReceiptSha256(), "CANDIDATE_SOURCE_RECEIPT_DIGEST_INVALID");
            requireDigest(value.learnerOutputSha256(), "CANDIDATE_OUTPUT_DIGEST_INVALID");
            requireText(value.trainingDatasetVersion(), "TRAINING_DATASET_VERSION_MISSING");
            requireIdentity(value.learnerIdentity(), "LEARNER_IDENTITY_MISSING");
            if (!value.hiddenDatasetNonAccessAttestation()) {
                throw failure("HIDDEN_DATASET_NON_ACCESS_ATTESTATION_REQUIRED");
            }
            requireAbsentUnlocked(EntryType.LEARNING_CANDIDATE, "candidate_id",
                    value.candidateId(), "CANDIDATE_REPLAY");
            return appendUnlocked(EntryType.LEARNING_CANDIDATE, value.candidateId(),
                    value.learnerOutputSha256(), Map.of(
                            "candidate_id", value.candidateId(),
                            "candidate_type", value.candidateType(),
                            "source_receipt_sha256", value.sourceReceiptSha256(),
                            "learner_output_sha256", value.learnerOutputSha256(),
                            "training_dataset_version", value.trainingDatasetVersion(),
                            "hidden_dataset_non_access_attestation", true,
                            "learner_identity", value.learnerIdentity()));
        });
    }

    public String requestValidation(ValidationRequest value) {
        return withLock(() -> {
            requireIdentifier(value.requestId(), "VALIDATION_REQUEST_ID_INVALID");
            requireIdentifier(value.candidateId(), "CANDIDATE_ID_INVALID");
            requireIdentifier(value.queueItemId(), "QUEUE_ITEM_ID_INVALID");
            requireText(value.policyVersion(), "POLICY_VERSION_MISSING");
            requireDigest(value.datasetVersionsDigest(), "DATASET_VERSIONS_DIGEST_INVALID");
            requireText(value.validatorVersion(), "VALIDATOR_VERSION_MISSING");
            requireIdentity(value.requestedBy(), "REQUESTER_IDENTITY_MISSING");
            JsonNode candidate = requireEntryUnlocked(
                    EntryType.LEARNING_CANDIDATE, "candidate_id", value.candidateId(),
                    "LEARNING_CANDIDATE_MISSING");
            if (payload(candidate, "learner_identity").equals(value.requestedBy())) {
                throw failure("LEARNER_CANNOT_REQUEST_OWN_VALIDATION");
            }
            requireAbsentUnlocked(EntryType.VALIDATION_REQUEST, "request_id",
                    value.requestId(), "VALIDATION_REQUEST_REPLAY");
            return appendUnlocked(EntryType.VALIDATION_REQUEST, value.candidateId(),
                    value.datasetVersionsDigest(), Map.of(
                            "request_id", value.requestId(),
                            "candidate_id", value.candidateId(),
                            "queue_item_id", value.queueItemId(),
                            "policy_version", value.policyVersion(),
                            "dataset_versions_digest", value.datasetVersionsDigest(),
                            "validator_version", value.validatorVersion(),
                            "requested_by", value.requestedBy()));
        });
    }

    public String issueValidationPack(ValidationPack value) {
        return withLock(() -> {
            requireIdentifier(value.packId(), "VALIDATION_PACK_ID_INVALID");
            requireIdentifier(value.requestId(), "VALIDATION_REQUEST_ID_INVALID");
            requireIdentifier(value.candidateId(), "CANDIDATE_ID_INVALID");
            requireSourceReference(value.sourceBaselineRef(), "SOURCE_BASELINE_REFERENCE_INVALID");
            requireDigest(value.fixtureDigest(), "FIXTURE_DIGEST_INVALID");
            requireDigest(value.harnessDigest(), "HARNESS_DIGEST_INVALID");
            requireDigest(value.oracleDigest(), "ORACLE_DIGEST_INVALID");
            requireDigest(value.expectedEvidenceDigest(), "EXPECTED_EVIDENCE_DIGEST_INVALID");
            JsonNode request = requireEntryUnlocked(
                    EntryType.VALIDATION_REQUEST, "request_id", value.requestId(),
                    "VALIDATION_REQUEST_MISSING");
            requireEqual(value.candidateId(), payload(request, "candidate_id"),
                    "VALIDATION_PACK_CANDIDATE_MISMATCH");
            JsonNode latestRequest = latestUnlocked(
                    EntryType.VALIDATION_REQUEST, "candidate_id", value.candidateId(),
                    "VALIDATION_REQUEST_MISSING");
            requireEqual(value.requestId(), payload(latestRequest, "request_id"),
                    "VALIDATION_PACK_MUST_BIND_LATEST_REQUEST");
            requireAbsentUnlocked(EntryType.VALIDATION_PACK, "pack_id", value.packId(),
                    "VALIDATION_PACK_REPLAY");
            String packContractDigest = sha256(mapper.writeValueAsBytes(new TreeMap<>(Map.of(
                    "pack_id", value.packId(),
                    "request_id", value.requestId(),
                    "candidate_id", value.candidateId(),
                    "source_baseline_ref", value.sourceBaselineRef(),
                    "fixture_digest", value.fixtureDigest(),
                    "harness_digest", value.harnessDigest(),
                    "oracle_digest", value.oracleDigest(),
                    "expected_evidence_digest", value.expectedEvidenceDigest()))));
            return appendUnlocked(EntryType.VALIDATION_PACK, value.candidateId(),
                    packContractDigest, Map.of(
                            "pack_id", value.packId(),
                            "request_id", value.requestId(),
                            "candidate_id", value.candidateId(),
                            "source_baseline_ref", value.sourceBaselineRef(),
                            "fixture_digest", value.fixtureDigest(),
                            "harness_digest", value.harnessDigest(),
                            "oracle_digest", value.oracleDigest(),
                            "expected_evidence_digest", value.expectedEvidenceDigest(),
                            "pack_contract_digest", packContractDigest));
        });
    }

    public String recordValidationReceipt(ValidationReceipt value) {
        return withLock(() -> {
            validateReceiptShape(value);
            JsonNode pack = requireEntryUnlocked(
                    EntryType.VALIDATION_PACK, "pack_id", value.packId(),
                    "VALIDATION_PACK_MISSING");
            requireEqual(value.candidateId(), payload(pack, "candidate_id"),
                    "VALIDATION_RECEIPT_CANDIDATE_MISMATCH");
            JsonNode latestPack = latestUnlocked(
                    EntryType.VALIDATION_PACK, "candidate_id", value.candidateId(),
                    "VALIDATION_PACK_MISSING");
            requireEqual(value.packId(), payload(latestPack, "pack_id"),
                    "VALIDATION_RECEIPT_MUST_BIND_LATEST_PACK");
            JsonNode candidate = requireEntryUnlocked(
                    EntryType.LEARNING_CANDIDATE, "candidate_id", value.candidateId(),
                    "LEARNING_CANDIDATE_MISSING");
            if (payload(candidate, "learner_identity").equals(value.verifierIdentity())) {
                throw failure("LEARNER_CANNOT_VALIDATE");
            }
            JsonNode request = latestUnlocked(
                    EntryType.VALIDATION_REQUEST, "candidate_id", value.candidateId(),
                    "VALIDATION_REQUEST_MISSING");
            if (payload(request, "requested_by").equals(value.verifierIdentity())) {
                throw failure("REQUESTER_CANNOT_VALIDATE");
            }
            if ("PASS".equals(value.decision()) && !value.independentRecalculation()) {
                throw failure("INDEPENDENT_RECALCULATION_REQUIRED");
            }
            if ("PASS".equals(value.decision()) && value.copiedLearnerOutput()) {
                throw failure("COPIED_LEARNER_OUTPUT_CANNOT_PASS");
            }
            requireAbsentUnlocked(EntryType.VALIDATION_RECEIPT, "receipt_id",
                    value.receiptId(), "VALIDATION_RECEIPT_REPLAY");
            List<JsonNode> existing = findUnlocked(
                    EntryType.VALIDATION_RECEIPT, "candidate_id", value.candidateId());
            if (existing.stream().anyMatch(node -> payload(node, "run_id").equals(value.runId()))) {
                throw failure("VALIDATION_RUN_ID_REPLAY");
            }
            return appendUnlocked(EntryType.VALIDATION_RECEIPT, value.candidateId(),
                    value.evidenceDigest(), Map.ofEntries(
                            Map.entry("receipt_id", value.receiptId()),
                            Map.entry("pack_id", value.packId()),
                            Map.entry("candidate_id", value.candidateId()),
                            Map.entry("run_id", value.runId()),
                            Map.entry("verifier_identity", value.verifierIdentity()),
                            Map.entry("verifier_key_id", value.verifierKeyId()),
                            Map.entry("purpose", value.purpose().name()),
                            Map.entry("decision", value.decision()),
                            Map.entry("projection_digest", value.projectionDigest()),
                            Map.entry("evidence_digest", value.evidenceDigest()),
                            Map.entry("recalculation_receipt_sha256",
                                    value.recalculationReceiptSha256()),
                            Map.entry("independent_recalculation",
                                    value.independentRecalculation()),
                            Map.entry("copied_learner_output", value.copiedLearnerOutput())));
        });
    }

    public String approvePromotion(Promotion value) {
        return withLock(() -> {
            requireIdentifier(value.promotionId(), "PROMOTION_ID_INVALID");
            requireIdentifier(value.candidateId(), "CANDIDATE_ID_INVALID");
            requireIdentifier(value.packId(), "VALIDATION_PACK_ID_INVALID");
            requireDigest(value.artifactDigest(), "PROMOTED_ARTIFACT_DIGEST_INVALID");
            requireText(value.applicationClass(), "APPLICATION_CLASS_MISSING");
            requireIdentity(value.reviewerIdentity(), "REVIEWER_IDENTITY_MISSING");
            requireIdentity(value.approverIdentity(), "APPROVER_IDENTITY_MISSING");
            requireIdentifier(value.rollbackPlanId(), "ROLLBACK_PLAN_ID_INVALID");
            JsonNode latestPack = latestUnlocked(
                    EntryType.VALIDATION_PACK, "candidate_id", value.candidateId(),
                    "VALIDATION_PACK_MISSING");
            requireEqual(value.packId(), payload(latestPack, "pack_id"),
                    "PROMOTION_MUST_BIND_LATEST_PACK");
            Set<String> prohibitedRoles = roleIdentitiesUnlocked(value.candidateId());
            if (value.reviewerIdentity().equals(value.approverIdentity())) {
                throw failure("REVIEWER_APPROVER_SEPARATION_REQUIRED");
            }
            if (prohibitedRoles.contains(value.reviewerIdentity())
                    || prohibitedRoles.contains(value.approverIdentity())) {
                throw failure("PROMOTION_ROLE_SEPARATION_REQUIRED");
            }
            List<JsonNode> passes = passReceiptsUnlocked(
                    value.candidateId(), value.packId(), ReceiptPurpose.CANDIDATE_VALIDATION);
            if (passes.size() < 2) throw failure("TWO_PASS_RECEIPTS_REQUIRED");
            List<JsonNode> selected = passes.stream()
                    .sorted(Comparator.comparing(node -> payload(node, "run_id")))
                    .limit(2).toList();
            requireDistinct(selected, "run_id", "TWO_DISTINCT_RUNS_REQUIRED");
            requireDistinct(selected, "verifier_identity",
                    "TWO_DISTINCT_VERIFIER_IDENTITIES_REQUIRED");
            requireDistinct(selected, "verifier_key_id",
                    "TWO_DISTINCT_VERIFIER_KEYS_REQUIRED");
            if (!payload(selected.get(0), "projection_digest")
                    .equals(payload(selected.get(1), "projection_digest"))) {
                throw failure("TWO_RUN_PROJECTION_MISMATCH");
            }
            requireAbsentUnlocked(EntryType.PROMOTION, "promotion_id", value.promotionId(),
                    "PROMOTION_REPLAY");
            return appendUnlocked(EntryType.PROMOTION, value.candidateId(),
                    value.artifactDigest(), Map.of(
                            "promotion_id", value.promotionId(),
                            "candidate_id", value.candidateId(),
                            "pack_id", value.packId(),
                            "artifact_digest", value.artifactDigest(),
                            "application_class", value.applicationClass(),
                            "reviewer_identity", value.reviewerIdentity(),
                            "approver_identity", value.approverIdentity(),
                            "rollback_plan_id", value.rollbackPlanId(),
                            "validation_receipt_ids", selected.stream()
                                    .map(node -> payload(node, "receipt_id")).sorted().toList()));
        });
    }

    public String lockApplied(AppliedLock value) {
        return withLock(() -> {
            requireIdentifier(value.lockId(), "APPLIED_LOCK_ID_INVALID");
            requireIdentifier(value.candidateId(), "CANDIDATE_ID_INVALID");
            requireDigest(value.artifactDigest(), "APPLIED_ARTIFACT_DIGEST_INVALID");
            requireText(value.activeSelector(), "ACTIVE_SELECTOR_MISSING");
            requireDigest(value.activeArtifactDigest(), "ACTIVE_ARTIFACT_DIGEST_INVALID");
            requireGitObjectId(value.mainOrStableRefSha(), "MAIN_OR_STABLE_REF_SHA_INVALID");
            requireDigest(value.immutableEvidenceBundleDigest(),
                    "EVIDENCE_BUNDLE_DIGEST_INVALID");
            requireIdentifier(value.postApplyVerificationReceiptId(),
                    "POST_APPLY_RECEIPT_ID_INVALID");
            requireText(value.rollbackPointer(), "ROLLBACK_POINTER_MISSING");
            requireDigest(value.appliedCountIncrementReceiptDigest(),
                    "APPLIED_COUNT_INCREMENT_RECEIPT_DIGEST_INVALID");
            JsonNode promotion = latestUnlocked(
                    EntryType.PROMOTION, "candidate_id", value.candidateId(),
                    "PROMOTION_MISSING");
            requireEqual(value.artifactDigest(), payload(promotion, "artifact_digest"),
                    "APPLIED_ARTIFACT_PROMOTION_MISMATCH");
            requireEqual(value.artifactDigest(), value.activeArtifactDigest(),
                    "ACTIVE_SELECTOR_NOT_PROMOTED_ARTIFACT");
            JsonNode receipt = requireEntryUnlocked(
                    EntryType.VALIDATION_RECEIPT, "receipt_id",
                    value.postApplyVerificationReceiptId(), "POST_APPLY_RECEIPT_MISSING");
            requireEqual(value.candidateId(), payload(receipt, "candidate_id"),
                    "POST_APPLY_RECEIPT_CANDIDATE_MISMATCH");
            requireEqual(payload(promotion, "pack_id"), payload(receipt, "pack_id"),
                    "POST_APPLY_RECEIPT_PACK_MISMATCH");
            requireEqual(ReceiptPurpose.POST_APPLY_REVERIFICATION.name(),
                    payload(receipt, "purpose"), "POST_APPLY_RECEIPT_PURPOSE_INVALID");
            requireEqual("PASS", payload(receipt, "decision"),
                    "POST_APPLY_RECEIPT_NON_PASS");
            requireEqual(value.artifactDigest(), payload(receipt, "projection_digest"),
                    "POST_APPLY_RECEIPT_ARTIFACT_MISMATCH");
            Set<String> promotionActors = Set.of(
                    payload(promotion, "reviewer_identity"),
                    payload(promotion, "approver_identity"));
            if (promotionActors.contains(payload(receipt, "verifier_identity"))) {
                throw failure("POST_APPLY_VERIFIER_NOT_SEPARATED_FROM_PROMOTION");
            }
            if (!value.readOnlyReverificationPass()) {
                throw failure("READ_ONLY_REVERIFICATION_REQUIRED");
            }
            requireAbsentUnlocked(EntryType.APPLIED_LOCK, "lock_id", value.lockId(),
                    "APPLIED_LOCK_REPLAY");
            return appendUnlocked(EntryType.APPLIED_LOCK, value.candidateId(),
                    value.immutableEvidenceBundleDigest(), Map.ofEntries(
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
                            Map.entry("read_only_reverification_pass", true),
                            Map.entry("assurance_class", "SELF_VALIDATION_NONFINAL"),
                            Map.entry("final_lock_allowed", false)));
        });
    }

    public CompletionStatus completionStatus(String candidateId) {
        return withLock(() -> completionStatusUnlocked(candidateId));
    }

    public void requireAppliedLocked(String candidateId) {
        CompletionStatus status = completionStatus(candidateId);
        if (status != CompletionStatus.APPLIED_LOCKED_NONFINAL) {
            throw failure("SCHEDULED_OR_VALIDATION_RESULT_NOT_COMPLETE:" + status);
        }
    }

    public ChainVerification verifyChain() {
        return withLock(this::verifyChainUnlocked);
    }

    public long appliedCount() {
        return withLock(() -> {
            ChainVerification chain = verifyChainUnlocked();
            if (!chain.valid()) throw failure("OFFICIAL_LEDGER_CHAIN_INVALID");
            return findUnlocked(EntryType.APPLIED_LOCK, null, null).stream()
                    .map(node -> payload(node, "candidate_id")).distinct().count();
        });
    }

    private CompletionStatus completionStatusUnlocked(String candidateId) throws Exception {
        requireIdentifier(candidateId, "CANDIDATE_ID_INVALID");
        ChainVerification chain = verifyChainUnlocked();
        if (!chain.valid()) throw failure("OFFICIAL_LEDGER_CHAIN_INVALID");
        if (findUnlocked(EntryType.LEARNING_CANDIDATE, "candidate_id", candidateId).isEmpty()) {
            return CompletionStatus.HOLD_NO_CANDIDATE;
        }
        if (findUnlocked(EntryType.VALIDATION_REQUEST, "candidate_id", candidateId).isEmpty()) {
            return CompletionStatus.HOLD_VALIDATION_NOT_REQUESTED;
        }
        List<JsonNode> packs = findUnlocked(EntryType.VALIDATION_PACK, "candidate_id", candidateId);
        if (packs.isEmpty()) return CompletionStatus.HOLD_VALIDATION_PACK_MISSING;
        String latestPackId = payload(packs.get(packs.size() - 1), "pack_id");
        if (passReceiptsUnlocked(candidateId, latestPackId,
                ReceiptPurpose.CANDIDATE_VALIDATION).size() < 2) {
            return CompletionStatus.HOLD_TWO_PASS_RECEIPTS_MISSING;
        }
        List<JsonNode> promotions = findUnlocked(EntryType.PROMOTION, "candidate_id", candidateId);
        if (promotions.isEmpty()) return CompletionStatus.HOLD_PROMOTION_MISSING;
        JsonNode promotion = promotions.get(promotions.size() - 1);
        List<JsonNode> postApply = passReceiptsUnlocked(
                candidateId, payload(promotion, "pack_id"),
                ReceiptPurpose.POST_APPLY_REVERIFICATION);
        if (postApply.isEmpty()) return CompletionStatus.HOLD_POST_APPLY_RECEIPT_MISSING;
        if (findUnlocked(EntryType.APPLIED_LOCK, "candidate_id", candidateId).isEmpty()) {
            return CompletionStatus.HOLD_APPLIED_LOCK_MISSING;
        }
        return CompletionStatus.APPLIED_LOCKED_NONFINAL;
    }

    private void validateReceiptShape(ValidationReceipt value) {
        requireIdentifier(value.receiptId(), "VALIDATION_RECEIPT_ID_INVALID");
        requireIdentifier(value.packId(), "VALIDATION_PACK_ID_INVALID");
        requireIdentifier(value.candidateId(), "CANDIDATE_ID_INVALID");
        requireIdentifier(value.runId(), "VALIDATION_RUN_ID_INVALID");
        requireIdentity(value.verifierIdentity(), "VERIFIER_IDENTITY_MISSING");
        requireIdentifier(value.verifierKeyId(), "VERIFIER_KEY_ID_INVALID");
        if (value.purpose() == null) throw failure("RECEIPT_PURPOSE_MISSING");
        requireText(value.decision(), "VALIDATION_DECISION_MISSING");
        requireDigest(value.projectionDigest(), "PROJECTION_DIGEST_INVALID");
        requireDigest(value.evidenceDigest(), "EVIDENCE_DIGEST_INVALID");
        requireDigest(value.recalculationReceiptSha256(),
                "RECALCULATION_RECEIPT_DIGEST_INVALID");
        if (!List.of("PASS", "FAIL", "INCONCLUSIVE").contains(value.decision())) {
            throw failure("VALIDATION_DECISION_INVALID");
        }
    }

    private Set<String> roleIdentitiesUnlocked(String candidateId) throws Exception {
        Set<String> identities = new HashSet<>();
        JsonNode candidate = requireEntryUnlocked(
                EntryType.LEARNING_CANDIDATE, "candidate_id", candidateId,
                "LEARNING_CANDIDATE_MISSING");
        identities.add(payload(candidate, "learner_identity"));
        for (JsonNode request : findUnlocked(
                EntryType.VALIDATION_REQUEST, "candidate_id", candidateId)) {
            identities.add(payload(request, "requested_by"));
        }
        for (JsonNode receipt : findUnlocked(
                EntryType.VALIDATION_RECEIPT, "candidate_id", candidateId)) {
            identities.add(payload(receipt, "verifier_identity"));
        }
        return identities;
    }

    private List<JsonNode> passReceiptsUnlocked(
            String candidateId, String packId, ReceiptPurpose purpose) throws Exception {
        return findUnlocked(EntryType.VALIDATION_RECEIPT, "candidate_id", candidateId).stream()
                .filter(node -> packId.equals(payload(node, "pack_id")))
                .filter(node -> purpose.name().equals(payload(node, "purpose")))
                .filter(node -> "PASS".equals(payload(node, "decision")))
                .filter(node -> node.path("payload").path("independent_recalculation").asBoolean())
                .filter(node -> !node.path("payload").path("copied_learner_output").asBoolean())
                .toList();
    }

    private void requireDistinct(List<JsonNode> nodes, String field, String error) {
        if (nodes.size() < 2 || payload(nodes.get(0), field)
                .equals(payload(nodes.get(1), field))) {
            throw failure(error);
        }
    }

    private String appendUnlocked(
            EntryType type, String subjectId, String artifactDigest, Map<String, ?> payload)
            throws Exception {
        ChainVerification chain = verifyChainUnlocked();
        if (!chain.valid()) throw failure("OFFICIAL_LEDGER_CHAIN_INVALID");
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
        writeLinesDurably(lines);
        String ledgerDigest = sha256(Files.readAllBytes(ledgerFile));
        anchorStore.write(Map.of(
                "contract", ANCHOR_CONTRACT,
                "sequence", lines.size(),
                "ledger_head_sha256", entryHash,
                "ledger_file_sha256", ledgerDigest,
                "anchor_mode", anchorStore.mode(),
                "final_claim_allowed", false,
                "anchored_at", Instant.now().toString()));
        ChainVerification written = verifyChainUnlocked();
        if (!written.valid()) throw failure("OFFICIAL_LEDGER_WRITE_VERIFY_FAILED");
        return entryHash;
    }

    private ChainVerification verifyChainUnlocked() throws Exception {
        List<String> violations = new ArrayList<>();
        String previous = GENESIS;
        long expectedSequence = 1;
        List<JsonNode> entries;
        try {
            entries = readEntriesUnlocked();
            for (JsonNode node : entries) {
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
            JsonNode anchor = anchorStore.read();
            if (entries.isEmpty()) {
                if (anchor != null && anchor.path("sequence").asLong() != 0) {
                    violations.add("LEDGER_EMPTY_ANCHOR_NONZERO");
                }
            } else if (anchor == null) {
                violations.add("LEDGER_HEAD_ANCHOR_MISSING");
            } else {
                if (!ANCHOR_CONTRACT.equals(anchor.path("contract").asText())) {
                    violations.add("LEDGER_HEAD_ANCHOR_CONTRACT_MISMATCH");
                }
                if (anchor.path("sequence").asLong() != entries.size()) {
                    violations.add("LEDGER_HEAD_ANCHOR_SEQUENCE_MISMATCH");
                }
                if (!previous.equals(anchor.path("ledger_head_sha256").asText())) {
                    violations.add("LEDGER_HEAD_ANCHOR_MISMATCH");
                }
                String fileDigest = sha256(Files.readAllBytes(ledgerFile));
                if (!fileDigest.equals(anchor.path("ledger_file_sha256").asText())) {
                    violations.add("LEDGER_FILE_ANCHOR_MISMATCH");
                }
            }
        } catch (Exception exception) {
            violations.add("LEDGER_UNREADABLE");
        }
        return new ChainVerification(
                violations.isEmpty(), violations, previous, expectedSequence - 1,
                anchorStore.mode());
    }

    private JsonNode requireEntryUnlocked(
            EntryType type, String field, String value, String error) throws Exception {
        return findUnlocked(type, field, value).stream().findFirst()
                .orElseThrow(() -> failure(error));
    }

    private JsonNode latestUnlocked(
            EntryType type, String field, String value, String error) throws Exception {
        List<JsonNode> entries = findUnlocked(type, field, value);
        if (entries.isEmpty()) throw failure(error);
        return entries.get(entries.size() - 1);
    }

    private void requireAbsentUnlocked(
            EntryType type, String field, String value, String error) throws Exception {
        if (!findUnlocked(type, field, value).isEmpty()) throw failure(error);
    }

    private List<JsonNode> findUnlocked(EntryType type, String field, String value)
            throws Exception {
        ChainVerification chain = verifyChainUnlocked();
        if (!chain.valid()) throw failure("OFFICIAL_LEDGER_CHAIN_INVALID");
        return readEntriesUnlocked().stream()
                .filter(node -> type.name().equals(node.path("entry_type").asText()))
                .filter(node -> field == null || value.equals(payload(node, field)))
                .toList();
    }

    private List<JsonNode> readEntriesUnlocked() throws Exception {
        if (!Files.exists(ledgerFile)) return List.of();
        List<JsonNode> entries = new ArrayList<>();
        for (String line : Files.readAllLines(ledgerFile, StandardCharsets.UTF_8)) {
            if (line.isBlank()) throw failure("OFFICIAL_LEDGER_BLANK_ENTRY");
            entries.add(mapper.readTree(line));
        }
        return List.copyOf(entries);
    }

    private void writeLinesDurably(List<String> lines) throws Exception {
        Files.createDirectories(ledgerFile.getParent());
        Path temporary = ledgerFile.resolveSibling(ledgerFile.getFileName() + ".tmp");
        String content = String.join(System.lineSeparator(), lines) + System.lineSeparator();
        try (FileChannel channel = FileChannel.open(temporary,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8)));
            channel.force(true);
        }
        moveReplacing(temporary, ledgerFile);
    }

    private <T> T withLock(CheckedSupplier<T> action) {
        try {
            Files.createDirectories(lockFile.getParent());
            try (FileChannel channel = FileChannel.open(lockFile,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) {
                return action.get();
            }
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("OFFICIAL_LEDGER_OPERATION_FAILED", exception);
        }
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    private static String payload(JsonNode node, String field) {
        return node.path("payload").path(field).asText();
    }

    private static void requireIdentifier(String value, String error) {
        if (value == null || !value.matches("[A-Za-z0-9._:-]{3,160}")) throw failure(error);
    }

    private static void requireIdentity(String value, String error) {
        if (value == null || !value.matches("[A-Za-z0-9@._:-]{3,256}")) throw failure(error);
    }

    private static void requireText(String value, String error) {
        if (value == null || value.isBlank()) throw failure(error);
    }

    private static void requireDigest(String value, String error) {
        if (value == null || !value.matches("[0-9a-f]{64}")) throw failure(error);
    }

    private static void requireGitObjectId(String value, String error) {
        if (value == null || !value.matches("[0-9a-f]{40}|[0-9a-f]{64}")) throw failure(error);
    }

    private static void requireSourceReference(String value, String error) {
        if (value == null || !value.matches(
                "git:[0-9a-f]{40}|git:[0-9a-f]{64}|sha256:[0-9a-f]{64}")) {
            throw failure(error);
        }
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
