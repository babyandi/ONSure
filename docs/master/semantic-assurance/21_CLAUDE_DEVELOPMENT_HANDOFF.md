# ONSure Semantic Assurance v2 — Claude Development Handoff

Status: `DEVELOPMENT_HANDOFF / NON_FINAL / V2_NOT_ACTIVE`
Branch: `codex/meta-validation-doc-refresh`
PR: `#44 [DRAFT] Meta-validation assurance design refresh`

## 1. 목적
이 문서는 Claude가 현재 ONSure Semantic Assurance v2 설계를 기준으로 실제 개발·실행을 계속하기 위한 개발 인수인계 정본이다. 설계 문서·Candidate Contract·Fixture·Runtime Candidate가 이미 존재하므로, 이 단계의 목적은 설계를 다시 해석하거나 단순 정리하는 것이 아니라 **실제 실행 가능한 구현으로 연결하고 증거를 생성하는 것**이다.

Claude는 이 문서와 아래 기준 산출물을 먼저 읽은 뒤 개발을 시작한다.

필수 기준:
- `docs/master/00_ONSURE_MASTER_DESIGN_SET.md`
- `docs/master/semantic-assurance/README.md`
- `docs/master/semantic-assurance/10_FINDING_LEDGER.md`
- `docs/master/semantic-assurance/11_CONTRACT_UPGRADE_BLUEPRINT.md`
- `docs/master/semantic-assurance/12_P0_VERTICAL_TRACEABILITY_AND_APPLICATION.md`
- `docs/master/semantic-assurance/13_V2_CONTRACT_MIGRATION_AND_VALIDATION_PLAN.md`
- `docs/master/semantic-assurance/14_V1_V2_SEMANTIC_GAP_MATRIX.md`
- `docs/master/semantic-assurance/16_ARTIFACT_COVERAGE_AND_COMPLETION_MATRIX.md`
- `docs/master/semantic-assurance/17_RUNTIME_WIRING_AND_ADAPTER_IMPLEMENTATION.md`
- `docs/master/semantic-assurance/18_VALIDATION_FINAL_DENOMINATOR_MIGRATION.md`
- `docs/master/semantic-assurance/19_FINAL_REVIEW_AND_EXECUTION_BLOCKERS.md`
- `docs/master/semantic-assurance/20_POST_V2_FINAL_REVIEW_FINDINGS.md`

현재 source-grounded 기준:
- raw observation baseline: **562**
- canonical P0: **FL-P0-001~141 / 141건**
- canonical P1: **FL-P1-001~050 / 50건**
- `VERIFIED_CLOSED = 0`
- v2 Schema Candidate: **23**
- valid fixture: **23**
- semantic-invalid fixture: **46**
- Active Selector: **HOLD / V2_NOT_ACTIVE**

## 2. 개발 원칙 — 절대 변경 금지
다음 원칙을 테스트 통과 편의를 위해 약화하지 않는다.

1. v1 `PASS`를 v2 `PASS`로 자동 승격하지 않는다.
2. `NOT_RUN`, `HOLD`, `BLOCKED`, `INCONCLUSIVE`, `INPUT_REQUIRED`, `STALE`, `STATUS_UNKNOWN`을 `PASS`로 변환하지 않는다.
3. failing fixture/test를 삭제·skip·disable하여 green 상태를 만들지 않는다.
4. invalid fixture가 통과하면 fixture를 약화하지 말고 Schema/Runtime을 먼저 검토한다.
5. `independent=true`, `signature_verified=true`, `QUALIFIED`, `explicit_acceptance=true`, `critical_miss_count=0`, `revoked=false` 같은 caller self-attestation을 실제 증명으로 취급하지 않는다.
6. Local `OTESTER/OAUDIT` 이름만으로 independent gate를 통과시키지 않는다.
7. Candidate Contract 존재를 Active Contract로 취급하지 않는다.
8. v2 Active Selector를 임의 활성화하지 않는다.
9. FinalLock / Production GO / Commercial GO 권위를 생성하지 않는다.
10. GitHub Actions를 사용하지 않는다. 로컬/서버 실행과 실제 증거만 사용한다.
11. raw evidence, execution receipt, failing output을 보존한다.
12. 같은 branch에서 수정했다는 이유로 Finding을 `VERIFIED_CLOSED`로 올리지 않는다.

## 3. 현재 Runtime Candidate
주요 구현 후보:
- `src/main/java/kr/co/oruda/onsure/platform/SemanticAssuranceV2Reconstructor.java`
- `src/main/java/kr/co/oruda/onsure/platform/SemanticAssuranceV2WorkflowService.java`
- `src/main/java/kr/co/oruda/onsure/platform/SemanticAssuranceV2DispatcherBridge.java`
- `src/main/java/kr/co/oruda/onsure/platform/SemanticAssuranceShadowGateComparator.java`
- `src/main/java/kr/co/oruda/onsure/platform/TenantRbacService.java`

