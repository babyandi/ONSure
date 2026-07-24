# ONSURE VS Code·Agent·Git 구현 로드맵

## 1. 목표

ONSURE Standalone MVP는 다음 한 줄의 사용자 경험을 실제로 제공해야 합니다.

```text
VS Code에서 대화로 저장소를 학습하고, 결함을 검증하고, 승인된 보완을 적용한 뒤, Git Branch·Commit·Draft PR까지 생성한다.
```

## 2. 구현 단계

### Phase 0. 제품 계약 고정

산출물:

- 독립 제품 기준선
- Agent 권한 모델
- Run·Task·Approval 상태 모델
- Program Profile Schema
- Finding·Improvement·Evidence Schema
- Git 변경 계약

완료 조건:

- ORUDA 필수 의존성 0건
- VS Code, CLI, Core API가 동일 계약 사용
- 학습과 최종 판정 권한 분리

### Phase 1. Local Agent Core

구현:

- Workspace Intake
- 파일 검색·읽기
- 코드 구조 분석
- Shell 실행
- Tool Registry
- Plan/Act Loop
- Run 상태 저장
- 중단·재개

완료 시나리오:

```text
저장소 열기
→ 질문
→ 관련 파일 탐색
→ 실행 계획 생성
→ 안전 명령 실행
→ 결과 저장
```

### Phase 2. Program Learning MVP

구현:

- 소스·설정·문서·테스트 인벤토리
- 목적·기능·모듈·AI 구성 추출
- 불확실성 및 충돌 표시
- Program Profile 버전 관리
- Incremental Learning

완료 시나리오:

```text
최초 학습
→ Program Profile v1
→ Commit 변경
→ 변경 부분만 재학습
→ Profile v2 Diff
```

### Phase 3. Verification MVP

구현:

- Build/Test 명령 탐지
- 정적·실행 검증
- 정상·경계·실패 시나리오 생성
- Finding 및 RCA 초안
- Evidence 저장

완료 조건:

- 실패 재현 명령 제공
- Source SHA와 결과 결속
- NOT_RUN과 PASS를 명확히 구분

### Phase 4. Improvement MVP

구현:

- 수정 계획 생성
- 위험도 및 영향 범위 계산
- Patch 생성
- Hunk 단위 승인
- 적용 후 테스트
- Before/After 비교

완료 조건:

- 검증 결과 없는 임의 수정 차단
- 실패 시 원상복구 가능
- 개선 효과 미입증 시 HOLD

### Phase 5. Git Full-Chain

구현:

- Dirty Workspace 보호
- Worktree/Branch 생성
- Commit 계획과 Commit 생성
- Remote Push
- Draft PR 생성
- CI 상태 회수
- PR Evidence 갱신

완료 조건:

```text
전용 Branch
→ Patch
→ Test
→ Commit
→ Push
→ Draft PR
```

실제 GitHub 저장소에서 2회 연속 재현합니다.

### Phase 6. VS Code Extension

구현:

- Activity Bar
- Chat Webview
- Program Profile Tree
- Learning/Verification/Improvement View
- Diff 및 승인 UI
- Terminal·Run 상태 UI
- Git & PR View
- 재접속 상태 복구

완료 조건:

- VS Code를 벗어나지 않고 MVP Full-Chain 수행
- Extension 재시작 후 Run과 승인 상태 복구

### Phase 7. Claude형 Autopilot

구현:

- 장기 작업 Queue
- 실패 시 RCA·수정·재실행
- 단계별 Budget
- 권한 경계
- 사용자 개입 요청
- 최종 요약 및 Evidence

완료 조건:

```text
목표 입력
→ 계획
→ 반복 실행
→ 실패 복구
→ 검증
→ Draft PR
```

단, Merge와 기준선 변경은 기본 수동 승인입니다.

### Phase 8. 배포 및 상용화

구현:

- VS Code Marketplace 패키지
- Local Runtime Installer
- 라이선스 인증
- Update Channel
- Telemetry 동의·비동의
- Enterprise Offline Mode
- 제품 로그와 진단 번들

## 3. 권장 기술 구조

```text
onsure-core
├─ agent-runtime
├─ learning-engine
├─ verification-engine
├─ improvement-engine
├─ evidence-store
├─ git-engine
├─ provider-adapters
└─ local-api

onsure-vscode
├─ chat
├─ views
├─ diff-approval
├─ run-monitor
├─ git-pr
└─ local-api-client

onsure-cli
└─ local-api-client
```

Core는 IDE와 분리해 CLI·CI·향후 Web Console에서도 재사용합니다.

## 4. 로컬 실행 방식

VS Code Extension이 직접 모든 작업을 수행하지 않습니다.

```text
VS Code Extension
↕ Local authenticated channel
ONSURE Local Runtime
↕
Workspace / Git / Build / Test / LLM Provider
```

이 구조는 Extension 종료 후에도 장기 작업을 보존하고, IDE 업데이트와 Core Runtime을 분리합니다.

## 5. 모델 구성

특정 모델에 종속하지 않습니다.

- Planner Model
- Code/Improvement Model
- Reviewer Model
- Embedding Model
- Local or Enterprise Model

사용자는 Provider를 선택할 수 있고, 모델 선택·비용·Token 사용·데이터 전송 범위를 확인할 수 있어야 합니다.

## 6. 1차 MVP에서 제외

- 모바일 앱
- 전사 포트폴리오 대시보드
- 완전 자동 Merge
- 모든 언어·프레임워크 지원
- 범용 신규 제품 개발
- 모델 자체 사전학습
- 대규모 조직 권한·과금 체계

## 7. 1차 MVP 수용 기준

다음 데모가 Script 없이 실제로 성공해야 합니다.

1. 사용자가 VS Code에서 저장소를 엽니다.
2. ONSURE에 저장소 학습을 요청합니다.
3. Program Profile과 불확실성을 확인합니다.
4. 검증을 실행해 실제 결함을 재현합니다.
5. ONSURE이 RCA와 수정 계획을 제시합니다.
6. 사용자가 특정 수정만 승인합니다.
7. ONSURE이 전용 Branch에서 수정합니다.
8. 전체 테스트와 Before/After 검증을 수행합니다.
9. Commit·Push·Draft PR을 생성합니다.
10. VS Code를 재시작해도 상태와 Evidence가 복구됩니다.

이 흐름이 완료되어야 Claude형 ONSURE 작업 환경의 MVP로 인정합니다.
