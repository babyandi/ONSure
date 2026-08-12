# ONSure Semantic Assurance Companion Design Set

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

이 디렉터리는 ONSure의 false assurance, 권위 오판, 독립성 오판, stale/revoked 결과 재사용, denominator 축소, Receipt 의미손실, canonical gate 우회를 기존 `docs/master/02~08` 책임구조에 맞춰 통합한다.

## 1. 문서 Set
### Core / Review / Migration
- `00_INTEGRATION_AND_OWNERSHIP.md`
- `02_FUNCTIONAL_REQUIREMENTS_EXTENSION.md`
- `03_REVIEW_SPECIFICATION_EXTENSION.md`
- `04_ARCHITECTURE_DATA_API_EXTENSION.md`
- `05_UI_UX_WORKFLOW_EXTENSION.md`
- `06_TEST_OPERATION_EXTENSION.md`
- `07_AI_AGENT_METHOD_EXTENSION.md`
- `08_OPEN_DECISIONS_EXTENSION.md`
- `09_INDEPENDENT_REVIEW_FINDINGS_INTEGRATION.md`
- `10_FINDING_LEDGER.md`
- `11_CONTRACT_UPGRADE_BLUEPRINT.md`
- `12_P0_VERTICAL_TRACEABILITY_AND_APPLICATION.md`
- `13_V2_CONTRACT_MIGRATION_AND_VALIDATION_PLAN.md`
- `14_V1_V2_SEMANTIC_GAP_MATRIX.md`
- `15_V2_STATIC_QUALIFICATION_FIXTURE_SPEC.md`
- `16_ARTIFACT_COVERAGE_AND_COMPLETION_MATRIX.md`
- `17_RUNTIME_WIRING_AND_ADAPTER_IMPLEMENTATION.md`
- `18_VALIDATION_FINAL_DENOMINATOR_MIGRATION.md`
- `19_FINAL_REVIEW_AND_EXECUTION_BLOCKERS.md`
- `20_POST_V2_FINAL_REVIEW_FINDINGS.md`

### Claude Development Handoff
- `21_CLAUDE_DEVELOPMENT_HANDOFF.md`: 현재 Claude 개발 실행 정본

### Design Continuation 21~69
`21_INDEPENDENT_ASSURANCE_EXECUTION_ARCHITECTURE.md`부터 `69_CUSTOMER_DELIVERY_REPORT_AND_CLAIM_LANGUAGE_GOVERNANCE.md`까지 Independent Assurance, Deployment/Runtime, Currentness/Revocation, Product Composition/Evidence Graph, Certificate/Offline/Enterprise, Scale/Plugin/AI/Meta, Persistence/API/Security/Privacy/Observability/DR/External Trust, Trace/Authority/Safe Default, Machine Contract/Policy/Industry/Tier/Claim Governance를 정의한다.

### 30개 설계 폐쇄 Batch 70~80
- `70_THIRTY_TASK_DESIGN_CLOSURE_MASTER_PLAN.md`
- `71_MACHINE_CONTRACT_FIELD_BY_FIELD_SPEC.md`
- `72_CROSS_CONTRACT_SEMANTIC_RULE_TABLE.md`
- `73_GLOBAL_STATE_TRANSITION_MATRIX.md`
- `74_OPERATION_EVENT_RECEIPT_AUTHORITY_MATRIX.md`
- `75_POLICY_INDUSTRY_AND_ASSURANCE_TIER_BASELINE.md`
- `76_COMPOSITION_EVIDENCE_INVALIDATION_RECOVERY_CERTIFICATE_FINAL_SPEC.md`
- `77_EXTERNAL_PLUGIN_AI_META_ASSURANCE_FINAL_SPEC.md`
- `78_DATA_API_THREAT_OBSERVABILITY_SAFE_DEFAULT_FINAL_SPEC.md`
- `79_NAMING_CONFLICT_TRACE_AND_MASTER_INDEX_CLOSURE.md`
- `80_DESIGN_BASELINE_CANDIDATE_LOCK_PRECONDITIONS.md`