주요 test candidate:
- `src/test/java/kr/co/oruda/onsure/platform/SemanticAssuranceV2WorkflowServiceTest.java`
- `src/test/java/kr/co/oruda/onsure/platform/SemanticAssuranceV2DispatcherBridgeTest.java`

Runtime에 이미 반영된 hardening:
- legacy PASS 자동 승격 금지
- missing/null legacy material은 explicit gap/HOLD
- collection/List digest의 Map coercion 제거
- WorkflowService package-local 및 server-bound context 요구
- semantic operation을 실제 operation 이름으로 `TenantRbacService` durable authorization ledger에 기록
- target ownership 검증과 semantic call을 같은 durable RBAC mutation 경계에서 실행
- `RegisteredTarget.sourceRoot`를 server-side authoritative source root로 사용
- target root 밖 reperformance path 차단
- `_authorized_*` caller injection 차단
- Applicability는 SA-01~14 exact capability denominator 요구
- denominator empty/duplicate/invalid disposition fail-close
- N/A/Excluded에는 evidence/rationale 요구
- caller self-attestation 기반 independent/human/qualification/authority 승격 금지
- target-bound deployment identity 부재 시 `deployment.verify-installed` BLOCKED
- Shadow Comparator Runtime/Schema discriminator 및 null semantics 정합화

## 4. 개발 실행 순서
Claude는 아래 순서를 유지한다. 앞 단계의 실제 결과 없이 뒤 단계의 positive 상태를 선언하지 않는다.

### DEV-01 Repository / Branch 동기화
- repository: `babyandi/ONSure`
- branch: `codex/meta-validation-doc-refresh`
- PR #44를 기준으로 checkout
- `main`이나 다른 branch에서 작업하지 않는다.
- 시작 시 `git status`, current branch, HEAD SHA를 evidence에 기록한다.

완료 증거:
- branch name
- HEAD SHA
- clean/dirty status
- Java/Python/tool versions

### DEV-02 Static Schema Qualification 실제 실행
실행 대상:
- `scripts/validate-semantic-assurance-v2-contracts.py`
- registry: `contracts/semantic-assurance-v2-schema-instance-registry.candidate.v1.json`

기대:
- valid 23개 → 전부 schema-valid
- semantic invalid 46개 → 전부 schema-invalid

해야 할 일:
1. Python dependency 확인/설치
2. validator 실행
3. unexpected pass/fail 전부 저장
4. Schema 결함인지 fixture 결함인지 분류
5. 수정 후 처음부터 재실행

금지:
- invalid fixture 삭제
- validation rule 완화로 억지 PASS
- 일부만 실행하고 전체 PASS 선언

필수 산출물:
- actual execution receipt
- schema SHA-256
- fixture SHA-256
- expected vs observed
- validator version
- started_at/completed_at
- final state는 `SELF_VALIDATION_NONFINAL`

### DEV-03 Java Compile / JUnit
전체 프로젝트 compile/test를 먼저 실행한다.

최소 확인 대상:
- `SemanticAssuranceV2Reconstructor`
- `SemanticAssuranceV2WorkflowService`
- `SemanticAssuranceV2DispatcherBridge`
- `SemanticAssuranceShadowGateComparator`
- `TenantRbacService`

새 테스트가 실패하면 assertion을 약화하기 전에 구현이 설계와 맞는지 확인한다.

필수 공격 테스트:
- v1 PASS → v2 HOLD
- duplicate denominator
- empty denominator
- invalid denominator disposition
- N/A rationale/evidence 누락
- SA-01~14 capability denominator 누락/중복
- cross-tenant semantic call
- target path escape
- `_authorized_*` injection
- direct WorkflowService authorization bypass
- self-attested independence
- self-attested OTester/OAudit
- self-attested Human Acceptance
- self-attested Validator Qualification
- self-attested Authority validity
- List canonical digest
- mismatched deployed artifact
- target-bound deployment identity 미존재

필수 산출물:
- compile log
- JUnit summary
- failing test details
- exact commit SHA
- execution receipt

### DEV-04 Primary Dispatcher Wiring
목표는 별도 candidate Bridge만 존재하는 상태에서 실제 canonical dispatch path가 v2 operation을 인지하도록 만드는 것이다.

