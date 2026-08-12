# ONSure Assurance Policy·Open Decision 통합 부속 정본

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Parent: `08_REVIEW_CHECKLIST_OPEN_DECISIONS.md`

## 1. 목적
기존 08의 재무/법무/엔지니어링/규제/계약 결정 이력을 보존하면서, 35·42·61·66~69에서 새로 생긴 Assurance 정책 결정을 한 곳에서 추적한다. 기존 08을 대체하지 않는다.

## 2. P0 configurable policy로 전환할 항목
- evidence TTL
- currentness evaluation interval/TTL
- validator qualification validity
- certificate validity/revalidation period
- offline grace 및 revocation snapshot max-age
- critical HARD dependency propagation
- N/A proof requirement
- four-eyes required operation set
- delegation maximum depth
- break-glass maximum TTL
- statistical confidence level / minimum sample / zero-failure claim rule
- retry/flakiness classification threshold
- canary/rolling population closure rule
- multi-region currentness aggregation rule
- plugin qualification expiry/requalification trigger
- AI model/prompt/RAG/provider drift trigger
- ONSure release qualification validity/requalification trigger

## 3. 결정 유형
각 항목은 `FIXED_POLICY | TENANT_CONFIGURABLE_WITH_FLOOR | INDUSTRY_PROFILE | PRODUCT_TIER | CONTRACT_OVERRIDE_WITH_CEILING | OPEN` 중 하나로 분류한다.

### 고정해야 하는 안전 불변식
다음은 configurable value가 아니라 고정 invariant다.
- NOT_RUN/HOLD/BLOCKED/INCONCLUSIVE/UNKNOWN을 PASS로 승격 금지
- revoked/invalid signature를 CURRENT로 사용 금지
- Critical HARD child FAIL/INVALIDATED/REVOKED를 parent PASS가 숨기지 못함
- self-validation을 independent verification으로 승격 금지
- expired/revoked authority를 effect-time authority로 사용 금지
- exact denominator/population commitment 없이 product/final certificate 발급 금지
- break-glass로 Assurance strength 상승 금지
- offline uncertainty를 online currentness처럼 표시 금지

## 4. 초기 안전 기본값 후보
값은 구현 편의를 위한 확정값이 아니라 `BASELINE_CANDIDATE`이며 실제 운영 데이터/산업 profile로 조정한다.
- unknown/missing/unverifiable → HOLD 또는 UNKNOWN
- stale observer/currentness data → REASSESSMENT_REQUIRED
- deployment/runtime identity mismatch → INVALIDATED
- certificate revocation service unreachable: online mandatory profile에서는 verification UNKNOWN/HOLD
- same-principal two-key approval → four-eyes 불충족
- unsupported target feature → PARTIAL/NOT_PROVEN
- retry after failure → prior attempt history 보존, stable PASS 자동부여 금지

## 5. Industry Profile 결정 연결
- 금융: 폐쇄망/강한 SoD/감사·보존/독립검증/currentness 강화 후보
- 공공: data residency, offline, 공급망·artifact provenance 강화 후보
- 의료: 개인정보/데이터 provenance/human acceptance 강화 후보
- 일반 Enterprise: 조직 policy override는 허용하되 hard invariant 완화 금지

## 6. Product Assurance Tier 결정 연결
상품 Plan과 기술 Assurance Tier를 분리한다. 고가 Plan이라고 높은 Assurance Tier를 자동 부여하지 않는다.
- AT0 UNASSESSED
- AT1 EXECUTED
- AT2 EVIDENCE_BOUND
- AT3 INDEPENDENT
- AT4 QUALIFIED
- AT5 PRODUCTION_BOUND_CURRENT

각 Tier는 `68_PRODUCT_ASSURANCE_TIER_AND_SERVICE_PROFILE_DESIGN.md`의 증거 조건을 따른다.

## 7. 미확정값의 처리
미확정 정책이 P0 의미에 영향을 주면 구현이 임의 상수를 넣지 않는다. `policy_source=UNRESOLVED`, `decision=HOLD/NOT_AVAILABLE`로 노출하거나 안전 floor를 사용하고 해당 floor의 provenance를 기록한다.

## 8. 완료조건
- P0 의미에 영향을 주는 OPEN 값은 모두 fixed invariant 또는 configurable policy schema로 전환
- industry/product/tenant override 우선순위 명시
- 안전 floor보다 약한 override 차단
- policy epoch/digest가 Receipt/Final/Certificate에 결속