### 다음 개발 Batch·Lock·Global Denominator 81~91
- `81_NEXT_DEVELOPMENT_BATCH_F_TO_K.md`
- `82_SCHEMA_AND_CROSS_CONTRACT_IMPLEMENTATION_SEQUENCE.md`
- `83_PERSISTENCE_MIGRATION_AND_DUAL_WRITE_GOVERNANCE.md`
- `84_POLICY_BOOTSTRAP_AND_PROFILE_DEFAULTS.md`
- `85_RUNTIME_API_AND_ERROR_CONTRACT_HANDOFF.md`
- `86_DESIGN_ARTIFACT_INVENTORY_AND_LOCK_GOVERNANCE.md`
- `87_DESIGN_LOCK_CHECK_AND_REPOSITORY_ORPHAN_SCAN.md`
- `88_GLOBAL_REQUIREMENT_UNIVERSE_AND_DENOMINATOR.md`
- `89_REQUIREMENT_ID_NORMALIZATION_AND_SEMANTIC_DEDUPLICATION.md`
- `90_GLOBAL_TRACE_CLOSURE_SCANNER_DESIGN.md`
- `91_REQUIREMENT_UNIVERSE_MATERIALIZATION_HANDOFF.md`

### 50개 설계 정밀화 Batch 92~101
- `92_REQUIREMENT_UNIVERSE_TAXONOMY_APPLICABILITY_AND_CHANGE_IMPACT.md`
- `93_REQUIREMENT_CONFLICT_BASELINE_AND_CHANGE_CONTROL.md`
- `94_SCHEMA_RELATION_OPERATION_EVENT_RECEIPT_STATE_FINALIZATION.md`
- `95_ASSURANCE_ALGEBRA_COMPOSITION_EVIDENCE_INVALIDATION_CURRENTNESS.md`
- `96_DEPLOYMENT_CERTIFICATE_AUTHORITY_POLICY_INDUSTRY_TIER_FINALIZATION.md`
- `97_AI_PLUGIN_META_ASSURANCE_FINALIZATION.md`
- `98_DATA_MIGRATION_API_SECURITY_OBSERVABILITY_RECOVERY_EXTERNAL_FINALIZATION.md`
- `99_SAFE_DEFAULT_NAMING_MASTER_COMPLETION_AND_ORPHAN_CLOSURE.md`
- `100_DESIGN_INVENTORY_LOCK_CLAUDE_DRIFT_CHANGE_QUEUE_AND_FINAL_BASELINE.md`
- `101_FIFTY_TASK_DESIGN_CLOSURE_MASTER_MATRIX.md`

### 15단계 Design Lock Closure 102~107
- `102_GLOBAL_REQUIREMENT_MATERIALIZATION_APPLICABILITY_AND_TRACE_EXECUTION_PLAN.md`
- `103_GLOBAL_ORPHAN_CONTRADICTION_INVENTORY_AND_BASELINE_LOCK.md`
- `104_DESIGN_BASELINE_CANDIDATE_DECISION_AND_CHANGE_CONTROL.md`
- `105_DESIGN_TO_IMPLEMENTATION_INVENTORY_ALIGNMENT.md`
- `106_CLAUDE_SEMANTIC_CHANGE_INTAKE_AND_DESIGN_LOCK_CANDIDATE.md`
- `107_FIFTEEN_STEP_DESIGN_LOCK_CLOSURE_MASTER_MATRIX.md`

## 2. Parent 정본 통합 상태
- `02`: FR-COM-001~013, FR-META-001~060 및 프로그램 기능/수용기준 직접 존재
- `03`: Runtime/Composition/Certificate/Meta-Assurance Review 직접 흡수
- `04`: Deployment/Currentness/Composition/Certificate/Scale Architecture 직접 흡수
- `05`: Runtime Currentness/Product Composition/Certificate UX 직접 흡수
- `06`: Runtime/Composition/Certificate/Scale/Plugin/AI/Meta-Assurance 시험 직접 흡수
- `07`: AI Runtime/Behavior Population/Tool/Memory/RAG/Multi-Agent/ONSure Meta-Assurance 직접 흡수
- `08`: 기존 결정이력 보존
- `08A_ASSURANCE_POLICY_AND_OPEN_DECISION_INTEGRATION.md`: 신규 Assurance policy 결정 부속 정본

