package kr.co.oruda.onsure.web;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import kr.co.oruda.onsure.platform.ProductCatalog;
import kr.co.oruda.onsure.platform.ProductCatalog.Project;
import kr.co.oruda.onsure.platform.ProductCatalog.RegisteredTarget;
import kr.co.oruda.onsure.platform.ProductCatalog.Workspace;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only projection of the existing ONSure ProductCatalog for the browser Workbench.
 *
 * <p>No Web-owned catalog is created and no direct JSON parsing is performed here. If the
 * authoritative workspace is not configured or cannot be read safely, the projection fails
 * closed instead of inventing state.</p>
 */
@RestController
@RequestMapping("/api/v1/workbench")
public final class CoreCatalogReadModelController {
    public static final String CONTRACT = "ONSURE_WEB_CATALOG_READ_MODEL_V1";
    private final String configuredWorkspaceRoot;

    public CoreCatalogReadModelController(@Value("${onsure.workspace-root:}") String configuredWorkspaceRoot) {
        this.configuredWorkspaceRoot = configuredWorkspaceRoot == null ? "" : configuredWorkspaceRoot.strip();
    }

    @GetMapping("/catalog")
    public CatalogSnapshot catalog() {
        if (configuredWorkspaceRoot.isBlank()) {
            return unavailable("WORKSPACE_ROOT_NOT_CONFIGURED", "NOT_CONFIGURED_NONFINAL");
        }

        final Path workspaceRoot;
        try {
            workspaceRoot = Path.of(configuredWorkspaceRoot).toAbsolutePath().normalize();
        } catch (RuntimeException invalidPath) {
            return unavailable("WORKSPACE_ROOT_INVALID", "CORE_READ_MODEL_BLOCKED_NONFINAL");
        }

        if (!Files.isDirectory(workspaceRoot, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(workspaceRoot)) {
            return unavailable("WORKSPACE_ROOT_UNAVAILABLE", "CORE_READ_MODEL_BLOCKED_NONFINAL");
        }

        try {
            ProductCatalog catalog = new ProductCatalog(workspaceRoot.resolve(".onsure/product-catalog"));
            List<WorkspaceSummary> workspaces = catalog.workspaces().stream()
                    .map(CoreCatalogReadModelController::workspaceSummary)
                    .toList();
            List<ProjectSummary> projects = catalog.projects().stream()
                    .map(CoreCatalogReadModelController::projectSummary)
                    .toList();
            List<TargetSummary> targets = new ArrayList<>();
            for (Project project : catalog.projects()) {
                for (RegisteredTarget registered : catalog.targets(project.projectId())) {
                    targets.add(targetSummary(registered));
                }
            }
            return new CatalogSnapshot(
                    CONTRACT,
                    "CORE_READ_MODEL_NONFINAL",
                    true,
                    null,
                    catalog.revision(),
                    workspaces,
                    projects,
                    List.copyOf(targets),
                    false,
                    false,
                    false);
        } catch (Exception unreadable) {
            return unavailable("PRODUCT_CATALOG_READ_FAILED", "CORE_READ_MODEL_BLOCKED_NONFINAL");
        }
    }

    private static WorkspaceSummary workspaceSummary(Workspace workspace) {
        return new WorkspaceSummary(workspace.workspaceId(), workspace.name(), workspace.createdAt().toString());
    }

    private static ProjectSummary projectSummary(Project project) {
        return new ProjectSummary(
                project.projectId(), project.workspaceId(), project.name(), project.createdAt().toString());
    }

    private static TargetSummary targetSummary(RegisteredTarget registered) {
        var target = registered.target();
        return new TargetSummary(
                registered.projectId(),
                target.targetId(),
                target.targetName(),
                target.targetType().name(),
                target.immutableSourceReference(),
                target.adapterId(),
                target.policyProfile(),
                target.executionProfile(),
                registered.registeredAt().toString());
    }

    private static CatalogSnapshot unavailable(String reason, String state) {
        return new CatalogSnapshot(
                CONTRACT, state, false, reason, 0L, List.of(), List.of(), List.of(),
                false, false, false);
    }

    public record WorkspaceSummary(String workspaceId, String name, String createdAt) {}

    public record ProjectSummary(String projectId, String workspaceId, String name, String createdAt) {}

    /** Deliberately excludes target.sourceRoot so the browser API does not disclose local filesystem paths. */
    public record TargetSummary(
            String projectId,
            String targetId,
            String targetName,
            String targetType,
            String immutableSourceReference,
            String adapterId,
            String policyProfile,
            String executionProfile,
            String registeredAt) {}

    public record CatalogSnapshot(
            String contract,
            String state,
            boolean available,
            String blockedReason,
            long catalogRevision,
            List<WorkspaceSummary> workspaces,
            List<ProjectSummary> projects,
            List<TargetSummary> targets,
            boolean independentVerificationComplete,
            boolean finalClaimAllowed,
            boolean productionGo) {}
}
