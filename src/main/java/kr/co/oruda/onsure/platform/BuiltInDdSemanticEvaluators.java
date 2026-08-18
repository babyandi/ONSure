package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Concrete DD-001..040 evaluators. Positive decisions are computed from normalized evidence facts. */
public final class BuiltInDdSemanticEvaluators {
    public static final String VERSION = "builtin-dd-evaluators-v2";

    @FunctionalInterface private interface Rule { boolean test(Map<String, JsonNode> facts); }
    private record RuleSpec(String dd, List<String> requiredFacts, String description, Rule rule,
                            Map<String,Object> positiveFixture, Map<String,Object> negativeFixture) {}

    private static final List<RuleSpec> RULES = List.of(
      rule("DD-001","mandatory_dimensions,observed_dimensions","mandatory dimensions are observable",
        m("mandatory_dimensions",l("a","b"),"observed_dimensions",l("a","b","c")),m("mandatory_dimensions",l("a","b"),"observed_dimensions",l("a")),f->set(f,"observed_dimensions").containsAll(set(f,"mandatory_dimensions"))),
      rule("DD-002","qualified_current_oracle_count,unresolved_oracle_conflict_count","exactly one qualified current oracle and no conflict",
        m("qualified_current_oracle_count",1,"unresolved_oracle_conflict_count",0),m("qualified_current_oracle_count",2,"unresolved_oracle_conflict_count",1),f->i(f,"qualified_current_oracle_count")==1&&i(f,"unresolved_oracle_conflict_count")==0),
      rule("DD-003","independence_score,required_independence_score,critical_shared_control_count","independence floor without critical shared control",
        m("independence_score",100,"required_independence_score",80,"critical_shared_control_count",0),m("independence_score",60,"required_independence_score",80,"critical_shared_control_count",1),f->d(f,"independence_score")>=d(f,"required_independence_score")&&i(f,"critical_shared_control_count")==0),
      rule("DD-004","provider_binding_current,replacement_equivalence_qualified,material_opaque_drift_count","current binding or qualified equivalent and no opaque drift",
        m("provider_binding_current",true,"replacement_equivalence_qualified",false,"material_opaque_drift_count",0),m("provider_binding_current",false,"replacement_equivalence_qualified",false,"material_opaque_drift_count",1),f->(b(f,"provider_binding_current")||b(f,"replacement_equivalence_qualified"))&&i(f,"material_opaque_drift_count")==0),
      rule("DD-005","expected_population_count,accounted_population_count,parse_failure_count,unsupported_required_count","exact required population accounting",
        m("expected_population_count",10,"accounted_population_count",10,"parse_failure_count",0,"unsupported_required_count",0),m("expected_population_count",10,"accounted_population_count",9,"parse_failure_count",1,"unsupported_required_count",0),f->i(f,"expected_population_count")==i(f,"accounted_population_count")&&i(f,"parse_failure_count")==0&&i(f,"unsupported_required_count")==0),
      rule("DD-006","critical_tcb_component_count,healthy_current_qualified_component_count","all critical TCB components healthy/current/qualified",
        m("critical_tcb_component_count",5,"healthy_current_qualified_component_count",5),m("critical_tcb_component_count",5,"healthy_current_qualified_component_count",4),f->i(f,"critical_tcb_component_count")==i(f,"healthy_current_qualified_component_count")),
      rule("DD-007","trust_key_compromised,trust_key_revoked,unrequalified_affected_receipt_count","trusted epoch uncompromised and affected receipts requalified",
        m("trust_key_compromised",false,"trust_key_revoked",false,"unrequalified_affected_receipt_count",0),m("trust_key_compromised",true,"trust_key_revoked",false,"unrequalified_affected_receipt_count",1),f->!b(f,"trust_key_compromised")&&!b(f,"trust_key_revoked")&&i(f,"unrequalified_affected_receipt_count")==0),
      rule("DD-008","required_work_count,executed_work_count,hidden_scope_reduction_count","denominator completed without hidden reduction",
        m("required_work_count",100,"executed_work_count",100,"hidden_scope_reduction_count",0),m("required_work_count",100,"executed_work_count",90,"hidden_scope_reduction_count",10),f->i(f,"required_work_count")==i(f,"executed_work_count")&&i(f,"hidden_scope_reduction_count")==0),
      rule("DD-009","waiver_expired,waiver_invalidated,scope_match,finding_preserved","waiver current/in-scope and finding preserved",
        m("waiver_expired",false,"waiver_invalidated",false,"scope_match",true,"finding_preserved",true),m("waiver_expired",true,"waiver_invalidated",false,"scope_match",true,"finding_preserved",true),f->!b(f,"waiver_expired")&&!b(f,"waiver_invalidated")&&b(f,"scope_match")&&b(f,"finding_preserved")),
      rule("DD-010","impacted_claim_count,reassessed_claim_count,unresolved_systemic_dependency_count","all impacted claims reassessed",
        m("impacted_claim_count",4,"reassessed_claim_count",4,"unresolved_systemic_dependency_count",0),m("impacted_claim_count",4,"reassessed_claim_count",3,"unresolved_systemic_dependency_count",1),f->i(f,"impacted_claim_count")==i(f,"reassessed_claim_count")&&i(f,"unresolved_systemic_dependency_count")==0),
      rule("DD-011","rights_valid,contaminated,withdrawn,unresolved_derived_impact_count","eligible corpus contribution and descendant impact resolved",
        m("rights_valid",true,"contaminated",false,"withdrawn",false,"unresolved_derived_impact_count",0),m("rights_valid",false,"contaminated",true,"withdrawn",false,"unresolved_derived_impact_count",1),f->b(f,"rights_valid")&&!b(f,"contaminated")&&!b(f,"withdrawn")&&i(f,"unresolved_derived_impact_count")==0),
      rule("DD-012","reviewer_qualification_current,conflict_count,risk_class_authorized","qualified conflict-free reviewer",
        m("reviewer_qualification_current",true,"conflict_count",0,"risk_class_authorized",true),m("reviewer_qualification_current",false,"conflict_count",1,"risk_class_authorized",false),f->b(f,"reviewer_qualification_current")&&i(f,"conflict_count")==0&&b(f,"risk_class_authorized")),
      rule("DD-013","rights_basis_current,purpose_scope_match,jurisdiction_scope_match","rights basis covers purpose/jurisdiction",
        m("rights_basis_current",true,"purpose_scope_match",true,"jurisdiction_scope_match",true),m("rights_basis_current",false,"purpose_scope_match",false,"jurisdiction_scope_match",true),f->b(f,"rights_basis_current")&&b(f,"purpose_scope_match")&&b(f,"jurisdiction_scope_match")),
      rule("DD-014","authorization_current,target_match,operation_in_scope,time_window_active","effectful testing explicitly authorized",
        m("authorization_current",true,"target_match",true,"operation_in_scope",true,"time_window_active",true),m("authorization_current",false,"target_match",true,"operation_in_scope",true,"time_window_active",false),f->b(f,"authorization_current")&&b(f,"target_match")&&b(f,"operation_in_scope")&&b(f,"time_window_active")),
      rule("DD-015","unresolved_grant_count,active_token_count,active_job_count,unresolved_retention_obligation_count","offboarding obligations closed",
        m("unresolved_grant_count",0,"active_token_count",0,"active_job_count",0,"unresolved_retention_obligation_count",0),m("unresolved_grant_count",1,"active_token_count",1,"active_job_count",0,"unresolved_retention_obligation_count",1),f->i(f,"unresolved_grant_count")==0&&i(f,"active_token_count")==0&&i(f,"active_job_count")==0&&i(f,"unresolved_retention_obligation_count")==0),
      rule("DD-016","semantic_diff_count,required_limitation_missing_count,accessibility_required_gap_count","localized claim preserves semantics/accessibility",
        m("semantic_diff_count",0,"required_limitation_missing_count",0,"accessibility_required_gap_count",0),m("semantic_diff_count",1,"required_limitation_missing_count",1,"accessibility_required_gap_count",1),f->i(f,"semantic_diff_count")==0&&i(f,"required_limitation_missing_count")==0&&i(f,"accessibility_required_gap_count")==0),
      rule("DD-017","ambiguous_delivery_count,duplicate_effect_count,dead_letter_unreconciled_count","external effect delivery reconciled",
        m("ambiguous_delivery_count",0,"duplicate_effect_count",0,"dead_letter_unreconciled_count",0),m("ambiguous_delivery_count",1,"duplicate_effect_count",1,"dead_letter_unreconciled_count",1),f->i(f,"ambiguous_delivery_count")==0&&i(f,"duplicate_effect_count")==0&&i(f,"dead_letter_unreconciled_count")==0),
      rule("DD-018","possible_committed_effect_replay_count,checkpoint_consistent,dedupe_ready","resume is replay-safe",
        m("possible_committed_effect_replay_count",0,"checkpoint_consistent",true,"dedupe_ready",true),m("possible_committed_effect_replay_count",1,"checkpoint_consistent",false,"dedupe_ready",false),f->i(f,"possible_committed_effect_replay_count")==0&&b(f,"checkpoint_consistent")&&b(f,"dedupe_ready")),
      rule("DD-019","competing_head_count,reconciliation_complete,all_heads_preserved","evidence heads reconciled and preserved",
        m("competing_head_count",2,"reconciliation_complete",true,"all_heads_preserved",true),m("competing_head_count",2,"reconciliation_complete",false,"all_heads_preserved",true),f->i(f,"competing_head_count")<=1||(b(f,"reconciliation_complete")&&b(f,"all_heads_preserved"))),
      rule("DD-020","data_export_complete,keys_closed,evidence_continuity,replacement_qualified","vendor exit continuity complete",
        m("data_export_complete",true,"keys_closed",true,"evidence_continuity",true,"replacement_qualified",true),m("data_export_complete",false,"keys_closed",false,"evidence_continuity",false,"replacement_qualified",false),f->b(f,"data_export_complete")&&b(f,"keys_closed")&&b(f,"evidence_continuity")&&b(f,"replacement_qualified")),
      rule("DD-021","removed_required_verification_field_count,commitment_verified,omission_boundary_disclosed","redacted evidence remains verifiable",
        m("removed_required_verification_field_count",0,"commitment_verified",true,"omission_boundary_disclosed",true),m("removed_required_verification_field_count",1,"commitment_verified",false,"omission_boundary_disclosed",false),f->i(f,"removed_required_verification_field_count")==0&&b(f,"commitment_verified")&&b(f,"omission_boundary_disclosed")),
      rule("DD-022","ttl_exceeded,scope_violation,retrospective_review_complete,unreconciled_effect_count","break-glass bounded/reconciled/reviewed",
        m("ttl_exceeded",false,"scope_violation",false,"retrospective_review_complete",true,"unreconciled_effect_count",0),m("ttl_exceeded",true,"scope_violation",true,"retrospective_review_complete",false,"unreconciled_effect_count",1),f->!b(f,"ttl_exceeded")&&!b(f,"scope_violation")&&b(f,"retrospective_review_complete")&&i(f,"unreconciled_effect_count")==0),
      rule("DD-023","policy_current,control_pack_current,superseded,applicable","current applicable policy/control pack",
        m("policy_current",true,"control_pack_current",true,"superseded",false,"applicable",true),m("policy_current",false,"control_pack_current",false,"superseded",true,"applicable",true),f->b(f,"policy_current")&&b(f,"control_pack_current")&&!b(f,"superseded")&&b(f,"applicable")),
      rule("DD-024","benchmark_exposure_count,training_tuning_overlap_count","benchmark uncontaminated",
        m("benchmark_exposure_count",0,"training_tuning_overlap_count",0),m("benchmark_exposure_count",1,"training_tuning_overlap_count",1),f->i(f,"benchmark_exposure_count")==0&&i(f,"training_tuning_overlap_count")==0),
      rule("DD-025","applicable_regulation_set_count,wrong_version_count,unresolved_transition_count","one applicable regulation set and transitions resolved",
        m("applicable_regulation_set_count",1,"wrong_version_count",0,"unresolved_transition_count",0),m("applicable_regulation_set_count",2,"wrong_version_count",1,"unresolved_transition_count",1),f->i(f,"applicable_regulation_set_count")==1&&i(f,"wrong_version_count")==0&&i(f,"unresolved_transition_count")==0),
      rule("DD-026","material_context_delta_count,recomputation_performed,justified_unchanged","material context delta triggers recomputation/justification",
        m("material_context_delta_count",1,"recomputation_performed",true,"justified_unchanged",false),m("material_context_delta_count",1,"recomputation_performed",false,"justified_unchanged",false),f->i(f,"material_context_delta_count")==0||b(f,"recomputation_performed")||b(f,"justified_unchanged")),
      rule("DD-027","timezone_resolved,calendar_version_current,cutoff_resolved,ambiguous_time_count","deterministic business-time binding",
        m("timezone_resolved",true,"calendar_version_current",true,"cutoff_resolved",true,"ambiguous_time_count",0),m("timezone_resolved",false,"calendar_version_current",false,"cutoff_resolved",false,"ambiguous_time_count",1),f->b(f,"timezone_resolved")&&b(f,"calendar_version_current")&&b(f,"cutoff_resolved")&&i(f,"ambiguous_time_count")==0),
      rule("DD-028","unobservable_required_dimension_count,claim_excluded_unobservable_count","claim ceiling respects unobservable dimensions",
        m("unobservable_required_dimension_count",2,"claim_excluded_unobservable_count",2),m("unobservable_required_dimension_count",2,"claim_excluded_unobservable_count",0),f->i(f,"unobservable_required_dimension_count")==0||i(f,"unobservable_required_dimension_count")==i(f,"claim_excluded_unobservable_count")),
      rule("DD-029","authorization_current,owner_chain_valid,operation_in_scope","owner-derived third-party testing authority",
        m("authorization_current",true,"owner_chain_valid",true,"operation_in_scope",true),m("authorization_current",false,"owner_chain_valid",false,"operation_in_scope",true),f->b(f,"authorization_current")&&b(f,"owner_chain_valid")&&b(f,"operation_in_scope")),
      rule("DD-030","stale_principal_count,stale_token_count,authority_rebinding_complete,ownership_lineage_resolved","organization transfer authority rebound",
        m("stale_principal_count",0,"stale_token_count",0,"authority_rebinding_complete",true,"ownership_lineage_resolved",true),m("stale_principal_count",1,"stale_token_count",1,"authority_rebinding_complete",false,"ownership_lineage_resolved",false),f->i(f,"stale_principal_count")==0&&i(f,"stale_token_count")==0&&b(f,"authority_rebinding_complete")&&b(f,"ownership_lineage_resolved")),
      rule("DD-031","target_region_qualified,key_custody_qualified,subprocessor_profile_qualified","residency/sovereignty target qualified",
        m("target_region_qualified",true,"key_custody_qualified",true,"subprocessor_profile_qualified",true),m("target_region_qualified",false,"key_custody_qualified",false,"subprocessor_profile_qualified",false),f->b(f,"target_region_qualified")&&b(f,"key_custody_qualified")&&b(f,"subprocessor_profile_qualified")),
      rule("DD-032","replacement_semantic_contract_pass,requalification_complete,alias_only_assumption","dependency replacement qualified",
        m("replacement_semantic_contract_pass",true,"requalification_complete",true,"alias_only_assumption",false),m("replacement_semantic_contract_pass",false,"requalification_complete",false,"alias_only_assumption",true),f->b(f,"replacement_semantic_contract_pass")&&b(f,"requalification_complete")&&!b(f,"alias_only_assumption")),
      rule("DD-033","current_crypto_profile_qualified,deprecated_presented_as_current,historical_anchor_verifiable,migration_receipt_valid","crypto current and historical evidence verifiable",
        m("current_crypto_profile_qualified",true,"deprecated_presented_as_current",false,"historical_anchor_verifiable",true,"migration_receipt_valid",true),m("current_crypto_profile_qualified",false,"deprecated_presented_as_current",true,"historical_anchor_verifiable",false,"migration_receipt_valid",false),f->b(f,"current_crypto_profile_qualified")&&!b(f,"deprecated_presented_as_current")&&b(f,"historical_anchor_verifiable")&&b(f,"migration_receipt_valid")),
      rule("DD-034","recipient_authorized,purpose_authorized,custody_receipts_complete,hidden_limitation_count,original_commitment_linked","authorized disclosure with custody and visible limitations",
        m("recipient_authorized",true,"purpose_authorized",true,"custody_receipts_complete",true,"hidden_limitation_count",0,"original_commitment_linked",true),m("recipient_authorized",false,"purpose_authorized",false,"custody_receipts_complete",false,"hidden_limitation_count",1,"original_commitment_linked",false),f->b(f,"recipient_authorized")&&b(f,"purpose_authorized")&&b(f,"custody_receipts_complete")&&i(f,"hidden_limitation_count")==0&&b(f,"original_commitment_linked")),
      rule("DD-035","material_privileged_effect_count,reconciled_privileged_effect_count,affected_claim_count,requalified_claim_count","privileged mutation effects reconciled and claims requalified",
        m("material_privileged_effect_count",2,"reconciled_privileged_effect_count",2,"affected_claim_count",3,"requalified_claim_count",3),m("material_privileged_effect_count",2,"reconciled_privileged_effect_count",1,"affected_claim_count",3,"requalified_claim_count",2),f->i(f,"material_privileged_effect_count")==i(f,"reconciled_privileged_effect_count")&&i(f,"affected_claim_count")==i(f,"requalified_claim_count")),
      rule("DD-036","independence_score,required_independence_score,shared_verdict_draft,shared_control_critical,shared_knowledge_critical","independence beyond account separation",
        m("independence_score",100,"required_independence_score",80,"shared_verdict_draft",false,"shared_control_critical",false,"shared_knowledge_critical",false),m("independence_score",60,"required_independence_score",80,"shared_verdict_draft",true,"shared_control_critical",true,"shared_knowledge_critical",true),f->d(f,"independence_score")>=d(f,"required_independence_score")&&!b(f,"shared_verdict_draft")&&!b(f,"shared_control_critical")&&!b(f,"shared_knowledge_critical")),
      rule("DD-037","original_digest_verified,current_readback_success,unsupported_format_count,migration_chain_valid","long-horizon evidence remains readable/verifiable",
        m("original_digest_verified",true,"current_readback_success",true,"unsupported_format_count",0,"migration_chain_valid",true),m("original_digest_verified",false,"current_readback_success",false,"unsupported_format_count",1,"migration_chain_valid",false),f->b(f,"original_digest_verified")&&b(f,"current_readback_success")&&i(f,"unsupported_format_count")==0&&b(f,"migration_chain_valid")),
      rule("DD-038","environment_safe_simulation,explicit_high_risk_authority,unreconciled_real_effect_count,abort_control_ready","financial effect boundary safe/authorized/reconcilable",
        m("environment_safe_simulation",true,"explicit_high_risk_authority",false,"unreconciled_real_effect_count",0,"abort_control_ready",true),m("environment_safe_simulation",false,"explicit_high_risk_authority",false,"unreconciled_real_effect_count",1,"abort_control_ready",false),f->(b(f,"environment_safe_simulation")||b(f,"explicit_high_risk_authority"))&&i(f,"unreconciled_real_effect_count")==0&&b(f,"abort_control_ready")),
      rule("DD-039","observation_coverage_ratio,required_observation_coverage_ratio,collector_health_ratio,required_collector_health_ratio,material_gap_count","observation supports bounded operating-effectiveness claim",
        m("observation_coverage_ratio",1.0,"required_observation_coverage_ratio",0.95,"collector_health_ratio",1.0,"required_collector_health_ratio",0.99,"material_gap_count",0),m("observation_coverage_ratio",0.5,"required_observation_coverage_ratio",0.95,"collector_health_ratio",0.5,"required_collector_health_ratio",0.99,"material_gap_count",1),f->d(f,"observation_coverage_ratio")>=d(f,"required_observation_coverage_ratio")&&d(f,"collector_health_ratio")>=d(f,"required_collector_health_ratio")&&i(f,"material_gap_count")==0),
      rule("DD-040","wave_count,new_p0_total,contaminated_wave_count,independent_wave_lineage_count,same_tree,same_authority_digest","two uncontaminated independent zero-new-P0 waves on same baseline",
        m("wave_count",2,"new_p0_total",0,"contaminated_wave_count",0,"independent_wave_lineage_count",2,"same_tree",true,"same_authority_digest",true),m("wave_count",2,"new_p0_total",1,"contaminated_wave_count",1,"independent_wave_lineage_count",1,"same_tree",false,"same_authority_digest",false),f->i(f,"wave_count")>=2&&i(f,"new_p0_total")==0&&i(f,"contaminated_wave_count")==0&&i(f,"independent_wave_lineage_count")>=2&&b(f,"same_tree")&&b(f,"same_authority_digest"))
    );

