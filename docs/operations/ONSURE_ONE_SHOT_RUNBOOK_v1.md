# ONSURE One-Shot 실행 Runbook v1

## 1. 목적

개발자나 운영자가 수십 개의 명령을 순서대로 실행하지 않도록 ONSURE의 정적 계약검사와 가능한 실행검증을 하나의 명령으로 묶는다.

## 2. 실행 순서 정책

Codespace 또는 동등한 실행환경은 맨 마지막에 사용한다.

1. GitHub에서 설계·계약·코드·상태 보완
2. PR 정적 검토와 병합
3. 마지막에 Codespace에서 One-Shot 실행
4. 실패 시 생성된 로그와 Receipt로 RCA
5. 수정 후 전체 One-Shot 재실행
6. 독립 OTester·OAudit
7. 실제 VS Code·Web Product Full-Chain

## 3. 실행 모드

### 정적 모드

```bash
bash scripts/onsure-one-shot.sh --static-only
```

검사 항목:

- 필수 설계·계약·Schema 존재
- 전체 JSON 구문
- 요구사항 ID와 상태
- Traceability 참조 파일 존재
- Traceability Summary 일치
- Core/Adapter 분리 계약
- README 내부 링크
- Shell 구문

이 모드는 Java·Maven·Runtime을 실행하지 않으며 결과는 `NON_FINAL`이다.

### Standalone Core 모드

```bash
bash scripts/onsure-one-shot.sh --profile core
```

ORUDA 파일이나 Adapter를 필수로 요구하지 않는다.

실행 항목:

- 정적 모드 전체
- Java 17과 Maven Preflight
- Maven/JUnit
- Python 회귀시험
- 사용 가능한 범용 Harness 2회
- 로그·단계 Receipt·Hash Manifest

성공 상한은 `SELF_VALIDATION_NONFINAL`이다.

### 선택형 ORUDA 모드

```bash
bash scripts/onsure-one-shot.sh --profile oruda
```

Core 검증에 더해 ORUDA Adapter·Fixture·고정 ONGuard 대상을 검증한다. ORUDA 검증 실패가 Core 설계 독립성을 변경하지 않는다.

## 4. 출력 위치

기본 출력:

```text
.onsure/one-shot/<UTC timestamp>/
```

주요 파일:

- `result.json`
- `repository-contract-report.json`
- `source-commit.txt`
- `tracked-index.sha256`
- `logs/*.stdout`
- `logs/*.stderr`
- `receipts/*.json`
- `evidence.sha256`

## 5. 실패 처리

One-Shot은 단계 실패를 다음과 같이 처리한다.

- 즉시 중단
- 실패 단계 ID 기록
- stdout와 stderr 분리 보존
- 단계 Receipt 기록
- 전체 결과 `FAIL`
- Final claim 금지 유지

실패 로그를 숨기거나 `SKIPPED`를 PASS로 바꾸지 않는다.

## 6. 결과 해석

- `ONSURE_ONE_SHOT_STATIC_NONFINAL`: 정적 계약과 Shell 검사는 통과했지만 Runtime은 미실행
- `ONSURE_ONE_SHOT_SELF_VALIDATION_NONFINAL`: 사용 가능한 내부 자동화는 통과했지만 독립 검증과 제품 Full-Chain은 미실행
- `ONSURE_ONE_SHOT_FAIL`: 하나 이상의 필수 단계 실패

## 7. Final 금지 조건

다음 중 하나라도 충족하지 않으면 FinalLock·Production GO·Commercial GO를 금지한다.

- 실제 Standalone VS Code Full-Chain 2회
- 필요한 Web Full-Chain 2회
- 현재 Source 기준 Evidence
- Critical/High 0건
- 독립 OTester 2회
- 독립 OAudit 2회
- 책임·실행 Identity와 Signing Key 분리
- Rollback과 Recovery
- Human Acceptance

## 8. 권장 운영 명령

사용자는 최종적으로 다음 명령 하나만 실행한다.

```bash
bash scripts/onsure-one-shot.sh --profile core
```

추가 대상 Adapter는 제품 Core가 통과한 뒤 별도 Profile로 실행한다.
