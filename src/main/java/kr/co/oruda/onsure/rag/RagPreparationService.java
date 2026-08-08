package kr.co.oruda.onsure.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import kr.co.oruda.onsure.platform.ValidationModel.ValidationReport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prepares retrieval material without creating embeddings, indexes, or training runs.
 *
 * <p>ONSure owns its run-level candidate ledger. A validated program owns any environment
 * bootstrapped below its own source root. Bootstrap is an explicit, separately authorized action;
 * validation never mutates the target program implicitly.
 */
public final class RagPreparationService {
    public static final String CANDIDATE_CONTRACT = "ONSURE_RAG_CANDIDATE_V1";
    public static final String BOOTSTRAP_CONTRACT = "ONSURE_TARGET_RAG_BOOTSTRAP_RECEIPT_V1";
    public static final String TARGET_ENVIRONMENT = ".onsure/rag-preparation";
    public static final String LEARNING_PROFILE_CONTRACT = "ONSURE_PROGRAM_LEARNING_PROFILE_V1";

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public Map<String, Object> prepareOwnCandidate(
            ValidationReport report, Path onsureManagedRoot) throws Exception {
        Path root = normalized(onsureManagedRoot);
        Files.createDirectories(root);

        String valueDecision = valueDecision(report);
        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("contract", CANDIDATE_CONTRACT);
        candidate.put("candidate_id", "RAG-" + report.jobId());
        candidate.put("owner", "ONSURE");
        candidate.put("source_type", "VALIDATION");
        candidate.put("target_id", report.target().targetId());
        candidate.put("target_source_ref", report.target().immutableSourceReference());
        candidate.put("validation_report_id", report.reportId());
        candidate.put("validation_decision", report.decision().name());
        candidate.put("value_decision", valueDecision);
        candidate.put("rag_environment_required", !"LOCAL_ONLY".equals(valueDecision));
        candidate.put("reason_codes", reasonCodes(report));
        candidate.put("prepared_at", Instant.now().toString());
        candidate.put("embedding_status", "NOT_RUN");
        candidate.put("rag_index_status", "NOT_CREATED");
        candidate.put("training_status", "NOT_RUN");
        candidate.put("application_status", "NOT_RUN");
        candidate.put("source_report_sha256",
                sha256(reportPathBytes(report)));

        writeJson(root.resolve("candidates").resolve(report.jobId() + ".json"), candidate);
        writeJson(root.resolve("receipts").resolve(report.jobId() + ".json"), Map.of(
                "contract", "ONSURE_RAG_PREPARATION_RECEIPT_V1",
                "candidate_id", candidate.get("candidate_id"),
                "owner", "ONSURE",
                "value_decision", valueDecision,
                "candidate_sha256", sha256(mapper.writeValueAsBytes(candidate)),
                "actual_ingestion_performed", false));
        return Map.copyOf(candidate);
    }

    public Map<String, Object> bootstrapTargetEnvironment(
            ValidationReport report, Path targetRoot, boolean explicitlyAuthorized) throws Exception {
        if (!explicitlyAuthorized) {
            throw new IllegalStateException("TARGET_RAG_BOOTSTRAP_AUTHORIZATION_REQUIRED");
        }
        String decision = valueDecision(report);
        if ("LOCAL_ONLY".equals(decision)) {
            throw new IllegalStateException("TARGET_RAG_ENVIRONMENT_NOT_REQUIRED");
        }
        Path normalizedTarget = normalized(targetRoot);
        if (!normalizedTarget.equals(report.target().sourceRoot())) {
            throw new IllegalArgumentException("TARGET_RAG_OWNER_ROOT_MISMATCH");
        }
        Path environment = normalizedTarget.resolve(TARGET_ENVIRONMENT).normalize();
        if (!environment.startsWith(normalizedTarget)) {
            throw new IllegalArgumentException("TARGET_RAG_PATH_ESCAPE");
        }

        Files.createDirectories(environment.resolve("candidates"));
        Files.createDirectories(environment.resolve("source"));
        Files.createDirectories(environment.resolve("chunks"));
        Files.createDirectories(environment.resolve("quarantine"));
        Files.createDirectories(environment.resolve("receipts"));
        Files.createDirectories(environment.resolve("learning/candidates"));
        Files.createDirectories(environment.resolve("learning/requests"));
        Files.createDirectories(environment.resolve("learning/validation"));
        Files.createDirectories(environment.resolve("learning/promotion"));
        Files.createDirectories(environment.resolve("learning/application"));
        Files.createDirectories(environment.resolve("learning/post-validation"));
        Files.createDirectories(environment.resolve("learning/rollback"));
        writeTextIfMissing(environment.resolve("source/source_pack.md"),
                "# RAG source pack\n\nTarget-owned reusable knowledge only. No raw conversation dump.\n");
        writeTextIfMissing(environment.resolve("chunks/chunks.jsonl"), "");
        writeJsonIfMissing(environment.resolve("manifest.json"), Map.of(
                "contract", "TARGET_PROGRAM_RAG_MANIFEST_V1",
                "owner_target_id", report.target().targetId(),
                "preparation_status", "ENVIRONMENT_READY",
                "embedding_status", "NOT_RUN",
                "rag_index_status", "NOT_CREATED",
                "training_status", "NOT_RUN",
                "automatic_learning_status", "READY_DISABLED",
                "required_artifacts", List.of(
                        "source/source_pack.md", "chunks/chunks.jsonl",
                        "manifest.json", "ingest_guide.md",
                        "learning/profile.json", "learning/policy.json")));
        writeJsonIfMissing(environment.resolve("learning/profile.json"),
                learningProfile(report));
        writeJsonIfMissing(environment.resolve("learning/policy.json"), Map.of(
                "contract", "TARGET_PROGRAM_AUTO_LEARNING_POLICY_V1",
                "owner_target_id", report.target().targetId(),
                "automatic_learning_enabled", false,
                "require_explicit_candidate_approval", true,
                "require_immutable_source", true,
                "require_validation_receipt", true,
                "require_rollback_plan", true,
                "require_post_apply_validation", true,
                "final_lock_allowed", false));
        writeTextIfMissing(environment.resolve("ingest_guide.md"),
                "# Ingest guide\n\nPreparation only. Review, de-identify, deduplicate, and approve "
                        + "before any external ingestion.\n");

        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("contract", BOOTSTRAP_CONTRACT);
        receipt.put("owner", report.target().targetId());
        receipt.put("target_source_ref", report.target().immutableSourceReference());
        receipt.put("trigger_candidate", "RAG-" + report.jobId());
        receipt.put("value_decision", decision);
        receipt.put("environment", TARGET_ENVIRONMENT);
        receipt.put("created_at", Instant.now().toString());
        receipt.put("actual_ingestion_performed", false);
        receipt.put("receipt_sha256", sha256(mapper.writeValueAsBytes(receipt)));
        writeJson(environment.resolve("receipts/bootstrap-" + report.jobId() + ".json"), receipt);
        return Map.copyOf(receipt);
    }

