# ONSURE Final Execution Environment v1

## 목적

Issue #4 종료와 PR #2 Ready 판단에 필요한 JDK 17·Maven 로컬 실행 환경과 최종 실행 명령을 고정한다.

## Codespace

PR #2의 `design/assurance-architecture-v1` 브랜치에서 Codespace를 생성한다.

`.devcontainer/devcontainer.json`은 다음을 고정한다.

- JDK 17 기반 Java Dev Container
- Maven 설치
- Gradle 미설치
- Java Extension Pack
- 생성 후 `scripts/prepare-assurance-environment.sh` 자동 실행

환경 준비 성공 출력:

```text
ONSURE_ASSURANCE_ENVIRONMENT_READY commit=<sha> java=17 maven=<version>
NEXT_COMMAND bash scripts/execute-issue-4-final-gate.sh
```

## Clean worktree

JDK 17·Maven·Git·sha256sum·cmp가 설치된 Linux clean worktree에서도 동일하게 실행할 수 있다.

```bash
bash scripts/prepare-assurance-environment.sh
```

## 최종 실행

```bash
bash scripts/execute-issue-4-final-gate.sh
```

이 명령은 다음을 수행한다.

```text
환경·commit·clean worktree 확인
-> 전체 Runner 연속 2회
-> 각 실행 현재 Source 기준 재검증
-> 후속 Ledger append 후 Run 1 per-run 재검증
-> 두 실행 Summary 생성
-> 최종 실행 Evidence Manifest 생성
```

성공 출력:

```text
ISSUE4_FINAL_GATE_EVIDENCE_READY <evidence-dir> <run-root-1> <run-root-2>
```

최종 증거 폴더:

```text
receipts/local/final-gate-<timestamp>-<pid>/
├─ whole-run-twice.log
├─ run-1-summary.md
├─ run-2-summary.md
├─ final-gate-result.txt
└─ evidence.sha256
```

## 종료 판단

다음이 모두 확인된 경우에만 Issue #4 종료와 PR #2 Ready·병합 판단을 수행한다.

- `LOCAL_ASSURANCE_TWICE_PASS`
- `ISSUE4_FINAL_GATE_EVIDENCE_READY`
- 두 Summary의 Read-only verifier PASS
- Security Finding Gate PASS
- Critical/High 미해결 0건
- Final Lock·Ledger·Final Receipt PASS

스크립트는 GitHub Issue 종료, PR Ready 전환 또는 병합을 자동 수행하지 않는다. 최종 증거를 독립 검토한 뒤 명시적으로 결정한다.
