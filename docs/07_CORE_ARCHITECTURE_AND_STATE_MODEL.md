# ONSURE 핵심 아키텍처 및 상태 모델

## 1. 설계 원칙

ONSURE는 VS Code Extension에 모든 기능을 넣지 않습니다. IDE는 사용자 경험을 담당하고, 실제 학습·검증·보완·Git 실행은 Local Runtime이 담당합니다.

```text
VS Code Extension / CLI / Web Console
                  ↓
          Local Authenticated API
                  ↓
ONSURE Local Runtime
├─ Agent Runtime
├─ Workspace Intake
├─ Learning Engine
├─ Scenario Engine
├─ Verification Engine
├─ Diagnosis Engine
├─ Improvement Engine
├─ Regression & Proof Engine
├─ Git Engine
├─ Evidence Store
├─ Memory Store
└─ Provider Adapters
```

## 2. 컴포넌트 책임

### Agent Runtime

사용자 의도 해석, 계획, 권한 확인, 도구 실행, 관찰, 재계획과 완료 판정을 담당합니다.

### Workspace Intake

저장소 상태, 언어, 프레임워크, 빌드·테스트 명령, AI 구성과 보안 경계를 인식합니다.

### Learning Engine

Program Model, Behavior Model, Failure Memory와 Improvement Memory 후보를 생성합니다. 학습 결과만으로 최종 PASS를 결정하지 않습니다.

### Scenario Engine

정상·경계·실패·적대·회귀 시나리오와 Fixture를 생성하고 예상 결과와 오라클을 연결합니다.

### Verification Engine

정적·동적·AI 행동 검증을 실행하고 Evidence 기반 판정을 생성합니다.

### Diagnosis Engine

재현 가능한 실패를 기준으로 RCA, 최초 실패 지점, 영향 범위와 신뢰도를 생성합니다.

### Improvement Engine

코드·프롬프트·RAG·도구·설정·테스트 Patch를 생성합니다. 승인되지 않은 고위험 변경은 적용하지 않습니다.

### Regression & Proof Engine

동일 기준선에서 수정 전후를 비교하고 개선·효과 없음·회귀를 판정합니다.

### Git Engine

Dirty Workspace 보호, Worktree·Branch, Diff, Commit, Push, PR, CI 결과와 Rollback을 관리합니다.

### Evidence Store

소스 SHA, 환경, 입력, 명령, 출력, 판정, Patch와 Approval을 불변 실행 기록으로 보존합니다.

### Memory Store

프로젝트 전용 지식과 범용 개선 패턴을 물리·논리적으로 분리하고 버전·승격·폐기를 관리합니다.

## 3. 핵심 데이터 객체

- `Project`: 등록된 대상 프로그램
- `SourceBaseline`: Branch, Commit, Workspace 상태
- `ProgramProfile`: 목적, 구조, 기능, AI 구성, 데이터 흐름
- `BehaviorProfile`: 성공·실패·변동·취약 조건
- `Scenario`: 입력, 실행 절차, 오라클, 위험도
- `Run`: 실제 실행 인스턴스
- `Finding`: 재현 가능한 결함·누락·위험
- `Diagnosis`: RCA와 영향 분석
- `ImprovementPlan`: 변경 목적, Patch 후보, 위험도, Rollback
- `Approval`: 사용자·정책 승인 기록
- `Evidence`: 실행과 판정의 증거
- `MemoryCandidate`: 학습·실패·개선 기억 후보
- `Promotion`: 후보의 활성 기준선 승격 기록
- `GitChangeSet`: Branch, Diff, Commit, PR 연결

## 4. 상태 모델

### 프로젝트 학습 상태

```text
UNREGISTERED
→ REGISTERED
→ INTAKE_READY
→ LEARNING
→ PROFILE_CANDIDATE
→ PROFILE_REVIEWED
→ PROFILE_ACTIVE
```

