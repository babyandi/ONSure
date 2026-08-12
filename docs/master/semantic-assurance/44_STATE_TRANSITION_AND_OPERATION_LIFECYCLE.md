# ONSure State Transition·Operation Lifecycle 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
ONSure operation이 단순 API 호출 성공/실패로 끝나지 않고, **의도·권한·실행·효과·증거·완료**를 분리하여 fail-closed 상태기계로 관리되도록 한다.

## 2. Operation Lifecycle
`REQUESTED → AUTHORIZED → INTENT_LOCKED → EXECUTION_PREPARED → RUNNING → EFFECT_OBSERVED → EVIDENCE_COMMITTED → DECIDED → COMPLETED`

예외:
`DENIED | BLOCKED | CANCEL_REQUESTED | CANCELLED | FAILED | RETRYABLE | INCONCLUSIVE | RECOVERY_REQUIRED`

`COMPLETED`는 PASS를 의미하지 않는다. operation이 정상 종료했다는 lifecycle 상태다.

## 3. OperationIntent
필수:
- operation_id
- operation_name/version
- tenant/project/target/subject
- actor principal
- authority grant/role
- purpose
- canonical parameters digest
- requested_effect_class
- policy epoch
- requested_at
- idempotency key

caller가 전달한 actor/authority/tenant 필드를 그대로 신뢰하지 않고 server-side context로 재구성한다.

## 4. Effect Class
- READ_ONLY
- LOCAL_MUTATION
- EVIDENCE_MUTATION
- EXTERNAL_EFFECT
- FINANCIAL_EFFECT
- AUTHORITY_EFFECT
- FINAL_ASSURANCE_EFFECT
- DEPLOYMENT_EFFECT
- REVOCATION_EFFECT

Effect class별 요구 approval/SoD/receipt/freshness가 다르다.

## 5. Authorization Timing
Authorization은 최소 두 번 본다.
1. intent acceptance 시점
2. effect commit 직전

장시간 operation 중 authority revoke/tenant move/policy epoch change가 발생할 수 있으므로 첫 authorization만으로 최종 effect를 commit하지 않는다.

## 6. Cancellation
Cancel은 성공 여부를 추정하지 않는다.
- CANCEL_REQUESTED: 중단 요청 접수
- cancellation safe point까지 진행 가능
- 외부 효과가 이미 발생했으면 compensation/reconciliation 요구
- incomplete evidence는 positive result로 사용 금지

## 7. Retry
Retryable error는 새 attempt를 만든다.
- logical_operation_id 동일
- attempt_id/number 증가
- prior attempt result 보존
- effect idempotency key 동일 또는 정책에 따른 child key

이전 FAIL을 새 PASS가 삭제하지 않는다.

## 8. Timeout
Timeout은 결과 부재다. `PASS`로 해석 금지.
- execution timeout
- observer timeout
- authority confirmation timeout
- external reconciliation timeout
을 분리한다.

## 9. Compensation
금전/배포/외부 시스템 effect는 transaction rollback이 불가능할 수 있다. Compensation은 원 effect를 지우지 않고 별도 effect/receipt를 생성한다.

## 10. State Transition Guard
각 transition은 machine-readable guard를 가진다.
예:
- AUTHORIZED → INTENT_LOCKED: authority current + object ownership + policy allow
- EFFECT_OBSERVED → EVIDENCE_COMMITTED: required observer complete
- EVIDENCE_COMMITTED → DECIDED: decision oracle current
- DECIDED → COMPLETED: required receipt sealed

Guard unknown이면 transition 금지.

## 11. Reconciliation
재시작 시 current_state 문자열을 신뢰하지 않고:
- operation intent
- event/ledger history
- external effect receipt
- evidence state
- idempotency ledger
를 재구성해 authoritative state를 결정한다.

## 12. Negative Test
- authority revoke after AUTHORIZED before effect commit
- cancelled run의 evidence를 PASS에 사용
- retry가 최초 failure를 덮어씀
- duplicate request가 외부 effect 2회 생성
- timeout을 success로 처리
- stale operation worker가 completion commit
- direct internal service call이 intent/authorization을 우회
- caller가 effect_class를 READ_ONLY로 축소 위장

## 13. 수용기준
- 모든 mutating operation은 OperationIntent와 lifecycle receipt를 남긴다.
- effect commit 시점 authority/policy를 재검증한다.
- retry/cancel/timeout이 history를 삭제하지 않는다.
- operation completion과 assurance PASS를 분리한다.
