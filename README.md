# ONSURE

ONSURE은 일반인·개발자·제품팀이 AI로 만든 AI 프로그램과 일반 프로그램을 검증하고, 오류·보안·정책·구조·실행 문제를 찾아내며, RCA·개선·검증 리포트·재검증까지 수행하는 **독립 상용 Software Validation Platform**입니다.

ONSURE은 ORUDA 전용 검증기가 아닙니다. ORUDA Adaptive Validation Master의 RCA·Failure Mode·Fixture·Harness·Oracle·Receipt·Regression Lock 구조는 범용 Validator Engine 설계 재료로 흡수하며, ORUDA는 ONSURE 독립 제품 완성 후 등록되는 **1호 검증 대상**입니다.

## 2026-07-24 설계 반영

이번 기준선은 ONSURE을 독립 프로그램으로 유지하면서 학습기·검증기·Executor·Evidence·Governance를 Core 설계에 반영한다.

```text
ONSURE Core
-> Queue Ledger / Executor Loop
-> Harness Runner
-> Validator Engine
-> Receipt / Evidence / Trace
-> Dataset Registry / Policy-as-Code
-> Learning Feedback Engine
-> Promotion / Rollback Gate
-> Dashboard / Drift / Incident Replay
```

반영 원칙:

- 제품은 하나이고 내부 엔진은 분리한다.
- Learning Engine은 개선 후보를 만들지만 PASS나 Gate를 결정하지 않는다.
- Validator Engine은 독립 재계산과 Golden/Hidden 검증으로 통과·차단을 판단한다.
- Executor는 실제 Queue를 소비하고 READY/RUNNING/DONE/RETRY/HOLD 전이 Receipt를 남긴다.
- Trace, Dataset Registry, Policy-as-Code, Model/Prompt/Tool Registry, Incident Replay를 설계 기준에 포함한다.
- ORUDA Adapter는 후순위 Target Pack으로 둔다.

## 제품 기준선

## 2026-07-24 추가 보완: 학습 적용 0건 원인과 적용 파이프라인

추가 검토 결과, 기존 설계는 학습 후보의 무검증 자동 적용을 잘 차단했지만, 검증 완료 후보를 실제 적용으로 넘기는 경로가 명확하지 않았다. 따라서 다음 계약을 추가한다.

```text
Learning Candidate
-> Validation Request
-> Validator PASS Receipt
-> Promotion Approval
-> Apply Commit or Stable Registry Activation
-> Post-Apply Verification
-> Applied Lock
```

적용 1건은 다음을 모두 만족할 때만 계산한다.

- 후보가 학습기에서 출발했다.
- Validator가 독립 재계산으로 PASS Receipt를 발행했다.
- Promotion Receipt와 권한 분리가 존재한다.
- Apply Commit 또는 Stable Registry Version이 존재한다.
- 실제 active selector가 promoted artifact를 참조한다.
- Post-Apply Verification Receipt와 Rollback Pointer가 존재한다.

따라서 후보 큐, 검증 요청, NON_FINAL 실험 채택, 열린 PR은 적용 건수로 세지 않는다. MVP의 첫 적용 목표는 ORUDA Target이 아니라 ONSURE Core 내부 Validation Pack에 대해 `APPLIED_LOCKED 1건`을 만드는 것이다.


```text
Product          INDEPENDENT_COMMERCIAL_SOFTWARE_VALIDATION_PLATFORM
Target Scope     AI_APPLICATION + GENERAL_SOFTWARE
Core Runtime     STANDALONE
First Target     ORUDA / PLANNED
Future Embed     ONSURE Agent or ONSURE Validation Module
Implementation   IMPLEMENTATION_READY
Execution        NOT_RUN
Gate             HOLD
PR               DRAFT
```

## 주요 사용자

- AI 코딩 도구로 프로그램을 만든 일반 사용자
- 소프트웨어 개발자와 제품팀
- 보안·품질·감사·내부통제 조직
- 여러 프로젝트를 동일 기준으로 검증하려는 기업

## 검증 대상

