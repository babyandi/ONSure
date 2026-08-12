# ONSure API Error·Idempotency·Transaction Semantics 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
API transport 성공과 business/assurance 성공을 분리하고, retry·중복·부분실패가 권위 있는 상태를 왜곡하지 않도록 공통 API semantics를 정의한다.

## 2. 공통 응답 Envelope
- request_id
- correlation_id
- operation_id nullable
- result_state
- decision nullable
- error_code nullable
- retryable
- evidence_ref nullable
- current_version/epoch nullable
- warnings[]

HTTP 2xx는 Assurance PASS를 뜻하지 않는다. 예: 요청이 정상 처리되어 `HOLD`가 생성될 수 있다.

## 3. Error Class
- VALIDATION_ERROR
- AUTHENTICATION_ERROR
- AUTHORIZATION_ERROR
- OBJECT_OWNERSHIP_ERROR
- POLICY_BLOCK
- PRECONDITION_FAILED
- VERSION_CONFLICT
- IDEMPOTENCY_CONFLICT
- RESOURCE_LIMIT
- DEPENDENCY_UNAVAILABLE
- EXECUTION_FAILED
- OBSERVATION_INCOMPLETE
- EVIDENCE_COMMIT_FAILED
- RECONCILIATION_REQUIRED
- INTERNAL_ERROR

## 4. Retryable 규칙
retryable은 error code별 server policy로 결정한다. caller가 임의 판단하지 않는다.
- VERSION_CONFLICT: 최신 상태 reread 후 새 intent 필요
- AUTHORIZATION/POLICY: 자동 retry 금지
- dependency timeout: 조건부 retry
- evidence commit ambiguous: 먼저 reconciliation

## 5. Idempotency
Mutating API는 `Idempotency-Key`와 canonical intent digest를 결속한다.
같은 key + 같은 digest → 이전 결과 반환 가능.
같은 key + 다른 digest → `IDEMPOTENCY_CONFLICT`.

Key TTL이 만료돼도 external/final/financial effect의 historical duplicate detection은 별도 ledger로 유지한다.

## 6. Preconditions
보안·Final 관련 Write는 explicit precondition을 요구한다.
- expected aggregate version
- expected policy epoch
- expected target/requirement/currentness generation
- approval/authority generation

precondition mismatch는 자동 재시도하지 않고 새 판단을 요구한다.

## 7. Pagination Integrity
Evidence/Subject/Requirement population API는 pagination 중 denominator가 변하지 않도록 snapshot token 또는 population generation을 사용한다. 페이지별 최신조회 결과를 합쳐 exact denominator라고 주장하지 않는다.

## 8. Bulk API
Bulk operation은 항목별 결과를 보존한다.
- all-or-nothing 여부 명시
- partial success를 전체 PASS로 표시 금지
- 각 child operation_id/evidence 유지
- Critical child failure가 summary에서 숨겨지지 않음

## 9. Async API
202 Accepted는 실행 예정일 뿐 성공이 아니다.
- operation location
- lifecycle state
- poll/event 방식
- expiry/cancel semantics
을 반환한다.

## 10. External Webhook
Webhook은:
- signature
- event id
- occurred_at/received_at
- provider generation
- replay window
을 검증한다.
Event order inversion을 고려해 provider sequence/generation이 있으면 사용한다.

## 11. Sensitive Error Handling
오류 응답에 source snippet, secret, hidden corpus, private evidence location, key material을 포함하지 않는다. 내부 correlation ID로 상세 로그와 연결한다.

## 12. Negative Test
- 같은 idempotency key로 다른 Final candidate
- pagination 도중 requirement 추가
- HTTP 200/HOLD를 client가 PASS로 변환
- 202를 완료로 표시
- VERSION_CONFLICT 자동 blind retry
- bulk 99 PASS + 1 Critical FAIL을 전체 PASS로 요약
- webhook replay/out-of-order
- error body secret leak

## 13. 수용기준
- transport/business/assurance 상태가 서로 분리된다.
- duplicate mutation이 logical effect를 중복 생성하지 않는다.
- ambiguous failure는 성공으로 추정하지 않고 reconciliation한다.
- snapshot 없는 pagination을 authoritative denominator로 사용하지 않는다.
