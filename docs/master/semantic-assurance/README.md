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
- `10_FINDING_LEDGER.md`: P0/P1 canonical Finding ledger
- `11_CONTRACT_UPGRADE_BLUEPRINT.md`: v2 Contract Bundle A~J
- `12_P0_VERTICAL_TRACEABILITY_AND_APPLICATION.md`: Finding→02~08 수직 적용
- `13_V2_CONTRACT_MIGRATION_AND_VALIDATION_PLAN.md`: v1→v2 migration/shadow/selector
- `14_V1_V2_SEMANTIC_GAP_MATRIX.md`: DIRECT/READBACK/REPERFORMANCE/UNRECOVERABLE 분류
- `15_V2_STATIC_QUALIFICATION_FIXTURE_SPEC.md`: semantic negative fixture 기준
- `16_ARTIFACT_COVERAGE_AND_COMPLETION_MATRIX.md`: 현재 산출물·완성도 정본
- `17_RUNTIME_WIRING_AND_ADAPTER_IMPLEMENTATION.md`: Adapter/Reconstructor/Runtime wiring
- `18_VALIDATION_FINAL_DENOMINATOR_MIGRATION.md`: Validation/Final fixed-count authority 제거

기존 `docs/master/02~08` 본문에도 FR-META-001~043과 Meta Review/Architecture/Test/AI 기준이 직접 반영되어 있으며 companion 문서는 이를 삭제하거나 대체하지 않는다.

## 2. Finding 기준
현재 source-grounded review 기준:
- raw candidate observation: 551
- canonical P0: `FL-P0-001~132`
- canonical P1: `FL-P1-001~048`

Raw count는 canonical defect count가 아니다. `semantic-assurance-finding-disposition.candidate.v1.json`은 Candidate Contract/Fixture가 존재하더라도 실제 실행·독립검증 전 Finding 상태를 자동 승격하지 않는다. 현재 `VERIFIED_CLOSED=0`이다.

## 3. Machine Contract Set
현재 fixture registry가 추적하는 Schema Candidate는 **23개**다.

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

Validator entrypoint:
- `scripts/validate-semantic-assurance-v2-contracts.py`

실제 실행은 시도했으나 현재 ChatGPT container가 `github.com` DNS를 해석하지 못하고 branch가 local mount되어 있지 않아 `BLOCKED_NOT_RUN`이다. 실행 시도는 `evidence/semantic-assurance/v2-static-validation-attempt-20260812.json`에 보존한다. Fixture가 존재한다고 PASS/QUALIFIED를 주장하지 않는다.

## 5. Runtime Candidate
- `SemanticAssuranceV2Reconstructor.java`
- `SemanticAssuranceV2WorkflowService.java`
- `SemanticAssuranceV2DispatcherBridge.java`
- `SemanticAssuranceShadowGateComparator.java`
- `SemanticAssuranceV2WorkflowServiceTest.java`

Reconstructor는 v1 PASS를 v2 PASS로 자동 변환하지 않는다. 누락된 tenant/scope/requirement/denominator/authority/independence/qualification/freshness/oracle/validator 정보는 `READBACK`, `REPERFORMANCE`, `HUMAN_OR_EXTERNAL_AUTHORITY`, `UNRECOVERABLE`로 분류한다.

Runtime class가 존재한다고 `IMPLEMENTED`로 승격하지 않는다. compile/JUnit/primary dispatcher wiring evidence가 필요하다.

## 6. Canonical Gate 편입
Semantic Assurance가 실제 제품 Gate가 되려면 최소 다음 네 경로가 동시에 닫혀야 한다.
1. Product Process Lineage
2. Workflow Operation Registry / Dispatcher
3. Validation Case / Final Acceptance exact denominator
4. Final Reconstruction → Approval → Lock → Deployment currentness

현재 네 경로 모두 Candidate 설계는 존재하지만 v2가 active authority는 아니다.

## 7. Independent Gate 원칙
Local Agent의 `OTESTER|OAUDIT` 명칭은 `SELF_VALIDATION_NONFINAL`일 수 있다. 실제 independent gate는 `independence-profile.candidate.v2.schema.json`과 `independent-assurance-receipt.candidate.v2.schema.json`의 Principal/Credential Admin/Implementation/Oracle/Discovery/Knowledge independence 및 current Qualification을 요구한다.

다른 key/model/run ID만으로 independent를 주장하지 않는다.

## 8. Final / Selector 경계
Final은 다음 순서를 분리한다.
`Semantic Gate Reconstruction -> Independent OTester -> Independent OAudit -> Human Acceptance -> Final Approval -> Final Lock -> Verified-to-Deployed -> Currentness`

`contract-active-selector.candidate.v2.schema.json`은 Candidate일 뿐이며 `contract-selector-rollout-state.candidate.v1.json`은 현재 v1 authority 유지, v2 activation HOLD를 명시한다. Candidate 파일을 검색해 자동 활성화하지 않는다.

## 9. 현재 상태
- 설계: 광범위 반영
- P0 vertical trace: 존재
- v2 Schema Candidate: 23
- valid/invalid fixture: 23/46
- Adapter/Reconstructor/Workflow/Shadow runtime candidate: 존재
- Static Schema 실제 실행: `BLOCKED_NOT_RUN`
- Java compile/JUnit: `NOT_RUN`
- primary Dispatcher v2 wiring: `NOT_RUN`
- v1→v2 actual reconstruction population: `NOT_RUN`
- exact denominator migration execution: `NOT_RUN`
- independent OTester/OAudit: `NOT_RUN`
- Shadow Gate actual comparison: `NOT_RUN`
- Active Selector: `HOLD / V2_NOT_ACTIVE`
- FinalLock/Production/Commercial authority: 없음

따라서 현재 최고 표현은 **`DESIGN_CONTRACT_FIXTURE_AND_IMPLEMENTATION_CANDIDATES_PRESENT / EXECUTION_BLOCKED_OR_NOT_RUN / NON_FINAL`**이다.
