package io.onsure.assurance;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class PermitValidator {
    private static final Pattern SHA256 = Pattern.compile("^[a-f0-9]{64}$");

    public ValidationResult validate(
            PermitContext permit,
            String expectedWorkspaceId,
            String expectedSubjectDigest,
            String expectedPolicyDigest,
            Set<String> requiredScopes,
            Instant now) {
        List<String> violations = new ArrayList<>();
        if (permit == null) return ValidationResult.fail(List.of("PERMIT_MISSING"));
        if (requiredScopes == null) {
            violations.add("REQUIRED_PERMIT_SCOPES_MISSING");
            requiredScopes = Set.of();
        }
        if (now == null) {
            violations.add("PERMIT_VALIDATION_TIME_MISSING");
        }

        if (permit.permitId() == null || permit.permitId().isBlank()) violations.add("MISSING_PERMIT_ID");
        if (!Objects.equals(permit.workspaceId(), expectedWorkspaceId)) violations.add("PERMIT_WORKSPACE_MISMATCH");
        if (!Objects.equals(permit.subjectDigest(), expectedSubjectDigest)) violations.add("PERMIT_SUBJECT_MISMATCH");
        if (!Objects.equals(permit.policyDigest(), expectedPolicyDigest)) violations.add("PERMIT_POLICY_MISMATCH");
        if (!isDigest(permit.subjectDigest()) || !isDigest(permit.policyDigest())) violations.add("INVALID_PERMIT_DIGEST");
        if (permit.revoked()) violations.add("REVOKED_PERMIT");
        if (now != null) {
            if (permit.notBefore() == null || now.isBefore(permit.notBefore())) violations.add("PERMIT_NOT_YET_VALID");
            if (permit.expiresAt() == null || !now.isBefore(permit.expiresAt())) violations.add("EXPIRED_PERMIT");
        } else {
            if (permit.notBefore() == null) violations.add("PERMIT_NOT_YET_VALID");
            if (permit.expiresAt() == null) violations.add("EXPIRED_PERMIT");
        }
        if (permit.notBefore() != null && permit.expiresAt() != null
                && !permit.notBefore().isBefore(permit.expiresAt())) {
            violations.add("PERMIT_VALIDITY_WINDOW_INVALID");
        }
        if (permit.scopes() == null || !permit.scopes().containsAll(requiredScopes)) violations.add("PERMIT_SCOPE_MISSING");
        return violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations);
    }

    private static boolean isDigest(String value) {
        return value != null && SHA256.matcher(value).matches();
    }
}
