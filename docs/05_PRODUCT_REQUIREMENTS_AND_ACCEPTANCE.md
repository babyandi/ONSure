# ONSURE 제품 요구사항 및 수용 기준

## 1. 제품 목표

ONSURE는 사용자가 VS Code 안에서 대상 AI 프로그램을 학습시키고, 실제 동작을 검증하고, 승인된 보완을 적용한 뒤 Git Branch·Commit·Draft PR까지 생성할 수 있는 독립형 상용 제품이어야 합니다.

## 2. 핵심 사용자

- AI 코딩 결과를 검증하고 싶은 개인 개발자
- AI Agent·RAG 제품을 운영하는 개발팀
- 외부 개발 결과를 검수해야 하는 기업
- 품질·보안·내부통제 담당자
- 반복 납품하는 AI 솔루션 개발사

## 3. 핵심 사용자 여정

```text
프로젝트 등록
→ 저장소·설정·프롬프트·테스트 학습
→ Program Profile 확인
→ 검증 계획 승인
→ 정상·경계·실패·적대 시험 실행
→ Finding·RCA 확인
→ 보완 계획 승인
→ Patch 적용
→ 전체 회귀검증
→ Before/After 비교
→ Commit·Push·Draft PR
→ Evidence와 학습 기억 저장
```

## 4. 기능 요구사항

### FR-01 프로젝트 등록

- 로컬 폴더와 Git 저장소를 등록할 수 있어야 합니다.
- 현재 Branch, HEAD SHA, Dirty 상태, Submodule, LFS와 원격 Provider를 식별해야 합니다.
- 사용자의 기존 미커밋 변경을 자동 삭제·덮어쓰기·stash해서는 안 됩니다.

### FR-02 프로그램 학습

- 소스코드, 설정, 문서, 테스트, 프롬프트, RAG 구성, Tool Contract와 실행 로그를 인벤토리화해야 합니다.
- 프로그램 목적, 주요 기능, 모듈 관계, AI 구성, 데이터 흐름과 불확실성을 Program Profile로 생성해야 합니다.
- 최초 전체 학습과 변경분 기반 증분 학습을 지원해야 합니다.

### FR-03 행동 학습

- 입력·출력·도구 호출·실패 조건·응답 변동을 기록해야 합니다.
- 동일 입력 반복 실행을 통해 비결정성과 회귀 가능성을 측정해야 합니다.
- 성공·실패·정책 위반과 취약 조건을 Behavior Profile로 관리해야 합니다.

### FR-04 검증 계획

- 정상·경계·실패·적대 시나리오를 자동 제안해야 합니다.
- 실행 명령, 예상 결과, 필요한 권한, 시간과 비용을 실행 전 제시해야 합니다.
- 사용자가 시나리오를 전체·부분 승인할 수 있어야 합니다.

### FR-05 검증 실행

- 정적 분석, 단위·통합·E2E, API, CLI, 컨테이너와 AI 행동 검증을 실행할 수 있어야 합니다.
- `PASS`, `FAIL`, `HOLD`, `NOT_RUN`을 명확히 구분해야 합니다.
- 모든 판정은 Source SHA, 환경, 입력, 명령, 출력과 Evidence에 결속되어야 합니다.

### FR-06 진단 및 RCA

- 실패 재현 절차와 최초 실패 지점을 제시해야 합니다.
- 원인 후보, 영향 범위, 신뢰도와 미확인 사항을 구분해야 합니다.
- 코드·프롬프트·RAG·도구·설정·데이터 문제를 분리 진단해야 합니다.

### FR-07 자동 보완

- 검증 또는 학습 결과와 연결되지 않은 임의 수정을 기본 차단해야 합니다.
- 수정 계획, 변경 파일, 위험도, 영향 범위와 롤백 방법을 적용 전에 제시해야 합니다.
- 전체·파일·Hunk 단위 승인을 지원해야 합니다.

### FR-08 개선 입증

- 수정 전후 동일 Fixture와 환경으로 재검증해야 합니다.
- `IMPROVEMENT_PROVEN`, `NO_MEANINGFUL_IMPROVEMENT`, `REGRESSION_DETECTED`를 구분해야 합니다.
- 기존 정상 기능의 회귀가 있으면 최종 상태를 `HOLD`로 전환해야 합니다.

### FR-09 VS Code 작업환경

- Chat, Program Profile, Learning, Verification, Findings, Improvement, Evidence, Git & PR View를 제공해야 합니다.
- Ask, Plan, Act, Autopilot 모드를 제공해야 합니다.
- Extension 재시작 후 작업 상태와 승인 이력을 복구해야 합니다.

### FR-10 Git Full-Chain

- 전용 Worktree 또는 Branch를 생성해야 합니다.
- Patch, Test, Commit, Push, Draft PR과 로컬 Gate 결과 수집을 연결해야 합니다.
- 검증 미완료 상태에서 Ready 또는 Merge Ready를 주장해서는 안 됩니다.

### FR-11 CLI/API

- VS Code와 동일 Core API를 사용하는 CLI를 제공해야 합니다.
- 로컬 Gate와 외부 제품에서 사용할 공개 API 또는 SDK 경계를 제공해야 합니다.

### FR-12 학습·개선 기억

- Program Profile, Failure Memory, Improvement Memory와 Evidence Ledger를 버전 관리해야 합니다.
- 프로젝트 전용 정보와 범용화 가능한 익명 패턴을 분리해야 합니다.
- 학습 후보는 검증과 승격 전까지 활성 기준선이 되어서는 안 됩니다.

## 5. 비기능 요구사항

- 기본 로컬 실행, Enterprise Offline Mode 지원
- Secret 최소권한과 명시적 승인
- 모든 변경의 롤백 가능성
- 중단·재개 가능한 장기 작업
- Provider와 모델 교체 가능성
- 실행 비용·Token·데이터 전송 범위 가시화
- 다중 프로젝트 데이터 격리
- 재현 가능한 Evidence

## 6. MVP 제외 범위

- 범용 신규 시스템 전체 개발
- 기업 전체 지식경영
- 기반모델 사전학습
- 모바일 앱
- 완전 자동 Merge
- 모든 언어와 프레임워크 지원
- 전사 운영관제와 프로젝트 관리

## 7. MVP 수용 시나리오

1. VS Code에서 Git 저장소를 엽니다.
2. ONSURE가 저장소를 학습해 Program Profile을 생성합니다.
3. 사용자에게 불확실성과 충돌을 표시합니다.
4. 검증을 실행해 실제 결함 1건 이상을 재현합니다.
5. ONSURE가 RCA와 최소 수정 계획을 제시합니다.
6. 사용자가 일부 Patch만 승인합니다.
7. 전용 Branch에서 수정하고 전체 회귀검증합니다.
8. 수정 전후 개선 효과를 입증합니다.
9. Commit·Push·Draft PR과 Evidence를 생성합니다.
10. VS Code를 재시작해도 상태가 복원됩니다.

이 시나리오가 실제 저장소에서 2회 연속 성공해야 MVP Full-Chain으로 인정합니다.
