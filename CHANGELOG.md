# 변경 이력

이 문서는 ONSure 독립 저장소의 변경 후보를 기록한다. 실제 release 또는 ORUDA-Products 이관 완료를 의미하지 않는다.

## Unreleased — 모노레포 이관 준비

### Added

- source/OpenAPI digest와 1회 승인에 결속된 non-destructive loopback E2E에서
  인라인·로컬 `$ref` request schema의 결정적 합성 Fixture, path parameter materialization,
  응답 status·bounded schema oracle 및 body 비저장 digest Receipt 지원
- 필수 primitive query/header/cookie 합성, 승인된 `env:` 인증 참조의 HTTP bearer/basic·
  OAuth bearer·header API key 런타임 주입 및 credential 비저장 digest Receipt
- 자동 E2E의 source drift, external `$ref`, 인증 참조·값 미구성, 복합·query/cookie 인증,
  미지원 parameter serialization·schema assertion을 고객 결함과 분리해 `BLOCKED`로 판정하는
  fail-closed 경계
- 중단된 inferred E2E의 durable Receipt 완료 복구, read-only retry chain,
  write outcome 불명확 시 재승인 요구와 실행 claim/recovery digest 위변조 방지
- 동일 source/profile E2E Receipt의 단계별 개선·퇴행·변경 비교, 진단·개선 가이드 및
  관리화면 history projection
- OpenAPI business object별 lifecycle 추론, `CREATE → READ/UPDATE/DELETE` binding 검토·승인 결속,
  producer JSON Pointer에서 consumer path로 값을 전달하는 비저장 `CREATE → READ` 실제 실행
- OpenAPI response schema의 중첩 scalar JSON Pointer 탐색과 exact property/단일 `id` 근거 기반
  binding 신뢰도 산정
- 필수 query/header/request-body 입력의 exact-name 결속 후보와 민감 header 제외, 승인된
  query/header 및 producer/consumer scalar schema 타입이 호환되는 body 값의 실제 후속 요청 주입,
  전체 request schema 재검증과 원문 비저장 digest Receipt
- 일반 배열과 schema 보장 singleton 배열을 구분하는 bounded pointer template, 런타임 cardinality
  재검증 및 ambiguity 차단
- operation/tag/path/schema/component 근거에 결속된 Capability·Workflow 의미 가설과
  confidence·ambiguity·UNKNOWN·점수 제외 계약
- 관리화면의 총점 진단, DOMAIN→PHASE→GROUP→AREA→STEP drill-down, Before/After 세부 변화와
  자동 추론 confidence·근거·승인·실행 상태 표시
- final evidence integrity가 실패하거나 canonical Receipt digest/계약/scorecard/source 결속이
  깨지면 모든 획득 점수와 비교·DB 이력 노출을 보류하는 고객 화면 fail-closed 검증
- 실제 Git 저장소·fixture·합성 snapshot·미확인 대상을 구분하고 repository identity hash,
  commit, scope, source·manifest digest를 등록/Profile에 결속하는 target provenance 계약
- OpenAPI 문서별 service boundary와 optional pagination cursor, 2xx continuation header,
  bounded pagination/async poll 후보의 discovery-only 계약; 다중 service runtime은 `BLOCKED_NOT_RUN`
- 별도 승인 manifest에 결속된 동일 service GET/HEAD pagination 실행, 페이지별 token 비저장 digest
  Receipt와 반복 token·시간·횟수·누적 응답 크기 fail-closed 종료; async poll과 다중 service는
  계속 `BLOCKED_NOT_RUN`
- Universal validation Receipt·report·evidence의 target provenance 교차검증과 실행 전후
  repository commit/scope/clean/snapshot manifest 변조 차단, fixture의 실제 대상 범용성 집계 금지
- PostgreSQL 점수 이력의 project/target 격리, 전체 점수 node·finding·진단·개선 가이드 read-back,
  반복성 비교와 승인된 변경 lineage 기반 개선 비교 분리
- nested Maven·Gradle·Python·Node build root의 bounded/symlink-safe 자동 탐지와 ONGuard형
  `java/pom.xml` 독립 실행 후보 생성
- 소비된 Patch 승인 receipt와 Before/After proof를 PostgreSQL score run에 결속하는
  `score.improvement-bind` 공통 Workflow 및 교차 project/target DB 외래키 차단
- PostgreSQL V1→V3 동시 migration lock·rollback·finding read-back·backup/restore 실제 리허설과
  OCI sandbox 12개 경계 probe, VS Code Extension Host online/offline 재검증
