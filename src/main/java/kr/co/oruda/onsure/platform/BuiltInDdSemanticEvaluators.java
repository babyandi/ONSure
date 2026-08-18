package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Concrete built-in semantic evaluator implementations for DD-001..040.
 *
 * <p>These implementations are intentionally registered UNQUALIFIED by default. Code existence is
 * not qualification. Positive runtime use requires a separate current independent qualification
 * receipt in {@link DdSemanticEvaluatorRegistry}.</p>
 *
 * <p>All semantic inputs come from integrity-verified/current resolved evidence. Caller supplied
 * context is never used as an oracle.</p>
 */
public final class BuiltInDdSemanticEvaluators {
    public static final String VERSION = "builtin-dd-evaluators-v1";

    private record Spec(String dd, List<String> requiredFacts, String passFact) {}

    private static final List<Spec> SPECS = List.of(
            spec("DD-001", "visibility_profile,mandatory_dimensions,observed_dimensions", "mandatory_dimensions_observable"),
            spec("DD-002", "oracle_candidates,authority_lineage,effective_intervals", "qualified_oracle_unique_current"),
            spec("DD-003", "reviewer_lineage,parser_lineage,provider_lineage,control_lineage", "independence_floor_satisfied"),
            spec("DD-004", "provider_identity,model_identity,version_or_fingerprint,previous_binding", "provider_model_binding_current"),
            spec("DD-005", "expected_population,read_population,unsupported_population,parse_failures", "required_population_exactly_accounted"),
            spec("DD-006", "tcb_components,health_receipts,qualification_epoch", "critical_tcb_healthy_current"),
            spec("DD-007", "trust_epoch,key_status,revocation_status,receipt_bindings", "trust_epoch_current_unaffected"),
            spec("DD-008", "required_work,executed_work,budget_state", "required_denominator_completed"),
            spec("DD-009", "waiver,finding,scope,expiry,invalidation_events", "waiver_current_authorized_in_scope"),
            spec("DD-010", "dependency_graph,affected_dependency,dependent_claims", "dependent_claims_reassessed"),
            spec("DD-011", "contribution,rights_status,contamination_status,derived_lineage", "contribution_eligible_impact_resolved"),
            spec("DD-012", "reviewer_qualification,qualification_expiry,conflicts,assignment", "reviewer_qualified_conflict_free"),
            spec("DD-013", "data_subject_or_asset,purpose,rights_basis,scope", "rights_basis_covers_purpose"),
            spec("DD-014", "target_identity,test_type,authorization,time_window,scope", "testing_authority_current_in_scope"),
            spec("DD-015", "grants,tokens,jobs,retention_obligations,export_delete_receipts", "offboarding_obligations_closed"),
            spec("DD-016", "canonical_claim,localized_claim,limitations,accessibility_state", "localized_claim_semantically_equivalent_accessible"),
            spec("DD-017", "effect_identity,attempts,idempotency_key,delivery_receipts,dead_letter_state", "external_effect_reconciled_exactly_once"),
            spec("DD-018", "checkpoint,committed_effects,resume_plan,dedupe_state", "resume_replay_safe"),
            spec("DD-019", "evidence_heads,causal_lineage,reconciliation_decision", "evidence_heads_reconciled_preserved"),
            spec("DD-020", "exit_plan,data_export,keys,evidence_continuity,dependency_replacement", "vendor_exit_continuity_complete"),
            spec("DD-021", "original_commitment,redaction_manifest,verification_requirements", "redacted_evidence_verifiable"),
            spec("DD-022", "break_glass_receipt,scope,ttl,effects,independent_review", "break_glass_bounded_reconciled_reviewed"),
            spec("DD-023", "policy_version,control_pack,effective_interval,supersession", "policy_control_pack_current_applicable"),
            spec("DD-024", "benchmark_identity,training_tuning_population,exposure_lineage", "benchmark_independent_uncontaminated"),
            spec("DD-025", "jurisdiction,entity_type,effective_date,regulatory_versions,transition_rules", "regulatory_requirement_set_applicable"),
            spec("DD-026", "previous_context,current_context,materiality_rules,denominator", "applicability_denominator_recomputed_or_justified"),
            spec("DD-027", "timezone,business_calendar,cutoff,event_time", "business_time_binding_deterministic"),
            spec("DD-028", "access_capabilities,required_dimensions,unobservable_dimensions", "blackbox_claim_scope_within_observable_boundary"),
            spec("DD-029", "target_owner,testing_authority,operation,scope,expiry", "owner_derived_testing_authority_current"),
            spec("DD-030", "organization_lineage,tenant_transfer,principals,tokens,authority_rebinding", "organization_authority_rebound_requalified"),
            spec("DD-031", "source_region,target_region,keys,subprocessors,qualification_profile", "target_residency_profile_qualified"),
            spec("DD-032", "old_dependency,new_dependency,semantic_contract,requalification", "replacement_semantically_equivalent_qualified"),
            spec("DD-033", "crypto_profile,deprecation_policy,historical_anchor,migration_receipt", "crypto_current_and_history_verifiable"),
            spec("DD-034", "recipient,purpose,authority,disclosure_manifest,custody_receipts", "disclosure_authorized_limitations_and_custody_preserved"),
            spec("DD-035", "privileged_effect,affected_claims,clean_epoch,reconciliation", "privileged_effect_reconciled_claims_requalified"),
            spec("DD-036", "reviewers,accounts,control_graph,knowledge_lineage,verdict_lineage", "common_control_independence_floor_met"),
            spec("DD-037", "original_bytes_digest,format_generation,parser_versions,migration_receipts,readback", "historical_evidence_currently_readable_verifiable"),
            spec("DD-038", "test_profile,environment,effect_ceiling,authorization,abort_reconcile_controls", "financial_effect_test_boundary_safe_authorized"),
            spec("DD-039", "observation_window,collector_health,sampling,coverage_gaps,events", "observation_coverage_supports_bounded_effectiveness"),
            spec("DD-040", "frozen_tree,authority_digest,wave_a,wave_b,independence_lineage,new_p0_counts", "two_independent_uncontaminated_zero_new_p0_waves"));

