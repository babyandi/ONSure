# ONSURE 전체 상세설계 대비 기능 Gap 검증 v1

## 1. 판정

`BLOCKED — STANDALONE PRODUCT FULL-CHAIN NOT IMPLEMENTED`

현재 저장소는 범용 Fixture 실행, 파일 기반 Evidence, 일부 Receipt·Learning Ledger 및 자동 실행 보조 기능을 보유한다. 하지만 권위 설계가 정의한 Standalone ONSure 제품 전체는 구현되지 않았다.

## 2. 검증 범위

- 제품 기준선과 Master Design Set
- 기능 요구사항과 프로그램 명세
- OReview 상세설계
- Architecture, Data, API, OLicense 설계
- Web와 VS Code UI/UX
- 시험, 운영, 출시 계획
- Program Learning 방법론
- Contract, Schema, Java, Python, Shell, Fixture, Status

## 3. 정량 요약

기능군 20개 기준:

- `IMPLEMENTED`: 0
- `PARTIAL`: 8
- `STUB`: 4
- `DESIGN_ONLY`: 8
- Final claim allowed: false

상세 근거는 `contracts/requirements-traceability.v1.json`과 `status/implementation-matrix.v1.json`을 권위로 한다.

## 4. P0 부족 기능

### 4.1 Standalone Core

Core Preflight와 기본 엔진이 ORUDA 파일과 Adapter를 필수 경로로 취급한다. README의 독립 제품 원칙과 충돌한다. Core는 Generic Target만으로 실행돼야 하고 ORUDA는 명시적으로 선택한 Adapter Profile에서만 요구해야 한다.

### 4.2 Program Learning

현재 학습 오케스트레이터는 호출자가 제공한 Hash와 Boolean을 기록한다. Repository 이해, Component Graph, AI Component, Dependency Inventory, Data Flow, Dynamic Trace, Unknown/Conflict, Incremental Revision을 생성하지 않는다.

### 4.3 Behavior Learning

현재 AI 행동 단계는 위험 Marker 문자열을 소스에서 찾는다. 실제 모델 응답, Agent 상태, Tool 호출, Prompt Injection, RAG 오염, 반복 입력 변동을 관찰하지 않는다.

### 4.4 Planning

ExecutionPlan, ScenarioPlan, 비용·시간·Credit 추정, 권한, Stop Condition, 사용자 Plan Diff와 부분 승인이 없다.

### 4.5 OReview

요구사항, Architecture, Policy, Code, AI, Security, Performance, Test, Merge Review가 없다. 현재 Static Stage는 일부 문자열 검색이며 Inline Finding, 독립 Pass, Reconciliation, Merge Decision을 구현하지 않는다.

### 4.6 RCA

현재 RCA는 Finding Category에 미리 정한 문구를 대응시킨다. 재현, 최초 실패 지점, Trace, 입력 변이, 인과 실험이 없으므로 `RCA_CONFIRMED`가 아니라 `RCA_CANDIDATE`로만 사용해야 한다.

### 4.7 Automatic Improvement

Remediation Plan 문구는 존재하지만 Patch, 파일·Hunk 승인, Worktree, Test 추가, 적용, Abandon, Rollback과 Git 연결은 없다.

### 4.8 Improvement Proof

Finding Fingerprint 전후 비교는 있으나 동일 Fixture·환경·정책·도구 버전 강제와 품질·성능 지표 비교가 없다. `IMPROVEMENT_PROVEN`, `NO_MEANINGFUL_IMPROVEMENT`, `REGRESSION_DETECTED`의 기계 판정이 필요하다.

### 4.9 VS Code와 API

`.vscode/tasks.json`은 Extension이 아니다. Activity Bar, Chat, Program Profile, Review, Verification, Improvement, Evidence, Git View와 Local Authenticated API가 없다.

### 4.10 Git Full-Chain

Worktree, Branch, Hunk Patch, Commit, Push, Draft PR, CI 결과, Review Comment, Merge Readiness와 Rollback을 연결하는 엔진이 없다.

## 5. 구조적 문제

### 5.1 상태 모델 충돌

제품 Lifecycle, Validation Run, Assurance Publication 상태를 서로 다른 문서와 코드가 혼합한다. 세 State Machine을 분리하고 명시적 Mapping을 추가해야 한다.

### 5.2 E2E 명칭 과장

현재 ProductPlatformE2E는 Sample Fixture 검증 엔진 E2E다. VS Code, Runtime, 학습, 승인, Patch, Git, 재시작 복구를 포함한 Product Full-Chain과 다르다.

### 5.3 Evidence 불변성 부족

로컬 JSON과 Manifest는 개발 증적으로 유효하지만 상용 불변 원장은 아니다. External Anchor, 서명된 Head, Tenant Context, Retention, Legal Hold, CAS와 복구가 필요하다.

### 5.4 Learning Ledger 독립성 부족

서로 다른 Run만 강제하고 서로 다른 Verifier Identity와 Signing Key를 강제하지 않는다. 현재 Validation Pack 결속, signed independent recalculation, post-apply receipt resolution과 Git SHA 형식 구분도 필요하다.

## 6. 설계 자체의 누락

이번 변경에서 다음 기계 계약을 추가했다.

- Program Profile Schema
- Behavior Profile Schema
- Failure Memory Schema
- Improvement Memory Schema
- Detailed Evidence Receipt Schema
- Status Vocabulary
- Core and Optional Adapter Boundary
- Requirements Traceability

여전히 상세 DB, Local API, VS Code Contribution, GitHub/GitLab Adapter, 성능 목표, Blind Review 운영, 설치·SLA·보존·업그레이드 문서는 후속 구현과 함께 작성해야 한다.

## 7. Codespace 이전 처리 범위

- 설계 권위와 문서 우선순위
- 요구사항 추적성
- 누락·충돌 대장
- 기계 Schema
- Core/Adapter Preflight 분리
- 상태 파일과 완료 주장 정정
- 단일 실행기와 Runbook
- 정적 Repository Contract 검증

## 8. Codespace 최종 단계

Codespace 또는 동등 환경은 맨 마지막에 다음 목적에만 사용한다.

1. Java 17, Maven, JUnit, Python, Shell 실행
2. Generic와 AI Fixture E2E
3. 두 번의 결정적 회귀
4. Container, DB, Network, Recovery, Performance 시험
5. 실제 VS Code Extension Host Full-Chain
6. 독립 OTester와 OAudit

## 9. 허용 상태

- Design baseline: available
- Validator and Evidence slice: partial
- Standalone MVP: blocked
- Full-Chain: not run
- Independent assurance: not run
- FinalLock: false
- Production GO: false
- Commercial GO: false