- 주입된 HTTP client의 redirect 정책을 `NEVER`로 강제해 loopback 경계 이탈 차단
- 격리 Fixture별 run 전용 `TMPDIR`로 host `/tmp` 고갈과 실행 결과의 불필요한 결합 제거
- Fixture stdout Oracle과 stderr 진단 스트림을 분리해 도구 경고가 기능 판정을 오염하지 않도록 강화
- Ubuntu immutable install/idempotent-upgrade/rollback rehearsal과 archive traversal 방어
- loopback-only PostgreSQL custom backup runner, systemd service/timer, checksum·restore 검증·retention 경계
- VS Code Runtime/Admin View의 SecretStorage 기반 LLM Gateway health·token·cost·receipt-chain 표시
- Local API least-privilege VIEWER/OPERATOR/ADMIN/APPROVER roles, read-only external program
  registration, isolated Maven validation and append-only management audit evidence
- two-person secret-free Gateway setting request/approval flow with external apply boundary
- retryable failure, average latency, ledger size and sequence LLM monitoring metrics
- OpenAPI 3.1 정본과 구현 route drift 검증을 갖춘 Local API·LLM Gateway 계약
- exact Provider/no-retry/no-fallback LLM Gateway와 content-free append-only token·비용 evidence ledger
- LLM 설정·사용량·증적 chain, 등록 프로그램 검증 결과와 개선 후보를 표시하는 loopback 관리화면
- RHEL/Ubuntu systemd package용 별도 `onsure-llm-gateway.service`

- OpenAI Responses API용 fail-closed Provider 모듈과 file-input CLI
- PostgreSQL/Flyway migration 소유 모듈과 forward-only V1 schema
- 컨테이너 대신 RHEL 계열·Ubuntu 24.04 LTS 단독 서버 systemd 배포·패키징 후보
- network/data/cost 승인, exact-model/no-retry/no-fallback 정책과 failure tests
- Docker 없는 임시 PostgreSQL 16.14 Flyway apply/idempotency/dump/restore 리허설
- 현재 구조와 `products/onsure/` 미래 구조 대응표
- 전체 이관 대상 파일 기준 Manifest 후보와 비최종 readiness validator
- 공통 SHA-256 primitive 및 플랫폼 중립 RAG preparation request
- canonical/compatibility build 권위 계약과 모듈 경계 validator
- `ONSURE_PRODUCT_ROOT` 기반 제품 루트 해석
- `product.yaml`, 제품 전용 `AGENTS.md`, `.obuilder/` 준비 메타데이터
- 기존 238개 public Java class descriptor 무변경과 additive evidence SPI 2개를 고정한 240-class JVM baseline
- CycloneDX 1.6 SBOM과 dependency license 검토 inventory
- 격리된 `products/onsure/` cutover·rollback 리허설
- Draft PR #27/#28 head에 결속된 overlap·재검증 매트릭스
- rootless bubblewrap 실행환경 진단기와 host 구성 가이드
- fail-closed 배포·DB migration `DESIGN_ONLY_NONFINAL` 계약과 validator
- VS Code Extension의 등록 Workspace·Project·Target identity 결속과 로컬 파일 URI 검증
- VS Code용 실행계획 생성·서명 승인 확인·승인 Bundle 기반 Validation 명령
- Node 내장 회귀 테스트와 Local API 등록·학습·Plan HTTP 통합 테스트
- ZIP 순서·timestamp·compression을 정규화하는 재현 가능 VSIX 패키징 도구
- 등록 identity에 결속된 Local Workspace Snapshot API와 14개 VS Code 전용 read view
- 재시작 후 Profile·Plan·Run·Patch/Proof/Git receipt 복구 및 승인형 Patch·Commit·Draft PR 명령
- source digest 검증 Hunk diff, 선택 Hunk 외부 서명 요청 계약과 승인 purpose 정합화
- subprocess group 기반 Autopilot pause/resume/cancel 및 CLI·Local API·VS Code control journal
- Ask/Plan/Act/Verify/Improve/Autopilot/Audit/Offline별 exact capability matrix와 미분류 Workflow 기본 거부
- ValidationEngine의 digest-chain stage checkpoint 및 Local API·VS Code Runtime 표시
- Model Provider adapter schema/registry와 두 교체형 deterministic provider compatibility test
- 실행계획의 Token·데이터 전송 예산 필드와 VS Code Runtime 예산 표시
- stage 경계 validation context snapshot, digest/target/run 결속과 명시적 typed replay
- PID·PGID·start tick·command digest 기반 Autopilot orphan control 복구
- snapshot 기반 결정론적 ASK/PLAN 명령과 VS Code Extension Host E2E harness/preflight
- 독립 `onsure-provider-spi` 및 loopback-only `onsure-sdk` Maven 후보 모듈
- approval request/receipt/plan scope verifier와 HMAC 프로젝트 지식 익명화·공통 후보 분리
- bounded 성능·장애·backup/restore·observability 도구
- 실행 권한 없는 deployment/database migration preflight 골격
- deterministic Maven air-gap dependency pack plan/build/verify 도구
- SBOM unique purl/SHA-256, license/vulnerability policy와 package-lock-bound npm audit evidence
- digest-bound ValidationEngine 자동 resume와 stage replay ledger
- approval request verifier의 CLI·Local API·VS Code patch apply 연결
- Public SDK/API 익명화 helper와 1,000-entry 대규모 corpus 검증
- timeout·rate-limit·비용 상한·fallback 금지를 강제하는 독립 local/mock Provider 모듈
- SDK 구조화 오류, cursor pagination, 명시적 idempotent retry와 공개 API baseline
- 고정 VS Code/Xvfb 컨테이너 및 network-disabled Extension Host E2E runner
- benchmark 비교·bounded soak·ENOSPC 장애주입·합성 backup/restore/DR rehearsal
- Maven repository와 npm cache의 실제 offline install rehearsal
- Trivy 기반 CycloneDX 검사와 Maven module/VS Code dependency inventory 통합
- non-root/read-only/no-network Dockerfile·Compose 후보와 합성 SQLite migration/rollback/lock runner
- `io.onsure.platform` 단일 core ownership, CLI·Local API module-owned entrypoint와 split package 0 baseline
- 합성 migration transaction rollback/resume/digest-drift 및 backup archive traversal/symlink 방어
- `TargetEvidenceContributor` ServiceLoader SPI를 통한 `platform ↔ platform.oruda` cycle 제거

