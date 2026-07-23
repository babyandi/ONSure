# ORUDA Evidence Registry Schema

작성일: 2026-07-22

## 목적

Evidence Registry는 Failure, Fixture, Oracle, Harness, RCA, Regression, Blind Review, Lock 상태를 하나로 묶는 실행 증적 저장 형식이다.

## 최소 스키마

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| failure_id | string | yes | 실패 원형 ID |
| fixture_id | string | yes | 실행 가능한 Fixture ID |
| variant_type | enum | yes | Base, Boundary, Deception, Chain, Adversarial, Regression, Cross-program |
| severity | enum | yes | Critical, Major, Minor, Informational |
| primary_owner | string | yes | 1차 방어 프로그램 |
| secondary_guard | string | yes | 2차 방어선 |
| final_guard | string | yes | 최종 방어선 |
| oracle_id | string | yes | 적용 Oracle |
| harness_id | string | yes | 실행 Harness |
| input_hash | sha256 | yes | Fixture 입력 hash |
| policy_digest | sha256 | conditional | 정책 관련 Fixture일 때 필수 |
| receipt_digest | sha256 | conditional | Receipt 관련 Fixture일 때 필수 |
| run_id | string | yes | 실행 ID |
| run_number | integer | yes | 반복 실행 번호 |
| expected_result | enum | yes | EXPECTED_FAIL 또는 CLEAN |
| actual_result | enum | yes | 실제 프로그램 결과 |
| verdict | enum | yes | EXPECTED_FAIL, UNEXPECTED_PASS, CLEAN, ERROR, SKIPPED, INCONCLUSIVE |
| rca_id | string | conditional | UNEXPECTED_PASS일 때 필수 |
| fix_ref | string | conditional | 수정 발생 시 필수 |
| regression_run_1 | enum | conditional | 수정 후 필수 |
| regression_run_2 | enum | conditional | 수정 후 필수 |
| blind_review_id | string | conditional | Quality Critical일 때 필수 |
| lock_status | enum | yes | OPEN, BLOCKED, RCA_REQUIRED, REGRESSION_REQUIRED, LOCK_CANDIDATE |

## JSON 예시

```json
{
  "failure_id": "FAIL-CHAIN-EVID-001",
  "fixture_id": "FX-CHAIN-EVID-001-BASE",
  "variant_type": "Chain",
  "severity": "Critical",
  "primary_owner": "OReport",
  "secondary_guard": "OTester",
  "final_guard": "OAudit",
  "oracle_id": "ORC-CHAIN-001",
  "harness_id": "HARNESS-CHAIN-001",
  "input_hash": "sha256:<pending>",
  "policy_digest": "sha256:<pending>",
  "receipt_digest": "sha256:<pending>",
  "run_id": "RUN-20260722-001",
  "run_number": 1,
  "expected_result": "EXPECTED_FAIL",
  "actual_result": "BLOCKED",
  "verdict": "EXPECTED_FAIL",
  "rca_id": null,
  "fix_ref": null,
  "regression_run_1": null,
  "regression_run_2": null,
  "blind_review_id": null,
  "lock_status": "OPEN"
}
```

## 완료 금지 규칙

```text
fixture_id 없음 -> 완료 불가
harness_id 없음 -> 완료 불가
oracle_id 없음 -> 완료 불가
UNEXPECTED_PASS인데 rca_id 없음 -> 완료 불가
수정했는데 regression 2회 없음 -> 완료 불가
Quality Critical인데 blind_review_id 없음 -> 완료 불가
SKIPPED/NOT_RUN/INCONCLUSIVE -> 완료 집계 금지
```
