# 137 Claude Development Master Handoff

Status: `CLAUDE_IMPLEMENTATION_NOT_STARTED / DEVELOPMENT_ENTRYPOINT_READY`

이 문서는 main 병합 이후 Claude 개발의 단일 최신 진입점이다. 과거 `21_CLAUDE_DEVELOPMENT_HANDOFF.md`, `81_NEXT_DEVELOPMENT_BATCH_F_TO_K.md`, `91_REQUIREMENT_UNIVERSE_MATERIALIZATION_HANDOFF.md`를 폐기하지 않지만, 개발 순서와 최신 추가 설계(Safety/Hazard, Contestability/Appeal, FR-FRESH-001~003)는 이 문서를 우선한다.

## 0. 개발 전 정합성 선행조건
`138_IMPLEMENTATION_READY_DESIGN_BASELINE_RECONCILIATION.md`의 6개 pre-implementation reconciliation은 완료됐다. 다음 후보 계약은 Batch 0/1 입력으로 사용하되 Active로 간주하지 않는다.
- `contracts/business-actor-rbac-mapping.candidate.v1.json`
- `contracts/workflow-operation-extension.candidate.v2.json`
- `contracts/coverage-report.candidate.v1.schema.json`
- `contracts/acceptance-certificate.candidate.v1.schema.json`
- `contracts/policy-pack-version.candidate.v1.schema.json`
- `contracts/open-decision-policy-binding.candidate.v1.json`

Active Workflow Operation count의 단일 권위는 `contracts/workflow-operation-registry.v1.json`이다 (Batch 2/3 개발 중 real operation 추가로 개수는 계속 증가할 수 있으므로 이 문서에 숫자를 하드코딩하지 않는다; 현재값은 registry 파일과 `status/verification-status.v1.json`의 `workflow_surface_parity.dispatcher_operation_count`를 참조).

## 1. 기준선
- Product Design Scope: COMPLETE_CANDIDATE
- Design QA: IN_PROGRESS / HOLD
- Claude Implementation: NOT_STARTED
- Test / Independent Assurance / Production: NOT_STARTED
- Candidate Contract != Active Contract
- 구현 완료 != Test PASS != Independent Assurance PASS != Production GO

## 2. 참조 정본
1. `docs/master/00_ONSURE_MASTER_DESIGN_SET.md`
2. `docs/master/02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md` ~ `08_REVIEW_CHECKLIST_OPEN_DECISIONS.md`, `08A_ASSURANCE_POLICY_AND_OPEN_DECISION_INTEGRATION.md`
3. `docs/master/semantic-assurance/21_CLAUDE_DEVELOPMENT_HANDOFF.md`
4. `docs/master/semantic-assurance/81_NEXT_DEVELOPMENT_BATCH_F_TO_K.md`
5. `docs/master/semantic-assurance/91_REQUIREMENT_UNIVERSE_MATERIALIZATION_HANDOFF.md`
6. Safety/Hazard 및 Contestability/Appeal 설계
7. `128_FINAL_FRESH_REVIEW_RERUN_AND_PRODUCT_DESIGN_SCOPE_CLOSURE.md`
8. `136_PRE_MERGE_STATUS_CORRECTION_AND_BASELINE_HANDOFF.md`
9. `138_IMPLEMENTATION_READY_DESIGN_BASELINE_RECONCILIATION.md`

충돌 시 `docs/architecture/ONSURE_DESIGN_AUTHORITY_AND_SCOPE_v1.md`의 레이어/contract/supersession 규칙을 적용한다. 의미충돌은 임의 해석하지 말고 Design Change Queue로 등록한다.

## 3. 개발 Batch 0~9
### Batch 0 — Baseline / Requirement Materialization
Global Requirement Universe, deterministic non-ID requirement materialization, taxonomy/normalization, Applicability, Global Trace Registry, Design Lock scanner 입력 기반을 구현한다. 138에서 materialize한 Actor/RBAC, Operation extension, Open Decision policy binding을 denominator/trace에 포함한다.

### Batch 1 — Core Contract Foundation
JSON Schema 및 cross-contract validator, canonical state vocabulary, Authority/Grant, Operation/Event/Receipt contract를 구현한다. 138의 Candidate Schema/Registry에 valid/semantic-invalid fixture를 붙이고 activation 조건을 증명한다.

### Batch 2 — Runtime Foundation
Primary dispatcher, authenticated API, persistence, transaction/idempotency, event/receipt emission, fail-closed runtime을 구현한다.