- AI Application과 Agentic System
- Web·API·Desktop·Mobile Application
- 일반 업무 프로그램과 자동화 Workflow
- Source repository, package, binary, container, configuration
- Prompt·tool permission·model response를 포함한 AI 실행 구조

## 범용 Validator Engine

```text
Target Registration
-> Target Adapter / Intake
-> Immutable Source & Artifact Lock
-> Requirement / Policy / Architecture Reconstruction
-> Static Code / Configuration Validation
-> Runtime & E2E Harness
-> Security / Privacy / Supply-chain Validation
-> Failure Mode Registry
-> RCA Engine
-> Fixture Synthesizer & Registry
-> Oracle Evaluation
-> Remediation / Approved Patch
-> Regression Lock
-> Independent Revalidation
-> Validation Report
-> Final Lock / Receipt Ledger / Final Decision
```

## 핵심 산출물

- 오류·위험·누락 Finding
- 재현 가능한 Failure Mode
- 기술적 Root Cause Analysis
- 정상·경계·적대·장애 Fixture
- 실행 Harness와 독립 Oracle 판정
- 최소 수정안·지속 가능한 개선안·승인 필요사항
- 패치 전후 비교와 Regression Lock
- 일반 사용자용 요약 리포트
- 개발자용 재현·RCA·개선 리포트
- 감사 가능한 Receipt·Evidence·Final Validation Report

## 제품 독립성

- ORUDA와 별도 저장소·별도 제품·별도 Runtime
- ORUDA가 없어도 ONSURE Standalone Core 실행
- 모든 대상은 Target Adapter로 연결
- Target는 ONSURE Policy·Oracle·Final Decision을 변경할 수 없음
- Target 내부 결과는 claim이며 독립 재계산 전까지 신뢰하지 않음
- Embedded Agent/Module도 portable Receipt를 내보내야 함
- ORUDA 제거·장애가 ONSURE의 다른 Target 검증을 중단시키지 않음

## ORUDA 1호 검증 대상

ONSURE 독립 제품을 먼저 보강한 뒤 `contracts/validation-target-registry.v1.json`에 ORUDA를 1호 Target으로 등록합니다.

```text
ONSURE Standalone
-> ORUDA Target Adapter
-> ORUDA Source / Policy / Runtime Evidence
-> Generic Validator Engine
-> ORUDA Target Fixture Pack
-> Independent Harness / Oracle / Receipt / Report
```

장기적으로 ORUDA 내부에 ONSURE Agent 또는 ONSURE Validation Module을 이식할 수 있지만, ONSURE 자체는 단독 판매·배포·실행 가능한 제품 구조를 유지합니다.

## ORUDA 설계 재료 흡수

다음 구조는 ORUDA 전용 구현이 아니라 ONSURE 소유의 범용 계약으로 일반화합니다.

- RCA
- Failure Mode
- Fixture
- Harness
- Oracle
- Receipt
- Regression Lock

ORUDA 경로·Agent·Runtime·정책 Authority는 ONSURE Core 필수 의존성이 아닙니다.

## 현재 구현 상태

설계·계약·Validator·A01~A20 Fixture·보안 Finding Gate·Standalone Runner·Independent Verifier/Audit reference provider·서명 Receipt·Final Lock·Ledger·Final Receipt·재검증 CLI·Devcontainer 실행 환경이 구현되었습니다.

실제 JDK 17/Maven 실행 증거가 없으므로 PASS는 금지됩니다.

## 실행 환경

```text
JDK 17
Maven
Git
sha256sum
cmp
clean tracked worktree
```

Codespace 또는 Dev Container:

```bash
bash scripts/prepare-assurance-environment.sh
```

Issue #4 최종 실행:

```bash
bash scripts/execute-issue-4-final-gate.sh
```

성공 기준:

```text
LOCAL_ASSURANCE_TWICE_PASS
ISSUE4_FINAL_GATE_EVIDENCE_READY
```

## Preflight

```bash
bash scripts/preflight-local-assurance.sh
```