    private BuiltInDdSemanticEvaluators() {}
    public static List<DdSemanticEvaluator> all(){ return RULES.stream().map(RuleEvaluator::new).map(DdSemanticEvaluator.class::cast).toList(); }
    public static int count(){ return RULES.size(); }
    static Map<String,Object> syntheticFacts(String dd, boolean positive){
        RuleSpec s=RULES.stream().filter(x->x.dd().equals(dd)).findFirst().orElseThrow();
        return Map.copyOf(positive?s.positiveFixture():s.negativeFixture());
    }
    private static RuleSpec rule(String dd,String required,String description,Map<String,Object> positive,Map<String,Object> negative,Rule rule){
        return new RuleSpec(dd,List.of(required.split(",")),description,rule,positive,negative);
    }
    private static Map<String,Object> m(Object... kv){
        Map<String,Object> x=new LinkedHashMap<>(); for(int n=0;n<kv.length;n+=2)x.put((String)kv[n],kv[n+1]); return Map.copyOf(x);
    }
    private static List<String> l(String...v){ return List.of(v); }
    private static boolean b(Map<String,JsonNode> f,String k){ return f.get(k).asBoolean(); }
    private static long i(Map<String,JsonNode> f,String k){ return f.get(k).asLong(); }
    private static double d(Map<String,JsonNode> f,String k){ return f.get(k).asDouble(); }
    private static Set<String> set(Map<String,JsonNode> f,String k){ Set<String>x=new LinkedHashSet<>(); f.get(k).forEach(v->x.add(v.asText())); return x; }

