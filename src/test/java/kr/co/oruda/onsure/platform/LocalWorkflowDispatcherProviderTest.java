package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.co.oruda.onsure.platform.ModelProviderAdapter.ModelInvocationRecord;
import kr.co.oruda.onsure.platform.ModelProviderAdapter.ModelInvocationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises {@code provider.status}/{@code provider.usage} through the real dispatch() boundary
 * (RBAC included), covering both the current production state (no registry wired in) and a
 * registry populated with in-test fake adapters.
 */
class LocalWorkflowDispatcherProviderTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void providerStatusDegradesGracefullyWhenNoRegistryIsConfigured() throws Exception {
        LocalWorkflowDispatcher dispatcher = new LocalWorkflowDispatcher(temp);
        Map<String, Object> result = result(dispatcher.dispatch("provider.status", request(Map.of())));
        assertEquals("NO_PROVIDER_REGISTERED", result.get("state"));
        assertEquals(List.of(), result.get("providers"));
    }

    @Test
    void providerUsageReturnsAllZeroSummaryWhenNoUsageHasBeenRecordedYet() throws Exception {
        LocalWorkflowDispatcher dispatcher = new LocalWorkflowDispatcher(temp);
        Map<String, Object> result = result(dispatcher.dispatch("provider.usage", request(Map.of())));
        @SuppressWarnings("unchecked")
        Map<String, Object> providers = (Map<String, Object>) result.get("providers");
        assertTrue(providers.isEmpty());
        ModelInvocationLedger.ProviderUsage overall = (ModelInvocationLedger.ProviderUsage) result.get("overall");
        assertEquals(0, overall.invocationCount());
        assertEquals(0, overall.totalInputTokens());
        assertEquals(0, overall.totalOutputTokens());
        assertEquals(0, overall.totalCostMicros());
    }

    @Test
    void providerStatusListsRegisteredProvidersDeclaredModelsAndSupportedTaskClasses() throws Exception {
        ModelProviderRegistry registry = new ModelProviderRegistry(
                List.of(new FakeProvider("fake-alpha", List.of("alpha-model-v1"), Set.of("REVIEW")),
                        new FakeProvider("fake-beta", List.of("beta-model-v1", "beta-model-v2"),
                                Set.of("REVIEW", "PLANNING"))));
        LocalWorkflowDispatcher dispatcher = new LocalWorkflowDispatcher(
                temp, AuthenticatedWorkflowIdentity.localAdministrator(), registry);

        Map<String, Object> result = result(dispatcher.dispatch("provider.status", request(Map.of())));
        assertEquals("PROVIDERS_REGISTERED", result.get("state"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> providers = (List<Map<String, Object>>) result.get("providers");
        assertEquals(2, providers.size());

        Map<String, Object> alpha = providers.stream()
                .filter(entry -> "fake-alpha".equals(entry.get("provider_id"))).findFirst().orElseThrow();
        assertEquals(List.of("alpha-model-v1"), alpha.get("declared_model_ids"));
        assertEquals(List.of("REVIEW"), alpha.get("supported_task_classes"));

        Map<String, Object> beta = providers.stream()
                .filter(entry -> "fake-beta".equals(entry.get("provider_id"))).findFirst().orElseThrow();
        assertEquals(List.of("beta-model-v1", "beta-model-v2"), beta.get("declared_model_ids"));
        assertEquals(List.of("REVIEW", "PLANNING"), beta.get("supported_task_classes"));
    }

    @Test
    void providerUsageAggregatesRecordedInvocationsThroughTheDefaultLedgerPath() throws Exception {
        LocalWorkflowDispatcher dispatcher = new LocalWorkflowDispatcher(temp);
        Path ledgerFile = temp.resolve(".onsure/model-usage/ledger.jsonl");
        ModelInvocationLedger.record(ledgerFile, new ModelInvocationRecord(
                "inv-1", "fake-alpha", "alpha-model-v1", "1.0", "REVIEW", Map.of(),
                100, 40, 10, "LOCAL_ONLY_NO_EGRESS", "sha256:digest-1"));
        ModelInvocationLedger.record(ledgerFile, new ModelInvocationRecord(
                "inv-2", "fake-alpha", "alpha-model-v1", "1.0", "REVIEW", Map.of(),
                50, 20, 5, "LOCAL_ONLY_NO_EGRESS", "sha256:digest-2"));

        Map<String, Object> result = result(dispatcher.dispatch("provider.usage", request(Map.of())));
        @SuppressWarnings("unchecked")
        Map<String, ModelInvocationLedger.ProviderUsage> providers =
                (Map<String, ModelInvocationLedger.ProviderUsage>) result.get("providers");
        ModelInvocationLedger.ProviderUsage alpha = providers.get("fake-alpha");
        assertEquals(2, alpha.invocationCount());
        assertEquals(150, alpha.totalInputTokens());
        assertEquals(60, alpha.totalOutputTokens());
        assertEquals(15, alpha.totalCostMicros());

        ModelInvocationLedger.ProviderUsage overall = (ModelInvocationLedger.ProviderUsage) result.get("overall");
        assertEquals(2, overall.invocationCount());
        assertEquals(150, overall.totalInputTokens());
    }

    private JsonNode request(Map<String, Object> value) {
        return mapper.valueToTree(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> result(Map<String, Object> envelope) {
        return (Map<String, Object>) envelope.get("result");
    }

    /** Minimal in-test fake provider, distinct from any real provider implementation. */
    private static final class FakeProvider implements ModelProviderAdapter {
        private final String providerId;
        private final List<String> declaredModelIds;
        private final Set<String> supportedTaskClasses;

        FakeProvider(String providerId, List<String> declaredModelIds, Set<String> supportedTaskClasses) {
            this.providerId = providerId;
            this.declaredModelIds = declaredModelIds;
            this.supportedTaskClasses = supportedTaskClasses;
        }

        @Override public String providerId() { return providerId; }
        @Override public boolean supportsTaskClass(String taskClass) { return supportedTaskClasses.contains(taskClass); }
        @Override public List<String> declaredModelIds() { return declaredModelIds; }

        @Override
        public ModelInvocationRecord invoke(ModelInvocationRequest request) {
            return new ModelInvocationRecord(
                    providerId + "-invocation", providerId, request.modelId(), "1.0", request.taskClass(),
                    request.configuration(), request.inputTokenEstimate(), 0, 0,
                    "LOCAL_ONLY_NO_EGRESS", "sha256:fake");
        }
    }
}
