# ONSURE 메타 누락감사 v1

## 1. 감사 목적

이번 감사는 대상 프로그램이 아니라 ONSURE 검증기 자체를 검증한다.

질문은 세 가지다.

1. 설계 대비 기능·프로세스·데이터·제품 표면이 빠졌는가?
2. 빠졌다면 기존 ONSURE 검증이 왜 검출하지 못했는가?
3. 동일 유형이 다시 발생했을 때 ONSURE가 자동으로 차단하는가?

판정 상한은 `LOCAL_SELF_VALIDATION / NONFINAL`이다.

## 2. 추가 검출된 제품 누락

### 2.1 부분 Execution Plan 승인이 실제로 불가능

설계는 전체 또는 부분 승인을 요구했지만 기존 코드는 승인 Action 집합이 계획 전체와 정확히 같아야만 허용했다.

단순히 부분집합을 허용하는 것도 안전하지 않았다. Validation Engine이 승인된 Action을 Stage 실행 전에 확인하지 않았으므로 승인하지 않은 Fixture·RCA·Patch Plan까지 실행될 수 있었다.

수정:

- 승인 Action은 계획 Action의 비어 있지 않은 부분집합으로 제한
- 계획에 없는 Action 추가 금지
- `EXACT_PLAN_ACTION_SET`과 `PARTIAL_PLAN_ACTION_SET` 분리
- Stage별 필요 Action을 `ExecutionPlanActionPolicy`로 고정
- 미승인 Stage는 실행하지 않고 `NOT_RUN_NOT_APPROVED / HOLD` 기록
- Completion Gate는 승인된 범위의 Artifact만 요구
- 부분 실행 전체 판정은 Final PASS가 아닌 HOLD

### 2.2 프로젝트 등록 Core가 제품 표면에 연결되지 않음

`ProductCatalog`에는 Workspace·Project·Target 등록 기능이 있었지만 Shared Dispatcher에 호출 Operation이 없었다. 따라서 CLI·Local API·VS Code의 공통 Workflow에서는 프로젝트 등록을 실행할 수 없었다.

수정 Operation:

- `project.register-workspace`
- `project.register`
- `project.register-target`
- `project.read-target`
- `project.list-targets`

Dispatcher Workflow는 34개에서 39개로 증가했다.

## 3. 기존 ONSURE에서 검출되지 않은 원인

### 3.1 28개 대분류의 과도한 집계

기존 설계 Coverage는 28개 Capability에 대해 동일한 `INTAKE → PROCESS → VERIFY` 템플릿을 사용했다. 하나의 Capability가 `PARTIAL`이면 그 내부의 여러 하위 요구가 빠져도 별도 실패가 발생하지 않았다.

숨을 수 있었던 예:

- 최초 전체 학습과 증분 학습
- 전체 승인과 부분 승인
- VS Code 필수 View 8종
- Ask·Plan·Act·Autopilot
- 작업 중단·재개와 재시작 복구
- Token·비용·데이터 전송 가시화
- Public API·SDK
- Provider·Model 교체성

### 3.2 원자 Requirement 추출이 후보 전용

`extract-atomic-requirements.py`는 규범 표현을 찾아 Candidate를 생성하지만 권위 Requirement로 승격하지 않는다. 문서에 Code Symbol이 직접 적혀 있지 않으면 Code·Test 연결도 만들어지지 않는다.

기존 `validate-atomic-requirements.py`는 Candidate 형식과 Hash는 검사했지만 제품 설계의 모든 하위 문장이 권위 대장에 존재하는지는 검사하지 않았다.

### 3.3 파일·클래스·테스트 존재를 기능 도달성으로 오인

기존 Gate는 클래스와 테스트 파일 존재를 확인했다. 그러나 다음은 확인하지 않았다.

- UI·CLI·API에서 실제 호출 가능한가
- 승인 Scope가 실행 Stage에 적용되는가
- 상태 전이가 제품 흐름으로 이어지는가
- 일부 구현이 전체 기능처럼 보이지 않는가

