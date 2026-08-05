package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalGatewaySettingsServiceTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void requiresDistinctApproverAndNeverAutoApplies() throws Exception {
        LocalGatewaySettingsService service = new LocalGatewaySettingsService(temp, Map.of(
                "ONSURE_LLM_PROVIDER", "local-mock", "ONSURE_LLM_MODEL", "old-model"));
        var admin = new LocalAccessControl.Identity("admin", LocalAccessControl.Role.ADMIN, "a".repeat(64));
        var approver = new LocalAccessControl.Identity("approver", LocalAccessControl.Role.APPROVER, "b".repeat(64));
        Map<String, Object> request = service.request(mapper.valueToTree(Map.of(
                "provider", "local-mock", "model", "new-model", "requests_per_second", 10,
                "cost_per_token_micros", 2, "reason", "bounded test")), admin);
        assertEquals("AWAITING_APPROVAL", request.get("state"));
        assertFalse(Boolean.TRUE.equals(request.get("automatic_apply_enabled")));
        Map<String, Object> approved = service.approve(mapper.valueToTree(Map.of(
                "request_id", request.get("request_id"), "decision", "APPROVE",
                "reason", "reviewed")), approver);
        assertEquals("APPROVED_PENDING_EXTERNAL_APPLY", approved.get("state"));
        assertEquals(request.get("request_sha256"), approved.get("request_sha256"));
        assertTrue(approved.get("receipt_sha256").toString().matches("[0-9a-f]{64}"));
        assertThrows(IllegalArgumentException.class, () -> service.approve(mapper.valueToTree(Map.of(
                "request_id", request.get("request_id"), "decision", "APPROVE",
                "reason", "replay")), approver));
        assertEquals("old-model", ((Map<?, ?>) service.list(10).get("current")).get("model"));
    }

    @Test
    void rejectsSelfApprovalAndSecretFields() throws Exception {
        LocalGatewaySettingsService service = new LocalGatewaySettingsService(temp, Map.of());
        var admin = new LocalAccessControl.Identity("same", LocalAccessControl.Role.ADMIN, "a".repeat(64));
        var sameApprover = new LocalAccessControl.Identity("same", LocalAccessControl.Role.APPROVER, "b".repeat(64));
        Map<String, Object> request = service.request(mapper.valueToTree(Map.of(
                "provider", "local-mock", "model", "model", "requests_per_second", 10,
                "cost_per_token_micros", 0, "reason", "test")), admin);
        assertThrows(IllegalArgumentException.class, () -> service.approve(mapper.valueToTree(Map.of(
                "request_id", request.get("request_id"), "decision", "APPROVE", "reason", "x")), sameApprover));
        assertThrows(IllegalArgumentException.class, () -> service.request(mapper.valueToTree(Map.of(
                "provider", "local-mock", "model", "model", "requests_per_second", 10,
                "cost_per_token_micros", 0, "reason", "x", "api_key", "forbidden")), admin));
    }

    @Test
    void rejectsTamperedRequestBeforeApproval() throws Exception {
        LocalGatewaySettingsService service = new LocalGatewaySettingsService(temp, Map.of());
        var admin = new LocalAccessControl.Identity("admin", LocalAccessControl.Role.ADMIN, "a".repeat(64));
        var approver = new LocalAccessControl.Identity("approver", LocalAccessControl.Role.APPROVER, "b".repeat(64));
        Map<String, Object> request = service.request(mapper.valueToTree(Map.of(
                "provider", "local-mock", "model", "model", "requests_per_second", 10,
                "cost_per_token_micros", 0, "reason", "test")), admin);
        Path file = temp.resolve(".onsure/management/gateway-requests")
                .resolve(request.get("request_id") + ".json");
        @SuppressWarnings("unchecked")
        Map<String, Object> tampered = mapper.readValue(file.toFile(), Map.class);
        tampered.put("reason", "tampered");
        mapper.writeValue(file.toFile(), tampered);
        assertThrows(IllegalStateException.class, () -> service.approve(mapper.valueToTree(Map.of(
                "request_id", request.get("request_id"), "decision", "APPROVE",
                "reason", "reviewed")), approver));
        assertTrue(Files.isRegularFile(file));
    }
}
