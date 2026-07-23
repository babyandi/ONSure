# ONSURE First Applied Case Runbook v1

## 목적

ONSURE에서 최초로 "학습기에서 출발해서 검증 완료 후 실제 적용된 1건"을 만든다.

범위는 안전한 MVP 기준으로 제한한다.

```text
대상: ONSURE Core 내부 Validation Pack
제외: ORUDA Target 적용, 외부 제품 런타임 수정, Hidden answer key 기반 후보
목표: APPLIED_LOCKED 1건
```

## 1. 후보 선정

선정 가능한 후보:

| 후보 | 허용 |
|---|---:|
| Fixture 후보 | 예 |
| Rubric 후보 | 예 |
| Validator Rule 후보 | 예 |
| Policy Rule 후보 | 예 |
| Runtime source code 자동 수정 | 제한 |
| ORUDA Adapter 적용 | 아니오 |

후보는 다음 조건을 만족해야 한다.

```text
candidate_id
candidate_type
learner_source_receipt_id
learner_output_sha256
training_dataset_version
hidden_non_access_attestation
expected_improvement
rollback_plan
```

## 2. 검증 요청 생성

Validation Request에는 다음을 결속한다.

```text
queue_item_id
candidate_id
validator_version
policy_version
dataset_versions
golden_set_version
hidden_set_version
idempotency_key
```

## 3. 독립 검증

필수 검증:

| 검증 | 통과 조건 |
|---|---|
| Positive fixture | 모두 PASS |
| Negative fixture | 모두 BLOCK 또는 FAIL_EXPECTED |
| Golden regression | 기존 기준 불변 |
| Hidden minimum | 오염 없이 PASS |
| Reproducibility | 같은 조건 2회 동일 |
| Receipt verification | self hash와 chain 검증 |

검증기는 학습기 산출물을 그대로 믿지 않고 source evidence에서 재계산해야 한다.

## 4. Promotion Review

승격 승인 조건:

```text
validation_pass_receipt_id
reviewer_identity
approver_identity
reviewer != approver
scope = VALIDATION_PACK_APPLY
rollback_pointer
risk = LOW
```

## 5. Apply

적용은 둘 중 하나로 처리한다.

| 방식 | 설명 |
|---|---|
| Apply Commit | 저장소 main에 검증팩/정책/문서/계약 변경 병합 |
| Registry Activation | Stable selector가 새 artifact version을 참조 |

MVP 첫 건은 Apply Commit 방식이 가장 명확하다.

## 6. Post-Apply Verification

적용 후 다시 확인한다.

```text
active selector 또는 main commit이 promoted artifact를 참조하는가
기존 Golden이 깨지지 않았는가
새 후보가 Validator에서 실제 사용되는가
Rollback pointer가 유효한가
Receipt chain이 읽기 전용으로 재검증되는가
```

## 7. Applied Lock

다음 필드를 가진 Applied Receipt를 남긴다.

```json
{
  "applied_case_id": "ONSURE-APPLIED-0001",
  "state": "APPLIED_LOCKED",
  "candidate_id": "string",
  "application_class": "VALIDATION_PACK_APPLY",
  "validation_pass_receipt_id": "string",
  "promotion_receipt_id": "string",
  "apply_commit_sha_or_registry_version": "string",
  "post_apply_verification_receipt_id": "string",
  "rollback_pointer": "string",
  "applied_count_delta": 1
}
```

## 8. 운영 예약작업 보완

예약작업은 다음 단계별 결과를 보고해야 한다.

| 항목 | 보고 |
|---|---|
| learning_candidates_created | 후보 생성 건수 |
| validation_requests_created | 검증 요청 건수 |
| validation_passed | PASS Receipt 건수 |
| promotion_approved | Promotion Receipt 건수 |
| applied_locked | 실제 적용 건수 |
| blocked_reason_top3 | 적용되지 못한 주요 원인 |

이 구분이 없으면 학습 후보가 많아도 실제 적용 상태를 알 수 없다.

## 9. 첫 1건 실행 순서

```text
1. 저위험 Validation Pack 후보 1건 선정
2. Validation Request 생성
3. Validator 2회 실행
4. PASS Receipt 발행
5. Promotion Review 승인
6. Apply Commit 생성
7. Post-Apply Verification 실행
8. Applied Lock Receipt 발행
9. applied_count = 1 보고
```

## 10. 중단 조건

다음 중 하나라도 발생하면 HOLD다.

```text
Hidden overlap 의심
false pass 1건 이상
Golden regression 깨짐
reviewer와 approver 동일
Rollback pointer 없음
Apply 후 active selector 미참조
Receipt self verification 실패
```