충돌·입력 부족 시 `HOLD`로 이동합니다. 새로운 Source SHA가 확인되면 `STALE` 상태를 거쳐 증분 학습합니다.

### 검증 Run 상태

```text
PLANNED
→ AWAITING_APPROVAL
→ READY
→ RUNNING
→ OBSERVED
→ DECIDED
→ EVIDENCE_LOCKED
```

실패 상태는 `FAILED`, `RETRYABLE`, `CANCELLED`, `HOLD`, `NOT_RUN`으로 구분합니다.

### 보완 상태

```text
FINDING_CONFIRMED
→ IMPROVEMENT_PLANNED
→ AWAITING_PATCH_APPROVAL
→ PATCH_APPROVED
→ APPLYING
→ APPLIED_NON_FINAL
→ REGRESSION_RUNNING
→ IMPROVEMENT_PROVEN | NO_EFFECT | REGRESSION_DETECTED
→ DELIVERY_READY
```

`APPLIED_NON_FINAL`은 수정이 적용되었지만 최종 검증되지 않은 상태입니다.

### Git 전달 상태

```text
NO_CHANGE
→ WORKTREE_READY
→ CHANGES_APPLIED
→ LOCAL_VERIFIED
→ COMMITTED
→ PUSHED
→ DRAFT_PR_OPEN
→ REMOTE_CI_RUNNING
→ MERGE_READY_CANDIDATE
→ MERGED | ROLLED_BACK | HOLD
```

## 5. 승인 경계

다음은 기본 자동 허용 후보입니다.

- 읽기 전용 분석
- Program Profile 후보 생성
- 검증 계획 생성
- 안전한 테스트 실행
- Patch 미리보기

다음은 사용자 또는 정책 승인이 필요합니다.

- 파일 변경
- 네트워크 명령
- Branch·Commit·Push·PR
- 비용이 큰 모델 실행
- 장시간·고부하 시험

다음은 별도 고위험 승인 대상입니다.

- Merge
- 기준선·정책 변경
- 인증·권한·암호화 변경
- 데이터 삭제·Migration
- Secret 접근
- 외부 배포와 운영 환경 변경

## 6. 모델·Provider 구조

```text
Planner Provider
Improvement Provider
Reviewer Provider
Embedding Provider
Local Model Provider
Enterprise Private Provider
```

특정 LLM에 제품을 종속하지 않습니다. 모든 Provider 호출은 모델 ID, 버전, 설정, 입력 범위, 비용과 데이터 전송 정책을 기록합니다.

## 7. 배포 구조

### Developer

```text
VS Code Extension
+ Local Runtime
+ Local Evidence/Memory
+ 선택한 외부 또는 로컬 LLM
```

### Team

```text
개별 VS Code/CLI
+ 팀 Runtime 또는 Local Runtime
+ 중앙 정책·Memory·Evidence Server
+ Git Provider
```

### Enterprise

```text
온프레미스 Runtime Cluster
+ Private Model Gateway
+ 중앙 정책·라이선스·감사 저장소
+ 사내 Git·CI·Artifact·Secret 시스템
```

## 8. 장애·복구 원칙

- 모든 장기 작업은 Checkpoint를 남깁니다.
- Extension 종료와 Runtime 종료를 분리합니다.
- 같은 명령의 중복 실행은 Idempotency Key로 통제합니다.
- Patch 적용 전 복구 포인터를 생성합니다.
- 실행 도중 Source SHA가 변경되면 `HOLD` 처리합니다.
- Evidence가 불완전하면 최종 판정을 발급하지 않습니다.

## 9. 완료 기준

아키텍처는 문서 존재가 아니라 다음 실제 연결로 검증합니다.

```text
VS Code
→ Local Runtime
→ 저장소 학습
→ 검증 Run
→ Patch 승인·적용
→ 회귀검증
→ Git Full-Chain
→ Extension 재시작 후 상태 복구
```

이 Full-Chain의 데이터 객체와 상태 전이가 모두 Evidence로 추적되어야 합니다.
