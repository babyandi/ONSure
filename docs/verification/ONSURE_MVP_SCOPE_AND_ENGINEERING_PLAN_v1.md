# ONSURE MVP Scope and Engineering Plan v1

## 1. 원칙

설계에는 Core 1~7번을 모두 반영한다. 구현은 MVP 범위부터 개발한다. ORUDA Adapter는 후순위다.

```text
설계 반영: Queue, Executor, Validator, Receipt, Golden/Hidden, Learning, Promotion/Rollback, Dashboard
MVP 구현: Queue, Executor, Validator, Receipt, Golden/Hidden minimum, Basic Gate, Trace
후순위: ORUDA Adapter, full Learning automation, full Dashboard, advanced Rollback/Canary
```

## 2. MVP 구현 범위

| 항목 | MVP 구현 |
|---|---|
| Queue Ledger | READY/RUNNING/DONE/RETRY/HOLD |
| Executor Loop | lease, idempotency, retry, checkpoint |
| Harness Runner | fixture 실행, 결과 수집 |
| Validator Engine | 기본 rule/oracle 판정 |
| Receipt/Evidence | append-only chain, sha256, trace |
| Dataset Registry | Golden/Hidden 최소 분리 |
| Policy-as-Code | Gate 조건과 fail-closed rule |
| Promotion Gate | PASS/FAIL/HOLD 기본 판정 |
| Trace Snapshot | input/source/model/prompt/tool/runtime snapshot |

## 3. 설계만 우선 반영할 항목

| 항목 | 이유 |
|---|---|
| Learning Engine full automation | 검증 오염 방지 후 단계적 적용 필요 |
| Dashboard | MVP 이후 운영 UX로 확장 |
| Drift Monitor | 초기에는 Metric contract만 반영 |
| Rollback Drill | Last-known-good 계약 먼저 반영 |
| Canary Promotion | Gate model 먼저 반영 |
| Adversarial Generator | 수동 fixture pack 이후 자동화 |
| ORUDA Adapter | ONSURE Core 독립성 확보 후 연결 |

## 4. 1차 개발 순서

1. Contract and schema skeleton
2. Queue Ledger and state transition Executor
3. Receipt envelope and hash chain
4. Fixture/Harness runner
5. Validator Engine minimum
6. Dataset Registry minimum
7. Golden/Hidden fixture pack
8. PASS/FAIL/HOLD Gate
9. Read-only verifier
10. MVP runbook and report template

## 5. MVP 완료 조건
## 5. 추가 MVP 보완: 첫 Applied Case

MVP는 단순히 Queue를 읽고 PASS/FAIL/HOLD를 판단하는 데서 끝나면 안 된다. 학습기가 만든 저위험 후보 1건을 검증하고 실제 ONSURE Core 기준에 반영해 `APPLIED_LOCKED`까지 만들어야 한다.

첫 적용 범위:

```text
Application Class: VALIDATION_PACK_APPLY
대상: Fixture / Rubric / Validator Rule / Policy Rule 중 1건
제외: ORUDA Target 적용, 외부 제품 런타임 변경, Hidden answer key 기반 후보
```

완료 조건:

```text
learning_candidate_id 존재
validation_pass_receipt 존재
promotion_receipt 존재
apply_commit_sha_or_registry_version 존재
post_apply_verification_receipt 존재
rollback_pointer 존재
applied_count = 1
```

적용 건수로 세면 안 되는 것:

```text
학습 후보 큐 등록
검증 요청 생성
NON_FINAL 실험 채택
열린 PR
Stable selector가 참조하지 않는 산출물
Rollback 근거 없는 변경
```


```text
Queue item이 READY에서 RUNNING으로 lease 획득
동일 item 중복 실행 차단
Harness 실행 결과 수집
Validator PASS/FAIL/HOLD 판정
Receipt self hash 생성
previous receipt hash chain 유지
Golden/Hidden 데이터 분리 확인
missing evidence는 fail-closed
동일 입력 2회 결과 hash 비교
최종 report가 evidence에 결속
```

## 6. 기간 기준

| 단계 | 예상 |
|---|---|
| MVP | 6~8주 |
| 운영 베타 | 10~14주 |
| 상용화 수준 | 4~6개월 |

Learning Engine은 MVP의 중심이 아니라 후속 고도화다. MVP는 실제 검증 실행과 증적 체인부터 완성한다.
