# ONSure Design Baseline Candidate 판정·변경통제 설계

Status: `DESIGN_ONLY / CANDIDATE / NON_FINAL`
Covers task: **10**
Parents: `80`, `86`, `87`, `100`, `103`

## 1. Candidate decision state
Design baseline state는 다음을 구분한다.
- NOT_EVALUATED
- HOLD_MATERIALIZATION_INCOMPLETE
- HOLD_ORPHAN_PRESENT
- HOLD_CONTRADICTION_PRESENT
- HOLD_UNKNOWN_CRITICAL_REQUIREMENT
- READY_FOR_DESIGN_BASELINE_CANDIDATE
- DESIGN_BASELINE_CANDIDATE
- SUPERSEDED

`LOCKED`는 별도 authority와 receipt를 요구하며 이 문서에서 자동 발행하지 않는다.

## 2. READY 조건
다음이 모두 true여야 한다.
- global requirement exact population known
- applicability exact population known
- global trace scan complete
- P0-impact orphan 0
- unresolved P0 design contradiction 0
- unknown critical requirement 0
- exact artifact inventory content SHA-256 materialized
- baseline manifest reconstructable
- policy/naming/state authority known

## 3. 현재 후보 판정
현재 branch의 설계 상태는:
`HOLD_MATERIALIZATION_INCOMPLETE`

이유:
- global requirement exact population 미생성
- repository-wide orphan scan 미실행
- content SHA-256 inventory 미생성
- Design Lock Check 미실행

문서 1~15 설계가 존재한다는 사실로 READY를 만들지 않는다.

## 4. Baseline 이후 change class
- EDITORIAL: 의미/contract 영향 없음
- NON_BREAKING_DESIGN: optional capability 추가, 기존 invariant 유지
- ASSURANCE_MATERIAL: policy/claim/authority/trace 영향
- BREAKING_DESIGN: requirement/contract/state/authority 의미 변경

ASSURANCE_MATERIAL 이상은 impact analysis와 baseline re-evaluation을 요구한다.

## 5. 완료조건
Task 10은 candidate decision semantics와 현 상태를 HOLD로 명확히 고정함으로써 설계 완료한다. 실제 READY/LOCK 승격은 scanner evidence 이후 별도다.
