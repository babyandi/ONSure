# ONSURE

ONSURE는 등록된 AI 프로그램과 일반 소프트웨어를 학습·검증하고, 검증된 Finding에 한정해 승인형 개선과 Git 전달을 수행하도록 설계된 독립형 제품입니다.

## 제품 원칙

- **Evidence over assertion** — 실행 증거 없는 PASS를 금지합니다.
- **Fail closed** — 누락·충돌·NOT_RUN·HOLD·독립성 부족을 완료로 바꾸지 않습니다.
- **Standalone first** — ORUDA 없이 Core가 빌드·시험·실행돼야 합니다.
- **Fixed trust root** — 승인 검증의 Key Registry와 Replay Ledger를 요청자가 선택할 수 없습니다.
- **Transition revalidation** — Commit 때 유효했던 승인도 Push·Draft PR 직전에 다시 검증합니다.
- **Bounded execution** — 자식 프로세스는 출력 Drain·크기 제한·Wall-clock Timeout·Process-tree 종료를 함께 적용합니다.
- **Source-derived requirements** — 사람이 작성한 예상 ID가 아니라 규범 문서의 실제 Bullet을 요구사항 권위로 사용합니다.
- **Acceptance is not implementation** — 클래스와 기능이 존재해도 실제 사용자 여정이 성공하지 않으면 MVP 완료가 아닙니다.

## 검증 실행 정책

ONSURE 저장소는 **GitHub Actions를 사용하지 않습니다.**

- `.github/workflows/*.yml`과 `.yaml`은 금지합니다.
- 검증은 저장소 내부 로컬 실행기로만 수행합니다.
- 실행 결과는 `.onsure/` 아래 Receipt·로그·Hash로 보관합니다.
- 과거 원격 자동화 결과는 감사 이력일 뿐 현재 소스의 PASS 증적이 아닙니다.
- 로컬 자체검증은 `SELF_VALIDATION_NONFINAL` 상한을 넘을 수 없습니다.

## 현재 구현된 주요 경계

- Core·CLI·Loopback Local API·Optional ORUDA Adapter 모듈 경계
- Workspace·Project·Target 등록 Workflow
- Program/Behavior Profile 후보와 Observation Receipt
- 위험 기반 Plan, 전체·부분 서명 승인, 승인되지 않은 Stage 실행 차단
- 원본 Plan·승인 Plan·서명 Receipt·고정 Trust Root·소비 Ledger의 Approval Bundle 검증
- OReview와 Evidence-based RCA
- 승인형 Patch·Worktree·Rollback·Improvement Proof
- Commit 승인과 Push·Draft PR 전 승인 만료·Identity·서명·소비 상태 재검증
- Source Identity·Receipt·Ledger·Replay·Cross-process File Lock
- Rootless Bubblewrap Sandbox와 적대 Fixture
- Bounded child-process runner
- VS Code Extension·VSIX 및 OLicense·Service Case 상태 코어
- VS Code의 등록 → 정적 학습 → 실행계획 생성 → 외부 서명 승인 검증 → 승인 Bundle 기반 Validation 연결
- 등록 identity snapshot 기반 14개 VS Code 전용 View와 재시작 상태 복구
- 서명 Patch 승인 → 격리 Worktree → Improvement Proof → 승인 Commit·Draft PR 연결
- digest 검증 Hunk diff·외부 서명 요청과 checkpoint 기반 Autopilot pause/resume/cancel
- Ask/Plan/Act/Verify/Improve/Autopilot/Audit/Offline별 fail-closed 실행 권한
- ValidationEngine 단계별 digest-chain checkpoint, 자동 resume, stage idempotency/replay ledger와 Runtime 상태 표시
- Provider/Model 교체 adapter 계약, 독립 local/mock 구현과 timeout·rate-limit·비용·fallback 금지 검증
- stage-bound validation context snapshot과 digest 결속 replay
- process birth identity에 결속된 Autopilot orphan control 복구
- snapshot 기반 결정론적 ASK/PLAN과 독립 Provider SPI·Public SDK 후보 모듈
- 승인 request/receipt/plan scope verifier와 HMAC 기반 프로젝트 지식 익명화 후보
- 성능 baseline·bounded soak·장애주입·합성 DR 도구와 배포/DB 실행 후보
- 실제 offline 재설치를 검증한 Maven/npm air-gap pack
- root·8개 Maven 모듈·VS Code dependency inventory를 결합한 SBOM과 Trivy/license gate

## 이번 메타감사에서 확인된 검출기 사각지대

