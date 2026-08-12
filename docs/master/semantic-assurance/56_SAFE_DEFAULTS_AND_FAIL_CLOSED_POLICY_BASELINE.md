# ONSure Safe Defaults·Fail-Closed Policy Baseline

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
Open Decision이 미확정인 동안 구현자가 편의상 느슨한 값을 선택해 assurance가 강해지는 일을 방지한다. 수치값은 미확정이어도 **안전한 기본 방향**은 고정한다.

## 2. 기본 원칙
- Unknown → PASS 금지
- Missing Evidence → PASS 금지
- Missing Authority → DENY/HOLD
- Missing Qualification → NOT_PROVEN/HOLD
- Missing Currentness → UNKNOWN/REASSESSMENT
- Missing Revocation status → CURRENT 주장 금지
- Missing Applicability proof → denominator에서 임의 제외 금지
- Missing Target binding → Evidence/Deployment 사용 금지
- Mixed generation → Final/Composition 금지

## 3. Default Exposure
- network egress: DENY
- external AI provider data transfer: DENY unless policy allow
- shared corpus contribution: OPT_OUT
- hidden corpus access: DENY
- plugin privilege: 최소 declared scope
- cross-tenant access: DENY
- public certificate disclosure: 최소 공개 profile

## 4. Default Authority
- self-validation: NON_FINAL
- Human Business Acceptance: technical PASS 아님
- Risk Acceptance: defect resolution 아님
- OTester/OAudit independence: NOT_PROVEN until verified
- delegated Final authority: 금지 또는 explicit policy 필요
- break-glass: assurance strength 상승 금지

## 5. Default Lifecycle
- expired/stale evidence: positive Final input 제외
- retry FAIL→PASS: flaky history 유지
- cancellation/timeout: PASS 금지
- partial rollout: product-wide CURRENT 금지
- DR restore: recovery qualification 전 strong issuance 금지

## 6. Default Composition
- Critical HARD child unknown/hold/fail/stale → parent PASS/CURRENT ceiling
- N/A: proof 없으면 적용대상으로 유지 또는 HOLD
- conflicting evidence: latest-wins 금지, conflict HOLD
- self-validation strength와 independent strength 혼합 시 상위 strength는 최소 required child ceiling 적용

## 7. Default Offline
- revocation sync 불가: unlimited CURRENT 금지
- trusted time 불가: freshness ceiling 하향
- offline grace는 finite/configured
- reconnect conflict: remote/current authority와 reconciliation 전 HOLD

## 8. Default Scale
- budget exhaustion: PARTIAL/HOLD/BLOCKED
- dropped partition/work unit: aggregate PASS 금지
- duplicate result: denominator 증가 금지
- stale lease worker: commit 금지

## 9. Default Certificate
- signature valid만으로 CURRENT 금지
- limitation/exclusion 숨김 금지
- unknown certificate profile: historical signature verification만 허용
- revoked/stale/uncertain 상태를 public verifier가 표시

## 10. Default Policy Change
Assurance를 약화시키는 변화는 신규 강화보다 높은 승인/qualification을 요구한다. 정책값이 비어있으면 기존 강한 policy를 유지한다.

## 11. 구현 규칙
Safe default를 코드 상수로 흩어놓지 않고 AssurancePolicyProfile과 contract default/guard로 중앙화한다. consumer마다 다르게 해석하지 않는다.

## 12. 수용기준
미확정 정책값이 있다는 이유로 구현자가 fail-open하지 않는다. 모든 미확정 P0 semantic은 최소 HOLD/NOT_PROVEN/UNKNOWN ceiling으로 표현된다.
