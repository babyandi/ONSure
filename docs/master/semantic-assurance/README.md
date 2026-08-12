# ONSure Semantic Assurance Companion Design Set

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

이 디렉터리는 ONSure가 다른 제품을 검증할 때 발생할 수 있는 false assurance, 권위 오판, 독립성 오판, stale/revoked 결과 재사용, denominator 축소, Receipt 의미손실, canonical gate 우회를 기존 `docs/master/02~08`의 책임구조에 맞춰 통합한다.

## 1. 문서 Set
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
- `12_P0_VERTICAL_TRACEABILITY_AND_APPLICATION.md`: Finding→02~08 수직 적용
- `13_V2_CONTRACT_MIGRATION_AND_VALIDATION_PLAN.md`: v1→v2 migration/shadow/selector
- `14_V1_V2_SEMANTIC_GAP_MATRIX.md`: DIRECT/READBACK/REPERFORMANCE/UNRECOVERABLE 분류
- `15_V2_STATIC_QUALIFICATION_FIXTURE_SPEC.md`: semantic negative fixture 기준
- `16_ARTIFACT_COVERAGE_AND_COMPLETION_MATRIX.md`: 현재 산출물·완성도 정본
- `17_RUNTIME_WIRING_AND_ADAPTER_IMPLEMENTATION.md`: Adapter/Reconstructor/Runtime wiring
- `18_VALIDATION_FINAL_DENOMINATOR_MIGRATION.md`: Validation/Final fixed-count authority 제거
- `19_FINAL_REVIEW_AND_EXECUTION_BLOCKERS.md`: 1~15 최종 재검토 및 실제 실행 Blocker
- `20_POST_V2_FINAL_REVIEW_FINDINGS.md`: v2 Candidate 자체 재검토 Finding 확장 Ledger

기존 `docs/master/02~08` 본문에도 FR-META-001~043과 Meta Review/Architecture/Test/AI 기준이 직접 반영되어 있으며 companion 문서는 이를 삭제하거나 대체하지 않는다.

## 2. Finding 기준
현재 source-grounded review 기준:
- raw candidate observation baseline: **562**
- canonical P0: **`FL-P0-001~141` / 141건**
- canonical P1: **`FL-P1-001~050` / 50건**
- `VERIFIED_CLOSED`: **0**

Post-v2 review에서 Bridge/RBAC, target path, Shadow runtime/schema, Schema Registry, direct WorkflowService surface, independence/human/requalification/authority self-attestation, null fail-close, collection digest 문제를 추가 발견했다. 같은 branch에서 candidate fix가 존재해도 compile/test/independent verification 전에는 CLOSED하지 않는다.

## 3. Machine Contract Set
Fixture Registry가 추적하는 Schema Candidate는 **23개**다.

### Status / Receipt / Authority / Independence
- `assurance-status-vocabulary.candidate.v2.schema.json`
- `assurance-receipt-envelope.candidate.v2.schema.json`
- `authority-principal-profile.candidate.v2.schema.json`
- `independence-profile.candidate.v2.schema.json`
- `independent-assurance-receipt.candidate.v2.schema.json`

### Denominator / Applicability / Population
- `semantic-denominator-epoch.candidate.v2.schema.json`
- `semantic-applicability-set.candidate.v2.schema.json`
- `assurance-population-denominator.candidate.v2.schema.json`
- `validation-case-population.candidate.v2.schema.json`
- `final-acceptance-population.candidate.v2.schema.json`

### Execution / Qualification / Learning Assurance
- `execution-identity.candidate.v2.schema.json`
- `validator-qualification-record.candidate.v2.schema.json`
- `blind-context-manifest.candidate.v2.schema.json`
- `human-reviewer-qualification.candidate.v2.schema.json`
- `qualification-benchmark-manifest.candidate.v2.schema.json`
- `ground-truth-producer-qualification.candidate.v2.schema.json`
- `hidden-corpus-governance.candidate.v2.schema.json`

### Final / Deployment / Activation / Shadow
- `semantic-assurance-gate-receipt.candidate.v2.schema.json`
- `final-approval-receipt.candidate.v2.schema.json`
- `final-lock.candidate.v2.schema.json`
- `verified-to-deployed-receipt.candidate.v2.schema.json`
- `contract-active-selector.candidate.v2.schema.json`
- `shadow-gate-comparison.candidate.v1.schema.json`

Orchestration Candidate:
- `workflow-operation-registry.candidate.v2.json`
- `product-process-lineage.candidate.v2.json`
- `contract-selector-rollout-state.candidate.v1.json`

## 4. Static Fixture Coverage
`semantic-assurance-v2-schema-instance-registry.candidate.v1.json` 기준:
- 23 Schema
- 23 valid fixture
- 46 semantic invalid fixture
- Schema당 최소 2 negative fixture
- fixture registration pending 0

Validator entrypoint: `scripts/validate-semantic-assurance-v2-contracts.py`.