이전 검증은 28개 대분류 기능군, 파일·클래스·테스트 존재, 사람이 작성한 예상 Requirement ID를 주로 확인했습니다. 그 결과 다음 결함을 놓쳤습니다.

- 대분류 `PARTIAL` 안에 숨은 증분 학습·필수 View·Pause/Resume·Public SDK 누락
- 규범 문서에 존재하지만 예상 ID 목록에서 빠진 5개 하위 요구
- 요구사항 구현과 MVP 사용자 여정 완료를 같은 것으로 취급한 오류
- 설계가 요구한 부분 승인을 전체 승인으로만 제한한 구현
- 승인 Plan JSON만으로 Engine에 진입할 수 있던 서명 Bundle 우회
- 요청자가 Trusted Key Registry·Replay Ledger 경로를 바꿀 수 있던 Trust-root substitution
- `waitFor(timeout)`가 있어도 출력 읽기 순서 때문에 Timeout에 도달하지 못하는 Process hang
- Commit 때 검증한 승인을 Push 시점에 재검증하지 않는 상태전이 누락
- Core 기능이 존재하지만 CLI·Local API·VS Code 제품 표면에 연결되지 않은 경로
- MVP 수용 시나리오 10단계와 실제 저장소 2회 연속 성공 조건의 미추적
- Workflow Operation 수를 여러 상태 파일에서 손으로 중복 관리해 40개를 39개로 잘못 기록한 오류

이를 방지하기 위해 다음 권위 검사를 사용합니다.

```text
28개 설계·프로세스·데이터 실패주입
10개 원자 Requirement 실패주입
6개 Actions 금지·로컬 자동화 실패주입
15개 Verification Claim 실패주입
5개 Legacy 제품 하위 Requirement 실패주입
6개 Workflow Surface 실패주입
24개 Critical Callpath 실패주입
8개 Legacy MVP Acceptance 실패주입
8개 Final 제품 Requirement 실패주입
8개 Final Acceptance 실패주입
합계 118개
```

권위 파일:

- `status/product-subrequirement-coverage.v1.json` — 규범 문서에서 자동 추출한 43개 제품 하위 요구
- `scripts/validate-product-subrequirements.py` — 원문 Bullet과 대장의 1:1 매핑
- `status/mvp-acceptance-coverage.v1.json` — 10단계 사용자 여정과 2회 연속 성공 조건
- `scripts/validate-mvp-acceptance-coverage.py`
- `scripts/validate-mvp-status-consistency.py`
- `contracts/workflow-operation-registry.v1.json` — 43개 Workflow Operation 단일 권위
- `scripts/validate-workflow-surface-parity.py` — 43개 Workflow·3개 제품 표면
- `scripts/validate-critical-callpaths.py`
- `contracts/validation-case-registry.v1.json` — 성공·실패·공격 사례 단일 권위 목록
- `scripts/validate-validation-case-registry.py`
- `contracts/omission-failure-injection-counts.v1.json` — 실패주입 단일 분모
- `status/omission-detection-status.v1.json`
- `status/verification-status.v1.json`
- `status/remaining-work-register.v1.json`

## 명시적으로 미완료인 주요 기능

- 변경분 기반 증분 Program Learning
- Tool Contract 내용 분석과 실행 로그 인벤토리
- Behavior Profile의 취약 조건 분류와 Production 정책 Telemetry
- RCA의 명시적 영향 범위와 미확인 사항
- Patch 적용 전 위험도·영향 범위·Rollback 방법 Preview
- 프로젝트 지식 익명화의 실제 data-owner 승인과 공통 지식 승격 UX
- MVP 수용 시나리오 11개 전 항목 및 실제 저장소 2회 연속 성공
- 외부 signer 실제 연동과 승인 request/receipt 교환 Full-Chain
- 실제 외부 Provider 기반 Ask/Plan 응답과 설치 Extension Host 전체 사용자 여정
- VS Code 부분 Plan 승인 UX
- 비가역 stage side effect의 제품별 보상 작업
- 실제 Remote Provider 구현 및 가격 견적 연동
- Public SDK publish·외부 소비자 호환성 검증
- Identity·RBAC·Cross-tenant 격리
- Approval Replay Ledger의 외부 Anchor
- proprietary 고객 배포계약과 독립 라이선스/NOTICE 검토
- 장시간 성능·운영 DR 및 실제 배포/DB 실행 Pack
- 실제 Payment Provider와 Production Model Telemetry
- 독립 OTester·OAudit와 Human Acceptance

## 로컬 단일 검증

