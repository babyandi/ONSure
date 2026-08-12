# Runtime API·Error Contract 개발 Handoff 설계

Status: `DESIGN_ONLY / CANDIDATE / NON_FINAL`

## 1. 목적
Claude가 후속 API를 구현할 때 HTTP 2xx/4xx/5xx만으로 assurance semantics를 추정하지 않도록 Operation/Decision/Error/Receipt를 분리한다.

## 2. 공통 Response Envelope 후보
- operation_id
- request_id
- idempotency_key nullable
- execution_state
- assurance_decision nullable
- currentness nullable
- error_code nullable
- retryable
- evidence_refs[]
- receipt_ref nullable
- target_context_digest
- policy_epoch

## 3. Error Class
### Transport
AUTHENTICATION_FAILED, RATE_LIMITED, MALFORMED_REQUEST, SERVICE_UNAVAILABLE

### Authorization
TENANT_SCOPE_DENIED, RESOURCE_SCOPE_DENIED, PURPOSE_DENIED, SOD_DENIED, GRANT_EXPIRED, BREAK_GLASS_REQUIRED

### Assurance Input
TARGET_IDENTITY_INCOMPLETE, REQUIREMENT_UNIVERSE_INCOMPLETE, POLICY_INPUT_REQUIRED, QUALIFICATION_NOT_PROVEN, OBSERVABILITY_INCOMPLETE

### Evidence
EVIDENCE_MISSING, EVIDENCE_STALE, EVIDENCE_TAMPERED, RECEIPT_REPLAYED, ORIGIN_INDEPENDENCE_INSUFFICIENT

### Final/Currentness
FINAL_RECONSTRUCTION_MISMATCH, FRESHNESS_BARRIER_BLOCKED, DEPLOYMENT_IDENTITY_MISMATCH, RUNTIME_DRIFT_DETECTED, REASSESSMENT_REQUIRED

### Migration/Recovery
DUAL_WRITE_DIVERGED, RECONSTRUCTION_UNRECOVERABLE, RECOVERY_QUALIFICATION_REQUIRED

## 4. HTTP와 Assurance 분리
- HTTP 200이어도 `assurance_decision=HOLD` 가능
- HTTP 409는 semantic conflict 후보이며 PASS 의미 아님
- HTTP 503은 infra unavailable이며 target FAIL 의미 아님
- validation target defect와 ONSure infra failure를 같은 FAIL로 합치지 않는다.

## 5. Async Job
POST가 accepted되어도 assurance 완료가 아니다.
상태:
`ACCEPTED|AUTHORIZED|RUNNING|WAITING_EXTERNAL|BLOCKED|SUCCEEDED_NONFINAL|FAILED_EXECUTION|CANCELLED|TIMED_OUT`

`SUCCEEDED_NONFINAL`은 Final PASS와 다르다.

## 6. Idempotency
같은 idempotency key는 같은 semantic request digest에만 재사용 가능하다. request body/purpose/target이 달라지면 `IDEMPOTENCY_CONTEXT_MISMATCH`.

## 7. Pagination
Evidence/subject/requirement population pagination은 snapshot token에 결속한다. 페이지 사이 population change가 denominator 누락을 만들지 않아야 한다.

## 8. Bulk API
부분 성공을 overall success 하나로 축약 금지. 각 item의 effect/receipt/error를 반환하고 aggregate decision을 별도 계산한다.

## 9. 수용기준
Client가 transport success를 assurance success로 오해할 수 없는 typed response를 제공한다.
