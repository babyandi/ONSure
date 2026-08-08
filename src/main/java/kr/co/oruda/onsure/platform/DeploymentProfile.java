package kr.co.oruda.onsure.platform;

/**
 * The four deployment topologies this product supports (docs/master/04_ARCHITECTURE_DATA_API_OLICENSE.md
 * Sec.2). Each profile declares hard constraints that {@link DeploymentProfileValidator} enforces
 * against an actual runtime configuration -- e.g. an AIR_GAPPED deployment must never be
 * configured with external network egress, regardless of how it was launched.
 */
public enum DeploymentProfile {
    SAAS(true, true, false, false),
    HYBRID(true, true, false, false),
    ON_PREMISES(true, false, false, false),
    AIR_GAPPED(false, false, true, true);

    private final boolean externalNetworkEgressAllowed;
    private final boolean onlineLicenseValidationRequired;
    private final boolean signedOfflineSnapshotRequired;
    private final boolean manualEvidenceExportRequired;

    DeploymentProfile(
            boolean externalNetworkEgressAllowed,
            boolean onlineLicenseValidationRequired,
            boolean signedOfflineSnapshotRequired,
            boolean manualEvidenceExportRequired) {
        this.externalNetworkEgressAllowed = externalNetworkEgressAllowed;
        this.onlineLicenseValidationRequired = onlineLicenseValidationRequired;
        this.signedOfflineSnapshotRequired = signedOfflineSnapshotRequired;
        this.manualEvidenceExportRequired = manualEvidenceExportRequired;
    }

    public boolean externalNetworkEgressAllowed() { return externalNetworkEgressAllowed; }

    public boolean onlineLicenseValidationRequired() { return onlineLicenseValidationRequired; }

    public boolean signedOfflineSnapshotRequired() { return signedOfflineSnapshotRequired; }

    public boolean manualEvidenceExportRequired() { return manualEvidenceExportRequired; }
}
