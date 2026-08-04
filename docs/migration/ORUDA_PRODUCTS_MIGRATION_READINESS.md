# ONSure → ORUDA-Products 이관 준비 기준

상태: `PREPARATION_ONLY / NONFINAL`

조사 기준: `babyandi/ONSure` 원격 `main` (`dddf75c43ced52d22969462ebd629aa943389991`)

미래 제품 루트 후보: `products/onsure/`

현재 Java namespace: `io.onsure`

미래 Java namespace 후보: `kr.co.oruda.products.onsure`

이 문서는 실제 이관 명령이 아니다. 현재 저장소의 디렉터리, Java package, Maven 좌표와 공개 API는 변경하지 않는다. ORUDA-Products로 파일을 복사하거나 이동하지 않으며, namespace 변경은 미래 cutover의 별도 호환성 작업으로 남긴다.

## 조사 기준선

- GitHub 기본 브랜치: `main`
- 현재 열린 Draft 통합 PR: [#30](https://github.com/babyandi/ONSure/pull/30). 기존 [#27](https://github.com/babyandi/ONSure/pull/27), [#28](https://github.com/babyandi/ONSure/pull/28), [#29](https://github.com/babyandi/ONSure/pull/29)는 #30에 통합된 뒤 병합 없이 닫혔다.
- 원격 `main`과 로컬 `main`은 조사 시점에 61개 커밋 차이가 있었다. 이 문서는 PR이나 로컬 미발행 커밋이 아니라 원격 `main`만 기준으로 한다.
- 루트 `AGENTS.md`: 이관 준비 후보가 Draft PR #29에 추가됨
- 추적 파일: 465개, 약 4.0 MB(준비 산출물 추가 전)
- Git submodule, Git LFS, symlink: 없음
- 실행 비트가 설정된 추적 파일: 6개
- 루트 LICENSE/NOTICE/COPYING: 없음

## 현재 구조와 미래 구조 대응표

| 미래 경로 | 현재 근거 | 이관 시 처리 후보 | 현재 상태 |
|---|---|---|---|
| `products/onsure/product.yaml` | `product.yaml`, `contracts/product-scope.v1.json`, Maven 좌표 | 현재 후보를 ORUDA-Products 최종 schema에 맞춰 변환·검증 | `CANDIDATE_NONFINAL` |
| `products/onsure/AGENTS.md` | `AGENTS.md` | 현재 제품 경계를 유지하며 상위 모노레포 지침 상속 관계만 추가 | `CANDIDATE_NONFINAL` |
| `products/onsure/README.md` | `README.md` | 상대 링크를 제품 루트 기준으로 검증한 뒤 이동 | `MAPPED` |
| `products/onsure/CHANGELOG.md` | `CHANGELOG.md` | immutable cutover SHA와 실제 release 항목을 별도 승인 후 추가 | `CANDIDATE_NONFINAL` |
| `products/onsure/components/` | Local API, LLM Gateway, local web 관리화면, CLI, VS Code 확장, PostgreSQL migration, 동기식 검증 실행기 | 아래 실행 구성요소 표에 따라 배치. 독립 worker는 구현으로 가장하지 않음 | `PARTIAL` |
| `products/onsure/modules/` | `modules/onsure-*`, `onsure_core/` | core와 ORUDA adapter의 전용 source root, split package/cycle/shared root 0건을 그대로 이동 | `MAPPED_MODULE_OWNED` |
| `products/onsure/contracts/` | `contracts/` | 상대 경로와 schema registry를 함께 이동 | `MAPPED` |
| `products/onsure/config/` | `.devcontainer/`, `.vscode/`, `requirements-validation.txt` | 개발환경과 검증 설정을 제품 config/tooling 정책에 맞춰 분리 | `MAPPED_WITH_REVIEW` |
| `products/onsure/deploy/` | Ubuntu 24.04 LTS systemd 단독 서버 주 대상, RHEL 호환 후보, package script, 이전 container 합성 시험 자료 | non-root/read-only/loopback/외부 secret 후보와 배포판별 preflight 증적을 이동. 실제 Production 배포 권한은 포함하지 않음 | `CANDIDATE_NONFINAL` |
| `products/onsure/tests/` | `src/test/`, `modules/*/src/test/`, `tests/`, `fixtures/` | unit/integration/contract/fixture/acceptance로 재분류하되 fixture trust 경계 유지 | `MAPPED_WITH_REVIEW` |
| `products/onsure/assurance/` | `harness/`, `findings/`, `status/`, assurance Java package, 로컬 receipt 규칙 | 정적 권위와 실행 증적을 분리. `.onsure/` 동적 산출물은 이관 source에서 제외 | `MAPPED_WITH_REVIEW` |
| `products/onsure/docs/` | `docs/`, 루트 harness 안내 문서 | 상대 링크와 authoritative document registry를 재결속 | `MAPPED` |
| `products/onsure/assets/` | `vscode-extension/media/`, `assets/admin/` | VS Code·관리화면 자산을 component 소유로 유지하거나 product assets 정책에 등록 | `MAPPED_WITH_REVIEW` |
| `products/onsure/scripts/` | `scripts/`, `ONSURE_PRODUCT_ROOT` resolver | 이관 준비·경계 검증 스크립트는 제품 root 중립화 완료. 나머지 실행 스크립트는 단계적으로 치환 | `PARTIAL` |
| `products/onsure/.obuilder/` | `.obuilder/product-build.yaml` | ORUDA-Products 최종 schema 확정 후 후보를 변환·재검증 | `CANDIDATE_NONFINAL` |

## 실행 구성요소 분류

| 분류 | 현재 구현 | 엔트리포인트/근거 | 미래 위치 후보 | 판정 |
|---|---|---|---|---|
| `api` | Loopback bearer-token Local API | `LocalAuthenticatedApiServer`, `modules/onsure-local-api` | `components/api/` | `IMPLEMENTED_LOCAL_ONLY` |
| `api` | Loopback LLM Gateway | `LlmGatewayServer`, `modules/onsure-llm-gateway` | `components/llm-gateway/` | `IMPLEMENTED_LOCAL_ONLY_NONFINAL` |
| `worker` | 별도 daemon/queue worker 없음. Validation Engine과 Harness가 호출 프로세스 안에서 동기 실행 | `ValidationEngine`, `FixtureHarness`, `UniversalHarnessRunner` | 독립 실행이 필요해질 때 `components/worker/`; 현재는 core module 유지 | `NOT_IMPLEMENTED_AS_COMPONENT` |
| `web` | Local API가 제공하는 설정·검증·증적 관리화면 | `assets/admin/`, `/admin`, `/v1/management-overview` | `components/web/` 또는 `components/api/assets/` | `IMPLEMENTED_LOCAL_ONLY` |
| `cli` | 제품 CLI와 assurance/harness 관리 CLI | `ONSureCli`, `HarnessCli`, `Local*Main`, `modules/onsure-cli` | `components/cli/` 및 내부 `assurance/tools/` | `IMPLEMENTED` |
| `migration` | PostgreSQL/Flyway forward-only migration과 SQLite 합성 rollback/lock runner | `modules/onsure-migration-postgresql`, `scripts/onsure_synthetic_db_migration.py` | `components/migration/` 또는 `modules/migration-postgresql/` | `IMPLEMENTED_CANDIDATE_NONFINAL` |
| `workbench` | VS Code 확장 | `vscode-extension/extension.js` | `components/vscode-extension/` | `IMPLEMENTED_PARTIAL` |
| `adapter` | Optional ORUDA target adapter | `modules/onsure-adapter-oruda`, `io.onsure.platform.oruda` | `components/adapters/oruda/` 또는 `modules/adapters/oruda/` | `IMPLEMENTED_OPTIONAL` |

## Capability와 미래 modules 후보

| Capability | 현재 상태 | 미래 module 후보 |
|---|---|---|
| CORE-ISOLATION | IMPLEMENTED_NONFINAL | `modules/core` |
| WORKSPACE-INTAKE | PARTIAL | `modules/intake` |
| PROGRAM-LEARNING | PARTIAL | `modules/learning` |
| BEHAVIOR-LEARNING | PARTIAL | `modules/learning` |
| RISK-PLANNING | PARTIAL | `modules/planning` |
| OREVIEW | PARTIAL | `modules/review` |
| VERIFICATION-STATIC | PARTIAL | `modules/verification` |
| VERIFICATION-BUILD | DESIGN_ONLY | `modules/verification` |
| VERIFICATION-RUNTIME | PARTIAL | `modules/verification-runtime` |
| VERIFICATION-API-UI | STUB | `modules/verification-surface` |
| VERIFICATION-SECURITY | PARTIAL | `modules/security` |
| VERIFICATION-PERFORMANCE-RECOVERY | PARTIAL_SYNTHETIC | `modules/resilience` |
| RCA | PARTIAL | `modules/rca` |
| IMPROVEMENT-PATCH | PARTIAL | `modules/remediation` |
| IMPROVEMENT-PROOF | PARTIAL | `modules/remediation` |
| GIT-DELIVERY | PARTIAL | `modules/delivery` |
| EVIDENCE-RECEIPTS | PARTIAL | `modules/evidence` |
| LEARNING-MEMORY | PARTIAL | `modules/learning-memory` |
| VSCODE-EXTENSION | PARTIAL | `components/vscode-extension` |
| LOCAL-AUTHENTICATED-API | PARTIAL | `components/api` |
| LLM-GATEWAY-MONITORING | PARTIAL_NONFINAL | `components/llm-gateway` 및 `modules/provider-*` |
| MANAGEMENT-WEB | PARTIAL_LOCAL_ONLY | `components/web` |
| WEB-SERVICE-CASE | PARTIAL | `modules/service-case` |
| OLICENSE | PARTIAL | `modules/licensing` |
| TENANT-IDENTITY | STUB | `modules/identity` |
| SANDBOX | PARTIAL | `modules/sandbox` |
| RETENTION-DELETION | PARTIAL | `modules/governance` |
| OBSERVABILITY-OPERATIONS | PARTIAL_SYNTHETIC | `modules/operations` |
| DELIVERY | PARTIAL | `modules/delivery` |
| DEPLOYMENT | CANDIDATE_NONFINAL | `deploy/` 및 `modules/deployment-contracts` 후보 |

이 표는 package나 파일을 지금 나누라는 지시가 아니다. Capability 간 공개 계약과 의존 방향을 먼저 고정한 뒤, 한 번에 하나의 물리 모듈만 분리해야 한다.

## 제품 전용 코드와 공통 라이브러리 후보

| 후보 | 현재 소유 | 분류 | 추출 전 조건 |
|---|---|---|---|
| Canonical receipt serialization, 서명 검증, replay/file ledger | ONSure assurance | 공통 후보 | ONSure decision vocabulary와 저장 경로를 인터페이스 밖으로 분리하고 두 번째 실제 소비자를 검증 |
| `BoundedProcessRunner`와 sandbox launch 계약 | ONSure platform | 공통 후보 | OS별 실행 정책, timeout/output/resource contract를 제품 독립 schema로 고정 |
| SHA-256 primitive, source identity, immutable source binding | ONSure common/platform/harness | 공통 후보 | `io.onsure.common.Sha256`로 digest primitive를 수렴하고 기존 facade 호환성을 유지. source-tree 정책은 두 번째 실제 소비자 확인 후 별도 추출 |
| Universal fixture/oracle harness | ONSure harness | 공통 후보 | ONSure Final/HOLD 권위를 제거하지 않은 채 실행 primitive만 분리 |
| JSON Schema 검증·계약 registry 도구 | ONSure scripts/contracts | 공통 후보 | 모노레포 공용 schema lifecycle과 versioning owner 확정 |
| RAG preparation request contract | ONSure `io.onsure.rag` | 제품 경계 후보 | `RagPreparationRequest`를 신규 호출 경계로 사용하고, 기존 `ValidationReport` overload는 호환 기간 후 platform adapter로 격리 |
| Cause-aware verification Python | ONSure `onsure_core/` | 공통 후보/중복 검토 | ORUDA의 같은 상대경로 구현과 API·digest가 달라 divergent copy 여부 결정 |
| Product Catalog, Program/Behavior Learning, OReview, RCA, remediation, service case, OLicense | ONSure | 제품 전용 | 공통화하지 않음. ONSure product semantics와 evidence authority 유지 |
| ORUDA adapter와 ORUDA receipt/materialization classes | ONSure optional adapter | 제품 통합 전용 | target-neutral evidence SPI 역전과 adapter 전용 source root 완료. adapter version owner 확정 필요 |

공통 후보는 이번 작업에서 복사·이동·추출하지 않는다. “두 제품에서 이름이 같다”는 이유만으로 공유 라이브러리로 승격하지 않는다.

## 독립 build, test, package, deploy 명령

현재 독립 저장소 기준 명령이다. 미래 모노레포에서는 동일 명령을 `products/onsure/`를 작업 디렉터리로 실행하는 wrapper가 필요하다.

| 목적 | 명령 | 기준 상태 |
|---|---|---|
| 권위 root clean verify | `mvn -B -ntp -q clean verify` | `CANONICAL / PASS_NONFINAL` (current candidate local 2회 + 독립 clone, 각 282 tests) |
| 전체 물리 모듈 build/package | `mvn -B -ntp -f pom-modular.xml clean package` | `PASS_NONFINAL` (11 modules, 37 tests; 독립 clone 포함) |
| Unit/통합 Java regression | `mvn -B -ntp test` | `PASS_NONFINAL` (`clean verify`에 포함, 282 tests) |
| 대표 제품 E2E | `mvn -B -ntp -Dtest=ValidationPlatformE2ETest test` | `PASS_NONFINAL` (`clean verify`에 포함) |
| Python regression | `python3 -m unittest discover -s tests -p 'test_*.py'` | `PASS_NONFINAL` (current local 159 tests; prior independent clone 154 tests) |
| 정적 비최종 gate | `bash scripts/onsure-local-gate.sh --mode static --profile core` | `PASS_NONFINAL` (local + 독립 clone) |
| 전체 비최종 gate | `bash scripts/onsure-local-gate.sh --mode full --profile core` | `FAIL_HOST_ENVIRONMENT` (`bwrap` loopback 권한 거부, downstream 9 failures) |
| VS Code package | `(cd vscode-extension && npm ci --ignore-scripts --no-audit --no-fund && npm test && npm run package)` | `PASS_NONFINAL` (9 Node tests, Gateway 운영 View, VSIX SHA-256 `fb2f0bf6c505...`; proprietary LICENSE/third-party notice 포함) |
| VS Code Extension Host | `bash scripts/run-vscode-extension-host-e2e-container.sh` 후 `--offline` | `PASS_NONFINAL` (Node 22, VS Code 1.95.3/Xvfb, 양쪽 extension host exit 0, engine mismatch 경고 0, offline network 차단) |
| Ubuntu host preflight | `python3 scripts/onsure_ubuntu_host_preflight.py --runtime-root <external-runtime-root>` | `PASS_NONFINAL` (Ubuntu 24.04, 2 services active/enabled, 3 ports loopback-only, config 0600, secret read 0; AppArmor/UFW 정책은 권한 부족 `NOT_RUN`) |
| VS Code ↔ Ubuntu runtime | `python3 scripts/rehearse_onsure_vscode_runtime.py` | `PASS_NONFINAL` (Local API RUNNING, Gateway RUNNING/local-mock, token 23, cost 23, chain valid, content 저장 없음, token 노출 없음) |
| Manifest 생성 | `python3 scripts/onsure_monorepo_manifest.py` | `PASS_NONFINAL` (현재 변경 후보 799 files; 기존 668-file 기준선은 신규 구현 파일로 확장됨) |
| 이관 준비 정합성 | `python3 scripts/validate_monorepo_migration_readiness.py` | `PASS_NONFINAL` |
| Build·모듈 경계 | `python3 scripts/validate_onsure_build_boundary.py` | `PASS_NONFINAL` (171 module-owned main sources, 11 artifacts, artifact/package cycles 0; split package 0, shared source modules 0) |
| 제품 metadata | `python3 scripts/validate_onsure_product_metadata.py` | `PASS_NONFINAL` |
| Public Java API | `python3 scripts/onsure_java_api_baseline.py validate` | `PASS_NONFINAL` (240 public classes, 기존 238 descriptors delta 0 + additive SPI 2) |
| CycloneDX SBOM/license/vulnerability | `python3 scripts/onsure_supply_chain.py validate` | `PASS_NONFINAL` (ORUDA Labs proprietary root 11, Apache-2.0 9, BSD-2-Clause 1; VS Code 229, Trivy/npm audit vulnerability 0, license blocker 0) |
| 컨테이너 후보 | `python3 scripts/validate_onsure_container_candidate.py` | `PASS_NONFINAL` (build/run, UID 65532, read-only, network none, loopback ready; deployment `NOT_RUN`) |
| 배포·DB migration 설계 경계 | `python3 scripts/validate_onsure_operational_boundary.py` | `PASS_NONFINAL / Ubuntu systemd primary, RHEL compatibility candidate, PostgreSQL/Flyway` |
| 배포·DB preflight | `python3 scripts/onsure_deploy_migration_skeleton.py preflight` | `PASS_NONFINAL / deployment·migration NOT_RUN_NOT_AUTHORIZED` |
| Runtime assurance 도구 | `python3 scripts/onsure_runtime_assurance.py health` | `PASS_NONFINAL` (benchmark 비교, bounded soak, ENOSPC, 합성 DR 통과; 운영 long-run/real DR `NOT_RUN`) |
| Air-gap Maven/npm | repository/dependency pack과 `scripts/onsure_npm_airgap.py` | `PASS_NONFINAL` (Maven 5,205-entry offline canonical/modular, dependency 27-entry, npm 442-cache offline install; external signature `NOT_RUN`) |
| bubblewrap 환경 진단 | `python3 scripts/onsure_bubblewrap_diagnostics.py` | `BLOCKED_ENVIRONMENT / BWRAP_LOOPBACK_PERMISSION_DENIED` |
| 중첩 제품 root full rehearsal | `python3 scripts/rehearse_onsure_nested_root.py --mode full` | `PASS_NONFINAL` (799-file cutover + rollback, 10 commands, 외부 제품 저장소 미사용) |
| 열린 PR overlap | `python3 scripts/onsure_pr_overlap.py validate` | `PASS_NONFINAL / INTEGRATION_ORDER_RESOLVED` |
| Deploy | Ubuntu 단독 서버 systemd/package 주 대상, RHEL 호환 후보 | `Ubuntu tar·검증 runtime active·20/20 health·3회 restart PASS_NONFINAL / Production acceptance NOT_RUN` |
| Ubuntu lifecycle | `python3 scripts/onsure_ubuntu_lifecycle.py rehearse` | `PASS_NONFINAL` (33-file package checksum, immutable install, idempotent reinstall, upgrade, rollback; host path 변경 없음) |
| PostgreSQL backup timer | package의 `onsure-backup.service`/`.timer` | `PASS_NONFINAL` (loopback, flock, custom format, pg_restore list, mode 0600, SHA-256, retention; Production enable/run NOT_RUN) |
| DB migration | PostgreSQL/Flyway V1 + Ubuntu 호스트의 실제 임시 PostgreSQL 16.14 + SQLite 합성 runner | `APPLY/IDEMPOTENCY/VALIDATE/DUMP/RESTORE PASS_NONFINAL / PRODUCTION NOT_RUN` |

이전 구현 HEAD `3e2dbcae1c821522b87d6adbda95ef81082cbbbd`는 semantic work-mode 권한,
Java stage checkpoint, provider adapter 경계와 token/data-transfer budget를 추가했다.
로컬 canonical 246/246 2회와 원격 독립 clone 246/246, modular 11/11, API 238/238,
현재 변경 후보는 Python 139/139, Node 9/9를 통과했다. 772-file migration readiness/nested
rehearsal과 독립 clone 결과는 Manifest가 commit SHA에 재결속된 뒤 다시 기록한다.
VSIX는 두 환경에서 byte-identical SHA-256
`8c217e6fc446fdd4938121c6faa810b1c631e3cc7be908d7e4db10ea53374afe`를 생성했다.

현재 후속 후보는 자동 validation replay, local/mock provider, SDK 오류·pagination·retry,
승인 exchange 표면 연결, 익명화 corpus, container/Xvfb E2E, 합성 runtime/DB/DR,
Maven/npm offline pack과 Trivy/SBOM 통합을 추가했다. local clean Java 263/263 2회,
modular 36/36, Python 139/139, Node 9/9, root API 240/240(기존 238 descriptor 무변경), SDK API 5/5를 검증했다.
독립 clone에서 같은 build/test/API/SBOM/readiness/static gate를 통과했고 현재 772-file
중첩 cutover/rollback도 외부 제품 저장소 없이 통과했다.

Standalone 검증은 임시 디렉터리에 `babyandi/ONSure`만 clone한 뒤 위 Maven/Python 명령을 수행한다. `ORUDA`, `aTops`, `AsterDB` workspace는 clone하거나 mount하지 않는다.

VS Code 등록·승인 흐름 implementation HEAD `bf1ace9`, 생성 의존성 경계 fix `c566fe1`,
재현 VSIX implementation/fix HEAD `980d823`·`f5f55d5`, workspace snapshot/전용 view HEAD
`0db1f12`·`aa30f84`와 Hunk review/Autopilot control HEAD `d7c989a`를 통합했다.
local 및 원격 독립 clone HEAD `f5849782cdc49e52c4b724173e93b1a8b8950bfe`에서 권위 build
242/242, modular package 11/11, API 238/238, Python 104/104, Node 7/7, SBOM,
build/operational boundary와 627개 중첩 cutover/rollback이 통과했다. whole-file approval preview
HEAD `e14995c29d29b86fedec31a16311a42160412dcc`에서도 Python 104/104, Node 7/7,
627개 중첩 rehearsal과 독립 clone을 통과했다. 최종 VSIX SHA-256은 두 환경 모두
`d7e75a3fac896d024ed64944821d06142e7525027942b52fa4b0b91e927843cd`이다.
전체 gate의 9개 실패는 canonical build 실패가 아니라 현재 host가 bubblewrap loopback network
namespace 설정을 허용하지 않아 발생한 실행환경 차단이다.

2026-08-04 Ubuntu 후속 검증에서 Local API와 LLM Gateway dual-health soak 44/44,
수동 restart 3/3, 예상 밖 restart 0을 확인했다. 실제 PostgreSQL custom backup의
`pg_restore --list`와 별도 PostgreSQL 16.14 restore/validate/동시 migration lock rehearsal이
통과했다. tenant ownership ledger는 동시 동일 자원 claim에서 단일 tenant만 성공하며,
위변조 후 모든 mutation을 fail-closed하는 회귀시험을 추가했다. VS Code Extension Host는
컨테이너가 최초 `npm ci`를 비-root로 자체 준비하도록 보강했고, Xvfb online 실행과
`--network none` cached offline 실행이 모두 exit 0을 반환했다. 전체 gate는 최신 HEAD에서도
동일한 `BWRAP_LOOPBACK_PERMISSION_DENIED`로 환경 차단됐으며 보안 우회는 적용하지 않았다.
별도 clone은 최신 운영 기능 commit `1125d2b`에서 canonical 282/282, Python 154/154,
API 240/240, proprietary supply-chain validation, migration readiness와 static gate를 외부
제품 workspace 없이 통과했다.

소유자 제공 선언에 따라 root copyright holder는 `ORUDA Labs`, outbound license는
`LicenseRef-ORUDA-Labs-Proprietary`로 기록했다. 다른 개발자·회사·외주, 외부 저장소 source
복사와 외부 asset 복사는 모두 `NONE_ATTESTED`다. RHEL·Ubuntu package는 `LICENSE`, `NOTICE`,
`THIRD_PARTY_NOTICES.md`와 번들 JAR에서 추출한 upstream license 원문 2개를 포함하며 33개
파일/32개 내부 checksum에 결속한다. 현재 RHEL SHA-256은 `74ec4e2c844f...`, Ubuntu는
`e6b53761183a...`다. 이 소유자
선언은 독립 법률 검토, 고객 배포계약, Production GO 또는 Final PASS를 대신하지 않는다.

`pom.xml`은 현재 독립 release 후보 검증의 권위 build다. `pom-modular.xml`은 미래 물리 분해를 위한 compatibility gate이며 release 권위를 갖지 않는다. 이 결정은 `contracts/onsure-build-boundary.v1.json`, `product.yaml`, `.obuilder/product-build.yaml`에서 동일하게 검증한다.

## Manifest 후보

- 생성기: `scripts/onsure_monorepo_manifest.py`
- 산출물: `assurance/migration/onsure-migration-manifest.v1.json`
- 검증기: `scripts/validate_monorepo_migration_readiness.py`
- 각 파일 항목: 현재/미래 후보 경로, SHA-256, byte 크기, Git mode, filesystem owner/group, repository owner, license, 민감정보 pattern 결과
- Manifest 자신의 digest는 재귀 문제 때문에 목록에서 제외하고 그 사유를 top-level에 기록한다.
- Manifest를 포함하는 commit SHA를 Manifest 안에 다시 넣는 자기참조도 피한다. 조사 기준 commit은 이 문서에 기록하고, 실제 cutover에서는 외부 서명 receipt가 immutable source commit과 Manifest digest를 함께 결속해야 한다.
- filesystem owner는 이관 권리자가 아니라 검사 host의 메타데이터다. 저작권자는 별도 owner declaration으로 `ORUDA Labs`에 결속한다.

## 완료 판정 규칙

이 준비 작업이 통과해도 실제 이관 완료, Final PASS, Production GO를 의미하지 않는다. [차단요인 문서](ORUDA_PRODUCTS_MIGRATION_BLOCKERS.md)의 P0 항목이 닫히고, 열린 remediation PR과 기준 브랜치가 수렴하며, 독립 clone 검증과 라이선스/데이터 owner 승인이 있어야 실제 파일 이동 계획을 승인할 수 있다.
