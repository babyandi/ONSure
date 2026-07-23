# ONSURE Local Execution Runbook v1

## 1. 목적과 현재 상태

ONSURE 로컬 검증을 동일한 명령·증거·판정 순서로 수행하는 공식 절차다. GitHub Actions와 외부 CI는 PASS 근거로 사용하지 않는다.

```text
Implementation  IMPLEMENTATION_READY
Execution       NOT_RUN
Gate            HOLD
PR              DRAFT
```

## 2. 사전 조건

```text
JDK 17
Maven
Git
sha256sum
cmp
검증 대상 commit이 checkout된 clean tracked worktree
```

```bash
java -version
mvn -version
git rev-parse HEAD
git status --short
```

`receipts/local/`은 Git 추적 대상이 아닌 실행 증거 디렉터리다.

## 3. Preflight

```bash
bash scripts/preflight-local-assurance.sh
```

검사 항목:

- JDK 17, Maven, Git, sha256sum, cmp
- POM과 단일·2회 Runner, 재검증기, 요약기
- State·Lane·Receipt·Agent Receipt·Run Context·Source Lock·Final Receipt 계약
- Security Finding 계약과 Register
- A01~A20 Fixture
- immutable commit SHA와 clean tracked worktree
- Maven project validate

성공:

```text
LOCAL_ASSURANCE_PREFLIGHT_PASS <commit-sha>
```

Runner는 Preflight를 내부에서 다시 실행한다.

## 4. 단일 전체 Runner

```bash
bash scripts/run-local-assurance.sh
```

```text
Preflight
-> Run Context
-> Source Lock
-> Fixture Contract Snapshot
-> Security Findings Snapshot / blocking gate
-> Maven/JUnit regression-1
-> target 초기화
-> Maven/JUnit regression-2
-> Summary·Class Hash·A01~A20 Report 비교
-> Evidence Manifest 검증
-> OTester 별도 JVM·별도 Ed25519 key
-> OAudit 별도 JVM·별도 Ed25519 key
-> Key Registry Snapshot
-> Final Lock
-> Append-only Ledger
-> Final Receipt와 자기검증
```

성공:

```text
LOCAL_ASSURANCE_PASS <run-root>
```

## 5. 최종 Gate용 전체 Runner 연속 2회

```bash
bash scripts/run-local-assurance-twice.sh
```

검증 내용:

```text
Runner 1 PASS
-> Run 1 현재 저장소 기준 재검증
-> Runner 2 PASS
-> 후속 Ledger append 후 Run 1 per-run 결속 재검증
-> Run 2 재검증
-> Source Lock 동일
-> Fixture·Security Snapshot 동일
-> 양쪽 Summary·Class Hash·Fixture Report 동일
```

`evidence.sha256`는 실행별 절대 경로를 포함한다. 따라서 Manifest 바이트 자체는 전체 실행 간 비교하지 않고, 각 실행에서 정확한 파일 집합과 SHA-256을 검증한 뒤 원본 Evidence를 비교한다.

성공:

```text
LOCAL_ASSURANCE_TWICE_PASS <run-root-1> <run-root-2>
```

OTester/OAudit의 실행 ID·키·서명·Ledger 기록 시각은 실행마다 달라야 하므로 Receipt 바이트 동일성은 요구하지 않는다.

## 6. 필수 실행 증거

```text
receipts/local/<UTC timestamp>-<pid>/
├─ run-context.json
├─ source-lock.json
├─ adversarial-transition-fixtures.snapshot.json
├─ security-findings.snapshot.json
├─ regression-1/
│  ├─ test-summary.txt
│  ├─ classes.sha256
│  ├─ adversarial-fixtures.tsv
│  └─ evidence.sha256
├─ regression-2/
│  ├─ test-summary.txt
│  ├─ classes.sha256
│  ├─ adversarial-fixtures.tsv
│  └─ evidence.sha256
├─ otester/receipt.json
├─ oaudit/receipt.json
├─ keys/otester-public.key
├─ keys/oaudit-public.key
├─ key-registry.snapshot.json
├─ final-lock.sha256
└─ final-receipt.json
```

공통 Ledger:

```text
receipts/local/receipt-ledger.jsonl
```

개인키는 실행 종료 시 삭제한다.

## 7. 읽기 전용 재검증

```bash
bash scripts/verify-local-assurance.sh receipts/local/<run-directory>
```

공식 재검증은 다음을 확인한다.

- 현재 checkout의 commit·tracked tree·policy와 Source Lock
- Run Context
- Fixture Contract Snapshot과 A01~A20 Report
- Security Findings Snapshot과 open Critical/High 0건
- Snapshot과 Source-Locked 저장소 원본의 바이트 일치
- Summary·Class Hash·Evidence Manifest
- OTester/OAudit 계약·역할·시간·입력·서명·키
- Final Lock path/digest/필수 Evidence
- 전체 Ledger chain과 해당 Run ID의 OTester/OAudit Entry·per-run head
- Final Receipt의 Source·Fixture·Security·Registry·Final Lock 결속

성공:

```text
LOCAL_ASSURANCE_REVERIFY_PASS <run-root>
```

다른 commit이 checkout된 상태에서는 Source drift로 fail-closed 된다. Ledger에 후속 실행이 추가되더라도 동일 source 기준 과거 실행의 per-run 결속은 유지되어야 한다.

## 8. 실행 결과 요약

```bash
bash scripts/summarize-local-assurance.sh receipts/local/<run-directory>
bash scripts/summarize-local-assurance.sh --verify receipts/local/<run-directory>
```

출력은 `ONSURE_LOCAL_EXECUTION_RESULT_TEMPLATE_v1.md`와 대응한다. `--verify`가 PASS한 경우에만 open Critical/High 0건을 확인된 것으로 표시한다.

## 9. 실패 코드

```text
1   Preflight fail-closed
64  사용법 오류
69  필수 명령 또는 Run Context 오류
70  JDK 17 불일치 또는 시간 역전
71  실행 폴더·Git 기준 오류
72  dirty worktree
77  Policy Snapshot과 Source-Locked 원본 불일치
78  Security Finding Gate 실패
79  Source Lock 실패
80  Evidence 실패
81  Final Lock 실패
82  Ledger 또는 Final Receipt 검증 실패
83  Ledger append 실패
84  Final Receipt 생성·자기검증 실패
85  Ledger rollback 실패
86  전체 Runner가 run-root를 보고하지 않음
87  전체 2회 Source commit 불일치
88  A01~A20 Fixture Report 계약 불일치
```

## 10. RCA 절차

```text
최초 실패 단계·오류 코드 고정
-> 재현 명령과 실패 증거 보존
-> 직접 원인·근본 원인 기록
-> 최소 수정
-> 집중 Test/Fixture
-> 전체 Runner 연속 2회
-> 두 실행 현재 저장소 기준 재검증
```

부분 테스트 성공만으로 Gate를 올리지 않는다.

## 11. Final Gate

```text
Preflight PASS
JDK 17 compile PASS
JUnit 전체 PASS
A01~A20 예상 판정 일치
Security Finding Gate PASS
단일 Runner 내부 회귀 2회 동일
전체 Runner 연속 2회 PASS
두 실행 Source Lock·Snapshot·Summary·Class Hash·Fixture Report 동일
OTester/OAudit 독립 Receipt PASS
Final Lock PASS
Ledger chain·per-run binding PASS
Final Receipt PASS
두 실행 현재 저장소 기준 재검증 PASS
Critical/High 미해결 0건
```

하나라도 미실행이거나 증거가 누락되면 `HOLD`다.
