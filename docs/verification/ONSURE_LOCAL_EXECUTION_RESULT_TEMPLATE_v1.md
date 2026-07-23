# ONSURE Local Execution Result Template v1

실제 실행 증거가 없는 항목은 `PASS`로 기록하지 않는다.

## 1. 실행 식별

- Issue: #4
- PR: #2
- Branch: `design/assurance-architecture-v1`
- Run root:
- Assurance run ID:
- Run started at (UTC):
- Source commit SHA:
- 실행자:
- OS:
- JDK:
- Maven:

## 2. 공식 실행 명령

```bash
bash scripts/preflight-local-assurance.sh
bash scripts/run-local-assurance.sh
bash scripts/verify-local-assurance.sh receipts/local/<run-directory>
bash scripts/summarize-local-assurance.sh --verify receipts/local/<run-directory>
```

최종 Gate:

```bash
bash scripts/run-local-assurance-twice.sh
```

## 3. 단일 실행 결과

```text
Preflight                  NOT_RUN
Source binding             NOT_RUN
Security Finding Gate      NOT_RUN
Maven compile              NOT_RUN
JUnit                      NOT_RUN
A01~A20 Fixture            NOT_RUN
Regression-1               NOT_RUN
Regression-2               NOT_RUN
Regression equality        NOT_RUN
OTester                     NOT_RUN
OAudit                      NOT_RUN
Final Lock                 NOT_RUN
Ledger chain               NOT_RUN
Per-run Ledger binding     NOT_RUN
Final Receipt              NOT_RUN
Read-only Verify           NOT_RUN
Critical/High              UNKNOWN
Gate                       HOLD
```

## 4. 증거 Inventory

| Evidence | Path | Status | SHA-256 |
|---|---|---|---|
| Run Context | `run-context.json` |  |  |
| Source Lock | `source-lock.json` |  |  |
| Fixture Contract Snapshot | `adversarial-transition-fixtures.snapshot.json` |  |  |
| Security Findings Snapshot | `security-findings.snapshot.json` |  |  |
| Regression-1 Summary | `regression-1/test-summary.txt` |  |  |
| Regression-1 Classes | `regression-1/classes.sha256` |  |  |
| Regression-1 Fixture Report | `regression-1/adversarial-fixtures.tsv` |  |  |
| Regression-1 Manifest | `regression-1/evidence.sha256` |  |  |
| Regression-2 Summary | `regression-2/test-summary.txt` |  |  |
| Regression-2 Classes | `regression-2/classes.sha256` |  |  |
| Regression-2 Fixture Report | `regression-2/adversarial-fixtures.tsv` |  |  |
| Regression-2 Manifest | `regression-2/evidence.sha256` |  |  |
| OTester Receipt | `otester/receipt.json` |  |  |
| OAudit Receipt | `oaudit/receipt.json` |  |  |
| Registry Snapshot | `key-registry.snapshot.json` |  |  |
| Final Lock | `final-lock.sha256` |  |  |
| Final Receipt | `final-receipt.json` |  |  |
| Global Ledger | `../receipt-ledger.jsonl` |  |  |

## 5. Hash와 결속

- Source tree SHA-256:
- Policy SHA-256:
- Fixture Contract Snapshot SHA-256:
- Security Findings Snapshot SHA-256:
- OTester input digest:
- OTester receipt SHA-256:
- OAudit input digest:
- OAudit receipt SHA-256:
- Registry snapshot SHA-256:
- Final lock SHA-256:
- Final Receipt per-run Ledger head:
- Current global Ledger head:
- Final Receipt verified at:

## 6. 재현성

- Regression summary identical:
- Compiled class hash identical:
- A01~A20 report identical:
- Regression-1 Manifest valid:
- Regression-2 Manifest valid:

## 7. Fixture 결과

`regression-2/adversarial-fixtures.tsv`의 각 행을 기록한다.

| Fixture | Expected Decision | Expected Reason | Actual Decision | Actual Reasons | Result |
|---|---|---|---|---|---|
| A01~A20 |  |  |  |  | NOT_RUN |

## 8. Security Finding 결과

- Review status:
- Review method:
- Finding 총 건수:
- Open Critical:
- Open High:
- Accepted-risk Critical/High:
- Gate result:

## 9. 전체 Runner 연속 2회

- Run root 1:
- Run root 2:
- 두 실행 Source Lock 동일:
- 두 실행 Fixture Snapshot 동일:
- 두 실행 Security Snapshot 동일:
- 두 실행 Regression Summary 동일:
- 두 실행 Compiled Class Hash 동일:
- 두 실행 A01~A20 Report 동일:
- Run 1 현재 저장소 기준 재검증 PASS:
- Run 2 현재 저장소 기준 재검증 PASS:
- 후속 Ledger append 후 Run 1 per-run 결속 재검증 PASS:
- `LOCAL_ASSURANCE_TWICE_PASS` 확인:

`evidence.sha256`는 실행별 절대 경로가 포함되므로 전체 실행 간 바이트 동일성을 요구하지 않는다. 각 실행에서 Manifest 파일 집합과 SHA-256을 검증한다.

## 10. 실패와 RCA

- 최초 실패 단계:
- 오류 코드:
- 재현 명령:
- 직접 원인:
- 근본 원인:
- 영향 범위:
- 최소 수정:
- 추가 Fixture/Test:
- 집중 테스트 결과:
- 전체 2회 재실행 결과:

## 11. 최종 판정

- Source binding:
- Critical/High 미해결 건수:
- OTester/OAudit 독립성:
- Final Lock·Ledger·Final Receipt:
- 두 실행 현재 저장소 기준 재검증:
- Issue #4 종료 여부:
- PR #2 Ready 전환 여부:
- 병합 여부:
- 최종 Gate: `HOLD`