### Changed

- canonical standalone deployment target를 Ubuntu 24.04 LTS/systemd로 확정하고 RHEL 정의는
  compatibility candidate로 유지한다.
- `onsure-core`와 `onsure-adapter-oruda`를 module-owned source root로 물리 분리하고
  canonical 단일 artifact, `io.onsure` namespace와 공개 API 240/240을 유지한다.
- ValidationEngine resume가 replay-ledger digest 위변조도 명시적으로 거부하도록 회귀시험을 보강한다.
- 기존 hashing facade는 공개 API를 유지하면서 공통 SHA-256 primitive를 사용한다.
- `FileValidationStore`는 RAG package를 직접 호출하지 않고 공통 중립 candidate preparer를 사용한다.
- Manifest의 untracked 파일 mode는 Git index mode와 동일하게 정규화된다.
- 승인되지 않은 등록 Target Validation은 내부 실행 실패 대신
  `APPROVED_EXECUTION_PLAN_BUNDLE_REQUIRED`로 fail-closed 응답한다.
- VS Code Learn·Validation 요청은 금지된 source 및 product-state 경로 override를 더 이상 전송하지 않는다.
- VS Code 14개 View는 하나의 캐시된 snapshot을 공유하며 다른 View와 동일 상태 목록을 반복하지 않는다.
- Hunk 승인 purpose는 published receipt schema와 동일한 `PATCH_HUNK_APPROVAL`을 사용한다.
- 취약한 transitive `brace-expansion` 1.1.16을 1.1.18로 갱신해 npm audit high 1건을 0건으로 줄였다.
- Jackson 2.18.2를 2.18.9로 갱신해 고정 Trivy 검사에서 critical/high/medium/low를 0건으로 줄였다.

### Compatibility

- Java namespace와 Maven coordinate 변경 없음
- 기존 Local API 경로와 공개 Java API는 유지하고 신규 endpoint/module만 additive로 추가
- 기존 `RagPreparationService`의 `ValidationReport` overload 유지
- 실제 ORUDA-Products 이동, main 병합, 배포, Production GO, Final PASS 없음

### Known migration blockers

- root source license·소유권은 ORUDA Labs proprietary owner 선언으로 반영; 고객 데이터 승인과 독립 법률 검토는 미실행
- Ubuntu/RHEL 단독 서버와 PostgreSQL topology는 선택됨; 운영 데이터 승인·실제 Production migration·RHEL 호스트 검증은 미실행
