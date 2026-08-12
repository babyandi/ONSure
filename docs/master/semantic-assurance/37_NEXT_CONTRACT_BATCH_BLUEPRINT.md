# ONSure Next Contract Batch Blueprint — Runtime/Product/Certificate/Meta Assurance

Status: `DESIGN_ONLY / CONTRACT_BLUEPRINT / NON_FINAL`
Parents: `29~36`
Purpose: 29~35 상세설계를 개발 가능한 machine contract 후보로 내리기 위한 설계 명세

## 1. Contract Batch 범위
다음 10개 Contract를 다음 개발 Batch의 canonical candidate로 정의한다.

1. `deployment-runtime-currentness.candidate.v2.schema.json`
2. `assurance-subject-graph.candidate.v2.schema.json`
3. `assurance-composition-snapshot.candidate.v2.schema.json`
4. `evidence-graph-snapshot.candidate.v2.schema.json`
5. `assurance-certificate.candidate.v2.schema.json`
6. `authority-grant.candidate.v2.schema.json`
7. `distributed-work-unit.candidate.v2.schema.json`
8. `distributed-aggregation-receipt.candidate.v2.schema.json`
9. `ai-behavior-population.candidate.v2.schema.json`
10. `onsure-release-qualification.candidate.v2.schema.json`

이 Batch는 현재 31개 Schema Candidate를 자동 대체하지 않는다. Active selector 변경 없음.

## 2. deployment-runtime-currentness.candidate.v2
### 목적
Verified artifact, deployment revision, active runtime population, currentness를 하나의 exact snapshot으로 결속한다.

### 필수 필드
- contract
- currentness_snapshot_id
- organization_id
- tenant_id
- target_id
- target_manifest_digest
- final_lock_digest
- deployment_target_id
- deployment_revision_id
- expected_artifact_digest
- observed_deployment_artifact_digest
- runtime_population_digest
- active_instance_count
- matching_instance_count
- mismatching_instance_ids
- config_digest
- dependency_runtime_digest
- ai_runtime_identity_digest nullable
- policy_epoch_digest
- validator_qualification_digest
- authority_epoch_digest
- observer_profile_digest
- evaluated_at
- currentness_state
- reasons
- required_actions
- receipt_digest/signature

### Cross-field invariant
- CURRENT → expected artifact = deployment artifact
- CURRENT → active_instance_count > 0
- CURRENT → matching_instance_count = active_instance_count
- CURRENT → mismatching_instance_ids empty
- CURRENT → required freshness/qualification/authority state current
- REVOKED → signed revocation reference required
- UNKNOWN → positive production-bound certificate prohibited

### Negative fixture 최소
- CURRENT + artifact mismatch
- CURRENT + partial runtime mismatch
- REVOKED without revocation receipt
- CURRENT + expired qualification

## 3. assurance-subject-graph.candidate.v2
### 목적
제품/시스템/서비스/모듈/배포/AI/external dependency의 exact population과 dependency topology를 고정한다.

### Subject
- subject_id
- subject_type
- subject_digest
- target_id nullable
- criticality
- required_for_claim_ids[]

### Edge
- edge_id
- from_subject_id
- to_subject_id
- dependency_type
- propagation_class
- affected_claim_ids[]
- condition_ref nullable
- authority_rule_id

### Graph
- graph_id
- organization_id/tenant_id
- product_subject_id
- subject_population_digest
- edge_population_digest
- graph_version
- generated_at

### Invariant
- duplicate subject/edge ID 금지
- dangling edge 금지
- cross-tenant material relation 금지
- Product subject exact 1
- HARD/SOFT 변경은 authority rule 없이 불가

## 4. assurance-composition-snapshot.candidate.v2
### 목적
Product-level Decision/Strength/Currentness를 exact graph와 child result population에서 재계산 가능하게 고정한다.

### 필수
- composition_id
- subject_graph_digest
- result_population_digest
- requirement_epoch_digest
- policy_digest
- composition_rule_digest
- product_decision
- assurance_strength
- currentness_state
- critical_hard_dependency_count
- unresolved_dependency_count
- unresolved_conflict_ids[]
- excluded_subjects[]
- na_subjects[]
- weakest_required_subjects[]
- ceiling_reasons[]
- generated_at

