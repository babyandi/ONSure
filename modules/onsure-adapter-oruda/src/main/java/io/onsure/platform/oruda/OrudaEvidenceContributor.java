package io.onsure.platform.oruda;

import io.onsure.assurance.ValidationResult;
import io.onsure.platform.TargetEvidenceContributor;
import io.onsure.platform.ValidationContext;

/** Service-provider bridge that keeps ORUDA evidence implementation outside core imports. */
public final class OrudaEvidenceContributor implements TargetEvidenceContributor {
    private static final String TARGET_ADAPTER_ID = "ONSURE_ORUDA_TARGET_ADAPTER_V1";
    private final OrudaEvidenceRegistry registry;

    public OrudaEvidenceContributor() {
        this(new OrudaEvidenceRegistry());
    }

    OrudaEvidenceContributor(OrudaEvidenceRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean supports(String adapterId) {
        return TARGET_ADAPTER_ID.equals(adapterId);
    }

    @Override
    public ValidationResult persistAndVerify(ValidationContext context) throws Exception {
        registry.populate(context);
        return registry.verify(context.runRoot(), context.target().sourceRoot());
    }
}
