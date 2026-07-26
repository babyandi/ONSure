# ONSURE Post-Merge Self-Audit v1

## 1. 판정

`BLOCKED — POST-MERGE P0 REMEDIATION REQUIRED`

이 문서는 PR #19 병합 후 동일 작성자가 자신의 변경을 다시 검토한 결과다. 기존 병합은 설계·계약·상태 기준선 통합으로는 유효하지만, Standalone Core 독립 빌드, 원자 요구사항 추적성, Source/Evidence 재현성, One-Shot 실행 정확성은 아직 완료되지 않았다.

## 2. 자기검증에서 정정한 주장

- `Core와 ORUDA 분리 완료`는 과장이다. 기본 Adapter 등록은 분리됐지만 단일 Maven 모듈과 Core 클래스가 ORUDA 클래스를 직접 참조한다.
- `전체 요구사항 추적성`은 과장이다. 현재 20개 기능군 수준이며 세부 FR, API, 화면, 상태, 시험 항목을 원자 단위로 추적하지 않는다.
- `정적 계약 검증`은 JSON 구문과 파일 존재 중심이다. JSON Schema 적합성, YAML, JSONL, 전체 Markdown 링크, 실제 Evidence 계보를 검증하지 않는다.
- `전체 작업 완료`는 Codespace-free 기준선 통합 완료를 의미할 뿐 제품 구현 또는 전체 검증 완료를 의미하지 않는다.
- 자기검증 문서를 `main`에 직접 기록한 커밋 `189c7075...`은 Branch·PR 정책 위반이다. 사용자 권한 위임은 독립 리뷰와 변경관리 절차를 대체하지 않는다.

## 3. P0 발견사항

### SELF-P0-001 Core 독립 빌드 미완료

- `ValidationEngine`은 기본 등록을 Generic으로 제한했지만 `withOrudaAdapter`에서 ORUDA 클래스를 직접 참조한다.
- `FileValidationStore`가 `OrudaEvidenceRegistry`를 직접 import하고 ORUDA 분기를 포함한다.
- `ProductPlatformE2EMain`은 Generic, AI, ORUDA Fixture를 한 클래스에서 실행한다.
- 단일 `pom.xml`이 전체 Source와 Test를 함께 컴파일한다.

필요 조치:

- `onsure-core`, `onsure-adapter-oruda`, `onsure-cli`, `onsure-test-fixtures` 모듈 분리
- Core 모듈의 ORUDA import 0건
- ORUDA 모듈 삭제 상태에서도 Core compile, unit, generic/AI fixture E2E PASS

### SELF-P0-002 Core One-Shot 검증 범위 오류

- `--profile core`는 Generic/AI Validator Fixture E2E를 명시적으로 실행하지 않는다.
- `mvn test`는 ORUDA Test도 전부 실행하므로 Core-only 검증이 아니다.
- 범용 Harness Script가 없을 때 단계가 생략돼도 결과 JSON은 `available_runtime_harness=PASS`로 기록될 수 있다.

필요 조치:

- Core와 ORUDA Test Suite 및 E2E Runner 분리
- 미실행 단계는 `NOT_RUN`, 누락 필수 단계는 `FAIL/BLOCKED`
- Core 결과에 ORUDA Source, Test, Fixture가 전혀 필요하지 않음을 증명

### SELF-P0-003 Source 기준선 불일치

- Git mode Dirty 검사에서 untracked 파일을 제외한다.
- Generic Target Tree Hash는 전체 `Files.walk`를 사용해 실제 Repository 대상에서는 `.git`, untracked, 실행 산출물이 포함될 수 있다.
- 실행기는 untracked Python, Shell, Java Source/Test에 영향을 받을 수 있는데 Source Snapshot은 이를 제외한다.

정정 메모:

- `LocalSourceLockVerifier.digestTrackedFiles`는 Git 추적 파일을 사용하므로 Local Source Lock이 Receipt 디렉터리를 직접 Hash한다는 해석은 맞지 않는다.
- 남은 문제는 Local Dirty 판정이 untracked를 제외한다는 점, Policy Digest가 추적 여부를 분리하지 않는 점, Generic Validation의 `Hashing.tree`가 Git 추적 파일이 아닌 전체 Filesystem을 사용한다는 점이다.

필요 조치:

- Git 대상은 `git ls-files -s` 또는 Git Tree Object 기반으로 Digest 생성
- untracked 파일이 실행에 영향을 주는 경우 즉시 차단
- `.git`, build, receipt, runtime output 제외를 코드가 아니라 추적파일 집합으로 보장
- 시작·종료 시 Source/Policy 재검증

### SELF-P0-004 Traceability가 원자 요구사항 수준이 아님

현재 20개 기능군만 존재하며 다음 연결이 없다.

- 원자 Requirement ID
- Acceptance Criterion ID
- 설계 절·문단
- Code Symbol
- Test Method
- 실제 Evidence Receipt
- Verification State

필요 조치:

- Master/V2/Architecture/UI/Test/Operation 문서의 SHALL·필수·금지·수용기준을 원자 단위로 추출
- 각 Requirement에 `implementation_status`와 `verification_status`를 분리
- 파일 존재가 아닌 Code Symbol과 Test Method를 연결
- Evidence가 없으면 `NOT_RUN` 유지

