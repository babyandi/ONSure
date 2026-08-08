package kr.co.oruda.onsure.platform;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Server-side identity bound to an already authenticated local credential. */
public record AuthenticatedWorkflowIdentity(
        String organizationId,
        String tenantId,
        String workspaceId,
        String actorId,
        Set<Role> roles,
        String dataRegion,
        AuthenticationMethod authenticationMethod) {
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9._:-]{1,160}");

    public enum Role { VIEWER, OPERATOR, APPROVER, AUDITOR, ADMIN }
    public enum AuthenticationMethod { LOCAL_BEARER_TOKEN, SIGNED_ENTERPRISE_IDENTITY }

    public AuthenticatedWorkflowIdentity {
        organizationId = requireId(organizationId, "organizationId");
        tenantId = requireId(tenantId, "tenantId");
        workspaceId = requireId(workspaceId, "workspaceId");
        actorId = requireId(actorId, "actorId");
        roles = Set.copyOf(Objects.requireNonNull(roles, "roles"));
        if (roles.isEmpty()) throw new IllegalArgumentException("IDENTITY_ROLE_REQUIRED");
        if (dataRegion == null || dataRegion.isBlank() || dataRegion.length() > 80) {
            throw new IllegalArgumentException("IDENTITY_DATA_REGION_INVALID");
        }
        dataRegion = dataRegion.trim();
        authenticationMethod = Objects.requireNonNull(authenticationMethod, "authenticationMethod");
    }

    public static AuthenticatedWorkflowIdentity localAdministrator() {
        return new AuthenticatedWorkflowIdentity(
                "LOCAL_ORGANIZATION", "LOCAL_TENANT", "LOCAL_WORKSPACE",
                "LOCAL_API_BEARER", Set.of(Role.ADMIN), "LOCAL",
                AuthenticationMethod.LOCAL_BEARER_TOKEN);
    }

    private static String requireId(String value, String field) {
        if (value == null || !ID.matcher(value).matches()) {
            throw new IllegalArgumentException("IDENTITY_ID_INVALID:" + field);
        }
        return value;
    }
}
