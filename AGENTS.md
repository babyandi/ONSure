# ONSure Codex 작업 지침

## 정본과 작업 범위

- 이 저장소의 ONSure 설계 정본은 `docs/official-baseline/v2026.07.29/`이다.
- 현재 통합 작업 브랜치는 `agent/onsure-final-remediation-20260729`이다.
- 현재 통합 검토 대상은 Draft PR #28이다.
- 완료되지 않은 항목을 PASS, CLEAN, Final 또는 Production GO로 기록하지 않는다.
- GitHub Actions는 사용하지 않는다. 저장소 내부 로컬 실행기와 Source-bound Receipt를 사용한다.

## Codex가 먼저 읽을 파일

1. `README.md`
2. `docs/official-baseline/v2026.07.29/README.md`
3. `docs/verification/ONSURE_REMEDIATION_WORKLOG_20260730.md`
4. `assets/onsure-remediation/manifest.v1.json`
5. `scripts/run-onsure-remediation.sh`
6. `status/remaining-work-register.v1.json`

## 실행 계약

저장소 루트에서 다음 명령 하나를 사용한다.

```bash
./scripts/run-onsure-remediation.sh --codespace-final
```

스크립트는 Source SHA, 브랜치, 깨끗한 Worktree, 단계별 로그와 Receipt를 고정한다. 독립 ONTester/ONAudit 실행기는 각각 `ONSURE_ONTESTER_RUNNER`, `ONSURE_ONAUDIT_RUNNER` 환경변수로만 주입한다. 실제 독립 실행기나 사람 승인이 없으면 Exit 75/HOLD가 정상적인 Fail-Closed 결과다.

## 자산 규칙

- 작업에 실제 사용한 이미지만 `assets/onsure-remediation/`에 저장한다.
- 모든 이미지에는 SHA-256, 출처, 라이선스/사용권, 용도, 연결 문서를 Manifest에 기록한다.
- 외부 URL이나 채팅 첨부파일만 참조하고 Git에 원본을 보관하지 않는 방식은 금지한다.
- 현재 이 Remediation 코드 작업에는 사용 이미지가 없으며 Manifest의 `assets` 배열은 비어 있다.
