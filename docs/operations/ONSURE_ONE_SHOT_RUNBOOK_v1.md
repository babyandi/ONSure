# ONSURE One-Shot 실행 Runbook v1.1

## 1. 목적

개발자나 운영자가 수십 개의 명령을 순서대로 실행하지 않도록 ONSURE의 정적 계약검사와 가능한 실행검증을 하나의 명령으로 묶는다.

이 Runbook은 제품 완료나 Final 판정을 부여하지 않는다. Issue #20의 P0가 열려 있는 동안 Runtime 모드는 필요한 내부 시험이 통과하더라도 최종 결과를 `BLOCKED_NONFINAL`로 유지한다.

## 2. 실행 순서 정책

Codespace 또는 동등한 실행환경은 맨 마지막에 사용한다.

1. GitHub에서 설계·계약·코드·상태 보완
2. Branch·PR 정적 검토와 비최종 통합
3. Codespace-free P0가 0건인지 확인
4. 마지막에 Codespace에서 One-Shot 실행
5. 실패 시 생성된 로그와 Receipt로 RCA
6. 수정 후 전체 One-Shot 재실행
7. 실제 제품 Full-Chain
8. 독립 OTester·OAudit
9. Human Acceptance

## 3. 실행 모드

### 3.1 정적 모드

```bash
bash scripts/onsure-one-shot.sh --static-only --profile core
```

검사 항목:

- 필수 설계·계약·Schema 존재
- Git 추적 JSON·JSONL 구문
- 구현 상태와 검증 상태 분리
- Traceability 참조 파일과 집계
- Core/Adapter 경계 계약
- 상태 머신 분리 규칙
- 추적 Markdown 내부 링크
- 추적 Shell 구문
- 안전한 상태 대장

현재 한계:

- JSON Schema Instance 적합성: `NOT_RUN`
- YAML Parser 검증: `NOT_RUN`
- Java·Maven·Runtime: `NOT_RUN`
- 원자 Requirement 100% 추적성: `NOT_COMPLETE`

정적 검사가 통과하면 종료 코드는 0이지만 결과는 `NON_FINAL`이다.

### 3.2 Core 지향 Runtime 모드

```bash
bash scripts/onsure-one-shot.sh --profile core
```

실행 항목:

- 정적 모드 전체
- Java 17과 Maven Preflight
- Maven/JUnit
- Python 회귀시험
- 내부 범용 Harness 2회
- 환경·명령·Source SHA·로그·단계 Receipt·Hash Manifest

현재 Core는 ORUDA Adapter를 기본 등록하지 않지만 단일 Maven Module과 일부 Core Class가 ORUDA Class를 직접 참조한다. 따라서 이 모드는 Core 독립 빌드를 증명하지 않는다.

모든 내부 시험이 성공해도 다음 상태로 종료한다.

```text
ONSURE_ONE_SHOT_BLOCKED_NONFINAL
exit code 75
reason=P0_CORE_COMPILE_ISOLATION_AND_ATOMIC_TRACEABILITY_OPEN
```

### 3.3 선택형 ORUDA 모드

```bash
bash scripts/onsure-one-shot.sh --profile oruda
```

Core 지향 시험에 ORUDA Adapter Fixture Pack을 추가한다. ONGuard는 별도 제품 대상이며 이 Profile에 자동 포함하지 않는다.

이 모드도 Issue #20 P0가 열린 동안 `BLOCKED_NONFINAL`로 끝난다.

## 4. Source와 작업공간 조건

One-Shot은 다음을 요구한다.

- Git HEAD 식별 가능
- 추적 변경 0건
- Untracked 파일 0건
- Git Index Digest 보존
- 환경 Manifest와 Tool 상태 보존

출력 디렉터리는 기본적으로 Ignore 대상인 다음 위치를 사용한다.

```text
.onsure/one-shot/<UTC timestamp>-<pid>/
```

사용자 지정 출력 경로가 저장소 안의 Ignore되지 않은 위치라면 Dirty 검사로 차단될 수 있다.

## 5. 주요 출력

- `result.json`
- `repository-contract-report.json`
- `source-commit.txt`
- `tracked-index.sha256`
- `environment.json`
- `logs/*.stdout`
- `logs/*.stderr`
- `receipts/*.json`
- `evidence.sha256`

각 단계 Receipt에는 다음을 포함한다.

- 단계 ID
- 시작·종료 시각
- 명령 인자
- 종료 코드
- Source Commit
- Profile
- Environment Digest
- stdout·stderr Hash
- Receipt Self Hash

## 6. 실패와 BLOCKED 처리

### 실패

- 필수 명령·파일 누락
- Dirty 또는 Untracked Workspace
- 정적 계약 오류
- Shell·Test·Harness 실패
- Receipt 생성 실패

결과:

```text
ONSURE_ONE_SHOT_FAIL
```

### 구조적 BLOCKED

실행 가능한 내부 시험은 통과했지만 Core 모듈 분리, 원자 추적성 또는 독립 Gate가 남은 경우다.

결과:

```text
ONSURE_ONE_SHOT_BLOCKED_NONFINAL
```

`BLOCKED`는 PASS로 승격하거나 숨기지 않는다.

## 7. 내부 Receipt 해석

Local OTester·OAudit 표기는 서로 다른 JVM과 Key를 사용한 내부 역할 분리다. 외부 독립 권위를 의미하지 않는다.

필수 상태:

```text
assurance_class=SELF_VALIDATION_NONFINAL
independent_otester=NOT_RUN
independent_oaudit=NOT_RUN
final_lock_allowed=false
production_go=false
commercial_go=false
```

## 8. Final 금지 조건

다음 중 하나라도 충족하지 않으면 FinalLock·Production GO·Commercial GO를 금지한다.

- Issue #20 P0 0건
- ORUDA Module 제거 상태의 Core Clean Build·Test·E2E 2회
- 원자 Requirement Traceability 100%
- 실제 Standalone VS Code Full-Chain 2회
- 필요한 Web Full-Chain 2회
- 현재 Source 기준 Evidence
- Critical/High 0건
- 별도 Identity·Key·Environment의 독립 OTester 2회
- 별도 Identity·Key·Environment의 독립 OAudit 2회
- Rollback과 Recovery
- Human Acceptance

## 9. 현재 권장 명령

Codespace 사용 전에는 다음 정적 명령만 권장한다.

```bash
bash scripts/onsure-one-shot.sh --static-only --profile core
```

Runtime 명령은 Issue #20의 Codespace-free P0 정리가 끝난 맨 마지막에 실행한다.
