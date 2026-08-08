package kr.co.oruda.onsure.assurance;

import java.util.List;

public record OMakerPlanContext(
        String producerAuthority,
        String approverAuthority,
        String sourceDigest,
        String requirementDigest,
        String designDigest,
        String policyDigest,
        String planDigest,
        List<String> declaredFileOperations,
        List<String> requiredTests,
        List<String> securityControls,
        boolean approvalRequired,
        boolean approvalPresent,
        boolean rollbackPlanPresent) {
}
