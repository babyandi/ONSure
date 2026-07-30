# ONSure 통합 Remediation 작업내역

기준일: 2026-07-30 KST  
저장소: `babyandi/ONSure`  
브랜치: `agent/onsure-final-remediation-20260729`  
PR: `#28 Complete ONSure final remediation gate coverage`

## 반영된 작업

- 13개 공식 설계 정본과 22개 최종 요구사항, 62개 원자 수용기준을 Source Hash에 결속
- Acceptance Case별 실행기, 정상/부정 Oracle, Evidence, Receipt 계약 추가
- Runtime 결과 봉투에 Source Commit, 실행자, 시간, 환경/입출력 Hash, Parent Receipt, Replay nonce 결속
- 가짜 PASS, 일부 Case COMPLETE 과장, 중복 실행, Receipt 변조, Source Drift 차단
- 금융·운영 E2E 실행기 `scripts/run-financial-operations-e2e.sh` 추가
- 설치·Rollback·DR·성능 구현 Lane 실행기 `scripts/run-install-rollback-dr-performance.sh` 추가
- Main Branch Protection 정책·검증 Marker와 Fail-Closed Evidence Gate 추가
- 전체 단계를 묶는 단일 실행기 `scripts/run-onsure-remediation.sh` 구현
- 단계별 로그 Hash, 상태 Receipt, 동일 Source 재개, Codespace 최종 단계 옵션 구현
- ONTester/ONAudit 독립 실행기를 제품팀 자체 결과로 대체하지 못하도록 권한 경계 유지
- Codex 참조 순서와 이미지 자산 관리 규칙을 루트 `AGENTS.md`에 고정

## 확인된 검증

- Python regression: 69/69 PASS
- Final requirement authority/self-test: 22/22 PASS
- Final acceptance authority: 62/62 PASS
- Main protection failure injection: 8/8 detected
- Integrated Prepare: 동일 Source 2회 PASS_NONFINAL
- Shell syntax 및 원격 Blob read-back: PASS
- GitHub Actions: 미사용

## 현재 비최종 상태

다음 항목은 실제 외부 실행환경·독립 권한·사람 승인이 필요하므로 완료로 기록하지 않는다.

- 62개 수용기준 전체의 실제 제품 Runtime 완료
- 실제 설치·업그레이드·백업·복구·DR·장시간 성능 시험
- GitHub 서버의 `main` 보호 설정 Read-back 증적
- 독립 ONTester 2회 CLEAN
- 독립 ONAudit 2회 CLEAN
- 보안·컴플라이언스·업무·운영·Release 사람 승인

```text
FinalLock=false
Production_GO=false
Commercial_GO=false
Final=HOLD
Merge=BLOCKED
```

## 자산

이번 Remediation 변경은 코드·계약·문서 작업이며 별도의 이미지가 사용되지 않았다. 이미지가 추가되면 반드시 `assets/onsure-remediation/manifest.v1.json`에 등록한다.

## 최종 실행

브랜치가 준비된 저장소에서는 다음 한 줄을 실행한다.

```bash
./scripts/run-onsure-remediation.sh --codespace-final
```

원격 갱신과 브랜치 전환까지 포함해야 하는 VS Code/Claude/Codex 터미널에서는 다음 한 줄을 사용한다.

```bash
git fetch origin agent/onsure-final-remediation-20260729 && git switch agent/onsure-final-remediation-20260729 && git pull --ff-only origin agent/onsure-final-remediation-20260729 && ./scripts/run-onsure-remediation.sh --codespace-final
```
