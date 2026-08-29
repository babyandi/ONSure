package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import kr.co.oruda.onsure.platform.ProductCatalog.Project;
import kr.co.oruda.onsure.platform.ProductCatalog.RegisteredTarget;
import kr.co.oruda.onsure.platform.ValidationModel.Evidence;

/** Read-only Core facade for external product surfaces such as Enterprise Web. */
public final class EnterpriseWebReadService {
    public enum Availability { KNOWN, NOT_AVAILABLE }

    public static final class NotAvailableException extends Exception {
        public NotAvailableException(String message) { super(message); }
    }

    public record ProjectSummary(String projectId, String workspaceId, String name, int targetCount, long coreRevision) {}
    public record TargetSummary(
            String projectId, String targetId, String name, String targetType,
            String immutableSourceReference, String adapterId,
            Availability assuranceAvailability, String canonicalAssuranceState, long coreRevision) {}
    public record AssuranceSnapshot(
            String targetId, Availability availability, String canonicalState, String reason, long coreRevision) {}
    public record EvidenceReceipt(
            String projectId, String targetId, String jobId, String evidenceId, String evidenceType,
            String source, String sha256, Instant collectedAt, Map<String, Object> attributes, String authority) {}

    private final Path catalogRoot;
    private final ProductCatalog catalog;
    private final Path validationRoot;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public EnterpriseWebReadService(Path catalogRoot, Path validationRoot) {
        this.catalogRoot = catalogRoot.toAbsolutePath().normalize();
        this.catalog = new ProductCatalog(this.catalogRoot);
        this.validationRoot = validationRoot.toAbsolutePath().normalize();
    }

    public List<ProjectSummary> projects() throws Exception {
        requireCatalogAvailable();
        long revision = catalog.revision();
        List<ProjectSummary> values = new ArrayList<>();
        for (Project project : catalog.projects()) {
            values.add(new ProjectSummary(project.projectId(), project.workspaceId(), project.name(),
                    catalog.targets(project.projectId()).size(), revision));
        }
        return List.copyOf(values);
    }

    public ProjectSummary project(String projectId) throws Exception {
        requireCatalogAvailable();
        Project project = catalog.requireProject(projectId);
        return new ProjectSummary(project.projectId(), project.workspaceId(), project.name(),
                catalog.targets(project.projectId()).size(), catalog.revision());
    }

    public List<TargetSummary> targets(String projectId) throws Exception {
        requireCatalogAvailable();
        catalog.requireProject(projectId);
        long revision = catalog.revision();
        return catalog.targets(projectId).stream().map(value -> targetSummary(value, revision)).toList();
    }

    public TargetSummary target(String projectId, String targetId) throws Exception {
        requireCatalogAvailable();
        RegisteredTarget value = catalog.targets(projectId).stream()
                .filter(candidate -> candidate.target().targetId().equals(targetId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("UNKNOWN_TARGET"));
        return targetSummary(value, catalog.revision());
    }

    public AssuranceSnapshot assurance(String projectId, String targetId) throws Exception {
        target(projectId, targetId);
        return new AssuranceSnapshot(targetId, Availability.NOT_AVAILABLE, null,
                "CORE_ASSURANCE_PROJECTION_NOT_IMPLEMENTED", catalog.revision());
    }

    public List<EvidenceReceipt> evidence(String projectId, String targetId) throws Exception {
        target(projectId, targetId);
        requireValidationStoreAvailable();
        Path targetRoot = validationRoot.resolve(safe(targetId)).normalize();
        if (!targetRoot.startsWith(validationRoot)) throw new IllegalArgumentException("unsafe target path");
        if (!Files.exists(targetRoot, LinkOption.NOFOLLOW_LINKS)) return List.of();
        if (!Files.isDirectory(targetRoot, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(targetRoot)) {
            throw new NotAvailableException("VALIDATION_TARGET_STORE_INVALID");
        }
        List<Path> runs;
        try (var stream = Files.list(targetRoot)) {
            runs = stream.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> !Files.isSymbolicLink(path))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
        }
        List<EvidenceReceipt> values = new ArrayList<>();
        for (Path run : runs) {
            Path evidenceFile = run.resolve("evidence.json").normalize();
            if (!evidenceFile.startsWith(targetRoot)
                    || !Files.isRegularFile(evidenceFile, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(evidenceFile)) continue;
            List<Evidence> evidence = mapper.readValue(evidenceFile.toFile(), new TypeReference<List<Evidence>>() {});
            for (Evidence item : evidence) {
                values.add(new EvidenceReceipt(projectId, targetId, run.getFileName().toString(),
                        item.evidenceId(), item.evidenceType(), item.source(), item.sha256(),
                        item.collectedAt(), item.attributes(), "ONSURE_CORE_VALIDATION_STORE"));
            }
        }
        return List.copyOf(values);
    }

    private void requireCatalogAvailable() throws NotAvailableException {
        if (!Files.isDirectory(catalogRoot, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(catalogRoot)) {
            throw new NotAvailableException("PRODUCT_CATALOG_NOT_AVAILABLE");
        }
    }

    private void requireValidationStoreAvailable() throws NotAvailableException {
        if (!Files.isDirectory(validationRoot, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(validationRoot)) {
            throw new NotAvailableException("VALIDATION_STORE_NOT_AVAILABLE");
        }
    }

    private static TargetSummary targetSummary(RegisteredTarget value, long revision) {
        var target = value.target();
        return new TargetSummary(value.projectId(), target.targetId(), target.targetName(), target.targetType().name(),
                target.immutableSourceReference(), target.adapterId(), Availability.NOT_AVAILABLE, null, revision);
    }

    private static String safe(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,128}")) throw new IllegalArgumentException("unsafe identifier");
        return value;
    }
}
