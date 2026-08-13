# 139 Design QA Applicability Status

Status: APPLICABILITY_PARTIAL / NON_FINAL

Applicability는 고객별 임의 판단이 아니라 Requirement Universe와 동일 epoch에 결속된 별도 population으로 관리한다.

## 기준 context dimensions
- product: ONSURE
- target archetype
- industry profile
- deployment mode: SaaS / Hybrid / On-premises / Air-gapped
- assurance tier
- policy profile
- requirement epoch

## 허용 상태
- APPLICABLE
- NOT_APPLICABLE
- CONDITIONAL
- UNKNOWN

NOT_APPLICABLE은 reason, authority, source evidence가 없으면 유효하지 않다. CONDITIONAL은 activation condition과 재평가 trigger를 가져야 한다. UNKNOWN은 숨기거나 N/A로 치환하지 않는다.

## Product Design Baseline용 기본 원칙
- 공통 FR-COM, 핵심 FR-META, core NFR은 기본 APPLICABLE 후보
- 특정 배포모드 요구는 해당 mode에서 APPLICABLE, 나머지에는 CONDITIONAL 또는 NOT_APPLICABLE proof 필요
- 규제산업 전용 요구는 industry profile이 활성화될 때 APPLICABLE
- Offline/Air-gapped 전용 요구는 mode 활성화 시 APPLICABLE
- Safety/Hazard는 target이 안전 영향 가능성을 가지는 경우 APPLICABLE이며, 영향 가능성을 아직 판정하지 못하면 UNKNOWN
- Contestability/Appeal은 ONSure의 authoritative decision이 고객 또는 제3자에게 영향을 주는 경우 APPLICABLE

## Lock gate
- Global Requirement Universe exact population과 1:1 cardinality
- every requirement has one applicability record
- Critical UNKNOWN = 0 for the selected baseline/profile
- N/A proof missing = 0
- contradictory applicability = 0
- applicability population digest 생성

현재 global requirement denominator가 exact하지 않으므로 authoritative applicability population도 아직 exact할 수 없다. 이전 73/78 기반 UNKNOWN population은 historical partial evidence로만 취급한다.

현재 판정: APPLICABILITY_RULES_FIXED / AUTHORITATIVE_POPULATION_PENDING_GLOBAL_DENOMINATOR / DESIGN_QA_HOLD.
