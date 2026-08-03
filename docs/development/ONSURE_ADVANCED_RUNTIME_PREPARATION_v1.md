# ONSure advanced runtime preparation v1

상태: `PARTIAL / SELF_VALIDATION_NONFINAL`

이 문서는 현재 독립 저장소와 `io.onsure` package를 유지한 채 추가한 실행·복구·통합 준비를 기록한다. 실제 provider credential, 배포, DB 적용, main 병합, Production GO, Final PASS 권한을 부여하지 않는다.

| 영역 | 구현/준비 상태 | 현재 검증 및 한계 |
|---|---|---|
| Java validation context 저장·재생 | `IMPLEMENTED_AUTOMATIC_RESUME` | `stage-context.json`과 `stage-replay-ledger.json`을 결속. 중단 stage의 새 파일만 제거하고 이전 파일 수정·삭제·symlink는 fail-closed 처리한 뒤 `validation.resume`으로 재생 |
| Autopilot orphan 복구 | `IMPLEMENTED_FAIL_CLOSED` | PID·PGID·Linux start tick·command SHA-256이 모두 같은 process group만 control 재연결. 유실된 stdout/marker 때문에 완료 후 `RCA_REQUIRED` |
| ASK/PLAN | `IMPLEMENTED_LOCAL_DETERMINISTIC` | snapshot 기반 read-only/non-executing Markdown 응답. 실제 모델 provider 호출은 `false` |
| Provider SPI | `LOCAL_IMPLEMENTATION_TESTED` | 별도 local/mock 모듈이 SPI만 의존. timeout/rate-limit/cost/fallback 금지, retryability, timeout 후 worker 복구 시험 통과. 외부 provider/credential은 `NOT_RUN` |
| Public Java SDK | `CANDIDATE_BASELINED` | loopback HTTP, 구조화 오류, idempotent retry, cursor page, 익명화 helper와 별도 SDK descriptor baseline 검증 |
| Extension Host E2E | `CONTAINER_XVFB_PASS` | VS Code 1.95.3 고정. 첫 실행 및 `network_mode=none` 재실행 모두 extension host exit 0 |
| 승인 request/receipt 추가 결속 | `CONNECTED_CLI_API_VSCODE` | plan path/digest, hunk/file/branch/impact/time/safety와 receipt 만료를 CLI·Local API·VS Code patch apply에서 결속. replay는 기존 서명 receipt ledger가 거부 |
| 프로젝트 지식 분리 | `EXPOSED_SDK_API_CORPUS_TESTED` | Local API/SDK 노출, 1,000-entry corpus와 invalid-IPv4 오탐 회귀 통과. `common.*`만 후보이고 사람 검토 필수 |
| 성능·장애·복구·관측성 | `SYNTHETIC_REHEARSAL_PASS` | benchmark baseline/comparison, bounded soak, synthetic ENOSPC, backup/restore/corruption rejection 증적. 운영 DR은 `NOT_RUN` |
| 배포·DB migration | `CANDIDATE_AND_SYNTHETIC_PASS` | container build·non-root/read-only/no-network 검사와 SQLite 합성 migration/idempotency/transaction rollback/interruption resume/digest drift/lock 시험. 실제 deploy/DB 선택은 `NOT_RUN` |
| Air-gap dependency pack | `OFFLINE_REHEARSAL_PASS` | 4,823-entry Maven repository로 canonical/modular `-o`, 442-entry npm cache로 offline `npm ci` 통과. 외부 signature `NOT_RUN` |
| SBOM·취약점·라이선스 | `SCANNER_COMPLETED_PARTIAL` | root+8 module CycloneDX, 229개 VS Code inventory, Trivy 0.65.0. Jackson 2.18.9 적용 후 모든 severity 0. root license 미선언 |

## 핵심 명령

```bash
mvn -B -ntp -q clean verify
mvn -B -ntp -q -f pom-modular.xml clean package
python3 -m unittest discover -s tests -p 'test_*.py'
(cd vscode-extension && npm ci --ignore-scripts && npm test && npm run test:e2e:preflight)
python3 scripts/onsure_runtime_assurance.py health
python3 scripts/onsure_deploy_migration_skeleton.py preflight
python3 scripts/onsure_airgap_pack.py repository-rehearse --archive /explicit/offline-repository.tar
python3 scripts/onsure_npm_airgap.py verify --archive /explicit/npm-cache.tar
python3 scripts/onsure_trivy_scan.py
python3 scripts/onsure_supply_chain.py validate
```

## 남은 차단요인

- 중단 stage가 기존 파일을 수정한 비가역 side effect의 자동 복구(현재 안전 거부)
- 승인된 외부 provider implementation, secret/egress/cost evidence
- 외부 SDK 소비자 및 artifact publishing/versioning 결정
- 익명화 data owner 승인과 공통 지식 승격 workflow
- 운영 부하·장시간 soak·실제 backup/restore/DR 환경
- 배포 topology/base image/orchestrator 및 database 도입 ADR
- air-gap pack 외부 signature
- Trivy DB update timestamp의 별도 24시간 freshness 증적
- root source license, copyright, redistribution/NOTICE 승인

독립 OTester·OAudit와 Human Acceptance는 모두 `NOT_RUN`이며 이 문서의 PASS는 비최종 자체검증 상한을 넘지 않는다.