실제 실행은 시도했으나 현재 ChatGPT container에서 branch가 local mount되지 않고 `github.com` DNS도 해석되지 않아 `BLOCKED_NOT_RUN`이다. 증적은 `evidence/semantic-assurance/v2-static-validation-attempt-20260812.json`에 보존한다. Fixture 존재를 PASS/QUALIFIED로 해석하지 않는다.

## 5. Runtime Candidate
- `SemanticAssuranceV2Reconstructor.java`
- `SemanticAssuranceV2WorkflowService.java` — package-local, server-bound context 필수
- `SemanticAssuranceV2DispatcherBridge.java`
- `SemanticAssuranceShadowGateComparator.java`
- `TenantRbacService.java` semantic operation durable authorization 확장
- `SemanticAssuranceV2WorkflowServiceTest.java`
- `SemanticAssuranceV2DispatcherBridgeTest.java`

재검토 후 Runtime 후보에 적용된 핵심 Hardening:
- Reconstructor null fail-closed crash 제거
- List/collection digest의 Map 강제변환 오류 제거
- WorkflowService public direct surface 제거
- Service는 server-bound project/target/root context 없이는 실행 불가
- semantic operation 이름을 그대로 `TenantRbacService` durable authorization ledger에 보존
- target ownership 검증과 semantic candidate call을 같은 authorization transaction 경계에서 실행
- RegisteredTarget.sourceRoot를 server-side file authority로 사용하고 target path escape 차단
- caller `_authorized_*` injection 금지
- applicability는 SA-01~14 exact denominator를 요구
- denominator는 empty/duplicate/invalid disposition을 fail-close하고 N/A/Excluded에는 disposition evidence 요구
- caller self-attested independence, signed flag, Human Acceptance, Qualification metric, authority validity를 assurance proof로 사용하지 않음
- 실제 verifier가 연결되기 전 Independent/Human/Qualification/Authority effect-time 결과는 HOLD
- target-bound deployment identity가 없으므로 실제 Bridge의 `deployment.verify-installed`는 BLOCKED
- Shadow comparator output/schema 정합화

Compile/JUnit/독립 재검증이 실행되지 않았으므로 이 코드는 `IMPLEMENTATION_CANDIDATE`다.

## 6. Canonical Gate 편입
Semantic Assurance가 실제 제품 Gate가 되려면 최소 다음 경로가 동시에 닫혀야 한다.
1. Product Process Lineage
2. Workflow Operation Registry / Dispatcher
3. Validation Case / Final Acceptance exact denominator
4. Final Reconstruction → Approval → Lock → Deployment currentness

현재 Candidate 설계와 fail-closed runtime 후보는 존재하지만 v2는 active authority가 아니다.

## 7. Independent Gate 원칙
Local Agent의 `OTESTER|OAUDIT` 명칭이나 caller `independent=true`는 독립성 증명이 아니다. 실제 independent gate는 Principal/Credential Admin/Implementation/Oracle/Discovery/Knowledge independence와 current Qualification, 서명/키 유효성, exact receipt binding을 검증해야 한다. 현재 runtime은 verifier가 없으므로 HOLD한다.

## 8. Final / Selector 경계
Final 순서는 다음을 분리한다.
`Semantic Gate Reconstruction -> Independent OTester -> Independent OAudit -> Human Acceptance -> Final Approval -> Final Lock -> Verified-to-Deployed -> Currentness`

Active Selector는 현재 HOLD이며 v1 authority를 유지한다. Candidate 파일을 검색해 자동 활성화하지 않는다.

## 9. 현재 상태
- canonical Finding: P0 141 / P1 50 / raw baseline 562
- v2 Schema Candidate: 23
- valid/invalid fixture: 23/46
- Fixture registration pending: 0
- Adapter/Reconstructor/Workflow/Bridge/Shadow runtime candidate: 존재
- semantic durable RBAC + target-bound JUnit: 존재
- Static Schema 실제 실행: `BLOCKED_NOT_RUN`
- Java compile/JUnit: `NOT_RUN`
- v1→v2 actual reconstruction population: `NOT_RUN`
- exact denominator migration execution: `NOT_RUN`
- true independent OTester/OAudit: `NOT_RUN`
- signed Human Acceptance verifier: `NOT_RUN`
- Validator Qualification independent execution: `NOT_RUN`
- Shadow Gate actual comparison: `NOT_RUN`
- target-bound Deployment identity: `NOT_AVAILABLE / deployment.verify-installed BLOCKED`
- Active Selector: `HOLD / V2_NOT_ACTIVE`
- FinalLock/Production/Commercial authority: 없음

현재 최고 표현은 **`DESIGN_CONTRACT_FIXTURE_AND_FAIL_CLOSED_IMPLEMENTATION_CANDIDATES_PRESENT / EXECUTION_BLOCKED_OR_NOT_RUN / NON_FINAL`**이다.