    private BuiltInDdSemanticEvaluators() {}

    public static List<DdSemanticEvaluator> all() {
        return SPECS.stream().map(RuleEvaluator::new).map(DdSemanticEvaluator.class::cast).toList();
    }

    public static int count() { return SPECS.size(); }

    private static Spec spec(String dd, String required, String passFact) {
        return new Spec(dd, List.of(required.split(",")), passFact);
    }

    private static final class RuleEvaluator implements DdSemanticEvaluator {
        private final Spec spec;

        private RuleEvaluator(Spec spec) { this.spec = spec; }

        @Override
        public String ddId() { return spec.dd(); }

        @Override
        public Evaluation evaluate(JsonNode request, EvaluationContext context) {
            List<String> refs = evidenceRefs(request);
            if (refs.isEmpty()) {
                return hold("DD_EVIDENCE_REQUIRED", List.of(), Map.of("pass_fact", spec.passFact()));
            }

            Map<String, JsonNode> facts = new LinkedHashMap<>();
            List<String> consumed = new ArrayList<>();
            for (String ref : refs) {
                var resolved = context.evidenceResolver().resolve(ref);
                if (resolved.isEmpty()) {
                    return hold("DD_EVIDENCE_UNRESOLVED:" + ref, consumed, Map.of("pass_fact", spec.passFact()));
                }
                var evidence = resolved.get();
                if (!evidence.integrityVerified()) {
                    return hold("DD_EVIDENCE_INTEGRITY_UNVERIFIED:" + ref, consumed, Map.of("pass_fact", spec.passFact()));
                }
                if (!evidence.current()) {
                    return hold("DD_EVIDENCE_STALE:" + ref, consumed, Map.of("pass_fact", spec.passFact()));
                }
                JsonNode evidenceFacts = evidence.document().path("facts");
                if (!evidenceFacts.isObject()) {
                    return hold("DD_EVIDENCE_FACTS_REQUIRED:" + ref, consumed, Map.of("pass_fact", spec.passFact()));
                }
                Iterator<Map.Entry<String, JsonNode>> fields = evidenceFacts.fields();
                while (fields.hasNext()) {
                    var field = fields.next();
                    JsonNode prior = facts.putIfAbsent(field.getKey(), field.getValue());
                    if (prior != null && !prior.equals(field.getValue())) {
                        return hold("DD_EVIDENCE_FACT_CONFLICT:" + field.getKey(), consumed,
                                Map.of("pass_fact", spec.passFact()));
                    }
                }
                consumed.add(ref);
            }

            List<String> missing = spec.requiredFacts().stream().filter(key -> !present(facts.get(key))).toList();
            if (!missing.isEmpty()) {
                return hold("DD_REQUIRED_FACTS_MISSING", consumed,
                        Map.of("missing_facts", missing, "pass_fact", spec.passFact()));
            }
            JsonNode pass = facts.get(spec.passFact());
            if (pass == null || !pass.isBoolean()) {
                return hold("DD_POSITIVE_ORACLE_FACT_MISSING", consumed,
                        Map.of("pass_fact", spec.passFact()));
            }
            if (!pass.asBoolean()) {
                return hold("DD_SAFE_FLOOR_NOT_SATISFIED", consumed,
                        Map.of("pass_fact", spec.passFact(), "pass_value", false));
            }

            return new Evaluation(
                    "PASS_NONFINAL",
                    List.of(),
                    List.copyOf(new LinkedHashSet<>(consumed)),
                    true,
                    false,
                    Map.of(
                            "evaluator_contract", VERSION,
                            "pass_fact", spec.passFact(),
                            "required_fact_count", spec.requiredFacts().size(),
                            "trusted_evidence_count", consumed.size(),
                            "policy_ref", context.policyRef(),
                            "authority_ref", context.authorityRef()));
        }

        private Evaluation hold(String reason, List<String> refs, Map<String, Object> details) {
            Map<String, Object> merged = new LinkedHashMap<>(details);
            merged.put("evaluator_contract", VERSION);
            return new Evaluation("HOLD", List.of(reason), List.copyOf(refs), false, false, Map.copyOf(merged));
        }

        private static boolean present(JsonNode value) {
            if (value == null || value.isNull() || value.isMissingNode()) return false;
            if (value.isTextual()) return !value.asText().isBlank();
            if (value.isArray() || value.isObject()) return value.size() > 0;
            return true;
        }

        private static List<String> evidenceRefs(JsonNode request) {
            JsonNode refs = request.path("evidence_refs");
            if (!refs.isArray()) return List.of();
            List<String> values = new ArrayList<>();
            refs.forEach(node -> {
                if (node.isTextual() && !node.asText().isBlank()) values.add(node.asText());
            });
            return List.copyOf(values);
        }
    }
}