## 3. 역할 분리
- Claude: 개발·컴파일·테스트·runtime implementation·migration execution
- ONSure 설계 정본: `docs/master`와 본 companion set
- 검토/독립검증: 실제 실행 산출물이 충분히 쌓인 후 별도 수행

Claude 구현은 설계 상태를 임의로 ACTIVE/QUALIFIED/FINAL로 승격하지 않는다.

## 4. Finding 기준
- raw candidate observation baseline: **562**
- canonical P0: **141**
- canonical P1: **50**
- VERIFIED_CLOSED: **0**

## 5. Machine-readable Design Closure·Handoff 산출물
- `contracts/design-trace-registry.candidate.v1.json`: FR-META-001~060 60행(부분 Universe)
- `contracts/design-orphan-report.candidate.v1.json`: FR-META requirement orphan 후보 0
- `contracts/design-conflict-report.candidate.v1.json`: unresolved P0 design semantic conflict 후보 0
- `contracts/design-baseline-manifest.candidate.v1.json`
- `contracts/design-baseline-receipt.candidate.v1.json`
- `contracts/next-development-batch-plan.candidate.v1.json`
- `contracts/design-artifact-inventory-policy.candidate.v1.json`
- `contracts/design-lock-check-report.candidate.v1.schema.json`
- `contracts/global-requirement-universe-plan.candidate.v1.json`
- `contracts/fifty-task-design-closure.candidate.v1.json`
- `contracts/fifteen-step-design-lock-closure.candidate.v1.json`
- `contracts/design-implementation-alignment.candidate.v1.json`
- `contracts/design-semantic-change-queue.candidate.v1.json`
- `contracts/design-baseline-candidate-decision.candidate.v1.json`

중요: 현재 machine trace 60행 + 확인된 FR-COM 13건의 **73건은 global Requirement 총수가 아니다.** NFR 및 ID 없는 Program 기능/수용기준/Invariant/Policy/Regulatory requirement까지 materialize한 exact population digest가 있어야 global denominator를 선언한다.

## 6. Machine Contract 구현 상태
PR #44 changed-file inventory에는 v2 candidate Contract/Registry, SemanticAssurance Runtime candidate, validator script, JUnit source가 존재한다. 그러나 81~107에서 설계한 Currentness/Composition/EvidenceGraph/Certificate/AuthorityGrant/DistributedWork/AIBehavior/ONSureReleaseQualification/PolicyProfile/Recovery/Global Lock Scanner 중 상당수는 독립 machine contract/runtime로 아직 materialize되지 않았다.

파일 존재는 ACTIVE/IMPLEMENTED/VALIDATED를 의미하지 않는다. `105_DESIGN_TO_IMPLEMENTATION_INVENTORY_ALIGNMENT.md`는 inventory-level 비교만 수행했으며 semantic code review는 의도적으로 뒤로 미뤘다.

## 7. Canonical Gate
실제 제품 Gate는 Product Lineage, Workflow Operation, exact global Requirement denominator, Independent Assurance, Final Reconstruction/Approval/Lock, Verified→Deployed→Running, Currentness/Revocation, Product Composition/Evidence Graph, Certificate verification, Active Selector, ONSure Release Qualification, Policy/Authority/Persistence/Recovery/Observability/Event/Receipt/Trace closure를 모두 요구한다.

## 8. 현재 설계 판단
50개 설계 정밀화와 후속 15단계 Design Lock Closure를 설계/대조/판정 수준까지 수행했다. 그러나 Global Requirement Universe materialization, repository-wide orphan/contradiction scan, exact content SHA-256 inventory, Design Lock Check, full implementation reverse scan은 아직 실행되지 않았다.

현재 최고 표현:
**`FIFTEEN_STEP_DESIGN_CLOSURE_DESIGNED / IMPLEMENTATION_INVENTORY_PARTIAL / DESIGN_BASELINE_CANDIDATE_HOLD / MACHINE_CONTRACT_IMPLEMENTATION_PENDING / NON_FINAL`**

`15/15 DESIGN TASKS ADDRESSED`를 `DESIGN LOCKED`, `QUALIFIED`, `FINAL`, `PRODUCTION READY`로 해석하지 않는다.
