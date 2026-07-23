# ONSURE Learning to Application Pipeline v1

## 1. 검토 결론

현재 ONSURE 설계는 학습 후보를 안전하게 막는 구조는 갖췄지만, 후보가 실제 적용까지 가는 공식 경로가 약했다. 그래서 큐와 후보가 쌓여도 적용 건수는 0건으로 남는다.

보완 결론은 다음과 같다.

```text
Learning Candidate
-> Validation Request
-> Validator 독립 검증
-> PASS Receipt
-> Promotion Review
-> Apply Commit 또는 Stable Registry Activation
-> Post-Apply Verification
-> Applied Lock
```

이 경로를 통과한 경우만 "학습 적용 1건"으로 계산한다.

## 2. 왜 기존 구조에서 0건이 정상적으로 발생했는가

| 원인 | 설명 | 보완 |
|---|---|---|
| Learning Engine 권한 제한 | 학습기는 후보만 만들고 PASS/Gate를 열 수 없음 | 유지 |
| PASS Receipt 부족 | 검증 요청은 있으나 검증 완료 PASS가 없음 | Validator Runner 연결 |
| Promotion 경로 부족 | PASS 이후 Apply로 가는 계약이 약함 | Promotion Receipt 추가 |
| 적용 정의 부족 | 후보 적재와 실제 적용의 경계가 불명확 | Applied Count Rule 추가 |
| 활성 선택자 부재 | 후보가 실제 프로그램/검증팩에서 참조되지 않음 | active selector 또는 main commit 필수화 |

## 3. 적용 완료의 정의

다음 조건을 모두 만족해야 적용 1건이다.

| 조건 | 필수 여부 |
|---|---:|
| 학습 후보 ID 존재 | 필수 |
| 학습 후보 Source Receipt 존재 | 필수 |
| Validator 독립 검증 PASS | 필수 |
| Golden/Hidden 최소 검증 PASS | 필수 |
| 같은 조건 2회 재현성 PASS | 필수 |
| Promotion Approval Receipt | 필수 |
| Apply Commit 또는 Registry Version | 필수 |
| Stable selector가 후보 산출물을 실제 참조 | 필수 |
| Post-Apply Verification Receipt | 필수 |
| Rollback Pointer | 필수 |

다음은 적용으로 세지 않는다.

```text
후보 큐 등록
검증 요청 생성
NON_FINAL 실험 채택
PASS 없는 문서화
PR 열림 상태
Registry selector 미변경
Rollback 근거 없는 적용
```

## 4. 최소 상태 전이

```text
LEARNING_CANDIDATE
  -> VALIDATION_REQUESTED
  -> VALIDATION_RUNNING
  -> VALIDATION_PASSED
  -> PROMOTION_REVIEW
  -> PROMOTION_APPROVED
  -> SHADOW_APPLIED
  -> STABLE_APPLIED
  -> APPLIED_LOCKED
```

MVP에서는 Canary를 생략할 수 있지만 Shadow와 Stable의 의미는 유지한다.

| 상태 | 의미 |
|---|---|
| LEARNING_CANDIDATE | 학습기가 만든 개선 후보 |
| VALIDATION_REQUESTED | 검증기가 검증해야 할 요청 |
| VALIDATION_PASSED | 독립 검증 PASS Receipt 발행 |
| PROMOTION_APPROVED | 권한 분리된 승인 완료 |
| SHADOW_APPLIED | 운영 기준에는 영향 없는 비활성 적용 |
| STABLE_APPLIED | 실제 ONSURE 검증팩/정책/코드가 참조 |
| APPLIED_LOCKED | 재검증과 Rollback 근거까지 고정 |

## 5. 첫 적용 1건의 범위

첫 적용은 ORUDA Target 적용이 아니라 ONSURE Core 내부의 작은 검증팩 적용으로 잡는다.

권장 1호 적용 후보:

```text
VALIDATION_PACK_APPLY
- Fixture 후보 1건
- Rubric 후보 1건
- Validator Rule 후보 1건
- Policy Rule 후보 1건
```

선정 기준:

| 기준 | 이유 |
|---|---|
| 대상 범위가 작다 | 최초 적용 실패 시 영향 최소화 |
| Hidden 정답 접근이 없다 | 학습 오염 방지 |
| 기존 Golden을 깨지 않는다 | 회귀 위험 감소 |
| 증적을 만들기 쉽다 | 첫 Applied Receipt 확보 |
| Rollback이 단순하다 | Stable selector 이전 버전으로 되돌릴 수 있음 |

## 6. Executor 책임 보완

예약작업은 후보 생성에서 끝나면 안 된다. 최소한 다음 Job Type을 분리해야 한다.

| Job Type | 책임 |
|---|---|
| LEARNING | 후보 생성 |
| VALIDATION | 후보 검증 |
| PROMOTION_CHECK | PASS 후보의 승격 조건 확인 |
| APPLY | 승인된 후보를 검증팩/정책/코드에 반영 |
| POST_APPLY_VERIFY | 적용 후 재검증 |
| LOCK | Applied Receipt 고정 |

MVP에서 자동 코딩 적용은 제한해도 된다. 그러나 검증팩이나 정책 Registry 적용은 ONSURE 내부 적용으로 처리할 수 있어야 한다.

## 7. 실패사례 검증과의 차이

이 파이프라인은 실패사례 수집용 검증이 아니다.

| 구분 | 실패사례 검증 | 학습 적용 파이프라인 |
|---|---|---|
| 출발점 | 실패/사고/취약 사례 | 학습기가 만든 개선 후보 |
| 목적 | 막아야 할 위험 확인 | ONSURE 기준 자체 개선 |
| 최종 상태 | Finding 또는 HOLD | APPLIED_LOCKED |
| 적용 조건 | 필요 없음 | Apply Receipt와 Stable selector 필수 |

## 8. MVP 보완 완료 조건

```text
applied_count = 1
learning_candidate_id 존재
validation_pass_receipt 존재
promotion_receipt 존재
apply_commit_sha_or_registry_version 존재
post_apply_verification_receipt 존재
rollback_pointer 존재
active selector가 promoted artifact를 참조
```

이 조건이 충족되기 전까지는 후보가 아무리 많아도 적용 0건으로 보는 것이 맞다.
