# ONSURE Claude형 작업 환경 기준

## 1. 목적

ONSURE은 보고서를 출력하는 외부 검사 도구에 머물지 않습니다. 사용자는 VS Code 안에서 ONSURE과 대화하고, 대상 저장소를 학습시키고, 검증을 실행하고, 보완 개발을 승인하며, Git 변경과 PR까지 하나의 작업 흐름에서 관리할 수 있어야 합니다.

제품 경험의 기준은 다음과 같습니다.

```text
대화
→ 저장소 이해
→ 실행 계획
→ 파일 수정
→ 테스트·검증
→ 수정 전후 비교
→ Git 변경 검토
→ Commit·Push·PR
→ 학습·개선 기억 갱신
```

ONSURE은 Claude 또는 Codex와 유사한 대화형 작업 경험을 제공하되, 범용 코딩보다 등록된 AI 프로그램의 학습·검증·보완 개발에 특화합니다.

## 2. 필수 제공 채널

### 2.1 VS Code Extension

VS Code Extension은 ONSURE의 기본 사용자 환경입니다.

필수 기능:

- ONSURE 사이드바와 대화 패널
- 현재 Workspace 및 Multi-root Workspace 인식
- 선택 파일·폴더·코드 범위 전달
- 저장소 전체 학습 시작 및 진행률 표시
- 학습된 Program Profile 열람
- 검증 시나리오 선택과 실행
- 발견사항·RCA·개선 후보 표시
- Patch 및 Diff 미리보기
- 수정 승인·거부·부분 적용
- 테스트·재검증 실행 상태 표시
- Git Branch·Commit·Push·PR 실행
- Receipt·Evidence·Before/After 결과 열람
- 학습 기억의 신규 등록·변경·폐기 내역 표시

### 2.2 CLI

CLI는 자동화, CI, 서버 환경 및 고급 사용자를 위한 동일 기능의 실행 채널입니다.

예시 명령 체계:

```bash
onsure init
onsure learn
onsure profile
onsure verify
onsure diagnose
onsure improve --plan
onsure improve --apply
onsure regress
onsure evidence
onsure git status
onsure git commit
onsure pr create
```

명령명은 구현 시 조정할 수 있지만, VS Code와 CLI는 같은 Core API와 상태 모델을 사용해야 합니다.

### 2.3 Web Console

Web Console은 여러 프로젝트의 상태, 학습 이력, 검증 결과, 개선 효과, 라이선스 및 조직 정책을 관리합니다. 초기 개인용 MVP에서 제외할 수 있지만 제품 구조에는 포함합니다.

### 2.4 API/SDK

타사 IDE, CI/CD, 자체 포털 및 향후 Embedded Edition에서 사용할 공개 API를 제공합니다. ORUDA는 향후 이 공개 인터페이스를 통해 기능을 포함할 수 있으나 ONSURE의 필수 의존성은 아닙니다.

## 3. 대화형 Agent 경험

대화는 단순 질의응답이 아니라 실행 가능한 작업 단위입니다.

사용자 예시:

```text
이 저장소를 학습하고 현재 AI 기능의 목적과 구조를 정리해.

실제 실행 결과를 기준으로 실패 가능성이 높은 경로를 찾아.

치명·중대 결함만 수정 계획으로 만들어. 아직 파일은 수정하지 마.

승인한 세 건만 수정하고 전체 회귀검증해.

개선 효과가 입증되면 새 브랜치에 커밋하고 Draft PR을 만들어.
```

ONSURE은 각 요청을 다음 구조로 처리합니다.

```text
Intent
→ Context Resolution
→ Program Knowledge Retrieval
→ Plan
→ Permission Check
→ Tool Execution
→ Observation
→ Replan 또는 Complete
→ Evidence
→ Memory Candidate
```

## 4. Agent 실행 모드

### 4.1 Ask

읽기 전용 질의와 설명을 수행합니다. 파일·Git·환경을 변경하지 않습니다.

### 4.2 Plan

학습·검증·보완 계획과 영향 범위를 생성하지만 변경하지 않습니다.

### 4.3 Act

승인된 범위에서 파일 수정, 명령 실행, 테스트를 수행합니다.

### 4.4 Autopilot

사전에 설정된 위험 범위와 정책 안에서 학습부터 Patch·검증·PR까지 반복 수행합니다.

Autopilot도 기준선, 정책, 고위험 변경, 외부 배포, Merge를 임의로 변경할 수 없습니다.

