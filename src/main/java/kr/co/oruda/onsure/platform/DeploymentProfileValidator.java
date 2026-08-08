package kr.co.oruda.onsure.platform;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates an actual runtime configuration against the hard constraints of its declared
 * {@link DeploymentProfile}, fail-closed: a mismatch throws rather than silently allowing a
 * dangerous configuration (e.g. AIR_GAPPED with external network egress enabled).
 */
public final class DeploymentProfileValidator {

    public record DeploymentConfiguration(
            boolean externalNetworkEgressEnabled,
            boolean onlineLicenseValidationConfigured,
            boolean signedOfflineSnapshotPresent,
            boolean manualEvidenceExportConfigured) {}

    public static final class DeploymentProfileViolationException extends RuntimeException {
        private final List<String> violations;

        DeploymentProfileViolationException(DeploymentProfile profile, List<String> violations) {
            super("DEPLOYMENT_PROFILE_VIOLATION[" + profile + "]: " + String.join(",", violations));
            this.violations = List.copyOf(violations);
        }

        public List<String> violations() { return violations; }
    }

    private DeploymentProfileValidator() {}

    /** Throws {@link DeploymentProfileViolationException} if configuration violates the profile. */
    public static void validate(DeploymentProfile profile, DeploymentConfiguration configuration) {
        if (profile == null) throw new IllegalArgumentException("profile");
        if (configuration == null) throw new IllegalArgumentException("configuration");

        List<String> violations = new ArrayList<>();
        if (configuration.externalNetworkEgressEnabled() && !profile.externalNetworkEgressAllowed()) {
            violations.add("EXTERNAL_NETWORK_EGRESS_NOT_ALLOWED_FOR_PROFILE");
        }
        if (profile.onlineLicenseValidationRequired() && !configuration.onlineLicenseValidationConfigured()) {
            violations.add("ONLINE_LICENSE_VALIDATION_REQUIRED_FOR_PROFILE");
        }
        if (profile.signedOfflineSnapshotRequired() && !configuration.signedOfflineSnapshotPresent()) {
            violations.add("SIGNED_OFFLINE_SNAPSHOT_REQUIRED_FOR_PROFILE");
        }
        if (profile.manualEvidenceExportRequired() && !configuration.manualEvidenceExportConfigured()) {
            violations.add("MANUAL_EVIDENCE_EXPORT_REQUIRED_FOR_PROFILE");
        }
        if (!violations.isEmpty()) {
            throw new DeploymentProfileViolationException(profile, violations);
        }
    }

    /** Returns true/false instead of throwing; useful for preflight UI checks. */
    public static boolean isValid(DeploymentProfile profile, DeploymentConfiguration configuration) {
        try {
            validate(profile, configuration);
            return true;
        } catch (DeploymentProfileViolationException violation) {
            return false;
        }
    }
}
