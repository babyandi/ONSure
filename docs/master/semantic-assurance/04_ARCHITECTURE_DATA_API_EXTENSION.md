# ONSure Semantic Assurance 아키텍처·데이터·API 상세설계 확장

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Parent authority: `../04_ARCHITECTURE_DATA_API_OLICENSE.md`

## 1. 목적
본 문서는 기존 ONSure 아키텍처를 유지하면서 Semantic Assurance Capability를 실제 Service/Entity/State/API/Evidence 구조로 내릴 때 필요한 논리 상세를 정의한다. 현재 계약·코드가 없는 항목은 구현된 것으로 간주하지 않는다.

## 2. 논리 컴포넌트 확장
기존 Orchestration/OReview/OVerification/OEvidence/OMemory에 다음 논리 서비스 또는 내부 모듈을 추가한다. 초기 구현은 별도 microservice가 아니라 기존 서비스 내 모듈이어도 된다.

### SemanticCoverageService
- denominator discovery와 epoch 관리
- candidate add/remove/merge/split 기록
- CoverageReport가 참조할 authoritative universe 제공

### ObligationClosureService
- obligation resolution expression 평가
- ALL_OF/ANY_OF/EXACTLY_ONE_OF member 상태 집계
- downstream routing과 stale propagation

### AuthorityAssuranceService
- authority lifecycle
- decision/effect authorization freshness
- SoD/principal uniqueness
- policy precedence와 representation provenance

### StateAuthorityService
- canonical state owner map
- writer uniqueness와 command boundary
- callback/ops/migration/AI authority bypass 탐지

### RightsAssuranceService
- right/remedy catalog와 actionability
- rights reachability
- transitive fixed-point closure
- restore 전후 rights invariant 비교

### DistributedEffectService
- handoff work identity
- batch/item receipt reconciliation
- effect reversibility classification
- terminal dependency disposition

### EvidenceReperformanceService
- subject read-back
- Oracle 재수행
- evidence-strength classification
- upstream narrative/evidence contradiction 탐지

### SemanticTraceService
- cross-model mapping
- responsibility/authority preservation
- schema-instance-validator-receipt closure

### ObserverDisclosureService
- observer equivalence class
- disclosure projection
- cross-channel differential profile

### ValidatorQualificationService
- validator/detector/rule/oracle version qualification
- hidden/private benchmark access boundary
- isolated run과 strict critical recall

## 3. 핵심 신규 엔터티
### SemanticCapabilityDefinition
- capability_id
- name
- applicability_rule
- required_contracts
- required_test_pack
- qualification_policy
- authority_boundary

### DenominatorEpoch
- denominator_id
- target_manifest_digest
- dimension
- epoch
- current_ids
- source_refs
- change_candidates
- created_at
- supersedes

### DenominatorChangeCandidate
- candidate_id
- dimension
- current_denominator
- change_type ADD/REMOVE/MERGE/SPLIT
- affected_ids
- rationale
- counterargument
- proof_required
- impacted_artifacts
- status

### ObligationClosure
- obligation_id
- source_requirement_or_claim
- resolution_groups
- discovery_state
- downstream_state
- target_stage
- owner
- source_hashes
- decision

### AuthorityLifecycleRecord
- authority_subject
- resource_scope
- lifecycle_role
- authority_source
- created_or_removed_authority
- delegation_chain
- effective_from/to
- revocation
- successor_or_dissolution_rule
- compromise_rebind_rule

### AuthorizationDecisionSnapshot
- operation_id
- actor/principal
- subject/resource/tenant/purpose
- authority_revision
- policy_revision
- claim_revision
- decision_at
- freshness_deadline
- effect_mode SNAPSHOT_AUTHORIZED/REVALIDATE_AT_EFFECT/HYBRID

### CanonicalStateAuthority
- state_id
- authoritative_owner
- command_id
- preconditions
- expected_version_or_concurrency_rule
- idempotency_scope
- policy_revision
- external_effect
- recovery_owner
- readback_oracle

### RightReachability
- right_id
- holder
- requester
- executor
- approver_or_verifier
- required_function
- authority
- resource_binding
- reachable_from_states
- current_exercisability
- blocked_reason
- remedies
- evidence_refs

### HandoffWork
- handoff_work_id
- source_function/result
- target_function
- subject_revision
- state
- delivery_semantics
- dedupe_key
- authorization_snapshot
- evidence_chain

### BatchOperation
- batch_operation_id
- population_snapshot
- atomicity_class
- item_operation_ids
- checkpoint
- cancellation_boundary
- retry_set_derivation
- summary_receipt

### ItemEffectReceipt
- item_operation_id
- target/revision
- authority/policy revision
- effect_state
- external_effect_state
- readback
- evidence

### EffectReversibilityRecord
- operation_id
- class REVERSIBLE/COMPENSATABLE/IRREVERSIBLE/EXTERNALLY_AMBIGUOUS
- irreversible_point
- pre_effect_cancel_rule
- post_effect_remedy
- compensation_rule
- reconciliation_rule
- historical_evidence_rule

### ReperformanceRun
- reperformance_id
- subject_refs
- subject_readback
- oracle_runs
- accepted_upstream_claims
- unreperformed_required_oracles
- evidence_strength
- decision