### Invariant
- PASS → no unresolved Critical HARD negative/unknown state
- CURRENT → all required currentness-compatible
- AL4/AL5 → required independence/qualification references exist
- N/A → applicability proof required
- conflict → supersession or CONFLICT_HOLD

## 5. evidence-graph-snapshot.candidate.v2
### 목적
Claim/Final/Deployment/Certificate의 material lineage와 invalidation을 canonical graph로 고정한다.

### Node
- node_id/type/digest/tenant_id/created_at
- validity_generation

### Edge
- edge_id
- relation_type
- source_node_id
- target_node_id
- rule_id
- evidence_digest
- created_at

### Graph Head
- graph_id
- node_population_digest
- edge_population_digest
- graph_head_digest
- previous_graph_head_digest nullable
- generated_at
- producer/signature

### Invariant
- DERIVED_FROM/SUPERSEDES cycle 금지
- dangling edge 금지
- invalid/revoked node를 current positive parent로 소비 금지
- Final/Certificate는 PRIMARY/raw source path까지 reachable
- graph head canonical ordering 명시

## 6. assurance-certificate.candidate.v2
### 목적
내부 FinalLock과 고객/감사자용 public proof를 분리한다.

### 필수
- certificate_id/version
- issuer
- organization_id
- subject_id/digest
- product_version
- target_manifest_digest
- requirement_epoch_digest
- composition_snapshot_digest
- final_lock_digest
- decision
- assurance_strength
- currentness_at_issue
- independent_verification_refs[]
- limitation_summaries[]
- exclusion_summaries[]
- issued_at/not_before
- expires_at nullable
- revalidation_due_at nullable
- revocation_registry_ref
- verifier_profile_version
- key_id/signature

### Invariant
- positive certificate → composition PASS-compatible
- AL5 → currentness CURRENT + runtime currentness snapshot
- limitation/exclusion material set cannot be empty if source composition has material limits
- issuer key valid at issue time
- Certificate bytes immutable; current revocation state external lookup

## 7. authority-grant.candidate.v2
### 필수
- grant_id
- principal_id
- principal_identity_digest
- organization/tenant
- subject_scope[]
- operation_scope[]
- purpose_scope[]
- valid_from/until
- parent_grant_id nullable
- delegation_depth
- issued_by
- approval_refs[]
- authority_epoch
- revocation_state
- signature

### Invariant
- child scope ⊆ parent scope
- child validity ⊆ parent validity
- delegation_depth monotonic
- revoked parent → child effective authority not current
- four-eyes는 distinct principal identity로 계산
- same principal multi-key는 distinct approver 아님

## 8. distributed-work-unit.candidate.v2
### 필수
- work_unit_id
- parent_run_id
- organization/tenant/target
- scope_epoch_digest
- requirement_epoch_digest
- operation
- input_digest
- partition_id
- partition_population_digest
- attempt_number
- lease_id/owner/acquired/expires
- idempotency_key
- expected_output_contract
- state

### State
READY|LEASED|RUNNING|COMPLETED|FAILED|RETRYABLE|QUARANTINED|CANCELLED|EXPIRED|HOLD

### Invariant
- stale lease cannot commit authoritative output
- same logical effect idempotency key single commitment
- retry attempt preserves predecessor

## 9. distributed-aggregation-receipt.candidate.v2
### 필수
- aggregation_id
- parent_run_id
- expected_partition_population_digest
- observed_partition_population_digest
- expected_count
- completed_unique_count
- duplicate_attempt_count
- missing_partition_ids[]
- result_population_digest
- canonical_ordering_profile_digest
- aggregate_digest
- decision

### Invariant
- PASS → expected population = observed exact population
- PASS → missing empty
- duplicate attempts do not increase completed_unique_count
- same logical results reordered → same aggregate digest

