package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fail-closed runtime boundary for post-final-target DD-001..040 assurance operations.
 *
 * <p>This class implements only the authenticated/contract envelope and exact DD routing
 * boundary. It MUST NOT synthesize semantic PASS. Until a DD-specific evaluator is qualified,
 * every operation returns HOLD with SEMANTIC_EVALUATOR_NOT_QUALIFIED.</p>
 */
public final class DdAssuranceOperationRuntime {
    public static final String CONTRACT = "ONSURE_DD_ASSURANCE_OPERATION_RUNTIME_V2";

    private static final Map<String, String> DD_BY_OPERATION = Map.ofEntries(
            Map.entry("assurance.visibility-evidence.evaluate", "DD-001"),
            Map.entry("assurance.oracle-authority.evaluate", "DD-002"),
            Map.entry("assurance.independence.evaluate", "DD-003"),
            Map.entry("provider.currentness.evaluate", "DD-004"),
            Map.entry("adapter.coverage.evaluate", "DD-005"),
            Map.entry("assurance.tcb-health.evaluate", "DD-006"),
            Map.entry("trust.epoch.requalify", "DD-007"),
            Map.entry("coverage.budget.evaluate", "DD-008"),
            Map.entry("waiver.currentness.evaluate", "DD-009"),
            Map.entry("portfolio.systemic-impact.evaluate", "DD-010"),
            Map.entry("corpus.contribution.requalify", "DD-011"),
            Map.entry("reviewer.qualification.evaluate", "DD-012"),
            Map.entry("data.rights-purpose.evaluate", "DD-013"),
            Map.entry("engagement.authorization.evaluate", "DD-014"),
            Map.entry("tenant.offboarding.qualify", "DD-015"),
            Map.entry("claim.localization.verify", "DD-016"),
            Map.entry("external-effect.delivery.reconcile", "DD-017"),
            Map.entry("checkpoint.effect.reconcile", "DD-018"),
            Map.entry("evidence-head.reconcile", "DD-019"),
            Map.entry("vendor.exit.qualify", "DD-020"),
            Map.entry("evidence.redaction.verify", "DD-021"),
            Map.entry("break-glass.review", "DD-022"),
            Map.entry("policy.currency.evaluate", "DD-023"),
            Map.entry("benchmark.contamination.evaluate", "DD-024"),
            Map.entry("regulation.change-impact.evaluate", "DD-025"),
            Map.entry("applicability.context.recalculate", "DD-026"),
            Map.entry("business-calendar.resolve", "DD-027"),
            Map.entry("blackbox.claim-ceiling.evaluate", "DD-028"),
            Map.entry("target-testing.authorization.evaluate", "DD-029"),
            Map.entry("organization.transfer.requalify", "DD-030"),
            Map.entry("residency.migration.requalify", "DD-031"),
            Map.entry("dependency.replacement.qualify", "DD-032"),
            Map.entry("crypto.historical-verification.requalify", "DD-033"),
            Map.entry("evidence.disclosure.transfer", "DD-034"),
            Map.entry("privileged-effect.requalify", "DD-035"),
            Map.entry("independence.common-control.evaluate", "DD-036"),
            Map.entry("evidence.schema-migration.verify", "DD-037"),
            Map.entry("financial-effect.test-authorize", "DD-038"),
            Map.entry("observation.coverage.evaluate", "DD-039"),
            Map.entry("discovery.saturation.evaluate", "DD-040"));

    public boolean supports(String operation) {
        return DD_BY_OPERATION.containsKey(operation);
    }

    public Set<String> operations() {
        return DD_BY_OPERATION.keySet();
    }

    public String ddIdFor(String operation) {
        String dd = DD_BY_OPERATION.get(operation);
        if (dd == null) throw new IllegalArgumentException("DD_OPERATION_UNSUPPORTED:" + operation);
        return dd;
    }

    public Map<String, Object> execute(String operation, JsonNode request) {
        String dd = ddIdFor(operation);
        DdAssuranceContractValidator.validateRequest(dd, operation, request);
        JsonNode evidence = request.path("evidence_refs");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contract", CONTRACT);
        result.put("dd_id", dd);
        result.put("operation", operation);
        result.put("decision", "HOLD");
        result.put("semantic_evaluator_state", "NOT_QUALIFIED");
        result.put("blocking_reasons", List.of("SEMANTIC_EVALUATOR_NOT_QUALIFIED"));
        result.put("evidence_ref_count", evidence.size());
        result.put("evidence_receipt_refs", List.of());
        result.put("claim_strengthening_allowed", false);
        result.put("external_effect_performed", false);
        result.put("final_claim_allowed", false);
        Map<String, Object> immutable = Map.copyOf(result);
        DdAssuranceContractValidator.validateFailClosedResult(dd, operation, immutable);
        return immutable;
    }
}
