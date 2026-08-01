package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnterpriseCapabilityRuntimeTest {
    private static final String SOURCE = "a".repeat(64);
    private static final String PARENT = "b".repeat(64);
    @TempDir Path temp;

    @Test
    void everyEnterpriseLaneCreatesOnlyNonFinalSourceBoundReceipt() throws Exception {
        EnterpriseCapabilityRuntime runtime = new EnterpriseCapabilityRuntime(temp.resolve("state"), SOURCE);
        int sequence = 0;
        for (EnterpriseCapabilityRuntime.Capability capability
                : EnterpriseCapabilityRuntime.Capability.values()) {
            EnterpriseCapabilityRuntime.Result result = runtime.execute(
                    request("req-" + (++sequence), capability,
                            EnterpriseCapabilityRuntime.requiredControls(capability),
                            Map.of("external_execution", "NOT_RUN",
                                    "independent_verification", "NOT_RUN")),
                    Instant.parse("2026-07-29T00:00:00Z"));
            assertEquals("PASS_NONFINAL", result.decision());
            assertEquals(false, result.receipt().get("final_claim_allowed"));
            assertEquals("SELF_VALIDATION_NONFINAL", result.receipt().get("assurance_class"));
        }
    }

    @Test
    void missingControlAndFinalityOverclaimAreBlocked() {
        EnterpriseCapabilityRuntime runtime = new EnterpriseCapabilityRuntime(temp.resolve("state"), SOURCE);
        Set<String> controls = new HashSet<>(
                EnterpriseCapabilityRuntime.requiredControls(
                        EnterpriseCapabilityRuntime.Capability.PACKAGE_DELIVERY));
        controls.remove("SBOM");
        EnterpriseCapabilityRuntime.Result result = assertNoThrow(() -> runtime.execute(
                request("req-package", EnterpriseCapabilityRuntime.Capability.PACKAGE_DELIVERY,
                        controls, Map.of("external_execution", "PASS",
                                "independent_verification", "PASS")),
                Instant.parse("2026-07-29T00:00:00Z")));
        assertEquals("BLOCK", result.decision());
        assertTrue(result.violations().contains("CONTROL_MISSING:SBOM"));
        assertTrue(result.violations().contains("EXTERNAL_EXECUTION_OVERCLAIMED"));
        assertTrue(result.violations().contains("INDEPENDENT_VERIFICATION_OVERCLAIMED"));
    }

    @Test
    void sourceDriftExpiredApprovalAndMissingParentAreBlocked() {
        EnterpriseCapabilityRuntime runtime = new EnterpriseCapabilityRuntime(temp.resolve("state"), SOURCE);
        EnterpriseCapabilityRuntime.Request original = request(
                "req-auto", EnterpriseCapabilityRuntime.Capability.AUTOMATION,
                EnterpriseCapabilityRuntime.requiredControls(
                        EnterpriseCapabilityRuntime.Capability.AUTOMATION),
                Map.of("external_execution", "NOT_RUN", "independent_verification", "NOT_RUN"));
        EnterpriseCapabilityRuntime.Request invalid = new EnterpriseCapabilityRuntime.Request(
                original.requestId(), original.capability(), "c".repeat(64), original.tenantId(),
                original.actor(), Instant.parse("2026-07-28T00:00:00Z"), original.controls(),
                original.claims(), "invalid");
        EnterpriseCapabilityRuntime.Result result = assertNoThrow(() -> runtime.execute(
                invalid, Instant.parse("2026-07-29T00:00:00Z")));
        assertTrue(result.violations().contains("SOURCE_SHA_MISMATCH"));
        assertTrue(result.violations().contains("APPROVAL_EXPIRED_OR_MISSING"));
        assertTrue(result.violations().contains("PARENT_RECEIPT_SHA_INVALID"));
    }

    @Test
    void duplicateRequestCannotReplayLedgerIdentity() throws Exception {
        EnterpriseCapabilityRuntime runtime = new EnterpriseCapabilityRuntime(temp.resolve("state"), SOURCE);
        EnterpriseCapabilityRuntime.Request request = request(
                "req-replay", EnterpriseCapabilityRuntime.Capability.REGULATORY_CONTROL,
                EnterpriseCapabilityRuntime.requiredControls(
                        EnterpriseCapabilityRuntime.Capability.REGULATORY_CONTROL),
                Map.of("external_execution", "NOT_RUN", "independent_verification", "NOT_RUN"));
        runtime.execute(request, Instant.parse("2026-07-29T00:00:00Z"));
        assertThrows(IllegalStateException.class,
                () -> runtime.execute(request, Instant.parse("2026-07-29T00:00:00Z")));
    }

    private EnterpriseCapabilityRuntime.Request request(
            String id, EnterpriseCapabilityRuntime.Capability capability,
            Set<String> controls, Map<String, String> claims) {
        return new EnterpriseCapabilityRuntime.Request(
                id, capability, SOURCE, "tenant-001", "owner-001",
                Instant.parse("2026-07-30T00:00:00Z"), controls, new HashMap<>(claims), PARENT);
    }

    private static <T> T assertNoThrow(ThrowingSupplier<T> operation) {
        try {
            return operation.get();
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    @FunctionalInterface
    interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
