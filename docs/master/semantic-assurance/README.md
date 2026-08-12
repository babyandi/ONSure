# ONSure Semantic Assurance Companion Design Set

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

이 디렉터리는 ONSure가 다른 제품을 검증할 때 발생할 수 있는 false assurance, 권위 오판, 독립성 오판, stale/revoked 결과 재사용, denominator 축소, Receipt 의미손실, canonical gate 우회를 기존 `docs/master/02~08` 책임구조에 맞춰 통합한다.

## 1. 문서 Set
### Core/Review Integration
- `00_INTEGRATION_AND_OWNERSHIP.md`: SA-01~14 통합·권위·중복 방지
- `02_FUNCTIONAL_REQUIREMENTS_EXTENSION.md`: 기능·입력·산출물·수용기준
- `03_REVIEW_SPECIFICATION_EXTENSION.md`: Finding·Review·Decision 규칙
- `04_ARCHITECTURE_DATA_API_EXTENSION.md`: Service·Entity·State·API·Invariant
- `05_UI_UX_WORKFLOW_EXTENSION.md`: Assurance 상태·Freshness·Rights·Authority UX
- `06_TEST_OPERATION_EXTENSION.md`: negative/adversarial/failure-injection
- `07_AI_AGENT_METHOD_EXTENSION.md`: AI-UC, GT, Blind, Reviewer, Qualification
- `08_OPEN_DECISIONS_EXTENSION.md`: 미확정 Contract/정책/임계치
- `09_INDEPENDENT_REVIEW_FINDINGS_INTEGRATION.md`: 독립검토 cross-cutting 통합
- `10_FINDING_LEDGER.md`: 최초 P0/P1 canonical Finding ledger
- `11_CONTRACT_UPGRADE_BLUEPRINT.md`: v2 Contract Bundle A~J

### Migration / Runtime / Final Gate
- `12_P0_VERTICAL_TRACEABILITY_AND_APPLICATION.md`: Finding→02~08 수직 적용
- `13_V2_CONTRACT_MIGRATION_AND_VALIDATION_PLAN.md`: v1→v2 migration/shadow/selector
- `14_V1_V2_SEMANTIC_GAP_MATRIX.md`: DIRECT/READBACK/REPERFORMANCE/UNRECOVERABLE 분류
- `15_V2_STATIC_QUALIFICATION_FIXTURE_SPEC.md`: semantic negative fixture 기준
- `16_ARTIFACT_COVERAGE_AND_COMPLETION_MATRIX.md`: 현재 산출물·완성도 정본
- `17_RUNTIME_WIRING_AND_ADAPTER_IMPLEMENTATION.md`: Adapter/Reconstructor/Runtime wiring
- `18_VALIDATION_FINAL_DENOMINATOR_MIGRATION.md`: Validation/Final fixed-count authority 제거
- `19_FINAL_REVIEW_AND_EXECUTION_BLOCKERS.md`: 최종 재검토 및 실제 실행 Blocker
- `20_POST_V2_FINAL_REVIEW_FINDINGS.md`: v2 Candidate 자체 재검토 Finding 확장 Ledger

### Claude Development Handoff
- `21_CLAUDE_DEVELOPMENT_HANDOFF.md`: Claude 개발 실행 정본. DEV-01~13, 금지사항, 실행증거, Batch A~E를 정의한다.

### Design Continuation — 개발보다 앞서 정의하는 다음 기준선
- `21_INDEPENDENT_ASSURANCE_EXECUTION_ARCHITECTURE.md`: 독립 OTester/OAudit/Human Fact Validation 실행 아키텍처
- `22_TARGET_BOUND_DEPLOYMENT_AND_RELEASE_IDENTITY.md`: Target→Build→Release→Deployment→Currentness identity
- `23_RUNTIME_EXECUTION_EVIDENCE_AND_QUALIFICATION.md`: 실행 Receipt/Attempt/Evidence/Qualification 상태 체계
- `24_ACTIVE_SELECTOR_ROLLOUT_AND_ROLLBACK_GOVERNANCE.md`: v1/v2 Shadow→Activation→Rollback governance
- `25_VALIDATOR_BUILD_PROVENANCE_AND_TCB.md`: Validator build provenance, SBOM, TCB, qualification binding
- `26_REQUIREMENT_UNIVERSE_AND_DISCOVERY_AUTHORITY.md`: Requirement Universe/Discovery/Applicability/Denominator authority
- `27_EVIDENCE_CANONICALIZATION_AND_CRYPTO_LIFECYCLE.md`: canonicalization, self-hash, signature, key lifecycle, replay
- `28_DISTRIBUTED_CURRENTNESS_AND_REVOCATION.md`: distributed stale/revocation/currentness/offline propagation

기존 `docs/master/02~08` 본문에도 FR-META-001~043과 Meta Review/Architecture/Test/AI 기준이 직접 반영되어 있다. Companion 문서는 이를 삭제하거나 대체하지 않는다.

