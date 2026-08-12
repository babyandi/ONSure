# Assurance Policy Bootstrap·Profile Default 설계

Status: `DESIGN_ONLY / CANDIDATE / NON_FINAL`

## 1. 목적
Open Decision을 구현자가 임의 상수로 박지 않도록 초기 Policy Profile의 구조와 보수적 default 전략을 정의한다.

## 2. Default 원칙
- 명시되지 않은 Critical 정책은 fail-open하지 않는다.
- 값이 미확정이면 `POLICY_INPUT_REQUIRED` 또는 보수적 ceiling을 사용한다.
- 산업 Profile은 Core Policy를 완화할 수 없고 동등 또는 강화만 가능하다.
- 고객 custom profile은 signed approval과 policy diff를 가진다.

## 3. Core Default 후보
아래 값은 `CANDIDATE_DEFAULT`이며 사업/법무 확정값이 아니다.
- critical_unknown_budget = 0
- unresolved_p0_allowed_for_positive_certificate = 0
- stale_evidence_positive_use = false
- revoked_receipt_use = false
- self_validation_can_satisfy_independent_gate = false
- same_principal_multi_key_counts_as_two_person = false
- rollback_auto_restores_currentness = false
- unsupported_capability_defaults_to = NOT_PROVEN
- observer_incomplete_defaults_to = HOLD
- migration_divergence_defaults_to = HOLD
- certificate_verification_without_current_revocation_state = STATUS_UNCERTAIN

## 4. Numeric Policy의 처리
TTL/sample size/retry/backoff/offline grace는 실제 운영값이므로 계약에 hardcode하지 않는다. Policy field에는:
- value
- unit
- source
- rationale
- approved_by
- effective_from
- expires/review_due
- industry override rules
을 둔다.

## 5. Policy Weakening 판정
다음 변화는 `WEAKENING` 후보:
- TTL 증가
- offline grace 증가
- critical propagation 완화
- independent gate 축소
- required denominator 축소
- qualification threshold 하향
- retry로 failure history 숨김 허용
- plugin privilege 확대
- certificate minimum strength 하향

WEAKENING은 일반 update보다 높은 authority, impact analysis, shadow comparison, rollback plan을 요구한다.

## 6. Bootstrap 순서
1. Core immutable fail-closed baseline
2. Industry profile overlay
3. Product Assurance Tier requirements
4. Tenant custom profile
5. Case-specific approved override

precedence가 높아져도 lower layer의 non-waivable invariant를 제거할 수 없다.

## 7. 수용기준
정책 미입력 때문에 구현 코드가 arbitrary default를 생성하지 않는다. 모든 effective policy는 source와 epoch가 추적 가능해야 한다.
