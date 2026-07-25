package io.onsure.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.onsure.assurance.Decision;
import io.onsure.assurance.ValidationResult;
import io.onsure.platform.ValidationModel.ValidationReport;
import io.onsure.platform.oruda.OrudaEvidenceRegistry;
import io.onsure.rag.RagPreparationService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** File-backed product store for evidence, findings, RCA, fixtures, locks and reports. */
public final class FileValidationStore {
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final Path root;

    public FileValidationStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Path createRunRoot(String targetId, String jobId) throws Exception {
        Path runRoot = root.resolve(safe(targetId)).resolve(safe(jobId)).toAbsolutePath().normalize();
        if (!runRoot.startsWith(root)) throw new IllegalArgumentException("run path escapes store root");
        Files.createDirectories(runRoot);
        return runRoot;
    }

    public void persist(ValidationContext context, ValidationReport report) throws Exception {
        Path run = context.runRoot();
        Files.createDirectories(run);
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
        new FailureModeRegistry(root.resolve("failure-mode-registry.json")).register(context.failureModes());
        verifyCompletedReceipts(context);
        persistOrudaEvidenceRegistry(context);
        writeManifest(run);
    }

    public ValidationReport readReport(Path runRoot) throws Exception {
        return mapper.readValue(runRoot.resolve("validation-report.json").toFile(), ValidationReport.class);
    }

    private static void persistOrudaEvidenceRegistry(ValidationContext context) throws Exception {
        if (!OrudaTargetAdapter.ID.equals(context.adapter().adapterId())) return;
        if (context.regressionLock() == null || context.fixtureResults().isEmpty()) return;
        OrudaEvidenceRegistry registry = new OrudaEvidenceRegistry();
        registry.populate(context);
        ValidationResult result = registry.verify(context.runRoot(), context.target().sourceRoot());
        if (result.decision() != Decision.PASS) {
            throw new IllegalStateException("ORUDA_EVIDENCE_REGISTRY_VERIFY_FAIL " + result.violations());
        }
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
            stream.filter(Files::isRegularFile)
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
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    private static String safe(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,128}")) {
            throw new IllegalArgumentException("unsafe identifier");
        }
        return value;
    }
}