범용 검증은 대상 저장소에 ONSure 전용 Manifest나 ORUDA/OReport 연결을 요구하지 않는다.
환경·의존성 → 구조 → 검증기 메타 → 단계 기능 → 실제 연결 E2E → 증적·판정 → 운영·복구
순서를 고정하며, 실행되지 않은 필수 항목은
`NOT_RUN`, 환경 제한은 `BLOCKED`, 증거가 불충분하면 `INCONCLUSIVE`다.

```bash
mvn -B -ntp -q compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=io.onsure.platform.ONSureCli \
  -Dexec.args="universal /absolute/source-root profile-id /absolute/empty-run-root"
```

대상 원본을 수정하지 않고 renderer, font, ClamAV, 서명 Fixture와 실행 권한을 필수조건으로
추가하려면 ONSure workspace에 `ONSURE_ENVIRONMENT_REQUIREMENT_PROFILE_V1` 파일을 만들고
다섯 번째 인자로 전달한다. 예시는 `config/validation/environment-requirements.example.json`이다.
프로필은 엄격히 파싱되며 의미 digest와 원본 파일 SHA-256이 실행 영수증에 기록된다.

```bash
mvn -B -ntp -q compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=io.onsure.platform.ONSureCli \
  -Dexec.args="universal /absolute/source-root profile-id /absolute/empty-run-root /absolute/environment-profile.json"
```

등록 Workflow/API에서는 `validation.run` 요청의 `environment_profile_file`에 workspace 내부
프로필 경로를 전달한다. Node dependency가 있으면 내장 Pack이 구조 검사 전에 고정된
`npm --offline ci --ignore-scripts`를 격리 snapshot에서 실행한다. lock/manifest drift 또는
offline cache 누락은 1단계 `BLOCKED`이며 2~7단계는 실행하지 않는다.
관리화면의 프로그램 검증 프로필도 `UNIVERSAL`을 선택하면 `/v1/programs/validate`가 같은
Runner를 사용한다. 선택한 workspace 내부 환경 프로필, 원본 불변성, universal receipt digest,
검증군 판정이 `validation-report.json` projection과 관리 감사 이력에 함께 기록된다.

등록된 Target은 기존 `validation.run` 요청에 `validation_mode=UNIVERSAL`을 지정해 실행한다.
실행 전 후보만 검토하려면 다음 명령을 사용한다. Node script, Maven module, Java/Python main,
OpenAPI operation, DB migration과 배포 정의를 source digest에 결속해 표시하지만 대상 명령은
실행하지 않으며 모든 후보는 `DISCOVERY_ONLY_REVIEW_REQUIRED`, `auto_execute=false`다.

```bash
mvn -B -ntp -q compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=io.onsure.platform.ONSureCli \
  -Dexec.args="inventory /absolute/source-root"
```

Local API 권위 계약은 `contracts/openapi/onsure-local-api.v1.json`, 범용 결과 계약은
`contracts/universal-validation-result.v1.schema.json`이다. 세부 7단계와 Pack SPI는
`docs/architecture/ONSURE_UNIVERSAL_VALIDATION_PROFILE_V1.md`를 따른다.
발견된 OpenAPI 계약은 첫 파일만 대표 검사하지 않고 계약별 독립 Step으로 실행한다.
각 PASS는 실행 로그 read-back SHA-256과 동일 실행환경 digest를 영수증에서 재검증한다.
연결 E2E Pack은 `contracts/portable-workflow-lineage.v1.schema.json` 영수증으로 producer output,
consumer input, 실제 artifact read-back, schema, permit, tester·audit·노출판정을 같은 digest에
결속한다. Pack 개발자는 생성된 snapshot을 다음 읽기 전용 CLI로 독립 재검증할 수 있다.

```bash
mvn -B -ntp -q compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=io.onsure.platform.ONSureCli \
  -Dexec.args="lineage /absolute/execution-snapshot-root"
```

VS Code Extension 개발 검증과 패키징:

```bash
cd vscode-extension
npm test
npm run package
```

사용 순서와 승인 경계는 `vscode-extension/README.md`를 따른다. Extension Host E2E는
`scripts/run-vscode-extension-host-e2e-container.sh`로 고정 VS Code/Xvfb 컨테이너에서 실행하며,
network-disabled 재실행까지 통과해도 제품 Full-Chain 완료를 주장하지 않는다.

정적 비최종 Gate:

```bash
bash scripts/onsure-local-gate.sh --mode static --profile core
```

Java 17·Maven·Sandbox·VSIX 포함 전체 로컬 비최종 Gate:

```bash
bash scripts/onsure-local-gate.sh --mode full --profile core
```

