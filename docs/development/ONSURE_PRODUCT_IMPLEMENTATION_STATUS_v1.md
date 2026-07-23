# ONSURE Product Implementation Status v1

## 판정

ONSURE 제품 개발의 권위 구현은 `io.onsure.platform`이다. `io.onsure.assurance`는 독립 검증·Receipt·Final Gate를 담당한다.

```text
Product Core Static Implementation          COMPLETE
Generic Validator Engine Static Implementation COMPLETE
General Program E2E Definition             COMPLETE
AI Program E2E Definition                  COMPLETE
Persistent Failure/RCA/Fixture/Oracle/Lock COMPLETE
ORUDA Target Adapter                       COMPLETE
Product E2E Actual Execution               NOT_RUN_IN_CURRENT_SESSION
ONSURE Self-Assurance Actual Execution      NOT_RUN_IN_CURRENT_SESSION
Development Gate                           HOLD
```

실제 JDK 17·Maven 실행 증거가 없으므로 PASS를 주장하지 않는다.

## 1. ONSURE 제품 Core

권위 파일:

- `ValidationModel.java`: ValidationTarget, ValidationJob, Evidence, Finding, FailureMode, RCA, FixtureResult, RegressionLock, ValidationReport, RevalidationDelta
- `ValidationContext.java`: 실행 중 제품 상태와 Stage 산출물
- `FileValidationStore.java`: Target별 Run Root와 JSON Evidence 영속화
- `ValidationEngine.java`: Adapter 선택, Stage 실행, 판정, 보고서, 저장
- `TargetAdapterRegistry.java`: Target Adapter 등록과 선택

## 2. 범용 Validator Engine

실행 흐름:

```text
Target Registration
-> Source Intake / Lock
-> Target Metadata
-> Static Validation
-> Runtime Validation
-> Security / AI Validation
-> Failure Mode / RCA
-> Fixture Registry
-> Harness / Oracle
-> Remediation Planning
-> Regression Lock
-> Independent Product Verifier
-> Independent Product Audit
-> Validation Report
```

권위 구현:

- `ValidationEngine.defaultEngine`
- `BuiltInStages.defaults`
- `FixtureRegistryStage`
- `RemediationPlanningStage`
- `IndependentProductVerifierStage`
- `IndependentProductAuditStage`

## 3. 일반 프로그램 전체 E2E

시나리오:

- `fixtures/e2e/general-program`: 결함 포함 기준선, 기대 판정 FAIL
- `fixtures/e2e/general-program-fixed`: 개선본, 기대 판정 PASS
- `RevalidationService`: 기준선과 개선본 비교
- 해결 Finding 존재, 신규 Finding 0건, Source/Regression 결과 변화 확인

## 4. AI 프로그램 전체 E2E

시나리오:

- `fixtures/e2e/ai-program`
- Prompt Injection 검출
- Tool Authorization 결함 검출
- 기대 판정 FAIL
- Independent Verifier와 Audit PASS 필수

## 5. Registry 실제 저장·실행

제품 E2E 실행 시 다음을 Target별 Run Root에 영속화한다.

- Target와 Job
- Evidence와 Stage Result
- Finding
- Failure Mode
- RCA
- Remediation Plan
- Fixture/Harness/Oracle Result
- Regression Lock
- Validation Report
- Revalidation Delta
- Independent Verifier/Audit Evidence

두 번 실행한 정규화 결과가 동일해야 한다.

## 6. ORUDA Target Adapter

- `OrudaTargetAdapter.java`
- ORUDA는 `AI_AGENTIC_PLATFORM` 외부 Target
- ONSURE Runtime은 ORUDA에 의존하지 않음
- ORUDA Claim은 ONSURE 재계산 전까지 신뢰하지 않음
- ORUDA는 ONSURE Final Decision을 기록할 수 없음
- Portable Receipt 요구

E2E 시나리오:

- `fixtures/e2e/oruda-target`: 결함 검출 대상, 기대 FAIL
- `fixtures/oruda/mvf-001`: 최소 검증 실행 패키지, 17개 Fixture PASS
- ORUDA 실행 패키지 Catalog, Document Materializer, Execution Registry, Evidence Registry, Receipt Lineage, Blind Review, Independent Run, Final Candidate, Final Approval, Final Lock 경로 포함

## 7. 공식 실행

제품 E2E:

```bash
bash scripts/run-product-platform-e2e.sh
```

수행 내용:

- 지정 제품·ORUDA 테스트 2회
- Class/Test Class Hash 비교
- 일반 기준선·개선본·AI·ORUDA·ORUDA MVF 실행 2회
- 정규화 결과 바이트 동일성 비교
- Revalidation Delta 확인
- Product E2E Lock 생성

전체 개발 Gate:

```bash
bash scripts/run-onsure-development-gate.sh
```

수행 내용:

```text
Preflight
-> Product Platform E2E
-> ONSURE Self-Assurance Final Gate
-> Development Gate Lock
```

## 8. 현재 실행 Blocker

현재 대화 세션의 실행 환경은 다음과 같다.

```text
Java/Javac 21
Maven      MISSING
Required   Java/Javac 17 + Maven
```

따라서 코드·Fixture·Runner·Gate 구현은 완료했지만 실제 Maven/JUnit/E2E/Receipt 증거는 아직 생성하지 못했다.

## 9. 다음 상태 전이

JDK 17·Maven clean worktree 또는 저장소 Devcontainer에서 다음 한 명령을 실행한다.

```bash
bash scripts/run-onsure-development-gate.sh
```

성공 출력:

```text
ONSURE_DEVELOPMENT_GATE_PASS <evidence-root>
```

이 출력과 Lock 검증 전에는 Issue 종료, PR Ready, Merge, ORUDA 공식 검증 완료 판정을 금지한다.
