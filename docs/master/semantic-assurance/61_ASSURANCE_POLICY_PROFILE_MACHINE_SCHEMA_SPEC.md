# ONSure AssurancePolicyProfile Machine Schema 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Parent: `42_ASSURANCE_POLICY_PROFILE_AND_RULE_GOVERNANCE.md`

## 1. 목적
Fail-closed 방향과 Open Decision 값을 하나의 versioned machine profile로 내려 consumer별 하드코딩 차이를 없앤다.

## 2. 필드군
### Identity
- policy_profile_id/version
- organization/tenant scope 또는 global profile
- product/assurance tier
- valid_from/until
- parent_profile nullable
- policy_epoch

### Assurance Ceiling
- minimum_strength_for_final
- minimum_strength_for_certificate
- currentness_required_for_production_bound_claim
- critical_unknown_limit

### Freshness
- evidence_ttl_by_type
- qualification_ttl
- currentness_observation_ttl
- offline_grace

### Composition
- hard_dependency_propagation
- soft_dependency_exception_requirements
- na_proof_requirements
- conflict_policy

### Independence/Authority
- required_independence_axes by decision class
- four_eyes_required_operations
- delegation_allowed/depth
- break_glass rules

### AI Statistics
- minimum_runs/sample policy by risk
- confidence method
- critical failure tolerance
- seed/exclusion governance

### Operations
- retry limits
- budget exhaustion decision
- recovery issuance ceiling
- degraded mode ceiling

## 3. Invariant
- weakening profile은 별도 weakening approval receipt 필요
- child tenant profile이 non-overridable global critical rule을 약화 못함
- offline grace/TTL은 finite
- critical_unknown_limit for highest tier는 0 후보
- certificate minimum strength가 Final source strength보다 높게 요구될 수는 있으나 낮춰서 source보다 강한 certificate를 만들 수 없음

## 4. Profile Resolution
`global mandatory → industry preset → product tier → organization additions` 순으로 합성한다. 충돌 시 더 강한 non-overridable rule을 우선하고 unresolved conflict는 HOLD.

## 5. Activation
DRAFT → VALIDATED → SHADOW → APPROVED → ACTIVE → SUPERSEDED/REVOKED
Active selector와 유사하게 signed activation receipt를 요구한다.

## 6. Negative Test
- tenant가 critical independence rule 제거
- unlimited offline grace
- critical unknown >0 with highest assurance
- policy weakening without approval
- consumer가 old cached profile로 Final 발급
- conflicting profile merge에서 느슨한 값 선택

## 7. 수용기준
모든 정책 의존 Final/Currentness/Composition/Certificate 판단이 exact active policy profile/epoch에 결속된다.
