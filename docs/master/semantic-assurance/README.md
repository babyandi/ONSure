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
- `21_CLAUDE_DEVELOPMENT_HANDOFF.md`: 현재 Claude 개발 실행 정본

### Design Continuation — 개발보다 앞서 정의하는 기준선
- `21_INDEPENDENT_ASSURANCE_EXECUTION_ARCHITECTURE.md`: 독립 OTester/OAudit/Human Fact Validation 실행 아키텍처
- `22_TARGET_BOUND_DEPLOYMENT_AND_RELEASE_IDENTITY.md`: Target→Build→Release→Deployment→Currentness identity
- `23_RUNTIME_EXECUTION_EVIDENCE_AND_QUALIFICATION.md`: 실행 Receipt/Attempt/Evidence/Qualification 상태 체계
- `24_ACTIVE_SELECTOR_ROLLOUT_AND_ROLLBACK_GOVERNANCE.md`: v1/v2 Shadow→Activation→Rollback governance
- `25_VALIDATOR_BUILD_PROVENANCE_AND_TCB.md`: Validator build provenance, SBOM, TCB, qualification binding
- `26_REQUIREMENT_UNIVERSE_AND_DISCOVERY_AUTHORITY.md`: Requirement Universe/Discovery/Applicability/Denominator authority
- `27_EVIDENCE_CANONICALIZATION_AND_CRYPTO_LIFECYCLE.md`: canonicalization, self-hash, signature, key lifecycle, replay
- `28_DISTRIBUTED_CURRENTNESS_AND_REVOCATION.md`: distributed stale/revocation/currentness/offline propagation
- `29_DEPLOYMENT_RUNTIME_CURRENTNESS_AND_REVOCATION_DESIGN.md`: Verified→Deployed→Running→Currentness, rollout, drift, invalidation, rollback/recovery
- `30_DISTRIBUTED_ASSURANCE_COMPOSITION_AND_EVIDENCE_GRAPH.md`: multi-target Product Assurance composition, dependency propagation, Evidence Graph
- `31_ASSURANCE_CERTIFICATE_OFFLINE_ENTERPRISE_GOVERNANCE.md`: customer Certificate, public verification, Offline Trust, delegation/four-eyes/break-glass
- `32_SCALE_PLUGIN_AI_META_ASSURANCE_DESIGN.md`: WorkUnit/scale, Plugin/Adapter trust, AI runtime/nondeterminism/multi-agent, ONSure release qualification
- `33_RUNTIME_COMPOSITION_CERTIFICATE_TEST_OPERATION_EXTENSION.md`: 29~32 negative/adversarial/failure-injection/운영 시험
- `34_AI_RUNTIME_MULTI_AGENT_AND_ONSURE_META_ASSURANCE_EXTENSION.md`: AI runtime identity, stochastic validation, multi-agent, provider drift, Meta-Assurance
- `35_RUNTIME_COMPOSITION_CERTIFICATE_OPEN_DECISIONS.md`: Currentness/Composition/Certificate/Offline/Enterprise/Scale/Plugin/AI 정책 미확정값
- `36_DESIGN_COMPLETION_AND_REMAINING_GAPS.md`: 설계 완성도와 잔여 구조/Contract 공백 기준선
- `37_NEXT_CONTRACT_BATCH_BLUEPRINT.md`: 다음 machine contract batch의 필드·불변식·negative fixture 설계
- `38_FORMAL_ASSURANCE_ALGEBRA_AND_STATE_LATTICE.md`: Decision/Strength/Currentness/Uncertainty/Independence/Qualification formal semantics
- `39_INVALIDATION_IMPACT_AND_CURRENTNESS_ENGINE.md`: 변경 이벤트→영향 graph→재평가/currentness 알고리즘
- `40_EVIDENCE_GRAPH_STORAGE_INDEX_AND_QUERY.md`: evidence graph persistence, graph head, index/query, compaction, tenant isolation
- `41_ASSURANCE_CERTIFICATE_VERIFICATION_PROTOCOL.md`: online/offline/historical certificate verification protocol
- `42_ASSURANCE_POLICY_PROFILE_AND_RULE_GOVERNANCE.md`: assurance policy profile, rule versioning, weakening gate, policy epoch
- `43_PERSISTENCE_CONSISTENCY_AND_RECOVERY_ARCHITECTURE.md`: authority storage, Evidence commit protocol, graph head, restore/recovery qualification
- `44_STATE_TRANSITION_AND_OPERATION_LIFECYCLE.md`: intent→authorization→effect→evidence→decision lifecycle, retry/cancel/timeout semantics
- `45_API_ERROR_IDEMPOTENCY_AND_TRANSACTION_SEMANTICS.md`: transport/business/assurance 분리, idempotency, snapshot pagination, bulk/async error semantics
- `46_SECURITY_PRIVACY_AND_DATA_GOVERNANCE.md`: data class/purpose/tenant/hidden corpus/AI provider/retention/export governance
- `47_OBSERVABILITY_AUDIT_AND_OPERATIONAL_SLO.md`: degraded mode, assurance issuance suspension, audit, incident→assurance impact
- `48_PHYSICAL_DATA_MODEL_AND_STORAGE_PARTITIONING.md`: physical tables, keys, uniqueness, partition, population storage, semantic migration
- `49_THREAT_MODEL_AND_TRUST_BOUNDARY_ARCHITECTURE.md`: trust boundary, attacker model, false-assurance threat classes, abuse cases
- `50_VERSIONING_COMPATIBILITY_AND_INTEROPERABILITY.md`: semantic versioning, mixed-version cluster, migration receipt, certificate/plugin compatibility
- `51_DISASTER_RECOVERY_AND_BUSINESS_CONTINUITY_ASSURANCE.md`: DR lifecycle, recovery qualification, failover authority, evidence/key loss
- `52_EXTERNAL_INTEGRATION_AND_SUPPLY_CHAIN_TRUST.md`: Git/CI/OLicense/AI/registry integration provenance, reconciliation, supply-chain trust