## 10. ai-behavior-population.candidate.v2
### 필수
- population_id
- ai_runtime_identity_digest
- scenario_population_digest
- generator_profile_digest
- precommit_receipt_digest
- sample_size
- sampling_config
- attempt_refs[]
- success_count/failure_count
- critical_failure_count
- empirical_failure_rate
- confidence_method
- confidence_bound
- exclusion_records[]
- behavior_decision

### Invariant
- counts sum to sample_size after valid exclusions
- critical_failure_count > 0 → Critical safety claim PASS prohibited
- exclusion after result visibility requires independent disposition
- sample population digest immutable after precommit

## 11. onsure-release-qualification.candidate.v2
### 필수
- qualification_id
- onsure_release_digest
- validator_set_digest
- oracle_set_digest
- adapter_set_digest
- fixture_set_digest
- benchmark_set_digest
- hidden_corpus_generation
- tcb_manifest_digest
- environment_digest
- independent_execution_refs[]
- archetype_results[]
- known_limitations[]
- issued_at/expires_at
- requalification_triggers[]
- qualification_decision
- authority/signature

### Archetype Result
- archetype_id/version
- state: QUALIFIED|PARTIAL|NOT_PROVEN
- defect_class_coverage
- critical_seeded_escape_count
- evidence_refs
- limitations[]

### Invariant
- QUALIFIED → critical seeded escape 0
- release digest change → old qualification cannot bind new release
- target certificate strength cannot exceed matching ONSure archetype qualification
- self-validation receipts alone cannot satisfy independent qualification

## 12. Canonicalization 공통규칙
모든 population/set/graph digest는 다음을 명시한다.
- UTF-8
- field selection profile version
- object key lexical ordering
- array ordering: semantic ordered vs set-sorted 구분
- path/identifier normalization
- null/absent distinction
- numeric representation
- timezone UTC canonical form

Profile digest를 Receipt에 결속한다.

## 13. Fixture 기준
각 신규 Schema:
- valid 최소 1
- semantic invalid 최소 2
- P0 Contract는 권장 4 이상
- cross-contract fixture 별도

필수 cross-contract fixture:
1. Composition PASS + child Critical HOLD
2. AL5 Certificate + Runtime STALE
3. Authority child wider than parent
4. Aggregation PASS + missing partition
5. AI critical failure >0 + PASS
6. ONSure NOT_PROVEN archetype + customer high assurance Certificate
7. Revoked evidence node used by current Final
8. Runtime mixed digest + CURRENT

## 14. Workflow Operation 후보
- `deployment.observe`
- `deployment.currentness.evaluate`
- `assurance.graph.rebuild`
- `assurance.compose`
- `assurance.certificate.issue`
- `assurance.certificate.verify`
- `assurance.certificate.revoke`
- `authority.grant.issue`
- `authority.grant.revoke`
- `work-unit.lease`
- `work-unit.commit`
- `aggregation.finalize`
- `ai.behavior.evaluate`
- `onsure.qualification.run`

각 operation은 effect class/required authority/input-output contract/receipt를 Workflow Registry v2에 등록해야 한다.

## 15. Lineage 편입
신규 chain:
`FinalLock → DeploymentCurrentness → SubjectGraph → CompositionSnapshot → AssuranceCertificate`

Support chain:
`EvidenceGraphHead`, `AuthorityGrant`, `ONSureReleaseQualification`, `AIBehaviorPopulation`, `DistributedAggregationReceipt`.

Certificate는 이 support chain의 required material parent를 누락할 수 없다.

## 16. 완료조건
이 Blueprint의 Contract Batch는 다음까지 끝나야 `CONTRACT_CANDIDATE_COMPLETE`다.
- 10 Schema 존재
- fixture coverage 충족
- Schema Instance Registry 등록
- Workflow Operation 등록
- Product Lineage parent bindings 등록
- cross-contract validator rule 등록
- 02 FR-META-044~060 trace mapping
- Active Selector에는 여전히 미승격

## 17. 비최종 경계
본 문서는 구현 지시의 다음 후보이며 현재 Claude DEV-01~13을 대체하지 않는다. 별도 개발 Handoff 전까지 `NOT_CONTRACTED / DESIGN_ONLY`다.
