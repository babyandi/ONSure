# ONSure Open Decision → Configurable Policy 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Parents: `35_RUNTIME_COMPOSITION_CERTIFICATE_OPEN_DECISIONS.md`, `61_ASSURANCE_POLICY_PROFILE_MACHINE_SCHEMA_SPEC.md`

## 1. 목적
TTL, Offline Grace, Sample Size, SLO처럼 고객/상품/산업마다 달라질 수 있는 값이 설계 미완료 상태로 남지 않게 한다. **의미 자체가 미정인 항목과 값만 configurable한 항목을 분리**한다.

## 2. Decision 분류
- SEMANTIC_INVARIANT: 설정 불가. 예: Unknown→PASS 금지, Risk Acceptance≠Fact Validation
- CONFIGURABLE_LIMIT: 값은 profile별 설정 가능. 예: evidence TTL
- CONFIGURABLE_REQUIREMENT: 특정 tier/industry에서 enable/disable 가능. 예: four-eyes
- BUSINESS_DEFAULT: 상품 기본값, 고객 계약으로 강하게 변경 가능
- UNSOLVED_DESIGN: 아직 의미/알고리즘 자체가 미정. 구현 금지/HOLD

## 3. PolicyKey 예
- evidence.ttl.by_type
- currentness.observation.max_age
- offline.grace.duration
- qualification.validity.duration
- composition.minimum_child_strength
- certificate.minimum_strength
- authority.four_eyes.operations
- ai.statistics.sample_policy
- ai.statistics.critical_failure_tolerance
- operation.retry.max_attempts
- distributed.work.lease_duration
- operational.currentness.max_lag

## 4. Value Metadata
각 key는:
- type/unit
- min/max
- safe_default
- weaken_direction
- overridable
- industry mandatory floor/ceiling
- change requires revalidation?
- affected claims/capabilities
을 가진다.

## 5. Weakening Detection
값 변경이 assurance를 약화하는 방향인지 machine function으로 정의한다.
예:
- TTL 증가 = weakening 후보
- minimum strength 하향 = weakening
- four-eyes 대상 축소 = weakening
- sample size 하향 = weakening
- critical failure tolerance 증가 = weakening

## 6. Resolution Order
`Semantic Invariant → Global Safety Floor → Industry Profile → Product Tier → Organization Policy → Case-specific stricter override`

Case가 상위 safety floor를 약화할 수 없다.

## 7. Open Decision Closure 상태
- OPEN_SEMANTIC
- POLICY_KEY_DESIGNED
- SAFE_DEFAULT_DEFINED
- PROFILE_DEFAULT_DEFINED
- CONTRACTED
- ACTIVE

설계 완성 판단에서는 `POLICY_KEY_DESIGNED + SAFE_DEFAULT_DEFINED`이면 구조적 gap은 닫힌 것으로 볼 수 있으나 Runtime authority는 아니다.

## 8. Negative Test
- unknown semantic decision을 arbitrary boolean로 구현
- tenant override가 industry floor 약화
- unit 혼동(minutes vs seconds)
- policy key 없는 hardcoded TTL
- weakening인데 normal approval로 activation

## 9. 수용기준
P0 의미에 영향을 주는 Open Decision은 `SEMANTIC_INVARIANT` 또는 `CONFIGURABLE_* + safe default`로 분류되어야 하며, 의미 미정 상태로 runtime에 들어가지 않는다.