조건:
- v1 operation 동작을 깨뜨리지 않는다.
- v2 semantic operation은 `TenantRbacService` durable ownership boundary를 반드시 통과한다.
- authorization event에는 alias가 아니라 실제 semantic operation 이름이 남아야 한다.
- target context는 caller가 아니라 server registry에서 resolve한다.
- effect/read scope와 target ownership이 동일 transaction boundary에서 검사되어야 한다.

외부 effect operation(`git.push`, deployment write)은 실제 authority/effect contract가 닫히기 전까지 BLOCKED 유지 가능하다.

### DEV-05 v1→v2 Actual Reconstruction Batch
기존 v1 Receipt/Approval/Final/Run population을 실제로 읽어 v2 reconstructor에 넣는다.

각 material field를 다음 중 하나로 분류:
- `DIRECTLY_MAPPABLE`
- `DERIVABLE_WITH_PROOF`
- `READBACK_REQUIRED`
- `REPERFORMANCE_REQUIRED`
- `EXTERNAL_AUTHORITY_REQUIRED`
- `UNRECOVERABLE`

핵심 집계:
- v1 PASS count
- v2 PASS/NON_FINAL/HOLD/BLOCKED count
- `v1 PASS / v2 HOLD` count
- unavailable nonce/expiry/authority/freshness/independence fields

금지:
- missing 값을 추정으로 채우기
- historical actor/key/authority를 이름만 보고 복원하기

### DEV-06 Validation Case Exact Population Migration
기존 minimum/count authority를 실제 exact population으로 materialize한다.

필수 필드:
- population_id
- denominator_epoch
- exact case IDs
- case content digest
- oracle/harness identity digest
- included/excluded disposition
- population digest

검증:
- duplicate ID 금지
- count = exact item population에서 계산
- legacy case와 current authority case 분리
- excluded/N/A에는 evidence 필요

### DEV-07 Final Acceptance Exact Population Migration
기존 Final Acceptance source/count authority를 exact source population으로 materialize한다.

필수:
- source document digest
- stable source identity
- requirement IDs
- denominator epoch
- exact population digest
- current/stale status

고정 숫자를 authority로 쓰지 않는다.

### DEV-08 Independent OTester / OAudit Runtime 준비
이 단계에서 실제 independent PASS를 만들지 못해도 된다. 다만 runtime은 self-validation과 independent verification을 타입 수준에서 분리해야 한다.

Independent acceptance가 positive가 되려면 최소:
- distinct principal
- credential admin independence
- implementation independence
- oracle independence
- discovery independence
- knowledge independence
- current qualification
- signed receipt
- current freshness

caller JSON의 boolean만 읽어 positive 상태를 만들면 실패다.

### DEV-09 Human Acceptance Verifier
현재 Human Acceptance는 caller self-attestation으로 positive가 되면 안 된다.

구현 목표:
- authoritative principal profile lookup
- approval purpose/scope binding
- nonce
- expiry
- revocation
- key/signature verification
- exact candidate/gate digest binding
- replay consumption ledger

이 verifier가 없으면 `assurance.human-accept`는 HOLD 유지.

### DEV-10 Validator Qualification Runtime
qualification은 caller의 `critical_miss_count=0` 선언을 믿지 않는다.

필수 흐름:
1. sealed/precommitted benchmark set
2. benchmark digest freeze
3. validator execution identity freeze
4. hidden corpus isolation
5. observed result calculation
6. critical miss/fpr/coverage 계산
7. independent qualification receipt
8. expiry/requalification trigger

### DEV-11 Shadow Gate 실제 비교
같은 target/source/candidate에 대해 legacy gate와 v2 reconstructed gate를 동시에 실행한다.

상태:
- `AGREE_POSITIVE`
- `AGREE_NEGATIVE`
- `DISAGREEMENT_HOLD`

특히 `v1 PASS / v2 HOLD`는 모두 root cause를 남긴다.

v2 selector activation 전 조건:
- unexplained disagreement 0
- 모든 disagreement explicit disposition
- no silent positive upgrade

### DEV-12 Target-bound Deployment Identity
현재 `deployment.verify-installed`가 BLOCKED인 이유를 해소하는 단계다.

설계 기준:
`Target → Build Artifact → Deployment Plan → Installation → Runtime Instance`

최소 entity:
- target_id
- artifact_digest
- deployment_id
- environment_id
- installation_id
- node/instance identity
- deployed artifact read-back digest
- deployment receipt
- rollback receipt

검증:
- verified artifact digest == deployed artifact digest
- partial deployment 탐지
- rollback 시 previous assurance stale/invalidate
- target과 무관한 deployed path substitution 금지

### DEV-13 Active Selector 준비
이 단계까지 와도 v2를 자동 활성화하지 않는다.