### 3.4 제품 표면별 요구가 없었음

Core 구현이 있으면 VS Code·CLI·Local API까지 구현된 것으로 오해할 수 있었다. 예를 들어 Hunk 승인 Core가 있어도 VS Code Hunk 승인 UX는 없다.

### 3.5 불완전 상태가 명시적 Gap을 요구하지 않았음

`PARTIAL`, `STUB`, `DESIGN_ONLY`가 무엇이 빠졌는지 기계적으로 요구하지 않아 세부 누락이 상태 한 단어 안에 숨었다.

## 4. ONSURE 검증기 수정

### 4.1 38개 제품 하위 요구 권위 대장

`status/product-subrequirement-coverage.v1.json`

각 항목은 다음을 필수로 가진다.

- 원문 규범 문장
- 구현 상태와 검증 상태
- Code·Test·Evidence 참조
- 필요한 제품 표면과 구현된 표면
- 남은 제어와 검출 제어
- 중요한 경우 실제 소스 Token Assertion

현재 분류:

```text
총 38
IMPLEMENTED 2
PARTIAL 28
STUB 4
DESIGN_ONLY 4
현재 Source 검증 NOT_RUN 38
```

### 4.2 하위 요구 실패 주입 10개

- Requirement 삭제
- ID 중복
- 원문 문장 Drift
- 불완전 상태인데 Gap 미기재
- IMPLEMENTED인데 Code/Test 없음
- PASS인데 Evidence 없음
- 참조 파일 누락
- 검출 제어 누락
- 제품 표면 누락을 감지하지 못함
- 핵심 구현 Token 삭제

### 4.3 Workflow Surface Parity Gate

`LocalWorkflowDispatcher`의 39개 Operation을 추출해 다음 공통 경로를 검사한다.

- CLI generic workflow
- Loopback Local Authenticated API `/v1/workflow`
- VS Code generic workflow request

Operation 삭제·중복 또는 제품 표면 경로 제거를 6개 실패 주입으로 검출한다.

### 4.4 전체 실패 주입

```text
설계·프로세스·데이터              28
원자 Requirement                  10
Actions 금지·로컬 자동화 경계       6
검증 Claim                         8
제품 하위 Requirement              10
Workflow Surface                    6
합계                               68
```

### 4.5 기존 Gate 결속

신규 Gate는 다음에서 필수 실행된다.

- `scripts/onsure-local-gate.sh`
- `scripts/onsure-one-shot.sh`
- `scripts/validate-codespace-free-remediation.py`
- `scripts/validate-status-consistency.py`

GitHub Actions는 사용자 정책에 따라 계속 금지된다.

## 5. 새로 명시된 주요 미완료 기능

- 변경분 기반 증분 Program Learning
- Tool Contract 내용 분석과 실행 로그 인벤토리
- VS Code 필수 View 전체
- Ask·Plan·Act·Autopilot
- VS Code 부분 Plan 승인 및 Hunk 승인 UX
- 작업 Checkpoint·Cancel·Pause·Resume·재시작 복구
- Provider·Model Adapter와 호환성 시험
- Token·비용·데이터 전송 범위 가시화
- 외부 제품용 Public SDK
- Identity·RBAC·Cross-tenant 격리
- 제품 SBOM·취약점·라이선스 Pack
- 성능·장애·복구·운영·배포 Pack

이 항목은 검출됐다는 이유로 구현 완료로 승격하지 않는다.

## 6. 검증 경계

이번 브랜치에서는 제품 코드와 검출기를 수정했다. 현재 `main`에 대한 로컬 Full Gate Receipt가 생성되기 전까지 다음은 `NOT_RUN`이다.

- Java 17 전체 Maven/JUnit
- Rootless Sandbox 전체
- VSIX 패키징
- Current-main Source-bound Receipt
- Independent OTester·OAudit

따라서 FinalLock, Production GO, Commercial GO는 계속 `false`다.
