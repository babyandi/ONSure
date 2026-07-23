# ONSURE Learning and Validation Engine Design v1

## 1. 결론

ONSURE에는 학습기와 검증기를 모두 포함한다. 그러나 두 엔진은 같은 판정 로직으로 섞지 않는다.

```text
ONSURE Product
  -> Learning Engine
  -> Validator Engine
  -> Executor Engine
  -> Evidence / Receipt Engine
  -> Governance Gate
```

제품은 하나이고 엔진은 분리한다.

## 2. 책임 분리

| 엔진 | 책임 | 금지 |
|---|---|---|
| Learning Engine | 실패 분석, 개선 후보, Fixture 후보, Rubric 후보 생성 | PASS 결정, Gate 개방, Hidden 정답 접근 |
| Validator Engine | 검증 실행, PASS/FAIL/HOLD 판정, False Pass/Fail 측정 | 학습 후보를 무검증 승격 |
| Executor Engine | Queue 소비, 상태 전이, Harness 실행, Receipt 생성 | 검증 결과 임의 수정 |
| Evidence Engine | Receipt chain, trace, sha, replay snapshot | 증거 없는 결론 생성 |
| Governance Engine | Promotion/Rollback, 권한 분리, 승인 Gate | learner self-approval |

## 3. 데이터 분리

| 데이터 | 용도 | 접근 |
|---|---|---|
| Training Set | 학습 후보 생성 | Learning Engine 허용 |
| Validation Set | 후보 1차 평가 | Validator Engine 허용 |
| Hidden Test Set | 오염 방지 평가 | 제한 접근 |
| Golden Set | 기준 회귀 평가 | 봉인/버전관리 |
| Regression Set | 재발 방지 | Validator/Executor 허용 |
| Incident Replay Set | 사고 재현 | 승인된 재검증에서만 사용 |

필수 규칙:

- Training/Validation/Hidden/Golden overlap 금지
- Dataset byte SHA-256 봉인
- Hidden answer key는 Learning Engine이 직접 읽을 수 없음
- Dataset 변경은 Rubric/Policy 변경 Receipt와 함께 승인 필요

## 4. Executor Queue Contract

상태:

```text
READY
RUNNING
DONE
RETRY
HOLD
CANCELLED
EXPIRED
```

필수 필드:

```json
{
  "queue_item_id": "string",
  "target_id": "string",
  "job_type": "VALIDATION | LEARNING | REGRESSION | REPLAY | PROMOTION_CHECK",
  "state": "READY",
  "priority": 0,
  "lease_id": null,
  "idempotency_key": "sha256",
  "attempt_count": 0,
  "max_retry": 2,
  "required_policy_version": "string",
  "required_dataset_versions": [],
  "created_at": "iso8601",
  "expires_at": "iso8601"
}
```

전이마다 transition receipt가 필요하다.

## 5. Validation Loop

```text
Queue READY
  -> lease acquire
  -> RUNNING receipt
  -> fixture materialization
  -> harness execution
  -> oracle evaluation
  -> validator decision
  -> independent receipt check
  -> DONE / RETRY / HOLD
```

PASS 조건:

- positive cases pass
- negative cases fail
- mutation/adversarial cases blocked
- false pass count = 0 for MVP critical gate
- false fail count = 0 for MVP critical gate
- same-condition 2x deterministic where required
- all required evidence present

## 6. Learning Feedback Loop

```text
FAIL/HOLD Receipt
  -> RCA candidate
  -> Failure Mode clustering
  -> Fixture/Oracle candidate
  -> Rubric candidate
  -> Validation Request
  -> Validator Engine 재검증
```

Learning Output은 항상 CANDIDATE다. Stable 기준으로 승격하려면 Promotion Gate를 통과해야 한다.

## 7. Hardening Backlog 반영

| ID | ONSURE 설계 반영 |
|---|---|
| HV-001 | durable Queue Ledger |
| HV-002 | state transition Executor |
| HV-003 | independent verifier/audit receipt |
| HV-004 | same-condition 2x reproducibility |
| HV-005 | false pass/false fail/nondeterminism calibration |
| HV-006 | Dataset Registry byte seal |
| HV-007 | source authority and license receipt |
| HV-008 | candidate/shadow/canary/stable/locked promotion |
| HV-009 | rollback and last-known-good evidence |
| HV-010 | lane metrics and stale queue alert |
| HV-011 | prompt injection/reference poisoning fixtures |
| HV-012 | model/prompt/tool/validator version snapshot |
| HV-013 | budget, loop, backpressure |
| HV-014 | reviewer/approver identity separation |
| HV-015 | queue lease and idempotency |
| HV-016 | tamper-evident receipt chain |
| HV-017 | rubric version and approval |
| HV-018 | retention, redaction, deletion tombstone |
| HV-019 | missing tool/partial evidence fail-closed |
| HV-020 | stale queue and expired evidence detection |

## 8. MVP 판정
## 8. 학습 후보 적용 파이프라인

학습 후보가 실제 ONSURE 기준으로 적용되려면 다음 경로를 통과해야 한다.

```text
LEARNING_CANDIDATE
  -> VALIDATION_REQUESTED
  -> VALIDATION_PASSED
  -> PROMOTION_APPROVED
  -> STABLE_APPLIED
  -> APPLIED_LOCKED
```

적용 완료는 PASS와 다르다. PASS는 검증 결과이고, 적용은 ONSURE의 실제 검증팩, 정책, 코드, 또는 Stable Registry selector가 해당 산출물을 참조하는 상태다.

필수 조건:

| 조건 | 설명 |
|---|---|
| Validation PASS Receipt | Validator가 독립 재계산 |
| Promotion Receipt | reviewer/approver 분리 |
| Apply Commit 또는 Registry Version | 실제 활성 기준 변경 |
| Post-Apply Verification | 적용 후 회귀 검증 |
| Rollback Pointer | 이전 안정 기준으로 복귀 가능 |
| Applied Lock Receipt | 적용 건수 증가의 유일 근거 |

따라서 현재 적용 0건은 후보 부족이 아니라 이 파이프라인 미실행 상태를 의미한다.


MVP는 완전 자동학습 제품이 아니다. MVP의 목표는 다음이다.

```text
Queue를 읽는다
Executor가 실제 실행한다
Harness가 검증을 돌린다
Receipt를 남긴다
PASS / FAIL / HOLD를 판단한다
Golden/Hidden 최소 세트로 오염을 막는다
```
