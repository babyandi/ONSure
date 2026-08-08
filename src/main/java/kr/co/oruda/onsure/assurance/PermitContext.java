package kr.co.oruda.onsure.assurance;

import java.time.Instant;
import java.util.Set;

public record PermitContext(
        String permitId,
        String workspaceId,
        String subjectDigest,
        String policyDigest,
        Set<String> scopes,
        Instant notBefore,
        Instant expiresAt,
        boolean revoked) {
}
