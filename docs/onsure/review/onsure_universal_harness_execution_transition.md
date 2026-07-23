# ONSURE Universal Harness Execution Transition

작성일: 2026-07-23

## 목적

이 문서는 반복 문서검토를 멈추고 ONSURE 범용 실행 검증으로 넘어가기 위한 전환 기준을 고정한다.

현재 결론은 다음과 같다.

```text
문서 반복검토: STOP_ALLOWED
실행 하네스: STOP_NOT_ALLOWED
ONSURE Final Lock: NOT_ALLOWED
현재 상태: STOP_DOCUMENT_REPETITION_AND_START_EXECUTION
```

## 전환 원칙

ONSURE은 모든 프로그램의 실제 사용 가능성을 증적 기반으로 판정한다.

```text
기능 테스트
+ 품질 평가
+ UI/UX 평가
+ 예외/부하/보안 검증
+ 설계-코드 일치성 검증
+ 코드 품질/리뷰 검증
+ 운영 준비도 검증
+ 증적 기반 최종 판정
```

## 실행 전환 순서

| 순서 | 작업 | 산출물 |
|---:|---|---|
| 1 | ONSURE 범용 검증 기준 확정 | verification_profile, risk_tier, scope_digest |
| 2 | 각 검증 축별 Harness 설계 | unit, integration, e2e, acceptance, security, performance, resource, review harness |
| 3 | Fixture 생성 | positive, negative, adversarial, boundary, load, recovery fixture |
| 4 | Oracle 생성 | expected_behavior, forbidden_behavior, quality_threshold, evidence_required |
| 5 | Evidence/Receipt 저장 구조 생성 | command, input_hash, output_hash, log_hash, metric, artifact_hash, replay instruction |
| 6 | 2회 이상 독립 실행 | run_1, run_2, independence receipt |
| 7 | 실패 건 RCA | failure_id, root_cause, fix_or_rule_change |
| 8 | 전체 회귀검증 | regression_1, regression_2, lock_set_id |
| 9 | Blind Review와 독립 리뷰 | human_review_receipt, independent_review_receipt |
| 10 | 종료 후보 판단 | final_candidate_decision, not final lock |

## 검증 축별 최소 Harness

| 검증 축 | 최소 Harness |
|---|---|
| 요구사항 | requirement-to-test trace validator |
| 설계 | architecture and state-transition review harness |
| 코드 | static analysis, lint, type check, complexity, dead-code scanner |
| 단위 | unit test runner with positive/negative/boundary fixture |
| 통합 | DB/API/external connector integration runner |
| E2E | user goal full-flow runner |
| 인수 | acceptance criteria runner |
| 보안 | auth/authz/input/file/secret/dependency scanner |
| 개인정보 | redaction/export/log-retention verifier |
| 성능 | latency/throughput benchmark runner |
| 부하 | concurrent user and data-volume runner |
| 자원 | memory/connection/file-handle/thread leak monitor |
| 데이터 | transaction, rollback, concurrent-write integrity runner |
| UI/UX | usability, accessibility, error-message, visual-quality oracle |
| 운영성 | deploy/config/migration/backup/recovery/runbook rehearsal |
| 리뷰 | code/design/security review closure checker |
| 증적 | evidence registry verifier and replay checker |

## 판정 체계

```text
PASS        실제 사용 가능
FAIL        결함 확인
BLOCKED     필수 증적 부족 또는 검증 불가
NOT_RUN     아직 검증 미실행
RISK_ACCEPT 조건부 허용, 잔여 위험 명시
```

## 종료 후보 조건

아래 조건을 모두 만족해야만 종료 후보가 될 수 있다.

```text
NOT_RUN = 0
BLOCKED = 0
CRITICAL_DEFECT = 0 for 2 consecutive runs
MAJOR_DEFECT = 0 for 2 consecutive runs
RCA_PENDING = 0
REGRESSION_RUNS = 2/2 clean
EVIDENCE_REQUIRED_FIELDS_COMPLETE = true
INDEPENDENT_REPLAY_COMPLETED = true
HUMAN_BLIND_REVIEW_COMPLETED = true
FINAL_LOCK_AUTOMATIC = false
```

## 현재 차단 상태

```text
UNIT_TEST_EXECUTION_NOT_RUN
INTEGRATION_TEST_EXECUTION_NOT_RUN
E2E_TEST_EXECUTION_NOT_RUN
ACCEPTANCE_TEST_EXECUTION_NOT_RUN
SECURITY_AUTHORIZATION_PRIVACY_NOT_RUN
PERFORMANCE_LOAD_STRESS_NOT_RUN
RESOURCE_LEAK_TEST_NOT_RUN
STATIC_ANALYSIS_NOT_RUN
CODE_REVIEW_CLOSURE_NOT_PROVEN
OPERABILITY_RECOVERY_REHEARSAL_NOT_RUN
HARNESS_META_TEST_NOT_RUN
EVIDENCE_REGISTRY_NOT_POPULATED
INDEPENDENT_REPLAY_NOT_RUN
```

## 다음 작업명

```text
ONSURE Universal Verification Harness v1
```

이 작업은 문서 보강이 아니라 실행 가능한 Fixture, Harness, Oracle, Evidence Registry, RCA, Regression 체계를 만드는 작업이다.
