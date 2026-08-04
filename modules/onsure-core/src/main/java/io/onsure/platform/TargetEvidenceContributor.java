package io.onsure.platform;

import io.onsure.assurance.ValidationResult;

/**
 * Target-specific evidence persistence extension loaded from an optional adapter module.
 * Implementations must recalculate and verify their evidence before returning success.
 */
public interface TargetEvidenceContributor {
    /** Returns whether this contributor owns evidence for the supplied target adapter. */
    boolean supports(String adapterId);

    /** Persists target-specific evidence and verifies the persisted representation. */
    ValidationResult persistAndVerify(ValidationContext context) throws Exception;
}
