# ONSure Machine Contract Field-by-Field Specification

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 공통 Envelope
모든 v2 Assurance contract는 최소 다음 공통 필드를 사용한다.
- `contract_type`: exact enum
- `schema_version`: semantic version
- `object_id`: globally unique immutable id
- `organization_id`, `tenant_id`
- `subject_id`, `subject_digest`
- `created_at`, `created_by_principal_id`
- `policy_epoch`, `authority_epoch`
- `canonicalization_profile_id`
- `content_sha256`
- `signature` 또는 `signature_ref` (권위 object일 경우)

nullable 필드는 의미를 명시하며 missing과 UNKNOWN을 혼동하지 않는다.

## 2. DeploymentRuntimeCurrentness
Required:
- target_manifest_digest
- build_artifact_digest
- deployment_target_id
- deployment_revision_id
- expected_artifact_digest
- observed_artifact_digest
- runtime_population_digest
- config_digest
- dependency_runtime_digest
- observation_epoch
- evaluated_at
- currentness_state: CURRENT|STALE|REASSESSMENT_REQUIRED|INVALIDATED|REVOKED|UNKNOWN
- reasons[]

Conditional:
- CURRENT이면 expected_artifact_digest == observed_artifact_digest
- CURRENT이면 runtime_population_complete=true
- REVOKED이면 revocation_receipt_digest 필수
- UNKNOWN이면 positive_final_eligible=false

## 3. AssuranceSubjectGraph
Required:
- graph_id, graph_generation
- product_subject_id
- subject_population_digest
- edge_population_digest
- graph_head_digest
- nodes[] exact IDs/digests
- edges[] exact IDs/digests

Edge fields:
- source_subject_id/digest
- target_subject_id/digest
- dependency_type
- propagation_class HARD|SOFT|CONDITIONAL|INFORMATIONAL
- affected_claim_ids[]
- condition_ref nullable

Invariant:
- HARD edge를 SOFT로 바꾸는 변경은 policy weakening event
- DERIVED_FROM/SUPERSEDES cycle 금지
- cross-tenant edge 금지

## 4. AssuranceCompositionSnapshot
Required:
- composition_id
- product_subject_digest
- subject_population_digest
- edge_population_digest
- graph_head_digest
- requirement_epoch
- policy_epoch
- composition_rule_version
- input_result_digests[]
- decision
- assurance_strength
- currentness_state
- qualification_state
- independence_state
- uncertainty_state
- ceiling_reasons[]
- generated_at

Invariant:
- Critical HARD child FAIL/INVALIDATED/REVOKED이면 parent PASS 금지
- child UNKNOWN/HOLD/NOT_RUN이면 positive parent decision 제한
- parent strength는 required child minimum strength를 넘지 못함
- N/A child는 applicability proof digest 필수

## 5. EvidenceGraphSnapshot
Required:
- evidence_graph_id
- graph_generation
- graph_head_digest
- node_population_digest
- edge_population_digest
- canonical_order_profile
- nodes[], edges[]
- created_at

Node:
- node_id/type/content_digest/origin_class/tenant_id
Edge:
- edge_id/type/source_digest/target_digest/rule_id/evidence_digest

Invariant:
- dangling edge 금지
- PRIMARY/DERIVED/AGGREGATED origin 보존
- SUPERSEDES/INVALIDATES/REVOKES는 이전 bytes 삭제가 아니라 새 validity generation 생성

## 6. AssuranceCertificate
Required:
- certificate_id/version
- subject_id/digest
- product_version
- target_manifest_digest
- requirement_epoch
- composition_snapshot_digest
- final_lock_digest
- assurance_tier
- decision
- currentness_state_at_issue
- independent_verification_summary_digest
- limitation_summary[]
- exclusion_summary[]
- issued_at/not_before
- revalidation_due_at 또는 expires_at
- verifier_identity_ref
- revocation_reference
- issuer_key_id/signature

Invariant:
- machine certificate와 PDF/HTML representation digest 분리
- signature valid != current assurance
- unresolved P0 scope blocker가 있으면 최고 tier 제한

## 7. AuthorityGrant
Required:
- grant_id
- principal_id
- organization_id/tenant_id
- allowed_operation_patterns[]
- resource_scope
- purpose_scope[]
- valid_from/valid_until
- delegation_depth
- parent_grant_id nullable
- issuer_principal_id
- approval_chain[]
- state ACTIVE|SUSPENDED|REVOKED|EXPIRED

Invariant:
- delegated scope는 parent subset
- child expiry <= parent expiry
- same principal multiple key를 multiple approver로 계산 금지
- effect_at가 validity window 밖이면 authority false

## 8. DistributedWorkUnit
Required:
- work_unit_id
- parent_run_id
- target/scope/requirement/policy epochs
- input_population_digest
- partition_key
- operation_name/version
- attempt_number
- lease_owner/lease_expires_at
- expected_output_contract
- idempotency_key
- state

Invariant:
- stale lease result authoritative commit 금지
- duplicate logical effect는 one committed receipt만 허용
- partition closure 없으면 aggregate PASS 금지

## 9. DistributedAggregationReceipt
Required:
- aggregate_id
- exact_work_unit_ids[]
- exact_result_digests[]
- denominator_digest
- missing_partitions[]
- duplicate_attempts[]
- canonical_sort_profile
- aggregate_digest
- decision

Invariant:
- scheduling order가 aggregate digest를 바꾸지 않음
- missing partition이면 complete=true 금지

## 10. AIBehaviorPopulation
Required:
- population_id
- target_runtime_identity_digest
- exact_scenario_population_digest
- sample_size
- observed_failures/critical_failures
- seed_profile_digest
- sampling_config_digest
- excluded_run_ids[] + reasons
- statistical_method/version
- confidence_level
- interval_or_bound
- generated_at

Invariant:
- excluded sample은 precommitted rule 근거 필요
- zero observed failure != zero failure probability
- runtime identity drift 시 previous population STALE

## 11. ONSureReleaseQualification
Required:
- onsure_release_digest
- validator_set_digest
- oracle_set_digest
- adapter_set_digest
- fixture_set_digest
- hidden_benchmark_generation
- build_provenance_digest
- sbom_digest
- tcb_manifest_digest
- archetype_qualification_map[]
- independent_verifier_receipts[]
- known_limitations[]
- valid_from/valid_until
- requalification_triggers[]
- state

Invariant:
- self-validation receipts만으로 QUALIFIED 금지
- target archetype별 scope 명시
- critical blind spot MissedFinding 발생 시 affected qualification STALE/REASSESSMENT_REQUIRED

## 12. RecoveryQualificationReceipt
Required:
- recovery_event_id
- restored_store/component ids
- pre_failure_generation
- restored_generation
- backup_manifest_digest
- restored_ledger_head_digest
- key_registry_snapshot_digest
- integrity_checks[]
- missing_or_unverifiable_objects[]
- qualification_decision
- assurance_ceiling

Invariant:
- missing authoritative evidence/ledger/key state가 있으면 prior Final 자동복원 금지

## 13. 공통 Negative Fixture 의무
각 contract는 최소:
1. valid positive 1개
2. cross-field semantic invalid 2개
3. cross-contract invalid 1개
4. stale/revoked/unknown edge case 1개
를 가져야 한다.
