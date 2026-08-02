# ONSure → ORUDA-Products 이관 준비 기준

상태: `PREPARATION_ONLY / NONFINAL`

조사 기준: `babyandi/ONSure` 원격 `main` (`dddf75c43ced52d22969462ebd629aa943389991`)

미래 제품 루트 후보: `products/onsure/`

현재 Java namespace: `io.onsure`

미래 Java namespace 후보: `kr.co.oruda.products.onsure`

이 문서는 실제 이관 명령이 아니다. 현재 저장소의 디렉터리, Java package, Maven 좌표와 공개 API는 변경하지 않는다. ORUDA-Products로 파일을 복사하거나 이동하지 않으며, namespace 변경은 미래 cutover의 별도 호환성 작업으로 남긴다.

## 조사 기준선

- GitHub 기본 브랜치: `main`
- 열린 Draft PR: [#27](https://github.com/babyandi/ONSure/pull/27), [#28](https://github.com/babyandi/ONSure/pull/28), [#29](https://github.com/babyandi/ONSure/pull/29)
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
| `products/onsure/components/` | Local API, CLI, VS Code 확장, 동기식 검증 실행기 | 아래 실행 구성요소 표에 따라 배치. 독립 worker/web/migration은 구현으로 가장하지 않음 | `PARTIAL` |
| `products/onsure/modules/` | `src/main/java`, `modules/onsure-*`, `onsure_core/` | 먼저 split package와 source-set 공유를 제거한 뒤 물리 모듈로 이동 | `BLOCKED` |
| `products/onsure/contracts/` | `contracts/` | 상대 경로와 schema registry를 함께 이동 | `MAPPED` |
| `products/onsure/config/` | `.devcontainer/`, `.vscode/`, `requirements-validation.txt` | 개발환경과 검증 설정을 제품 config/tooling 정책에 맞춰 분리 | `MAPPED_WITH_REVIEW` |
| `products/onsure/deploy/` | 배포 정의 없음 | 지원 배포 형태가 승인된 뒤 신규 작성 | `NOT_PRESENT` |
| `products/onsure/tests/` | `src/test/`, `modules/*/src/test/`, `tests/`, `fixtures/` | unit/integration/contract/fixture/acceptance로 재분류하되 fixture trust 경계 유지 | `MAPPED_WITH_REVIEW` |
| `products/onsure/assurance/` | `harness/`, `findings/`, `status/`, assurance Java package, 로컬 receipt 규칙 | 정적 권위와 실행 증적을 분리. `.onsure/` 동적 산출물은 이관 source에서 제외 | `MAPPED_WITH_REVIEW` |
| `products/onsure/docs/` | `docs/`, 루트 harness 안내 문서 | 상대 링크와 authoritative document registry를 재결속 | `MAPPED` |
| `products/onsure/assets/` | `vscode-extension/media/` | VS Code 자산을 component 소유로 유지하거나 product assets 정책에 등록 | `PARTIAL` |
| `products/onsure/scripts/` | `scripts/`, `ONSURE_PRODUCT_ROOT` resolver | 이관 준비·경계 검증 스크립트는 제품 root 중립화 완료. 나머지 실행 스크립트는 단계적으로 치환 | `PARTIAL` |
| `products/onsure/.obuilder/` | `.obuilder/product-build.yaml` | ORUDA-Products 최종 schema 확정 후 후보를 변환·재검증 | `CANDIDATE_NONFINAL` |

## 실행 구성요소 분류

| 분류 | 현재 구현 | 엔트리포인트/근거 | 미래 위치 후보 | 판정 |
|---|---|---|---|---|
| `api` | Loopback bearer-token Local API | `LocalAuthenticatedApiServer`, `modules/onsure-local-api` | `components/api/` | `IMPLEMENTED_LOCAL_ONLY` |
| `worker` | 별도 daemon/queue worker 없음. Validation Engine과 Harness가 호출 프로세스 안에서 동기 실행 | `ValidationEngine`, `FixtureHarness`, `UniversalHarnessRunner` | 독립 실행이 필요해질 때 `components/worker/`; 현재는 core module 유지 | `NOT_IMPLEMENTED_AS_COMPONENT` |
| `web` | 브라우저 Web UI 없음 | React/Next/Vite/Spring Web 구성 없음 | `components/web/`는 생성하지 않음 | `NOT_IMPLEMENTED` |
| `cli` | 제품 CLI와 assurance/harness 관리 CLI | `ONSureCli`, `HarnessCli`, `Local*Main`, `modules/onsure-cli` | `components/cli/` 및 내부 `assurance/tools/` | `IMPLEMENTED` |
| `migration` | DB schema/migration 도구와 SQL 없음 | Flyway/Liquibase/SQL 0건 | 영속 DB 채택 전 `components/migration/` 생성 금지 | `NOT_IMPLEMENTED` |
| `workbench` | VS Code 확장 | `vscode-extension/extension.js` | `components/vscode-extension/` | `IMPLEMENTED_PARTIAL` |
| `adapter` | Optional ORUDA target adapter | `modules/onsure-adapter-oruda`, `io.onsure.platform.oruda` | `components/adapters/oruda/` 또는 `modules/adapters/oruda/` | `IMPLEMENTED_OPTIONAL` |

## Capability와 미래 modules 후보

| Capability | 현재 상태 | 미래 module 후보 |
|---|---|---|
| CORE-ISOLATION | PARTIAL | `modules/core` |
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
| VERIFICATION-PERFORMANCE-RECOVERY | DESIGN_ONLY | `modules/resilience` |
| RCA | PARTIAL | `modules/rca` |
| IMPROVEMENT-PATCH | PARTIAL | `modules/remediation` |
| IMPROVEMENT-PROOF | PARTIAL | `modules/remediation` |
| GIT-DELIVERY | PARTIAL | `modules/delivery` |
| EVIDENCE-RECEIPTS | PARTIAL | `modules/evidence` |
| LEARNING-MEMORY | PARTIAL | `modules/learning-memory` |
| VSCODE-EXTENSION | PARTIAL | `components/vscode-extension` |
| LOCAL-AUTHENTICATED-API | PARTIAL | `components/api` |
| WEB-SERVICE-CASE | PARTIAL | `modules/service-case` |
| OLICENSE | PARTIAL | `modules/licensing` |
| TENANT-IDENTITY | STUB | `modules/identity` |
| SANDBOX | PARTIAL | `modules/sandbox` |
| RETENTION-DELETION | PARTIAL | `modules/governance` |
| OBSERVABILITY-OPERATIONS | DESIGN_ONLY | `modules/operations` |
| DELIVERY | PARTIAL | `modules/delivery` |
| DEPLOYMENT | DESIGN_ONLY | `deploy/` 및 `modules/deployment-contracts` 후보 |

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
| ORUDA adapter와 ORUDA receipt/materialization classes | ONSure optional adapter | 제품 통합 전용 | Core와 package/compile cycle을 제거하고 adapter SPI만 의존하도록 역전 |

공통 후보는 이번 작업에서 복사·이동·추출하지 않는다. “두 제품에서 이름이 같다”는 이유만으로 공유 라이브러리로 승격하지 않는다.

## 독립 build, test, package, deploy 명령

현재 독립 저장소 기준 명령이다. 미래 모노레포에서는 동일 명령을 `products/onsure/`를 작업 디렉터리로 실행하는 wrapper가 필요하다.

| 목적 | 명령 | 기준 상태 |
|---|---|---|
| 권위 root clean verify | `mvn -B -ntp -q clean verify` | `CANONICAL` |
| 전체 물리 모듈 build/package | `mvn -B -ntp -f pom-modular.xml clean package` | `NOT_RUN` |
| Unit/통합 Java regression | `mvn -B -ntp test` | `NOT_RUN` |
| 대표 제품 E2E | `mvn -B -ntp -Dtest=ValidationPlatformE2ETest test` | `NOT_RUN` |
| Python regression | `python3 -m unittest discover -s tests -p 'test_*.py'` | `NOT_RUN` |
| 정적 비최종 gate | `bash scripts/onsure-local-gate.sh --mode static --profile core` | `NOT_RUN` |
| 전체 비최종 gate | `bash scripts/onsure-local-gate.sh --mode full --profile core` | `NOT_RUN` |
| VS Code package | `(cd vscode-extension && npm install --ignore-scripts --no-audit --no-fund && npm run check && npm run package)` | `NOT_RUN` |
| Manifest 생성 | `python3 scripts/onsure_monorepo_manifest.py` | `NOT_RUN` |
| 이관 준비 정합성 | `python3 scripts/validate_monorepo_migration_readiness.py` | `NOT_RUN` |
| Build·모듈 경계 | `python3 scripts/validate_onsure_build_boundary.py` | `REQUIRED_NONFINAL_GATE` |
| 제품 metadata | `python3 scripts/validate_onsure_product_metadata.py` | `REQUIRED_NONFINAL_GATE` |
| Deploy | 정의 없음 | `NOT_RUN / BLOCKED` |
| DB migration | 구성요소 없음 | `NOT_RUN / NOT_APPLICABLE_CURRENTLY` |

Standalone 검증은 임시 디렉터리에 `babyandi/ONSure`만 clone한 뒤 위 Maven/Python 명령을 수행한다. `ORUDA`, `aTops`, `AsterDB` workspace는 clone하거나 mount하지 않는다.

`pom.xml`은 현재 독립 release 후보 검증의 권위 build다. `pom-modular.xml`은 미래 물리 분해를 위한 compatibility gate이며 release 권위를 갖지 않는다. 이 결정은 `contracts/onsure-build-boundary.v1.json`, `product.yaml`, `.obuilder/product-build.yaml`에서 동일하게 검증한다.

## Manifest 후보

- 생성기: `scripts/onsure_monorepo_manifest.py`
- 산출물: `assurance/migration/onsure-migration-manifest.v1.json`
- 검증기: `scripts/validate_monorepo_migration_readiness.py`
- 각 파일 항목: 현재/미래 후보 경로, SHA-256, byte 크기, Git mode, filesystem owner/group, repository owner, license, 민감정보 pattern 결과
- Manifest 자신의 digest는 재귀 문제 때문에 목록에서 제외하고 그 사유를 top-level에 기록한다.
- Manifest를 포함하는 commit SHA를 Manifest 안에 다시 넣는 자기참조도 피한다. 조사 기준 commit은 이 문서에 기록하고, 실제 cutover에서는 외부 서명 receipt가 immutable source commit과 Manifest digest를 함께 결속해야 한다.
- filesystem owner는 이관 권리자가 아니라 검사 host의 메타데이터다. 저작권·재라이선스 권리는 별도 확인이 필요하다.

## 완료 판정 규칙

이 준비 작업이 통과해도 실제 이관 완료, Final PASS, Production GO를 의미하지 않는다. [차단요인 문서](ORUDA_PRODUCTS_MIGRATION_BLOCKERS.md)의 P0 항목이 닫히고, 열린 remediation PR과 기준 브랜치가 수렴하며, 독립 clone 검증과 라이선스/데이터 owner 승인이 있어야 실제 파일 이동 계획을 승인할 수 있다.
