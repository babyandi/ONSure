package io.onsure.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.onsure.assurance.Decision;
import io.onsure.platform.ValidationModel.ValidationReport;
import io.onsure.rag.RagPreparationService;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** File-backed product store for evidence, findings, RCA, fixtures, locks and reports. */
public final class FileValidationStore {
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final Path root;
    private final Path lockFile;
    private final Path revisionFile;

    public FileValidationStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
        this.lockFile = this.root.resolve(".validation-store.lock");
        this.revisionFile = this.root.resolve("store-revision.json");
    }

    public Path createRunRoot(String targetId, String jobId) throws Exception {
        Path targetRoot = root.resolve(safe(targetId)).toAbsolutePath().normalize();
        Path runRoot = targetRoot.resolve(safe(jobId)).toAbsolutePath().normalize();
        if (!runRoot.startsWith(root)) throw new IllegalArgumentException("run path escapes store root");
        Files.createDirectories(root);
        try (FileChannel channel = FileChannel.open(lockFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var ignored = channel.lock()) {
            Files.createDirectories(targetRoot);
            if (Files.exists(runRoot, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("VALIDATION_RUN_ALREADY_EXISTS");
            }
            Files.createDirectory(runRoot);
            incrementRevision("CREATE_RUN_ROOT", runRoot);
        }
        return runRoot;
    }

    public void persist(ValidationContext context, ValidationReport report) throws Exception {
        Path run = context.runRoot().toAbsolutePath().normalize();
        if (!run.startsWith(root)
                || Files.isSymbolicLink(run)
                || !Files.isDirectory(run, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("validation run root outside store boundary");
        }
        Files.createDirectories(root);
        try (FileChannel channel = FileChannel.open(lockFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var ignored = channel.lock()) {
            writeJson(run.resolve("storage-context.json"), storageContext(context));
            writeJson(run.resolve("target.json"), context.target());
            writeJson(run.resolve("job.json"), context.job());
            writeJson(run.resolve("target-metadata.json"), context.attributes());
            writeJson(run.resolve("evidence.json"), context.evidence());
            writeJson(run.resolve("findings.json"), context.findings());
            writeJson(run.resolve("failure-modes.json"), context.failureModes());
            writeJson(run.resolve("rca.json"), context.rcaRecords());
            writeJson(run.resolve("remediation-plans.json"), context.remediationPlans());
            writeJson(run.resolve("fixture-results.json"), context.fixtureResults());
            writeJson(run.resolve("stage-results.json"), context.stageResults());
            writeJson(run.resolve("regression-lock.json"), context.regressionLock());
            writeJson(run.resolve("validation-report.json"), report);
            Map<String, Object> ragCandidate = new RagPreparationService()
                    .prepareOwnCandidate(report, root.resolve("rag-preparation"));
            writeJson(run.resolve("rag-preparation-candidate.json"), ragCandidate);
            new ValidationReportExporter().export(report, run);
            new FailureModeRegistry(root.resolve("failure-mode-registry.json"))
                    .register(context.failureModes());
            verifyCompletedReceipts(context);
            context.adapter().persistAdditionalEvidence(context);
            writeManifest(run);
            incrementRevision("PERSIST_VALIDATION_RUN", run);
        }
    }

    public ValidationReport readReport(Path runRoot) throws Exception {
        Path run = runRoot.toAbsolutePath().normalize();
        if (!run.startsWith(root)) throw new IllegalArgumentException("run path escapes store root");
        return mapper.readValue(run.resolve("validation-report.json").toFile(), ValidationReport.class);
    }

    private Map<String, Object> storageContext(ValidationContext context) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("contract", "ONSURE_VALIDATION_STORAGE_CONTEXT_V1");
        value.put("organization_id", attribute(context, "organization_id", "LOCAL_UNVERIFIED"));
        value.put("workspace_id", attribute(context, "workspace_id", "LOCAL_UNVERIFIED"));
        value.put("project_id", attribute(context, "project_id", "LOCAL_UNVERIFIED"));
        value.put("tenant_id", attribute(context, "tenant_id", "LOCAL_SINGLE_TENANT_UNVERIFIED"));
        value.put("target_id", context.target().targetId());
        value.put("job_id", context.job().jobId());
        value.put("cross_process_lock", true);
        value.put("tenant_isolation_verified", false);
        value.put("final_claim_allowed", false);
        return Map.copyOf(value);
    }

    private static String attribute(ValidationContext context, String key, String fallback) {
        Object value = context.attributes().get(key);
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }

    private static void verifyCompletedReceipts(ValidationContext context) throws Exception {
        boolean verifierPass = context.stageResults().stream()
                .anyMatch(value -> "INTERNAL_PRODUCT_VERIFIER".equals(value.stageId())
                        && value.decision() == Decision.PASS);
        boolean auditPass = context.stageResults().stream()
                .anyMatch(value -> "INTERNAL_PRODUCT_AUDIT".equals(value.stageId())
                        && value.decision() == Decision.PASS);
        if (verifierPass) {
            ProductReceiptWriter.verify(
                    context.runRoot().resolve("internal-verifier-receipt.json"),
                    "ONSURE_INTERNAL_VERIFIER_RECEIPT_V1", "ONSURE_INTERNAL_VERIFIER",
                    context.job().jobId());
        }
        if (auditPass) {
            ProductReceiptWriter.verify(
                    context.runRoot().resolve("internal-audit-receipt.json"),
                    "ONSURE_INTERNAL_AUDIT_RECEIPT_V1", "ONSURE_INTERNAL_AUDIT",
                    context.job().jobId());
        }
    }

    private void writeManifest(Path run) throws Exception {
        List<Path> files = new ArrayList<>();
        try (var stream = Files.list(run)) {
            stream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> !path.getFileName().toString().equals("manifest.sha256"))
                    .sorted()
                    .forEach(files::add);
        }
        StringBuilder manifest = new StringBuilder();
        for (Path file : files) {
            manifest.append(sha256(file)).append("  ")
                    .append(run.relativize(file).toString().replace('\\', '/')).append('\n');
        }
        writeText(run.resolve("manifest.sha256"), manifest.toString());
    }

    private void incrementRevision(String operation, Path subject) throws Exception {
        long revision = 0;
        if (Files.isRegularFile(revisionFile, LinkOption.NOFOLLOW_LINKS)) {
            revision = mapper.readTree(revisionFile.toFile()).path("revision").asLong(0);
        }
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("contract", "ONSURE_VALIDATION_STORE_REVISION_V1");
        value.put("revision", revision + 1);
        value.put("operation", operation);
        value.put("subject", root.relativize(subject).toString().replace('\\', '/'));
        value.put("updated_at", Instant.now().toString());
        value.put("cross_process_lock", true);
        writeJson(revisionFile, value);
    }

    private void writeJson(Path file, Object value) throws Exception {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.createDirectories(file.getParent());
        mapper.writeValue(temporary.toFile(), value);
        move(temporary, file);
    }

    private static void writeText(Path file, String value) throws Exception {
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

    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    private static String safe(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,128}")) {
            throw new IllegalArgumentException("unsafe identifier");
        }
        return value;
    }
}
