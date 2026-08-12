# 122 Claude Reverse Alignment·Semantic Change·Design Drift

Status: `EXECUTED_PARTIAL / NON_FINAL`

## 1. 목적
Tasks 32~35를 수행한다. 현재 PR의 changed-file inventory를 설계 registry와 대조하되, semantic code review는 사용자 방침대로 뒤로 미룬다.

## 2. Reverse alignment 분류
### Design → Implementation
- IMPLEMENTED_CANDIDATE_PRESENT
- CONTRACT_PRESENT_RUNTIME_PENDING
- DESIGN_ONLY_NOT_MATERIALIZED
- TEST_PENDING
- EVIDENCE_PENDING

### Implementation → Design
- DESIGN_OWNER_FOUND
- DESIGN_OWNER_AMBIGUOUS
- IMPLEMENTATION_ORPHAN_CANDIDATE

## 3. 현재 inventory-level 관찰
PR에는 다음 구현 후보가 존재한다.
- SemanticAssuranceV2Reconstructor
- SemanticAssuranceV2WorkflowService
- SemanticAssuranceV2DispatcherBridge
- SemanticAssuranceShadowGateComparator
- TenantRbacService modification
- v2 validator script
- WorkflowService/DispatcherBridge JUnit
- 다수 candidate v2 schemas/fixtures

반면 81 이후 후속 설계 중 Currentness Composition EvidenceGraph Certificate AuthorityGrant DistributedWork AIBehavior ONSureReleaseQualification PolicyProfile Recovery Global Lock Scanner는 다수가 독립 machine contract/runtime로 아직 미materialize 상태다.

## 4. Semantic Change Queue
구현 중 새 semantics는 다음 row로 등록한다.
- change_id
- source commit/path
- observed semantic
- affected requirement/design/contract
- severity
- compatibility
- required design action
- status

현재 queue가 0이라고 증명하지 않는다. `ZERO_NOT_PROVEN`을 유지한다.

## 5. Design Drift
Drift class:
- CONTRACT_DRIFT
- OPERATION_DRIFT
- STATE_DRIFT
- AUTHORITY_DRIFT
- POLICY_DRIFT
- ERROR_SEMANTIC_DRIFT
- EVIDENCE_BINDING_DRIFT
- DENOMINATOR_DRIFT

Drift가 P0이면 Design Baseline candidate는 HOLD다.

## 6. Task 판정
32. Claude 구현 reverse alignment: INVENTORY_LEVEL_EXECUTED
33. Semantic Change Queue: OPEN / ZERO_NOT_PROVEN
34. Design Drift Check: RULES_APPLIED_AT_INVENTORY_LEVEL / FULL_SEMANTIC_REVIEW_DEFERRED
35. Final Design Baseline Candidate: HOLD

최종 상태:
`IMPLEMENTATION_ALIGNMENT_PARTIAL / SEMANTIC_REVIEW_DEFERRED / CHANGE_QUEUE_OPEN / DESIGN_DRIFT_NOT_FULLY_PROVEN / BASELINE_CANDIDATE_HOLD`