### SELF-P0-005 내부 Receipt의 Final 오인 위험

- `LocalFinalReceipt`와 `final-lock.sha256`은 내부 자체검증인데 이름과 `decision=PASS`가 Final 승인처럼 보일 수 있다.
- OTester/OAudit Receipt도 같은 스크립트·호스트가 생성하므로 독립 권위가 아니다.

필요 조치:

- `assurance_class=SELF_VALIDATION_NONFINAL`
- `independent_otester=NOT_RUN`, `independent_oaudit=NOT_RUN`
- `final_lock_allowed=false`를 Receipt Schema와 Verifier에서 강제
- 내부 OTester/OAudit 명칭을 Internal Verifier/Internal Audit로 분리하거나 Authority Class를 명시

### SELF-P0-006 상태 대장 기준선 노후화

- PR #19 병합 후에도 상태 파일들이 `main@e1aad6...`와 병합 전 Branch를 기준으로 기록했다.
- Core Adapter 분리 상태도 `REMEDIATION_IN_BRANCH`로 남아 있었다.

필요 조치:

- 정적 상태 파일은 `runtime_source_commit=null`과 `PENDING_SOURCE_BOUND_RECEIPT`를 사용
- One-Shot 실행 시에만 실제 HEAD SHA를 Receipt에 기록
- 병합 Branch가 아닌 현재 Main 상태로 대장 갱신

### SELF-P0-007 Git 변경관리 위반

- 자기검증 문서 추가가 Branch·Draft PR 없이 `main`에 직접 반영됐다.
- 이는 `Main 직접 변경 금지`, 독립 리뷰, 병합 Gate 원칙과 충돌한다.

필요 조치:

- 위반 Commit을 감사대장에 보존
- 이후 모든 정정은 `audit/onsure-post-merge-self-audit-remediation-20260726` Branch와 PR을 사용
- Branch Protection 또는 Server-side Rule이 없으면 운영 절차만으로 차단됐다고 주장하지 않음

## 4. P1 발견사항

- JSON Schema 파일은 존재하지만 Meta-schema와 Instance 적합성을 실행하지 않는다.
- YAML과 JSONL 구문·계보 검증이 없다.
- README 외 Markdown 내부 링크를 검증하지 않는다.
- One-Shot Step Receipt에 실행 Command, Source SHA, Toolchain/Environment Digest가 없다.
- `.onsure/one-shot/`와 `receipts/validator-fixture-e2e/`가 `.gitignore`에 없다.
- One-Shot 출력 경로가 초 단위라 동시 실행 충돌 가능성이 있다.
- Static Validator가 Runtime Receipt JSON까지 스캔할 수 있어 실행 이력에 따라 결과가 달라질 수 있다.
- `ProgramProfile` 요소별 Evidence Hash와 Parent Lineage가 없다.
- `BehaviorProfile`에 Model, Prompt, Tool, Environment Version과 반복 실행 분포가 부족하다.
- Evidence Receipt는 독립 Receipt에서 Signature와 Key ID를 조건부 필수로 강제하지 않는다.
- Official Learning Ledger는 Verifier Identity/Key 분리, 최신 Pack 결속, Post-apply Receipt 해석, Cross-process Lock, External Anchor가 부족하다.
- Fixture Harness는 Bash Script를 Host 권한으로 실행하며 Network, Child Process, CPU, Memory, Filesystem Sandbox를 강제하지 않는다.
- File Store와 Product Catalog는 JVM 내 `synchronized`뿐이며 Cross-process CAS/Lock과 Tenant Isolation이 없다.

## 5. 추가 전수검증 범위

다음 문서는 아직 원자 Requirement로 전수 추출하지 않았다.

- `docs/architecture/*`
- `docs/v2/*`
- `docs/security/*`
- `docs/verification/*`
- `docs/operations/*`
- `docs/master/*`의 모든 하위 Bullet, API, 화면, 상태, 시험 조건

특히 Target AI Auto-Learning의 Dataset Registry, Evaluation, Model Registry, RAG/Prompt/Agent/Fine-tuning 경계와 OLicense Entitlement는 별도 추적 Lane이 필요하다.

## 6. 다음 처리 순서

1. 상태 대장과 자기검증 오류 정정
2. Core/Adapter Maven Module 분리
3. Source Snapshot·One-Shot·Receipt Hardening
4. 원자 Requirements Traceability 생성
5. Schema/YAML/JSONL/Link Validation 강화
6. Learning Ledger와 Evidence Store 독립성 강화
7. Codespace 이전 가능한 구현 보완 완료
8. 마지막에 Codespace One-Shot, 실패 RCA, 전체 회귀
9. 별도 Identity/Key/Environment의 OTester·OAudit

## 7. 허용 판정

- PR #19 Design Baseline Integration: `MERGED_NONFINAL`
- Post-merge self-audit: `P0_FOUND`
- Codespace-free remediation complete: `false`
- Runtime verification current main: `NOT_RUN`
- Standalone MVP: `BLOCKED`
- FinalLock / Production / Commercial GO: `false`
