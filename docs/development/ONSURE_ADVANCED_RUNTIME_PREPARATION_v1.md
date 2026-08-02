# ONSure advanced runtime preparation v1

상태: `PARTIAL / SELF_VALIDATION_NONFINAL`

이 문서는 현재 독립 저장소와 `io.onsure` package를 유지한 채 추가한 실행·복구·통합 준비를 기록한다. 실제 provider credential, 배포, DB 적용, main 병합, Production GO, Final PASS 권한을 부여하지 않는다.

| 영역 | 구현/준비 상태 | 현재 검증 및 한계 |
|---|---|---|
| Java validation context 저장·재생 | `IMPLEMENTED_EXPLICIT_REPLAY` | 각 stage 경계의 `stage-context.json`을 target/run/digest에 결속하고 typed aggregate로 복원. 자동 engine resume는 idempotency 계약 전까지 `false` |
| Autopilot orphan 복구 | `IMPLEMENTED_FAIL_CLOSED` | PID·PGID·Linux start tick·command SHA-256이 모두 같은 process group만 control 재연결. 유실된 stdout/marker 때문에 완료 후 `RCA_REQUIRED` |
| ASK/PLAN | `IMPLEMENTED_LOCAL_DETERMINISTIC` | snapshot 기반 read-only/non-executing Markdown 응답. 실제 모델 provider 호출은 `false` |
| Provider SPI | `CANDIDATE_MODULE` | `modules/onsure-provider-spi`, core 의존 0. 실제 provider/credential/egress 구현은 `NOT_RUN` |
| Public Java SDK | `CANDIDATE_MODULE` | `modules/onsure-sdk`, 명시적 loopback HTTP와 bounded timeout. publish 및 외부 소비자 호환 시험은 `NOT_RUN` |
| Extension Host E2E | `ENVIRONMENT_PREPARED_NOT_RUN` | VS Code 1.95.3과 `@vscode/test-electron` 고정. 현재 host는 display/xvfb 부재로 실행 `NOT_RUN` |
| 승인 request/receipt 추가 결속 | `IMPLEMENTED_INTERNAL_VERIFIER` | plan path/digest, hunk/file/branch/impact/time/safety를 결속. 외부 Ed25519 signer 검증은 기존 receipt gate 책임이며 이 verifier에는 포함하지 않음 |
| 프로젝트 지식 분리 | `IMPLEMENTED_INTERNAL_CANDIDATE` | workspace salt HMAC token으로 project/email/path/IP/secret 익명화. `common.*`만 후보이며 자동 공통 승격 금지·사람 검토 필수 |
| 성능·장애·복구·관측성 | `TOOLING_IMPLEMENTED` | bounded benchmark, nonzero/timeout containment, `.onsure` backup/격리 restore verify, local health. 장시간 부하·운영 DR은 `NOT_RUN` |
| 배포·DB migration | `PREFLIGHT_SKELETON_ONLY` | non-root/read-only/loopback/external-secret 조건 검증. deploy/migrate/rollback은 `NOT_AUTHORIZED`, DB engine/tool은 `NOT_SELECTED` |
| Air-gap dependency pack | `MAVEN_PAYLOAD_IMPLEMENTED_PARTIAL` | local Maven JAR/POM을 SBOM SHA-256에 결속한 deterministic tar. npm cache payload와 외부 signature는 `NOT_RUN` |
| SBOM·취약점·라이선스 | `HARDENED_PARTIAL` | CycloneDX unique purl/SHA-256, license policy, SBOM-bound scan evidence, lock-bound npm audit. npm 0건; Maven vulnerability scan `NOT_RUN`, root license 미선언 |

## 핵심 명령

```bash
mvn -B -ntp -q clean verify
mvn -B -ntp -q -f pom-modular.xml clean package
python3 -m unittest discover -s tests -p 'test_*.py'
(cd vscode-extension && npm ci --ignore-scripts && npm test && npm run test:e2e:preflight)
python3 scripts/onsure_runtime_assurance.py health
python3 scripts/onsure_deploy_migration_skeleton.py preflight
python3 scripts/onsure_airgap_pack.py plan --maven-repository /explicit/maven/repository
python3 scripts/onsure_supply_chain.py validate
```

## 남은 차단요인

- validation stage side effect idempotency와 실제 automatic resume
- 승인된 provider implementation, secret/egress/cost evidence 및 장애 호환 시험
- Extension Host 실행용 display/xvfb와 최초 VS Code binary
- 외부 SDK 소비자 및 artifact publishing/versioning 결정
- 익명화 오탐·누락 corpus, data owner 승인, 공통 지식 승격 workflow
- 운영 부하·장시간 soak·실제 backup/restore/DR 환경
- 배포 topology/base image/orchestrator 및 database 도입 ADR
- npm air-gap cache export, pack 외부 signature와 install rehearsal
- 승인된 Maven vulnerability scanner/database와 24시간 이내 scan evidence
- root source license, copyright, redistribution/NOTICE 승인

독립 OTester·OAudit와 Human Acceptance는 모두 `NOT_RUN`이며 이 문서의 PASS는 비최종 자체검증 상한을 넘지 않는다.
