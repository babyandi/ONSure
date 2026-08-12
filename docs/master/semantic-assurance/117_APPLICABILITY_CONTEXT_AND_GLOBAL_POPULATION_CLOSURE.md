# 117 Applicability Context 및 Global Population Closure

Status: `EXECUTED_PARTIAL / NON_FINAL`

## 1. Applicability Context
Requirement applicability는 임의 boolean이 아니라 다음 context digest로 계산한다.
- product_id/version
- target archetype
- system/program/component
- environment class
- industry profile
- purchased service plan
- requested assurance tier
- policy profile/epoch
- entitlement scope
- deployment/runtime profile

## 2. 상태
- APPLICABLE
- NOT_APPLICABLE
- CONDITIONAL
- UNKNOWN

NOT_APPLICABLE은 `rule_id + rationale + evidence_digest + evaluator + context_digest` 없이는 허용하지 않는다.

## 3. 현재 explicit 73 처리
기존 73/73 UNKNOWN을 그대로 positive로 바꾸지 않는다. 이번 배치에서는 context schema와 resolution rule을 확정했지만 실제 customer/target context가 고정되지 않았으므로 global applicability population은 아직 authoritative하지 않다.

FR-COM/FR-META 중 제품 공통 invariant 성격의 requirement는 baseline product-profile에서 APPLICABLE 후보로 분류할 수 있으나, Industry/Environment/Target-dependent requirement는 context 없이 확정하지 않는다.

## 4. Critical UNKNOWN ceiling
다음 class는 UNKNOWN이면 Design Lock 또는 해당 Assurance Tier의 positive claim을 제한한다.
- tenant isolation
- authority/SoD
- evidence integrity
- exact denominator
- independent assurance
- final reconstruction/lock
- deployment currentness가 요구되는 tier
- certificate revocation/currentness
- ONSure release qualification

## 5. Population identity
Applicability Population은 Requirement Universe Snapshot을 parent digest로 가진다.

`ApplicabilityPopulationDigest = H(requirement_universe_digest + context_digest + canonical rows)`

Requirement Universe가 바뀌면 기존 applicability population은 STALE 처리한다.

## 6. 현재 상태
- applicability model: CLOSED_AT_DESIGN_LEVEL
- explicit 73 authoritative applicability: NOT_PROVEN
- non-ID global requirement applicability: PENDING_MATERIALIZATION
- Critical UNKNOWN zero: NOT_PROVEN
