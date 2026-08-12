# ONSure Global Trace Closure Scanner 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Parents: `65_DESIGN_TRACE_REGISTRY_MACHINE_SPEC.md`, `87_DESIGN_LOCK_CHECK_AND_REPOSITORY_ORPHAN_SCAN.md`, `88_GLOBAL_REQUIREMENT_UNIVERSE_AND_DENOMINATOR.md`

## 1. 목적
Design Lock 이전에 Requirement→Design→Contract→Operation→Test→Evidence→Status 경로를 repository 전체에서 자동 점검한다.

## 2. Scanner 입력
- RequirementUniverseSnapshot
- DesignArtifactInventory
- Contract Registry
- Workflow Operation Registry
- Event/Receipt Registry
- Test/Fixture Registry
- Policy Profile Registry
- API/Surface Registry
- Finding Ledger

## 3. Trace Node
REQUIREMENT, DESIGN, CONTRACT, OPERATION, API, EVENT, RECEIPT, TEST, FIXTURE, EVIDENCE_TYPE, POLICY, AUTHORITY_RULE, UI_CLAIM, FINDING.

## 4. Trace Edge
DEFINED_BY, IMPLEMENTED_BY, ENFORCED_BY, EXPOSED_BY, EMITS, RECEIPTED_BY, VERIFIED_BY, GOVERNED_BY, AUTHORIZED_BY, PRESENTED_BY, CHALLENGED_BY.

## 5. 필수 Closure 규칙
Machine-enforced Requirement는 최소 DESIGN+CONTRACT+TEST edge 필요.
Effect Operation은 AUTHORITY_RULE+EVENT+RECEIPT edge 필요.
Positive UI Claim은 canonical status/assurance contract에 연결되어야 한다.
Final/Certificate Requirement는 Evidence/Currentness/Authority path가 모두 필요하다.
Security boundary Requirement는 negative fixture가 필요하다.

## 6. Orphan Severity
P0:
- strong claim without evidence/authority path
- effect operation without authorization
- Final/Certificate path without currentness/denominator
- machine contract that can create stronger state than parent requirement

P1:
- test without requirement
- design-only feature exposed in UI as available
- event without consumer/retention policy

P2:
- documentation alias drift
- non-authoritative duplicate narrative

## 7. Scanner 출력
- exact universe digest
- scanned node/edge counts
- orphan lists by class/severity
- unresolved semantic conflicts
- missing negative fixture list
- trace completeness ratio (보조지표)
- lock_eligible boolean candidate

`trace completeness ratio=100%`만으로 lock 금지. P0=0 및 mandatory closure rule 모두 충족해야 한다.

## 8. 수용기준
- 전체 Requirement Universe를 denominator로 사용한다.
- FR-META만 스캔하고 전체 완료를 주장하지 않는다.
- design-only node를 implemented로 오인하지 않는다.
- orphan P0 1건이라도 있으면 Design Lock candidate=false.
