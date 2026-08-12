# Design Lock Check·Repository-wide Orphan Scan 설계

Status: `DESIGN_ONLY / CANDIDATE / NON_FINAL`

## 1. 목적
Design Baseline Candidate를 사람의 감으로 LOCKED 처리하지 않고 repository 전체의 requirement/design/contract/operation/test/evidence 연결을 정적 검사한다.

## 2. Scanner 입력
- DesignArtifactInventory
- DesignTraceRegistry
- Requirement registries
- Contract/schema inventory
- Workflow Operation Registry
- API route registry
- Event/Receipt registry
- Fixture/Test registry
- Open Decision/Policy registry
- supersession/deprecation registry

## 3. Orphan 종류
### REQUIREMENT_ORPHAN
Requirement가 design component/contract/test 어느 쪽에도 연결되지 않음.

### DESIGN_ORPHAN
설계 기능이 Requirement/Decision authority 없이 존재.

### CONTRACT_ORPHAN
Contract가 Requirement/Design/Operation 소비자 없이 존재.

### OPERATION_ORPHAN
Operation이 authority/contract/event/receipt/test 연결 없이 존재.

### EVENT_ORPHAN
Event producer 또는 consumer/receipt relation이 없음.

### TEST_ORPHAN
Test가 어떤 requirement/contract invariant를 증명하는지 trace 없음.

### POLICY_ORPHAN
Policy field가 어떤 decision/operation에 적용되는지 없음.

### UI_CLAIM_ORPHAN
UI/Report에 표시하는 strong claim이 authoritative state/receipt에 연결되지 않음.

## 4. Contradiction 종류
- status vocabulary mismatch
- same concept multiple canonical names
- same operation different authority
- same claim different assurance ceiling
- same policy field conflicting default
- active + superseded simultaneous authority
- Final/Certificate semantics divergence
- Product Tier vs technical Assurance Tier conflation

## 5. Severity
- P0: strong positive assurance/authority bypass 가능
- P1: semantic ambiguity/stale/misleading UX 가능
- P2: documentation/index inconsistency

P0 > 0이면 Design Lock 불가.

## 6. Lock Check 단계
1. exact artifact inventory build
2. hash verification
3. trace completeness
4. orphan scan
5. contradiction scan
6. policy explicitness check
7. naming/supersession check
8. machine contract naming uniqueness
9. Master/README/index comparison
10. LockCandidateReport 생성

## 7. LockCandidateReport
- baseline_candidate_id
- commit_sha
- inventory_digest
- requirement_count
- traced_requirement_count
- orphan_counts_by_type
- contradiction_counts_by_severity
- open_decision_count
- hidden_default_count
- duplicate_canonical_name_count
- index_drift_count
- lock_eligible
- blockers[]

## 8. False Closure 방지
- 문서가 존재한다는 이유로 traced=true 금지
- candidate schema 이름만으로 contract_materialized=true 금지
- test source 존재만으로 executed=true 금지
- `OPEN`을 0으로 세기 위해 scope에서 제거 금지
- superseded 파일을 삭제해 과거 conflict를 숨기지 않음

## 9. 현재 후보 목표
설계 관점 Lock Candidate 목표:
- Requirement orphan = 0
- P0 contradiction = 0
- hidden policy default = 0
- duplicate canonical strong concept = 0
- Master/README/index drift = 0

구현/실행/독립검증은 Design Lock의 다른 단계가 아니라 별도의 Implementation/Qualification Gate다.
