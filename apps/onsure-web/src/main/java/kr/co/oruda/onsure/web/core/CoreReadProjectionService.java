package kr.co.oruda.onsure.web.core;

import java.nio.file.Path;
import java.util.List;
import kr.co.oruda.onsure.platform.EnterpriseWebReadService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Thin Web adapter over the authoritative Core read facade. */
@Service
public class CoreReadProjectionService {
    public enum Availability {
        KNOWN,
        NOT_AVAILABLE,
        UNKNOWN
    }

    public record Projection<T>(Availability availability, T value, String reason) {
        static <T> Projection<T> known(T value) {
            return new Projection<>(Availability.KNOWN, value, null);
        }
        static <T> Projection<T> unavailable(String reason) {
            return new Projection<>(Availability.NOT_AVAILABLE, null, reason);
        }
        static <T> Projection<T> unknown(String reason) {
            return new Projection<>(Availability.UNKNOWN, null, reason);
        }
    }

    private final EnterpriseWebReadService core;
    private final String unavailableReason;

    public CoreReadProjectionService(
            @Value("${onsure.core.catalog-root:}") String catalogRoot,
            @Value("${onsure.core.validation-root:}") String validationRoot) {
        if (catalogRoot == null || catalogRoot.isBlank()
                || validationRoot == null || validationRoot.isBlank()) {
            this.core = null;
            this.unavailableReason = "CORE_READ_ROOTS_NOT_CONFIGURED";
        } else {
            this.core = new EnterpriseWebReadService(Path.of(catalogRoot), Path.of(validationRoot));
            this.unavailableReason = null;
        }
    }

    public Projection<List<EnterpriseWebReadService.ProjectSummary>> projects() {
        if (core == null) return Projection.unavailable(unavailableReason);
        try {
            return Projection.known(core.projects());
        } catch (Exception e) {
            return Projection.unknown("CORE_PROJECT_READ_FAILED");
        }
    }

    public Projection<EnterpriseWebReadService.ProjectSummary> project(String projectId) {
        if (core == null) return Projection.unavailable(unavailableReason);
        try {
            return Projection.known(core.project(projectId));
        } catch (IllegalArgumentException e) {
            return Projection.unavailable(e.getMessage());
        } catch (Exception e) {
            return Projection.unknown("CORE_PROJECT_READ_FAILED");
        }
    }

    public Projection<List<EnterpriseWebReadService.TargetSummary>> targets(String projectId) {
        if (core == null) return Projection.unavailable(unavailableReason);
        try {
            return Projection.known(core.targets(projectId));
        } catch (IllegalArgumentException e) {
            return Projection.unavailable(e.getMessage());
        } catch (Exception e) {
            return Projection.unknown("CORE_TARGET_READ_FAILED");
        }
    }

    public Projection<EnterpriseWebReadService.TargetSummary> target(String projectId, String targetId) {
        if (core == null) return Projection.unavailable(unavailableReason);
        try {
            return Projection.known(core.target(projectId, targetId));
        } catch (IllegalArgumentException e) {
            return Projection.unavailable(e.getMessage());
        } catch (Exception e) {
            return Projection.unknown("CORE_TARGET_READ_FAILED");
        }
    }

    public Projection<EnterpriseWebReadService.AssuranceSnapshot> assurance(String projectId, String targetId) {
        if (core == null) return Projection.unavailable(unavailableReason);
        try {
            return Projection.known(core.assurance(projectId, targetId));
        } catch (IllegalArgumentException e) {
            return Projection.unavailable(e.getMessage());
        } catch (Exception e) {
            return Projection.unknown("CORE_ASSURANCE_READ_FAILED");
        }
    }

    public Projection<List<EnterpriseWebReadService.EvidenceReceipt>> evidence(String projectId, String targetId) {
        if (core == null) return Projection.unavailable(unavailableReason);
        try {
            return Projection.known(core.evidence(projectId, targetId));
        } catch (IllegalArgumentException e) {
            return Projection.unavailable(e.getMessage());
        } catch (Exception e) {
            return Projection.unknown("CORE_EVIDENCE_READ_FAILED");
        }
    }
}
