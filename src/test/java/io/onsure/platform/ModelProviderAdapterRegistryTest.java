package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModelProviderAdapterRegistryTest {
    private static final String INPUT = "a".repeat(64);
    private static final String OUTPUT = "b".repeat(64);

    @Test
    void providersAreReplaceableBehindTheSameDigestAndBudgetContract() throws Exception {
        ModelProviderAdapterRegistry.Adapter first = adapter("local-a", "fixture", false);
        ModelProviderAdapterRegistry.Adapter second = adapter("local-b", "fixture", false);
        ModelProviderAdapterRegistry registry = new ModelProviderAdapterRegistry(List.of(first, second));
        ModelProviderAdapterRegistry.Request request = request(false);

        var responseA = registry.invoke("local-a", "fixture", true, request);
        var responseB = registry.invoke("local-b", "fixture", true, request);
        assertEquals(responseA.outputSha256(), responseB.outputSha256());
        assertEquals(2, registry.descriptors().size());
        assertFalse(Boolean.TRUE.equals(registry.descriptors().get(0).get("final_claim_allowed")));
    }

    @Test
    void networkDataScopeBindingAndTokenBudgetsFailClosed() {
        ModelProviderAdapterRegistry local = new ModelProviderAdapterRegistry(
                List.of(adapter("local", "fixture", false)));
        assertThrows(IllegalArgumentException.class, () -> local.invoke(
                "local", "fixture", true,
                new ModelProviderAdapterRegistry.Request(
                        "request-001", "REVIEW", INPUT, 5000, 100,
                        "DIGEST_ONLY", false)));
        assertThrows(IllegalArgumentException.class, () -> local.invoke(
                "local", "fixture", true,
                new ModelProviderAdapterRegistry.Request(
                        "request-001", "REVIEW", INPUT, 10, 100,
                        "FULL_APPROVED_CONTEXT", false)));

        ModelProviderAdapterRegistry remote = new ModelProviderAdapterRegistry(
                List.of(adapter("remote", "hosted", true)));
        assertThrows(IllegalStateException.class,
                () -> remote.require("remote", "hosted", true));
        assertThrows(IllegalStateException.class,
                () -> remote.invoke("remote", "hosted", false, request(false)));
    }

    private static ModelProviderAdapterRegistry.Request request(boolean networkAllowed) {
        return new ModelProviderAdapterRegistry.Request(
                "request-001", "REVIEW", INPUT, 10, 100,
                "DIGEST_ONLY", networkAllowed);
    }

    private static ModelProviderAdapterRegistry.Adapter adapter(
            String provider, String model, boolean remote) {
        var descriptor = new ModelProviderAdapterRegistry.Descriptor(
                provider, model, "1", remote ? "REMOTE_PROVIDER" : "LOCAL_DETERMINISTIC",
                !remote, 4096, 1024, Set.of("DIGEST_ONLY", "REDACTED_CONTEXT"));
        return new ModelProviderAdapterRegistry.Adapter() {
            @Override public ModelProviderAdapterRegistry.Descriptor descriptor() { return descriptor; }
            @Override public ModelProviderAdapterRegistry.Response invoke(
                    ModelProviderAdapterRegistry.Request request) {
                return new ModelProviderAdapterRegistry.Response(
                        request.requestId(), provider, model, OUTPUT, request.estimatedInputTokens(),
                        25, remote, "receipt-001");
            }
        };
    }
}