## 5. 도구 실행 환경

ONSURE은 다음 실행 도구를 자체 제공하거나 안전한 Adapter로 연결합니다.

- 파일 읽기·검색·수정
- 코드 구조·의존성 분석
- Shell/Terminal 명령
- 빌드 도구
- 단위·통합·E2E 테스트
- 컨테이너 실행
- HTTP/API 호출
- 브라우저 또는 UI 자동화
- DB Migration 검증
- 로그·Trace 분석
- Git 및 GitHub/GitLab 연동

모든 도구 호출은 입력, 실행 위치, 권한, 종료 코드, 출력 Hash와 결과를 기록해야 합니다.

## 6. 권한 모델

권한은 기능별로 분리합니다.

```text
READ_REPOSITORY
READ_ENVIRONMENT
RUN_SAFE_COMMAND
RUN_NETWORK_COMMAND
MODIFY_WORKTREE
CREATE_BRANCH
COMMIT
PUSH
CREATE_PR
UPDATE_PR
MERGE
CHANGE_BASELINE
CHANGE_POLICY
ACCESS_SECRET
```

기본 원칙:

- 읽기와 계획은 기본 허용 가능
- 파일 수정은 작업 단위 승인 또는 정책 승인 필요
- Push·PR은 명시적 사용자 설정 필요
- Merge, 정책 변경, 기준선 변경, Secret 접근은 별도 고위험 권한
- 실제 승인 없이 허용 범위를 확대하지 않음

## 7. VS Code 화면 구조

```text
ONSURE Activity Bar
├─ Chat
├─ Program Profile
├─ Learning
├─ Verification
├─ Findings
├─ Improvement
├─ Evidence
├─ Git & PR
└─ Settings
```

### Chat

대화, 계획, 실행 승인, 진행 상태와 결과를 한 화면에서 다룹니다.

### Program Profile

목적, 주요 기능, 모듈 관계, AI 구성, 데이터 흐름, 정책, 알려진 위험과 신뢰 수준을 보여줍니다.

### Learning

학습 소스, 추출 지식, 충돌, 불확실성, 학습 후보와 승인 상태를 보여줍니다.

### Verification

검증 팩, 시나리오, 실행 상태, 실패 재현과 판정을 보여줍니다.

### Improvement

RCA, 수정 후보, 영향 범위, Patch, 위험도와 Before/After 결과를 보여줍니다.

### Git & PR

현재 Branch, 변경 파일, Commit 계획, 원격 상태, PR 상태와 관련 Evidence를 보여줍니다.

## 8. 상태 지속성과 세션 복구

ONSURE은 대화창이 닫혀도 작업을 잃지 않아야 합니다.

보존 대상:

- 프로젝트 식별자와 소스 기준선
- 학습 상태와 Program Profile 버전
- 실행 계획과 승인 내역
- 진행 중 Run과 재시도 상태
- 변경 파일 및 Worktree
- 검증 결과와 Evidence
- Git Branch·Commit·PR 연결
- 개선 기억 후보와 적용 상태

재접속 시 다음 상태를 표시합니다.

```text
어디까지 실행되었는가
무엇이 변경되었는가
무엇이 아직 검증되지 않았는가
다음 실행은 무엇인가
```

## 9. 제품 경계

Claude형 환경을 제공한다고 해서 ONSURE을 범용 신규 개발 AI로 확장하지 않습니다.

허용되는 작업:

- 대상 AI 프로그램 학습
- 학습·검증 결과에 근거한 수정
- 테스트 및 Fixture 보완
- Prompt·RAG·Tool·Workflow 개선
- 결함 제거와 품질 목표 개선
- 관련 문서·증적 갱신

제품 경계를 벗어나는 작업:

- 근거 없는 신규 서비스 전체 기획
- 등록 프로그램과 무관한 범용 개발
- 전사 프로젝트 관리
- 일반 문서·디자인 제작 플랫폼

## 10. 완료 기준

Claude형 작업 환경은 다음이 실제로 연결될 때 완료로 봅니다.

```text
VS Code 대화
→ 저장소 학습
→ Program Profile 생성
→ 검증 실행
→ 발견사항 선택
→ Patch 생성·적용
→ 전체 회귀검증
→ Before/After 입증
→ Branch·Commit·Push·Draft PR
→ 재접속 후 상태 복원
```

UI Mockup이나 명령 목록만 존재하는 경우 완료로 계산하지 않습니다.