### CrossModelMapping
- source_model/source_id
- target_model/target_id
- cardinality
- responsibility_carried
- state_or_authority_carried
- responsibility_not_carried
- revision_hashes
- status

### ObserverEquivalenceClass
- class_id
- observer_class
- protected_internal_states
- allowed_result_class
- status/schema/length/timing/retry/header/notification policies
- differential_oracle

### BusinessSemanticInvariant
- invariant_id
- value_type
- unit_or_currency
- precision
- rounding_mode/stage
- equations
- min/max/overflow rules
- authoritative_ledger
- reconciliation_oracle

### ValidatorQualificationRecord
- validator_identity/version/hash
- capability
- method_manifest
- benchmark_manifest
- isolation_attestation
- critical_denominator
- critical_found
- strict_recall
- critical_miss_count
- valid_from/to
- status

## 4. 상태 모델
### Handoff Work
`SOURCE_COMMITTED -> HANDOFF_DURABLE -> TARGET_CLAIMED -> TARGET_EFFECTED -> TARGET_READBACK_VERIFIED`

예외:
`DUPLICATE_SUPPRESSED`, `CANCELLED`, `SUPERSEDED`, `AMBIGUOUS`, `RECONCILIATION_REQUIRED`, `FAILED`

### Reperformance
`PLANNED -> SUBJECT_BOUND -> ORACLES_RUNNING -> EVIDENCE_COLLECTED -> CLASSIFIED -> DECIDED`

### Rights
존재상태:
`EXISTS | TERMINATED | INPUT_REQUIRED`

행사가능성:
`EXERCISABLE | TEMPORARILY_BLOCKED | UNREACHABLE | POLICY_PROHIBITED | INPUT_REQUIRED`

### Qualification
`UNQUALIFIED -> QUALIFICATION_PLANNED -> RUNNING -> QUALIFIED_NONFINAL | PARTIAL | FAILED | STALE`

## 5. Cross-Contract Invariant 추가
- Final Claim material evidence는 최소 요구된 evidence strength 이상이어야 함
- `DECLARED_RESULT_ONLY` evidence는 Final material claim에 사용 금지
- current CoverageReport.denominator_epoch = current DenominatorEpoch
- obligation mandatory member GAP/INPUT_REQUIRED이면 parent claim PASS 금지
- canonical state writer uniqueness = 1
- REVALIDATE_AT_EFFECT operation은 stale authorization snapshot 재사용 금지
- USER_ACTIONABLE/OPERATOR_ACTIONABLE right는 exercising path 필수
- batch summary count = item receipt reconciliation 결과
- terminal effect 전 current dependency graph revision 재확인
- cross-model mapping의 source/target orphan 0 또는 explicit disposition
- AI adopted UC는 profile/fallback/TEVV execution link 필수

## 6. API 후보 (`DESIGN_ONLY`)
구현 전 `workflow-operation-registry.v1.json` 등록이 선행되어야 한다.

### Coverage
- `semantic.denominator.discover`
- `semantic.denominator.challenge`
- `semantic.coverage.current`

### Evidence
- `semantic.reperformance.plan`
- `semantic.reperformance.run`
- `semantic.reperformance.read`

### Authority/Rights
- `semantic.authority.assess`
- `semantic.rights.assess`
- `semantic.state-authority.assess`

### Distributed Effect
- `semantic.handoff.assess`
- `semantic.batch.assess`
- `semantic.terminal.assess`

### Qualification
- `semantic.validator.qualify`
- `semantic.validator.qualification-status`

API가 추가되더라도 merge/final/production authority를 노출하지 않는다.

## 7. Freshness / Invalidation Graph
최소 전파:

`Target/Requirement/Denominator change`
→ Obligation/Coverage/Mapping stale
→ Test Plan stale
→ Execution stale
→ Reperformance/Audit stale
→ Report/Certificate current claim unavailable

`Authority/Policy/Claim revision change`
→ pending async effect eligibility 재평가
→ stale snapshot 사용 금지

`Validator/Oracle/Rule change`
→ 해당 Capability Qualification stale
→ 영향받는 historical validation 영향분석

## 8. Transaction / Survivability
Semantic Assurance record도 OEvidence 원칙을 따른다.
- PREPARED/COMMITTED 또는 동등한 durable state
- crash 중간 artifact를 PASS로 복원 금지
- DB/object/log 일부만 성공한 경우 reconciliation 상태 유지
- duplicate retry는 operation identity/idempotency로 단일 authoritative effect 보장

## 9. 보안 경계
- target repository가 ONSure trust registry/qualification store를 수정하지 못함
- validator qualification hidden corpus는 learner/target principal에 노출 금지
- Reperformance runner는 upstream 결과가 아닌 exact target read 권한만 최소 제공
- observer-equivalence raw internal state는 end-user projection으로 자동 노출 금지
- business semantic ledger/financial evidence 접근은 tenant/resource/purpose scope 강제

## 10. 구현 상태 경계
현재 문서는 설계다. 위 Service/Entity/API 이름이 존재한다고 Runtime이 구현된 것이 아니다. 실제 계약과 Operation Registry, 코드, 테스트, 실행 evidence가 생기기 전에는 `DESIGN_ONLY` 또는 `PARTIAL`로 유지한다.