### Batch 3 — Assurance Core
Evidence Graph, invalidation/currentness, deployment identity, product composition, certificate/revocation/offline verification을 구현한다.

### Batch 4 — Enterprise / Trust
Policy Profile, industry profile, RBAC/SoD/four-eyes/delegation/break-glass, Plugin/Adapter qualification, external integration trust를 구현한다.

### Batch 5 — AI / Meta-Assurance
Model/provider/prompt/RAG/tool/memory/multi-agent identity, stochastic validation, provider drift, ONSure release/validator/oracle/fixture/adapter qualification을 구현한다.

### Batch 6 — Safety / Appeal
Hazard/SafetyRequirement/SafetyControl/SafetyCase/ResidualSafetyRisk/currentness/fault injection과 AppealCase/independent reviewer/original decision immutability/appeal impact propagation을 구현한다.

### Batch 7 — Fresh Review Refinements
FR-FRESH-001 Rules of Engagement/Target Testing Authorization, FR-FRESH-002 Accessibility/i18n/locale integrity, FR-FRESH-003 Offboarding Export→Revoke→Delete→Closure Receipt를 구현한다.

### Batch 8 — Migration / Integration
v1→v2 dual-read/shadow-write/divergence/cutover/rollback, selector shadow gate, backward compatibility를 구현한다.

### Batch 9 — Test Materialization
positive/negative/semantic-invalid/adversarial/integration/fault-injection/security/tenant-isolation/migration/runtime-currentness/certificate/recovery/scale/plugin/AI/meta-assurance 테스트를 materialize한다.

## 4. Batch Done Gate
어떤 Batch도 source file 존재만으로 완료하지 않는다. 최소 Done Gate:
1. Contract/Schema 또는 명시적 interface contract
2. Implementation
3. Valid fixture 또는 positive test
4. Semantic-invalid/negative fixture 또는 negative test
5. Cross-contract/invariant test (해당 시)
6. Compile/build 성공
7. JUnit/자동검증 실제 실행 성공
8. Evidence/Receipt/Report 생성
9. Requirement/Design Trace 갱신
10. blocker/P0 unresolved 여부 기록

상태 승격은 `NOT_STARTED → IN_PROGRESS → IMPLEMENTED → TESTED → EVIDENCE_READY` 순서다. 실패/외부 의존은 `BLOCKED`. 중간 단계를 건너뛰지 않는다.

## 5. 금지사항
- failing test/fixture 삭제·skip·완화로 PASS 만들기 금지
- UNKNOWN/STALE/PARTIAL/NOT_RUN/INCONCLUSIVE를 PASS로 변환 금지
- v1 PASS를 v2 PASS로 자동 상속 금지
- caller self-attestation으로 independence/qualification/authority 생성 금지
- self-validation으로 OTester/OAudit/Final authority 대체 금지
- Active Selector 임의 변경 금지
- FinalLock / Production GO / Commercial GO 생성 금지
- evidence 없는 Finding CLOSED 금지
- 구현 편의를 위해 requirement/design 의미를 축소·삭제 금지
- main에 직접 개발 금지

## 6. Design Change Queue
개발 중 설계 공백/모순을 발견하면 즉시 구현으로 덮지 않는다. `contracts/design-change-queue.v1.json`에 change_id, 발견 Batch/Requirement/Design, 현재 설계 의미, 구현상 충돌/새 semantics, P0/P1/P2, proposed disposition, affected Contract/Test/Evidence를 기록한다. P0 unresolved이면 해당 Batch는 EVIDENCE_READY로 승격하지 않는다.

## 7. 개발 순서 원칙
Batch 0→1→2→3→4→5→6→7→8→9 순서를 기본으로 한다. 독립적으로 구현 가능한 세부 항목은 병렬화할 수 있지만 parent Contract/Authority/State semantics보다 앞서 positive authority를 생성하지 않는다.

## 8. 첫 개발 시작점
Claude는 먼저 Batch 0을 시작한다. `91_REQUIREMENT_UNIVERSE_MATERIALIZATION_HANDOFF.md`의 RU 흐름을 최신 Safety/Appeal/FR-FRESH와 138 Candidate wave까지 확장하고, exact denominator가 확정되기 전에는 coverage=100% 또는 orphan=0을 선언하지 않는다.

## 9. 개발 완료 후 경계
Batch 9까지 개발/자체 테스트가 완료되어도 바로 Release가 아니다. 그 다음 Phase는 별도의 Implementation QA → OTester/OAudit/Human Fact Validation → Validator/Release Qualification → target-bound deployment/currentness → Release Gate다.
