package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import kr.co.oruda.onsure.platform.ModelProviderAdapter.ModelInvocationRecord;
import kr.co.oruda.onsure.platform.ModelProviderAdapter.ModelInvocationRequest;
import org.junit.jupiter.api.Test;

/**
 * Proves NFR-05 (provider and model replaceability): two independent provider implementations
 * satisfy the identical {@link ModelProviderAdapter} contract, and callers can swap between them
 * through {@link ModelProviderRegistry} without any provider-specific code at the call site.
 */
class ModelProviderAdapterCompatibilityTest {

    /** Minimal in-repo stand-in for a hosted provider (e.g. a remote LLM API). */
    private static final class RemoteFakeProvider implements ModelProviderAdapter {
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public String providerId() { return "remote-fake"; }

        @Override
        public boolean supportsTaskClass(String taskClass) {
            return "REVIEW".equals(taskClass) || "PLANNING".equals(taskClass);
        }

        @Override
        public List<String> declaredModelIds() { return List.of("remote-model-v1"); }

        @Override
        public ModelInvocationRecord invoke(ModelInvocationRequest request) {
            return new ModelInvocationRecord(
                    "remote-" + sequence.incrementAndGet(),
                    providerId(),
                    request.modelId(),
                    "2026.08",
                    request.taskClass(),
                    request.configuration(),
                    request.inputTokenEstimate(),
                    request.inputTokenEstimate() / 2,
                    1200,
                    "EXTERNAL_NETWORK_EGRESS_ALLOWLISTED",
                    "sha256:" + Integer.toHexString(request.inputDigest().hashCode()));
        }
    }

    /** Minimal in-repo stand-in for an offline/local provider (no network egress). */
    private static final class LocalFakeProvider implements ModelProviderAdapter {
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public String providerId() { return "local-fake"; }

        @Override
        public boolean supportsTaskClass(String taskClass) {
            return "REVIEW".equals(taskClass);
        }

        @Override
        public List<String> declaredModelIds() { return List.of("local-model-v1"); }

        @Override
        public ModelInvocationRecord invoke(ModelInvocationRequest request) {
            return new ModelInvocationRecord(
                    "local-" + sequence.incrementAndGet(),
                    providerId(),
                    request.modelId(),
                    "0.9.3",
                    request.taskClass(),
                    request.configuration(),
                    request.inputTokenEstimate(),
                    request.inputTokenEstimate() / 3,
                    0,
                    "LOCAL_ONLY_NO_EGRESS",
                    "sha256:" + Integer.toHexString(request.inputDigest().hashCode()));
        }
    }

    private static void assertSatisfiesContract(ModelProviderAdapter provider) throws Exception {
        assertTrue(provider.supportsTaskClass("REVIEW"));
        assertTrue(provider.declaredModelIds().size() >= 1);
        ModelInvocationRequest request = new ModelInvocationRequest(
                "REVIEW", provider.declaredModelIds().get(0), Map.of("temperature", "0"), "digest-abc", 100);
        ModelInvocationRecord record = provider.invoke(request);
        assertEquals(provider.providerId(), record.providerId());
        assertEquals("REVIEW", record.taskClass());
        assertTrue(record.inputTokenCount() >= 0);
        assertTrue(record.outputTokenCount() >= 0);
        assertTrue(record.costMicros() >= 0);
        assertTrue(!record.dataTransferPolicy().isBlank());
        assertTrue(!record.invocationId().isBlank());
        assertTrue(!record.modelVersion().isBlank());
    }

    @Test
    void bothIndependentProvidersSatisfyTheSameContract() throws Exception {
        assertSatisfiesContract(new RemoteFakeProvider());
        assertSatisfiesContract(new LocalFakeProvider());
    }

    @Test
    void registryAllowsSwappingTheBoundProviderForATaskClassWithoutCallerChange() throws Exception {
        var registry = new ModelProviderRegistry(List.of(new RemoteFakeProvider(), new LocalFakeProvider()));
        registry.bindDefault("REVIEW", "remote-fake");
        ModelInvocationRequest request = new ModelInvocationRequest(
                "REVIEW", "any-model", Map.of(), "digest-xyz", 50);

        ModelInvocationRecord first = registry.require("REVIEW").invoke(request);
        assertEquals("remote-fake", first.providerId());

        registry.bindDefault("REVIEW", "local-fake");
        ModelInvocationRecord second = registry.require("REVIEW").invoke(request);
        assertEquals("local-fake", second.providerId());

        assertEquals(first.taskClass(), second.taskClass());
        assertEquals(first.invocationId().equals(second.invocationId()), false);
    }

    @Test
    void registryRejectsDuplicateProviderIds() {
        assertThrows(IllegalArgumentException.class, () ->
                new ModelProviderRegistry(List.of(new RemoteFakeProvider(), new RemoteFakeProvider())));
    }

    @Test
    void registryRejectsUnboundOrUnsupportedTaskClass() {
        var registry = new ModelProviderRegistry(List.of(new LocalFakeProvider()));
        assertThrows(IllegalArgumentException.class, () -> registry.require("PLANNING"));
        assertThrows(IllegalArgumentException.class, () -> registry.bindDefault("PLANNING", "local-fake"));
        assertThrows(IllegalArgumentException.class, () -> registry.bindDefault("REVIEW", "missing-provider"));
    }
}