    /**
     * Classifies whether a validated program needs a persistent learning/RAG environment.
     * The decision is evidence-based and is not permission to mutate or train the target.
     */
    public Map<String, Object> learningProfile(ValidationReport report) {
        String id = report.target().targetId().toLowerCase(java.util.Locale.ROOT);
        String name = report.target().targetName().toLowerCase(java.util.Locale.ROOT);
        boolean adaptiveType = report.target().targetType()
                == kr.co.oruda.onsure.platform.ValidationModel.TargetType.AI_APPLICATION;
        boolean designProgram = id.contains("odesign") || name.contains("odesign")
                || id.contains("oui") || name.contains("oui")
                || id.contains("oreport") || name.contains("oreport")
                || id.contains("odocument") || name.contains("odocument");
        boolean required = adaptiveType || designProgram;
        List<String> reasons = required
                ? List.of(adaptiveType ? "AI_APPLICATION" : "ADAPTIVE_OUTPUT_PROGRAM",
                        "QUALITY_DEPENDS_ON_REUSABLE_PATTERNS_AND_FAILURE_CASES")
                : List.of("NO_ADAPTIVE_LEARNING_CHARACTERISTIC_DETECTED");
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("contract", LEARNING_PROFILE_CONTRACT);
        profile.put("owner_target_id", report.target().targetId());
        profile.put("target_source_ref", report.target().immutableSourceReference());
        profile.put("learning_required", required);
        profile.put("rag_preparation_required", required);
        profile.put("automatic_learning_capable", required);
        profile.put("automatic_learning_enabled", false);
        profile.put("reason_codes", reasons);
        profile.put("decision_source_report_id", report.reportId());
        profile.put("actual_learning_performed", false);
        return Map.copyOf(profile);
    }

    static String valueDecision(ValidationReport report) {
        if (!report.rcaRecords().isEmpty() || !report.failureModes().isEmpty()) {
            return "RAG_READY";
        }
        if (!report.findings().isEmpty()
                || report.fixtureResults().stream().anyMatch(value -> !"PASS".equals(value.decision().name()))) {
            return "RAG_REVIEW_REQUIRED";
        }
        return "LOCAL_ONLY";
    }

    private static List<String> reasonCodes(ValidationReport report) {
        var reasons = new java.util.ArrayList<String>();
        if (!report.failureModes().isEmpty()) reasons.add("REUSABLE_FAILURE_MODE");
        if (!report.rcaRecords().isEmpty()) reasons.add("RCA_AND_REMEDIATION");
        if (!report.findings().isEmpty()) reasons.add("VALIDATION_FINDING");
        if (reasons.isEmpty()) reasons.add("NO_REUSABLE_KNOWLEDGE_DETECTED");
        return List.copyOf(reasons);
    }

    private byte[] reportPathBytes(ValidationReport report) throws Exception {
        return mapper.writeValueAsBytes(report);
    }

    private void writeJson(Path file, Object value) throws Exception {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        mapper.writeValue(temporary.toFile(), value);
        move(temporary, file);
    }

    private void writeJsonIfMissing(Path file, Object value) throws Exception {
        if (!Files.exists(file)) writeJson(file, value);
    }

    private static void writeTextIfMissing(Path file, String value) throws Exception {
        if (Files.exists(file)) return;
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, value, StandardCharsets.UTF_8);
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

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