기존 `docs/master/02~08` 본문에도 FR-META-001~060과 Meta Review/Architecture/Test/AI 기준이 직접 또는 companion으로 반영된다. Companion은 기존 정본을 대체하지 않고 상세설계와 machine-contract 후보를 제공한다.

## 2. 역할 분리
- **Claude**: 개발·컴파일·테스트·runtime implementation·migration execution
- **ONSure 설계 정본**: 본 디렉터리와 `docs/master/02~08`
- **검토/독립검증**: 실제 실행 산출물이 충분히 쌓인 후 별도 수행

Claude 구현은 설계 상태를 임의로 `ACTIVE`, `QUALIFIED`, `FINAL`로 승격하지 않는다.

## 3. Finding 기준
현재 source-grounded review 기준:
- raw candidate observation baseline: **562**
- canonical P0: **FL-P0-001~141 / 141건**
- canonical P1: **FL-P1-001~050 / 50건**
- `VERIFIED_CLOSED`: **0**

## 4. Machine Contract Set
현재 Schema Inventory 기준 **31개 Schema Candidate**이며, 기존 23개는 valid 23 + semantic-invalid 46 fixture가 있다. 신규 8개는 fixture/runtime 구현 대기다.

29~52에서 설계한 Composition, Evidence Graph, Certificate, AuthorityGrant, Distributed Work, AI Behavior Population, ONSure Release Qualification, Assurance Policy, Persistence/Operation/API/Data Governance/Observability/Physical Model/Versioning/DR/External Trust 영역은 다음 Contract/Runtime Batch다. 문서 존재를 Contract/Runtime 존재로 해석하지 않는다.

## 5. Canonical Gate 편입
실제 제품 Gate가 되려면 최소 다음이 동시에 닫혀야 한다.
1. Product Process Lineage
2. Workflow Operation Registry / Dispatcher
3. Requirement/Validation/Final exact denominator
4. Independent OTester/OAudit/Human Fact Validation
5. Final Reconstruction → Approval → Lock
6. Target-bound Deployment/Verified-to-Deployed
7. Running Population/Currentness/Revocation
8. Multi-target Product Composition / Evidence Graph
9. Certificate issuance/current verification
10. Active Selector transition/rollback
11. ONSure Release Qualification
12. Assurance Policy Profile/epoch binding
13. Authoritative persistence/graph-head/recovery qualification
14. Effect-time authorization/idempotency/operation lifecycle
15. Data governance/tenant/privacy trust boundary
16. Operational degraded-mode/issuance-suspension propagation

## 6. 현재 설계 판단
36번의 92~94% 평가는 00~35 시점 기준선이다. 38~52에서 formal semantics, persistence, operation, API, data governance, observability, physical storage, threat model, versioning, DR, external trust까지 추가되어 **큰 구조적 설계 공백은 더 감소했다.** 다만 Contract 제정, Open Decision 확정, 02~08 안전 병합, 개발·실행·독립검증이 남아 있으므로 별도 재평가 전 기존 숫자를 자동 상향하지 않는다.

현재 최고 표현은 **DESIGN_BASELINE_00_TO_52_EXTENDED_AHEAD_OF_DEVELOPMENT / MACHINE_CONTRACT_AND_POLICY_CLOSURE_PENDING / NON_FINAL**이다.