## 2. 역할 분리
- **Claude**: 개발·컴파일·테스트·runtime implementation·migration execution
- **ONSure 설계 정본**: 본 디렉터리와 `docs/master/02~08`
- **검토/독립검증**: 실제 실행 산출물이 쌓인 후 별도 수행

Claude 구현은 설계 상태를 임의로 `ACTIVE`, `QUALIFIED`, `FINAL`로 승격하지 않는다.

## 3. Finding 기준
현재 source-grounded review 기준:
- raw candidate observation baseline: **562**
- canonical P0: **`FL-P0-001~141` / 141건**
- canonical P1: **`FL-P1-001~050` / 50건**
- `VERIFIED_CLOSED`: **0**

같은 branch에서 candidate fix가 존재해도 compile/test/independent verification 전에는 CLOSED하지 않는다.

## 4. Machine Contract Set
현재 Schema Inventory 기준 총 **31개 Schema Candidate**다.

### Fixture-covered 23
기존 Status/Receipt/Authority/Independence, Denominator/Applicability/Population, Execution/Qualification, Blind/Reviewer/Benchmark/GT/Hidden, Final/Deployment/Selector/Shadow Schema 23개는 valid 23 + semantic-invalid 46 fixture를 가진다.

### 신규 Design Schema 8 — Claude fixture/runtime 구현 대기
- `independent-assurance-execution-plan.candidate.v2.schema.json`
- `target-deployment-identity.candidate.v2.schema.json`
- `runtime-execution-receipt.candidate.v2.schema.json`
- `contract-selector-transition-receipt.candidate.v2.schema.json`
- `validator-build-manifest.candidate.v2.schema.json`
- `requirement-universe-snapshot.candidate.v2.schema.json`
- `evidence-canonicalization-profile.candidate.v2.schema.json`
- `assurance-revocation-event.candidate.v2.schema.json`

`semantic-assurance-v2-schema-inventory.candidate.v1.json`은 `total=31 / fixture-covered=23 / pending=8`을 추적한다.

## 5. Static Fixture / Execution 상태
기존 fixture registry:
- 23 Schema
- 23 valid fixture
- 46 semantic invalid fixture
- Schema당 최소 2 negative fixture

신규 8 Schema는 설계 단계이며 fixture/runtime 구현 대기다.

실제 static execution은 현재 ChatGPT container의 branch mount/DNS 제약으로 `BLOCKED_NOT_RUN`. Fixture 존재를 PASS/QUALIFIED로 해석하지 않는다.

## 6. Runtime Candidate 원칙
현재 fail-closed runtime candidate는 다음 원칙을 가진다.
- legacy PASS 자동 v2 PASS 승격 금지
- direct WorkflowService public surface 제한
- server-bound tenant/project/target/root authority
- semantic operation durable authorization
- target sourceRoot 밖 file access 차단
- self-attested independence/Human Acceptance/Qualification/Authority validity 금지
- target-bound deployment identity 없으면 deployment verification BLOCKED
- Shadow runtime/schema 정합화

Compile/JUnit/독립 재검증 전에는 `IMPLEMENTATION_CANDIDATE`다.

## 7. Claude 개발 순서
`21_CLAUDE_DEVELOPMENT_HANDOFF.md`가 개발 실행 기준이다. 추가로 신규 설계는 다음 우선순위로 구현한다.

1. RuntimeExecutionReceipt / Attempt history
2. IndependentAssuranceExecutionPlan
3. TargetDeploymentIdentity
4. SelectorTransitionReceipt
5. ValidatorBuildManifest + TCB/SBOM
6. RequirementUniverseSnapshot
7. Canonicalization/Crypto common library
8. Distributed Revocation/Currentness

기존 Batch A 실패 상태에서 후속 positive assurance를 주장하지 않는다.

## 8. Canonical Gate 편입
실제 제품 Gate가 되려면 최소 다음이 동시에 닫혀야 한다.
1. Product Process Lineage
2. Workflow Operation Registry / Dispatcher
3. Requirement/Validation/Final exact denominator
4. Independent OTester/OAudit/Human Fact Validation
5. Final Reconstruction → Approval → Lock
6. Target-bound Deployment/Verified-to-Deployed
7. Currentness/Revocation
8. Active Selector transition/rollback

## 9. 현재 상태
- 설계 기준선: 00~28 + Claude handoff
- canonical Finding: P0 141 / P1 50 / raw 562
- Schema Candidate: 31
- Fixture-covered: 23
- 신규 fixture/runtime pending: 8
- 기존 valid/invalid fixture: 23/46
- Static 실행: `BLOCKED_NOT_RUN`
- Java compile/JUnit: `NOT_RUN`
- independent execution: `NOT_RUN`
- target-bound deployment identity runtime: `NOT_IMPLEMENTED`
- Shadow Gate actual comparison: `NOT_RUN`
- Active Selector: `HOLD / V2_NOT_ACTIVE`
- FinalLock/Production/Commercial authority: 없음

현재 최고 표현은 **`DESIGN_BASELINE_EXTENDED_AHEAD_OF_DEVELOPMENT / CANDIDATE_ONLY / NON_FINAL`**이다.