    private static final class RuleEvaluator implements DdSemanticEvaluator {
        private final RuleSpec spec; private RuleEvaluator(RuleSpec spec){this.spec=spec;}
        @Override public String ddId(){return spec.dd();}
        @Override public Evaluation evaluate(JsonNode request,EvaluationContext context){
            List<String> refs=evidenceRefs(request); if(refs.isEmpty())return hold("DD_EVIDENCE_REQUIRED",List.of(),Map.of("rule",spec.description()));
            Map<String,JsonNode> facts=new LinkedHashMap<>(); List<String> consumed=new ArrayList<>();
            for(String ref:refs){
                var resolved=context.evidenceResolver().resolve(ref); if(resolved.isEmpty())return hold("DD_EVIDENCE_UNRESOLVED:"+ref,consumed,Map.of("rule",spec.description()));
                var e=resolved.get(); if(!e.integrityVerified())return hold("DD_EVIDENCE_INTEGRITY_UNVERIFIED:"+ref,consumed,Map.of("rule",spec.description()));
                if(!e.current())return hold("DD_EVIDENCE_STALE:"+ref,consumed,Map.of("rule",spec.description()));
                JsonNode ef=e.document().path("facts"); if(!ef.isObject())return hold("DD_EVIDENCE_FACTS_REQUIRED:"+ref,consumed,Map.of("rule",spec.description()));
                Iterator<Map.Entry<String,JsonNode>> it=ef.fields(); while(it.hasNext()){var field=it.next(); JsonNode prior=facts.putIfAbsent(field.getKey(),field.getValue()); if(prior!=null&&!prior.equals(field.getValue()))return hold("DD_EVIDENCE_FACT_CONFLICT:"+field.getKey(),consumed,Map.of("rule",spec.description()));}
                consumed.add(ref);
            }
            List<String> missing=spec.requiredFacts().stream().filter(k->!present(facts.get(k))).toList();
            if(!missing.isEmpty())return hold("DD_REQUIRED_FACTS_MISSING",consumed,Map.of("missing_facts",missing,"rule",spec.description()));
            boolean pass;
            try{pass=spec.rule().test(Map.copyOf(facts));}catch(RuntimeException ex){return hold("DD_SEMANTIC_RULE_EVALUATION_FAILED",consumed,Map.of("rule",spec.description(),"failure_class",ex.getClass().getSimpleName()));}
            if(!pass)return hold("DD_SEMANTIC_RULE_NOT_SATISFIED",consumed,Map.of("rule",spec.description()));
            return new Evaluation("PASS_NONFINAL",List.of(),List.copyOf(new LinkedHashSet<>(consumed)),true,false,Map.of("evaluator_contract",VERSION,"rule",spec.description(),"required_fact_count",spec.requiredFacts().size(),"trusted_evidence_count",consumed.size(),"policy_ref",context.policyRef(),"authority_ref",context.authorityRef()));
        }
        private Evaluation hold(String reason,List<String> refs,Map<String,Object> details){Map<String,Object>x=new LinkedHashMap<>(details);x.put("evaluator_contract",VERSION);return new Evaluation("HOLD",List.of(reason),List.copyOf(refs),false,false,Map.copyOf(x));}
        private static boolean present(JsonNode v){if(v==null||v.isNull()||v.isMissingNode())return false;if(v.isTextual())return !v.asText().isBlank();if(v.isArray()||v.isObject())return v.size()>0;return true;}
        private static List<String> evidenceRefs(JsonNode request){JsonNode refs=request.path("evidence_refs");if(!refs.isArray())return List.of();List<String>x=new ArrayList<>();refs.forEach(n->{if(n.isTextual()&&!n.asText().isBlank())x.add(n.asText());});return List.copyOf(x);}
    }
}
