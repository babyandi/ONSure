# ONSure Exact Inventory·Design Lock·Claude Drift·Change Queue·Final Baseline 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Covers: Task 45~50

## 1. Exact Artifact Inventory 생성 준비
Lock population은 authoritative design docs + machine design registries만 포함하며 source/runtime files는 별도 implementation inventory로 분리한다.

각 member:
- path
- content_sha256
- git_blob_sha
- authority_class
- lifecycle_state
- source_commit
- supersession relation

Population commitment는 canonical sort(path) 후 member commitments를 해시한다.

## 2. Design Lock Check 실행 준비
Lock runner inputs:
- exact design population
- global requirement universe
- trace registry
- naming registry
- conflict registry
- policy/open decision registry

Mandatory checks:
- exact population digest verified
- global universe materialized
- P0 requirement orphan=0
- P0 contract/operation/event/receipt/test orphan=0
- unresolved P0 design conflict=0
- authority/state/naming contradiction=0
- all active policy unknowns explicitly configurable or HOLD-producing

## 3. DesignBaselineCandidateReceipt
필드:
- baseline_generation
- branch/head_commit
- design_population_digest
- requirement_universe_digest
- trace_registry_digest
- naming_registry_digest
- conflict_report_digest
- lock_check_report_digest
- open_policy_decision_digest
- status
- blockers[]
- generated_at

status 후보:
`NOT_READY|READY_FOR_LOCK_CHECK|LOCK_CANDIDATE|REJECTED`.
`LOCKED`는 별도 승인/검증 authority가 있어야 한다.

## 4. Claude Design Drift Management
Claude 개발 중 발견되는 불일치 유형:
- DESIGN_UNIMPLEMENTABLE
- CONTRACT_AMBIGUITY
- RUNTIME_SEMANTIC_GAP
- MIGRATION_GAP
- SECURITY_BOUNDARY_GAP
- TESTABILITY_GAP
- PERFORMANCE_CONSTRAINT_CONFLICT

Claude는 설계를 임의 수정해 의미를 약화하지 않고 `DesignDriftCandidate`를 생성한다.

DesignDriftCandidate:
- drift_id
- design refs
- implementation refs
- problem statement
- evidence
- proposed options
- security/assurance impact
- blocking severity

## 5. Design Change Intake Queue
State:
`SUBMITTED|TRIAGED|DESIGN_REQUIRED|ACCEPTED_FOR_NEXT_BASELINE|REJECTED|SUPERSEDED`.

Priority:
P0 semantic/authority/evidence/gate bypass
P1 major implementability/currentness/composition
P2 optimization/ergonomics

P0 drift는 affected development batch를 HOLD할 수 있으나 기존 active v1 authority를 자동 변경하지 않는다.

## 6. Final Design Baseline Candidate 판단
다음이 모두 참일 때만 `LOCK_CANDIDATE`:
1. exact design population complete
2. global requirement universe exact denominator complete
3. trace P0 orphan 0
4. contract/operation/event/receipt/test P0 orphan 0
5. unresolved P0 design conflict 0
6. all naming/state/authority semantics canonical
7. Open Decision이 configurable policy 또는 explicit blocker로 표현
8. DesignDrift P0 open 0
9. baseline receipt reconstructible

구현/컴파일/테스트/독립검증은 Design Lock과 별도 축이며 Product Final을 의미하지 않는다.

## 7. Acceptance
- 45 exact inventory 준비 완료
- 46 lock check 입력/규칙 완료
- 47 candidate receipt fields 완료
- 48 Claude drift governance 완료
- 49 change queue 완료
- 50 final candidate decision rule 완료

현재 설계상 최고 허용 상태는 `READY_FOR_LOCK_CHECK`; actual inventory/universe/scanner 실행 전 LOCK_CANDIDATE를 사실로 선언하지 않는다.