Preflight는 실행 환경, Product Scope, Target Registry, State·Lane·Receipt·Run Context·Source Lock·Final Receipt·Security Finding 계약, A01~A20 Fixture, immutable commit과 clean worktree를 확인합니다.

## 단일 전체 Runner

```bash
bash scripts/run-local-assurance.sh
```

```text
Preflight
-> Run Context
-> Source Lock
-> Fixture / Security Snapshot
-> Maven/JUnit regression-1
-> clean target
-> Maven/JUnit regression-2
-> Summary·Class Hash·Fixture Report 비교
-> Independent Verifier Receipt
-> Independent Audit Receipt
-> Final Lock
-> Append-only Ledger
-> Final Receipt와 자기검증
```

## 전체 Runner 연속 2회

```bash
bash scripts/run-local-assurance-twice.sh
```

- 각 Runner 내부 회귀 2회 동일성
- 전체 Runner 2회의 Source Lock·Snapshot·결정적 Evidence 동일성
- 두 실행 읽기 전용 재검증
- 후속 Ledger append 이후 과거 per-run Receipt 재검증

## 읽기 전용 재검증

```bash
bash scripts/verify-local-assurance.sh receipts/local/<run-directory>
```

## 실행 결과 요약

```bash
bash scripts/summarize-local-assurance.sh --verify receipts/local/<run-directory>
```

## 주요 계약

- 제품 범위: `contracts/product-scope.v1.json`
- 검증 대상 Registry: `contracts/validation-target-registry.v1.json`
- 검증 Lane: `contracts/assurance-lanes.v1.json`
- 상태 모델: `contracts/state-machine.v1.json`
- 일반 Receipt: `contracts/receipt-envelope.v1.schema.json`
- Independent Agent Receipt: `contracts/local-agent-receipt.v1.schema.json`
- Run Context: `contracts/local-run-context.v1.schema.json`
- Source Lock: `contracts/source-lock.v1.schema.json`
- Final Receipt: `contracts/local-final-receipt.v1.schema.json`
- Security Finding Register: `contracts/security-findings.v1.schema.json`
- Core Operating Architecture: `contracts/core-operating-architecture.v1.json`
- Learning/Validation Engine: `contracts/learning-validation-engine.v1.json`

## 기준 문서

- `docs/architecture/ONSURE_GENERAL_VALIDATION_PLATFORM_v1.md`
- `docs/architecture/ONSURE_ASSURANCE_ARCHITECTURE_v1.md`
- `docs/architecture/ONSURE_CORE_OPERATING_ARCHITECTURE_v1.md`
- `docs/architecture/ONSURE_LEARNING_VALIDATION_ENGINE_DESIGN_v1.md`
- `docs/verification/ONSURE_MVP_SCOPE_AND_ENGINEERING_PLAN_v1.md`
- `docs/research/ONSURE_EXTERNAL_PRODUCT_AND_FAILURE_REVIEW_v1.md`
- `docs/security/ONSURE_SECURITY_REMEDIATION_v1.md`
- `docs/verification/ONSURE_DESIGN_VALIDATION_PLAN_v1.md`
- `docs/verification/ONSURE_EXECUTION_ENVIRONMENT_v1.md`
- `docs/verification/ONSURE_LOCAL_EXECUTION_RUNBOOK_v1.md`
- `docs/verification/ONSURE_LOCAL_EXECUTION_RESULT_TEMPLATE_v1.md`

## Final Gate

```text
Product Scope·Target Registry 계약 일치
Preflight PASS
JDK 17 compile PASS
JUnit 전체 PASS
A01~A20 예상 판정 일치
단일 Runner 내부 회귀 2회 동일
전체 Runner 연속 2회 PASS
Independent Verifier/Audit Receipt PASS
Security Finding Gate PASS
Final Lock·Ledger·Final Receipt PASS
두 실행 읽기 전용 재검증 PASS
Critical/High 미해결 0건
```

하나라도 미실행이거나 증거가 누락되면 Gate는 `HOLD`입니다.

승인된 commit SHA와 해당 실행의 Target Profile·Source Lock·Fixture·Oracle·Regression Lock·Receipt chain이 Source of Truth입니다.