배포·DB migration 설계 경계와 bubblewrap host 진단:

```bash
python3 scripts/validate_onsure_operational_boundary.py
python3 scripts/onsure_deploy_migration_skeleton.py preflight
python3 scripts/validate_onsure_container_candidate.py
python3 scripts/onsure_runtime_assurance.py health
python3 scripts/onsure_bubblewrap_diagnostics.py
python3 scripts/onsure_sandbox_diagnostics.py
```

공급망과 air-gap plan:

```bash
python3 scripts/onsure_supply_chain.py validate
python3 scripts/onsure_airgap_pack.py plan --maven-repository /explicit/path/to/maven-repository
python3 scripts/onsure_airgap_pack.py repository-rehearse --archive /explicit/maven-repository.tar
python3 scripts/onsure_npm_airgap.py verify --archive /explicit/npm-cache.tar
python3 scripts/onsure_trivy_scan.py
```

Local API·LLM Gateway OpenAPI와 관리화면:

```text
http://127.0.0.1:47311/v1/openapi.json
http://127.0.0.1:47312/v1/openapi.json
http://127.0.0.1:47311/admin
```

관리화면은 Local API token을 브라우저 메모리에만 유지하며, 실제 `.onsure` catalog/validation/
improvement 산출물과 Gateway의 content-free token·비용·digest-chain projection을 표시한다.
선택적 VIEWER/OPERATOR/APPROVER token으로 조회·프로그램 실행·Gateway 승인 권한을 분리한다.
프로그램 검증은 원본 대신 bounded snapshot에서 수행하며, Gateway 설정 변경은 distinct approver가
결정한 뒤에도 외부 적용 대기 상태로 남고 모든 상태 변경은 append-only 감사 chain에 기록된다.
Gateway 환경변수와 단독 서버 실행 경계는
`docs/architecture/ONSURE_LLM_GATEWAY_AND_MANAGEMENT_UI_v1.md`를 따른다.

최종 단계 Source-bound One-Shot:

```bash
bash scripts/onsure-final-stage.sh --profile core
```

## 현재 판정 상한

현재 변경 후보의 로컬 검증은 Java 345개(조건부 11개 skip 포함), Python 199개, Node 9개,
Modular package 37개, root 공개 API 265개, SBOM/npm audit와 operational boundary를 통과했고,
로컬 clean Java build는 2회 연속 통과했다.
최신 VSIX는 ZIP metadata와 `[Content_Types].xml` 순서를 정규화해 SHA-256
`fb2f0bf6c5051ebf6197ec8e0f21c8d77fd3316b348016f1ccbd4fdb5dfd9589`를 생성했으며
동일 입력 2회 패키징 결과가 byte-identical했다.
Manifest 후보는 신규 구현을 포함한 886개 파일이며 정확한 파일 수와
digest는 `assurance/migration/onsure-migration-manifest.v1.json`을 정본으로 삼는다. 최신 commit에
결속된 격리 중첩 full rehearsal과 독립 clone 결과는 발행 전 다시 생성한다.
현재 host의 rootless bubblewrap은 private network namespace의 loopback 설정을 거부한다. Runner는
이 실패를 약화하지 않고, 로컬에 이미 존재하는 검증 이미지가 있을 때만 immutable image ID로
고정한 `OCI_DOCKER` backend를 선택한다. 이 backend는 image pull·network·host 원본 mount를
금지하고 read-only rootfs, capability 0, no-new-privileges, AppArmor/seccomp, PID·CPU·memory·timeout
한도를 적용한다. 12개 sandbox boundary probe와 ONSure 자체 및 중립 Java·Python·Node 대상의
정상·실패·재시도·차단·연결 E2E·portable lineage read-back·중단·재개·롤백·재실행이 이 격리
경로에서 `PASS_NONFINAL`이다. 4개 실행의 86개 PASS 단계는
`assurance/runtime/onsure-universal-validation-evidence.v1.json`에 로그·환경·결과 digest로
결속되어 있으며 원본 소스 변경은 0건이다.
Docker는 검증 실행 backend일 뿐 Ubuntu/RHEL systemd 단독 서버 배포 topology를 변경하지 않는다.
VS Code Extension Host E2E는 고정 컨테이너와 offline network에서
실행됐지만 MVP Full-Chain, 독립 OTester/OAudit와 Human Acceptance는 아직 실행되지 않았다.

```text
Assurance      SELF_VALIDATION_NONFINAL / LOCAL_OCI_SANDBOX
MVP Full-Chain NOT_RUN
FinalLock      false
Production GO  false
Commercial GO  false
```
