package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import kr.co.oruda.onsure.platform.DeploymentProfileValidator.DeploymentConfiguration;
import kr.co.oruda.onsure.platform.DeploymentProfileValidator.DeploymentProfileViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

class DeploymentProfileValidatorTest {

    @Test
    void saasWithOnlineLicenseAndNetworkEgressIsValid() {
        var config = new DeploymentConfiguration(true, true, false, false);
        Assertions.assertDoesNotThrow(() -> DeploymentProfileValidator.validate(DeploymentProfile.SAAS, config));
    }

    @Test
    void airGappedRejectsExternalNetworkEgressEvenIfEnabledByMistake() {
        var config = new DeploymentConfiguration(true, false, true, true);
        DeploymentProfileViolationException thrown = Assertions.assertThrows(
                DeploymentProfileViolationException.class,
                () -> DeploymentProfileValidator.validate(DeploymentProfile.AIR_GAPPED, config));
        assertTrue(thrown.violations().contains("EXTERNAL_NETWORK_EGRESS_NOT_ALLOWED_FOR_PROFILE"));
    }

    @Test
    void airGappedRequiresSignedOfflineSnapshotAndManualEvidenceExport() {
        var missingBoth = new DeploymentConfiguration(false, false, false, false);
        DeploymentProfileViolationException thrown = Assertions.assertThrows(
                DeploymentProfileViolationException.class,
                () -> DeploymentProfileValidator.validate(DeploymentProfile.AIR_GAPPED, missingBoth));
        assertTrue(thrown.violations().contains("SIGNED_OFFLINE_SNAPSHOT_REQUIRED_FOR_PROFILE"));
        assertTrue(thrown.violations().contains("MANUAL_EVIDENCE_EXPORT_REQUIRED_FOR_PROFILE"));

        var complete = new DeploymentConfiguration(false, false, true, true);
        Assertions.assertDoesNotThrow(() -> DeploymentProfileValidator.validate(DeploymentProfile.AIR_GAPPED, complete));
    }

    @Test
    void onPremisesDoesNotRequireOnlineLicenseValidation() {
        var config = new DeploymentConfiguration(true, false, false, false);
        Assertions.assertDoesNotThrow(() -> DeploymentProfileValidator.validate(DeploymentProfile.ON_PREMISES, config));
    }

    @Test
    void hybridRequiresOnlineLicenseValidation() {
        var missingLicenseCheck = new DeploymentConfiguration(true, false, false, false);
        DeploymentProfileViolationException thrown = Assertions.assertThrows(
                DeploymentProfileViolationException.class,
                () -> DeploymentProfileValidator.validate(DeploymentProfile.HYBRID, missingLicenseCheck));
        assertTrue(thrown.violations().contains("ONLINE_LICENSE_VALIDATION_REQUIRED_FOR_PROFILE"));
    }

    @Test
    void isValidReturnsBooleanInsteadOfThrowingForPreflightChecks() {
        var bad = new DeploymentConfiguration(true, false, true, true);
        assertFalse(DeploymentProfileValidator.isValid(DeploymentProfile.AIR_GAPPED, bad));
        var good = new DeploymentConfiguration(false, false, true, true);
        assertTrue(DeploymentProfileValidator.isValid(DeploymentProfile.AIR_GAPPED, good));
    }
}
