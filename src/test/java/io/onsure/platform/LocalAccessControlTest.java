package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class LocalAccessControlTest {
    private static final String ADMIN = "admin-" + "a".repeat(40);
    private static final String VIEWER = "viewer-" + "v".repeat(40);
    private static final String OPERATOR = "operator-" + "o".repeat(40);
    private static final String APPROVER = "approver-" + "p".repeat(40);

    @Test
    void mapsDistinctTokensToLeastPrivilegeRoles() {
        LocalAccessControl access = new LocalAccessControl(ADMIN, Map.of(
                "ONSURE_LOCAL_API_VIEWER_TOKEN", VIEWER,
                "ONSURE_LOCAL_API_OPERATOR_TOKEN", OPERATOR,
                "ONSURE_LOCAL_API_APPROVER_TOKEN", APPROVER));
        var viewer = access.authenticate("Bearer " + VIEWER);
        var operator = access.authenticate("Bearer " + OPERATOR);
        var admin = access.authenticate("Bearer " + ADMIN);
        var approver = access.authenticate("Bearer " + APPROVER);
        assertEquals(LocalAccessControl.Role.VIEWER, viewer.role());
        assertEquals(LocalAccessControl.Role.OPERATOR, operator.role());
        assertEquals(LocalAccessControl.Role.ADMIN, admin.role());
        assertEquals(LocalAccessControl.Role.APPROVER, approver.role());
        assertTrue(LocalAccessControl.allowed(viewer, LocalAccessControl.Permission.VIEW));
        assertFalse(LocalAccessControl.allowed(viewer, LocalAccessControl.Permission.OPERATE_PROGRAMS));
        assertTrue(LocalAccessControl.allowed(operator, LocalAccessControl.Permission.OPERATE_PROGRAMS));
        assertFalse(LocalAccessControl.allowed(operator, LocalAccessControl.Permission.REQUEST_SETTINGS));
        assertTrue(LocalAccessControl.allowed(admin, LocalAccessControl.Permission.REQUEST_SETTINGS));
        assertFalse(LocalAccessControl.allowed(admin, LocalAccessControl.Permission.APPROVE_SETTINGS));
        assertTrue(LocalAccessControl.allowed(approver, LocalAccessControl.Permission.APPROVE_SETTINGS));
        assertNull(access.authenticate("Bearer invalid"));
    }

    @Test
    void rejectsTokenReuseAcrossRoles() {
        assertThrows(IllegalArgumentException.class, () -> new LocalAccessControl(
                ADMIN, Map.of("ONSURE_LOCAL_API_APPROVER_TOKEN", ADMIN)));
    }
}
