# ONSure Remediation Codex 전용 지침

이 파일은 ONSure PR #28 작업에만 적용하는 참조 문서다. 저장소 루트의 공통 `AGENTS.md`로 사용하지 않으며, 다른 브랜치·채팅·제품 작업에는 자동 적용하지 않는다.

## 적용 범위

- 저장소: `babyandi/ONSure`
- 브랜치: `agent/onsure-final-remediation-20260729`
- PR: Draft PR #28
- 설계 정본: `docs/official-baseline/v2026.07.29/`

## Codex 참조 순서

1. `docs/codex/ONSURE_REMEDIATION_AGENT_20260730.md`
2. `README.md`
3. `docs/official-baseline/v2026.07.29/README.md`
4. `docs/verification/ONSURE_REMEDIATION_WORKLOG_20260730.md`
5. `assets/onsure-remediation/manifest.v1.json`
6. `scripts/run-onsure-remediation.sh`
7. `status/remaining-work-register.v1.json`

Codex에 작업을 넘길 때 다음과 같이 이 파일을 명시한다.

```text
docs/codex/ONSURE_REMEDIATION_AGENT_20260730.md를 먼저 읽고 해당 범위에서만 작업해.
```

## 실행 계약

```bash
./scripts/run-onsure-remediation.sh --codespace-final
```

- 완료되지 않은 항목을 PASS, CLEAN, Final 또는 Production GO로 기록하지 않는다.
- GitHub Actions를 사용하지 않는다.
- 독립 ONTester/ONAudit 실행기는 `ONSURE_ONTESTER_RUNNER`, `ONSURE_ONAUDIT_RUNNER`로만 주입한다.
- 독립 실행기나 사람 승인이 없을 때 Exit 75/HOLD는 정상적인 Fail-Closed 결과다.

## 이미지·자산

- 실제 사용한 이미지만 `assets/onsure-remediation/`에 저장한다.
- SHA-256, 출처, 사용권, 용도, 연결 문서를 Manifest에 기록한다.
- 현재 이 코드 Remediation에는 사용 이미지가 없으며 Manifest의 `assets` 배열은 비어 있다.