Selector 전환 필요 조건:
- Schema static qualification PASS
- Java/JUnit PASS
- canonical dispatcher wiring PASS
- actual reconstruction evidence
- exact Validation population
- exact Final Acceptance population
- independent OTester/OAudit
- Human Acceptance verifier
- Validator Qualification
- Shadow disagreement closure
- target-bound deployment identity
- canonical P0 blocker closure

조건 미충족 시 selector는 `HOLD / V1_ACTIVE / V2_NOT_ACTIVE` 유지.

## 5. Finding 처리 규칙
현재 `contracts/semantic-assurance-finding-disposition.candidate.v1.json`을 따른다.

Finding 상태를 다음 순서로 올린다.
`OPEN → DESIGN_ACCEPTED → CONTRACTED → IMPLEMENTED → EXECUTED → EVIDENCE_BOUND → INDEPENDENTLY_VERIFIED → QUALIFIED`

주의:
- branch에 코드가 있음 ≠ IMPLEMENTED
- JUnit 파일이 있음 ≠ EXECUTED
- self-validation PASS ≠ INDEPENDENTLY_VERIFIED
- independent run ≠ QUALIFIED
- 같은 branch에서 fix ≠ VERIFIED_CLOSED

기존 문서에서 `VERIFIED_CLOSED`라는 표현이 필요하면 별도 closure receipt와 current qualification이 있을 때만 사용한다.

## 6. 개발 중 새 결함 발견 시
Claude가 구현 중 새로운 결함을 발견하면 숨기거나 기존 Finding에 억지 병합하지 않는다.

절차:
1. 재현 조건 작성
2. source path/contract 기록
3. severity 후보 부여
4. 기존 Finding과 중복 여부 확인
5. 신규이면 `20_POST_V2_FINAL_REVIEW_FINDINGS.md` 뒤에 추가 후보 기록
6. machine Finding Ledger/Disposition 수치는 검토 단계에서 확정 가능하도록 evidence만 남겨도 됨
7. 수정했다고 즉시 CLOSED하지 않음

## 7. Claude가 수정하면 안 되는 설계 권위
Claude는 구현 과정에서 아래 구조를 임의로 축소하거나 제거하지 않는다.
- SA-01~14 exact capability denominator
- XC-01~30 control intent
- exact denominator epoch
- receipt semantic preservation
- authority lifecycle
- freshness/invalidation
- independence dimensions
- validator qualification
- hidden corpus/precommitment
- Final Reconstruction → Independent OTester → Independent OAudit → Human Acceptance → Final Approval → Final Lock → Deployment currentness 순서

설계상 충돌이나 구현 불가능성이 있으면 workaround로 지우지 말고 `BLOCKED_DESIGN_CONFLICT` evidence를 남긴다.

## 8. 우선 개발 Batch
가장 먼저 아래 Batch를 완료한다.

### Batch A — 실행 기반선
- DEV-01
- DEV-02
- DEV-03

### Batch B — Runtime authority
- DEV-04
- DEV-05

### Batch C — Denominator / Final population
- DEV-06
- DEV-07

### Batch D — Independent / Human / Qualification
- DEV-08
- DEV-09
- DEV-10

### Batch E — Shadow / Deployment / Selector
- DEV-11
- DEV-12
- DEV-13

Batch A가 실패하면 B~E의 positive assurance를 주장하지 않는다. 개발 자체는 병렬 진행할 수 있으나 상태 승격은 순서를 따른다.

## 9. 완료 시 Claude가 남겨야 할 요약
각 Batch 완료 시 branch에 최소 다음 파일을 갱신하거나 evidence를 추가한다.

- 실제 실행 명령
- 실행 시작/종료 시간
- commit SHA
- tool/runtime versions
- input hashes
- output hashes
- PASS/FAIL/HOLD/BLOCKED count
- failed test/fixture 목록
- 변경 파일 목록
- 새 Finding 후보
- 남은 blocker

요약에는 반드시 다음 두 줄을 포함한다.

`ACTIVE_SELECTOR_CHANGED=false`  
`FINAL_PRODUCTION_COMMERCIAL_AUTHORITY_CREATED=false`

실제로 별도 승인과 qualification이 완료되기 전에는 두 값 모두 false다.

## 10. 현재 개발 출발점
현재 branch의 최고 상태는:

`DESIGN_CONTRACT_FIXTURE_AND_FAIL_CLOSED_IMPLEMENTATION_CANDIDATES_PRESENT / EXECUTION_BLOCKED_OR_NOT_RUN / NON_FINAL`

Claude의 첫 목표는 이 상태를 문서상 승격하는 것이 아니라, **Batch A를 실제 실행하여 처음으로 재현 가능한 execution evidence를 만드는 것**이다.
