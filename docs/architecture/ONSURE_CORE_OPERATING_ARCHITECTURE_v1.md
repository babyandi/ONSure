# ONSURE Core Operating Architecture v1

## 1. 설계 결정

ONSURE은 ORUDA 내부 기능이 아니라 독립 상용 검증 프로그램이다. 학습기와 검증기는 ONSURE 안에 함께 포함하되, 내부 엔진·데이터·권한·Receipt 체인은 분리한다.

```text
제품 단위       ONSURE 하나
실행 단위       Standalone Core 우선
내부 구조       Learning Engine / Validator Engine / Executor 분리
대상 연결       Target Adapter / Validation Pack
ORUDA Adapter   MVP 이후
```

핵심 원칙:

1. 학습기는 개선 후보를 만들 수 있지만 Gate를 열 수 없다.
2. 검증기는 통과·차단 판단을 수행하지만 학습 데이터 생성자가 될 수 없다.
3. Executor는 Queue를 소비하고 상태와 Receipt를 남기는 실행 책임만 가진다.
4. Hidden/Golden 데이터는 Dataset Registry에서 분리·봉인한다.
5. 모든 판정은 Trace, Policy, Dataset, Tool, Prompt, Model, Source digest에 결속한다.
6. 증거가 없거나 부분 증거만 있으면 PASS가 아니라 HOLD 또는 FAIL_CLOSED다.

## 2. 최상위 Plane

| Plane | 책임 | MVP 반영 |
|---|---|---|
| Control Plane | Queue, 권한, 정책, 상태 전이, 실행 제한 | 구현 |
| Execution Plane | Executor Loop, Harness Runner, Sandbox, 재시도·복구 | 구현 |
| Validation Plane | Validator Engine, Fixture, Golden/Hidden, Regression | 기본 구현 |
| Evidence Plane | Receipt, SHA-256, Trace, Source/Artifact Lock, Replay Snapshot | 구현 |
| Governance Plane | PASS/FAIL/HOLD, Promotion, Rollback, 승인 분리 | 기본 Gate |
| Learning Plane | 실패 분석, Fixture 후보, Rubric 후보, 개선 후보 | 설계 우선 |
| Observability Plane | Dashboard, Drift, SLA, 오래된 Queue 감지, Incident Replay | 설계+최소 Trace |

## 3. Core Runtime Flow

```text
Intake/API/CLI
  -> Target / Job Registration
  -> Queue Ledger
  -> Executor Lease
  -> RUNNING State Receipt
  -> Harness Runner
  -> Validator Engine
  -> Evidence / Trace / Receipt Chain
  -> PASS / FAIL / HOLD Gate
  -> Report / Dashboard
  -> Learning Feedback Queue
```

Learning Feedback Queue는 검증 실패와 운영 사고를 학습 후보로 전환한다. 단, 학습 후보는 다시 Validator와 Golden/Hidden 검증을 통과하기 전까지 운영 기준이 될 수 없다.

## 4. 내부 엔진

### Executor Engine

필수 상태 전이:

```text
READY -> RUNNING
RUNNING -> DONE
RUNNING -> RETRY
RUNNING -> HOLD
RETRY -> RUNNING
HOLD -> READY_AFTER_RCA
```

필수 통제:

- queue lease
- idempotency key
- duplicate consume block
- checkpoint resume
- max retry without RCA
- stale queue detection
- missing tool fail-closed
- partial evidence fail-closed

### Validator Engine

필수 기능:

- static validation
- runtime and E2E validation
- security and privacy validation
- supply-chain validation
- prompt injection and reference poisoning validation
- false pass / false fail / nondeterminism calibration
- Golden/Hidden/Regression set evaluation
- independent verifier/audit receipt validation

Validator는 Learning Engine의 제안을 신뢰하지 않고 독립 재계산한다.

### Learning Engine

허용 기능:

- 실패 Receipt 분석
- RCA clustering
- Failure Mode candidate 생성
- Fixture/Harness/Oracle candidate 생성
- Rubric improvement candidate 생성
- Remediation pattern candidate 생성
- Drift signal candidate 생성

금지 기능:

- PASS 결정
- Promotion Gate 개방
- Hidden answer key 접근
- Validator Rubric 무승인 변경
- 자기 학습 결과를 직접 Stable 기준으로 승격

## 5. Registry

| Registry | 내용 |
|---|---|
| Target Registry | 검증 대상, Adapter, 정책 Profile, 실행 Profile |
| Queue Ledger | READY/RUNNING/DONE/RETRY/HOLD 상태와 lease |
| Dataset Registry | Training/Validation/Hidden/Golden/Regression 세트와 SHA |
| Policy Registry | Policy-as-Code, 금지조건, Gate 조건, 승인권한 |
| Rubric Registry | 평가 기준 버전, 변경 diff, 승인 Receipt |
| Model/Prompt/Tool Registry | 모델·프롬프트·도구·검증 코드 버전 |
| Receipt Ledger | append-only evidence chain |
| Incident Replay Ledger | 사고 입력·환경·도구·결과 재현 Snapshot |
| Promotion Registry | Candidate/Shadow/Canary/Stable/Locked 전이 |

## 6. Harness 구조

MVP Harness:

- Fixture Harness
- Golden/Hidden 최소 Harness
- Regression Harness
- Security Negative Harness
- Receipt Verification Harness

MVP 이후 Harness:

- adversarial fixture generator
- drift monitor harness
- rollback drill harness
- independent OTester/OAudit equivalent harness
- visual/document quality blind review harness

## 7. MVP 구현 경계

MVP에서 반드시 구현한다.

```text
Queue Ledger
Executor Loop
Basic Harness Runner
Validator Engine
Receipt/Evidence Chain
Dataset Registry minimum
Policy-as-Code minimum
Golden/Hidden minimum set
PASS/FAIL/HOLD Gate
Trace Snapshot
```

MVP 이후로 둔다.

```text
Learning Engine full automation
Dashboard full UX
Canary/Rollback advanced drill
Adversarial automatic generation
ORUDA Adapter
Enterprise approval workflow
```

## 8. ORUDA Adapter 후순위 원칙

ORUDA는 ONSURE의 첫 검증 대상이 될 수 있지만 Core 의존성이 아니다.

```text
ONSURE Core 먼저
-> 범용 Queue/Executor/Validator/Receipt 먼저
-> ORUDA Adapter는 나중에 Target Pack으로 연결
```
