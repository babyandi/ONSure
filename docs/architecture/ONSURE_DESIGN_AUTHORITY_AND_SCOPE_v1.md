# ONSURE 설계 권위와 적용 범위 v1

Document-ID: `ONSURE-DESIGN-AUTHORITY-0001`
Status: `CURRENT_NORMATIVE_AUTHORITY / NON_FINAL`

파일명(`_v1`)은 자동화/기존 참조를 위해 유지하며, 권위 식별자는 파일번호가 아니라 `Document-ID`를 사용한다.

## 0. 개정 이력
- 2026-08-07: `docs/master`를 개발 설계 정본으로 승격.
- 2026-08-13: Semantic Assurance, Safety/Hazard, Contestability/Appeal, Design QA, 개발 Handoff 권위 체계 정리.
- 2026-08-18: `160_FINAL_TARGET_PRODUCT_AUTHORITY_RECONCILIATION.md` 이후 권위 충돌을 정정. `docs/05 + docs/41~44`는 CURRENT FINAL-TARGET PRODUCT AUTHORITY, `docs/40`은 REFERENCE_ONLY로 재분류. Product Design Scope는 `DISCOVERY_REOPENED`로 변경하고 `126/128 COMPLETE_CANDIDATE`는 historical pre-final-target evidence로 제한한다. `08_REVIEW_CHECKLIST_OPEN_DECISIONS.md`는 tracking-only, `08A_ASSURANCE_POLICY_AND_OPEN_DECISION_INTEGRATION.md`는 policy/open-decision design authority로 고정한다.

## 1. 목적
다수 설계 문서·계약·상태·코드가 서로 다른 시점과 범위에서 작성될 때 잘못된 정본을 선택하지 않도록 설계 권위, 적용범위, supersession, 상태표현을 고정한다.

## 2. 권위 계층
동일 의미영역의 충돌은 파일번호/경로/작성시각이 아니라 책임 레이어와 명시적 relation으로 해결한다.

### L0 — 제품 불변 원칙 / Trust Boundary
1. `README.md`의 독립제품·fail-closed·Standalone-first·금지된 Final/Production authority
2. 본 문서 `ONSURE-DESIGN-AUTHORITY-0001`

### L1A — Final-Target Product Authority
- `docs/05_PRODUCT_REQUIREMENTS_AND_ACCEPTANCE.md` — `FR-FIN-01~22` canonical identity/text anchor
- `docs/41_ONSURE_FINAL_TARGET_ARCHITECTURE.md` — final-target architecture refinement
- `docs/42_VSCODE_AGENT_AND_GIT_FULL_CHAIN_DESIGN.md` — VS Code/Git full-chain refinement
- `docs/43_FINANCIAL_CONTROL_TRACE_AND_ACCEPTANCE.md` — financial-control trace/acceptance refinement
- `docs/44_UNIFIED_AI_WORK_DEVELOPER_ASSURANCE_DESIGN.md` — unified work/developer/assurance refinement
- `docs/40_FINAL_PRODUCT_RESEARCH_AND_ROLE_MODELS.md` — `REFERENCE_ONLY`; source-derived requirement 후보를 만들 수 있으나 explicit `FR-FIN`을 직접 override하지 못함

위 분류는 `160_FINAL_TARGET_PRODUCT_AUTHORITY_RECONCILIATION.md`를 따른다. 이 문서군을 DEPRECATED로 취급하는 과거 문구는 superseded다.

### L1B — Development / Product Design Realization Authority
- `docs/master/00_ONSURE_MASTER_DESIGN_SET.md`
- `docs/master/01_BUSINESS_PRODUCT_SERVICE_PLAN.md`
- `docs/master/02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md`
- `docs/master/03_OREVIEW_CODE_REVIEW_SPECIFICATION.md`
- `docs/master/04_ARCHITECTURE_DATA_API_OLICENSE.md`
- `docs/master/05_UI_UX_WORKFLOW_SPECIFICATION.md`
- `docs/master/06_TEST_OPERATION_IMPLEMENTATION_PLAN.md`
- `docs/master/07_COMPONENT_MODEL_AND_AI_METHODOLOGY.md`
- `docs/master/08A_ASSURANCE_POLICY_AND_OPEN_DECISION_INTEGRATION.md`

`docs/master/08_REVIEW_CHECKLIST_OPEN_DECISIONS.md`는 **TRACKING_ONLY / NON_NORMATIVE**다. 미확정값/검토상태를 추적하지만 그 자체로 제품 의미를 확정하지 않는다.

### L2 — Semantic Assurance / 확장 설계 권위
`docs/master/semantic-assurance/*` 중 Requirement Authority Manifest에서 `NORMATIVE_CURRENT` 또는 `NORMATIVE_REFINEMENT`로 승인된 문서만 requirement-originating authority가 된다.

현재 post-final-target 핵심 relation:
- `160_FINAL_TARGET_PRODUCT_AUTHORITY_RECONCILIATION.md` — final-target/master dual-current authority 결정
- `162_FINAL_TARGET_DELTA_DESIGN_DISCOVERY_REOPENING.md` — DD-001~024 discovery authority
- `163_FINAL_TARGET_DELTA_MISSING_DESIGN_CLOSURE.md` — DD-001~024 companion design
- `165_BLIND_DESIGN_DISCOVERY_WAVES_2_3.md` — DD-025~040 discovery authority
- `166_WAVES_2_3_MISSING_DESIGN_CLOSURE.md` — DD-025~040 companion design
- `167_POST_DELTA_AUTHORITY_EPOCH3_AND_CLOSURE_EXECUTION_PREPARATION.md` — status/execution boundary; requirement-originating authority가 아님

과거 `126/128 ... SCOPE_CLOSURE`는 당시 scope의 historical evidence이며 현재 post-final-target Product Design Scope closure authority가 아니다.

### L3 — Machine Semantic Contract
`contracts/*.json`, `contracts/*.schema.json` 중 해당 의미영역의 canonical/active contract. Candidate/Design-only contract는 Active 의미를 자동 대체하지 않는다.

### L4 — 구현 / Test / Evidence
실행 코드, 자동 Test/Fixture, Evidence/Receipt/Status. 구현은 상위 의미를 묵시적으로 변경하지 못한다.

### L5 — 참고/과거 문서
Reference-only, tracking-only, superseded/deprecated 문서. 신규 positive claim의 단독 근거가 될 수 없다.

## 3. 영구 문서 ID 및 relation 규칙
- 숫자 prefix(`21`, `126`, `127`, `160` 등)는 정렬용 label일 뿐 authority key가 아니다.
- authority, supersession, trace는 `contracts/design-document-authority-registry.v1.json`의 immutable `document_id`를 사용한다.
- 관계 유형: `SUPERSEDES`, `REFINES`, `DECOMPOSES`, `STATUS_FOR`, `EVIDENCES`, `REFERENCE_FOR`, `CONFLICTS_WITH`.
- 동일 prefix 중복은 허용하되 short-number-only reference는 금지한다.

## 4. Supersession 규칙
- 큰 번호/최신 작성시각만으로 우선하지 않는다.
- 명시적 relation 또는 canonical owner 결정만 supersession authority를 가진다.
- `160` 이후 `docs/05 + 41~44` CURRENT 판정이 이전 DEPRECATED 판정을 supersede한다.
- `162~166` 이후 `PRODUCT_DESIGN_SCOPE_COMPLETE_CANDIDATE`는 현재 denominator에 승계되지 않는다.
- status 문서는 normative requirement를 생성하지 않는다.

## 5. 제품 경계
ONSure Core는 Workspace Intake, Program/Behavior/Improvement Learning, Planning, Review, Verification, Diagnosis, Improvement, Evidence, Memory, Git Full-Chain, CLI, Local Authenticated API, VS Code Extension을 독립 제공한다. ORUDA 관련 코드는 선택형 Target Adapter다.

## 6. 구현 상태 용어
- `IMPLEMENTED`: 권위 요구사항에 맞는 실행경로와 자동시험 존재
- `PARTIAL`: 일부만 구현
- `STUB`: 고정값/Marker/caller Boolean 등에 의존
- `DESIGN_ONLY`: 설계/후보 계약만 존재
- `NOT_RUN`: 실행 가능하나 현재 기준선 증적 없음
- `BLOCKED`: 필수 입력·환경·독립권한·선행 구현 없음
- `CONFLICT`: 권위 문서/계약/코드가 다른 의미 정의
- `DEPRECATED`: 현행 기준선 사용 금지
- `TRACKING_ONLY`: 진행/결정상태 추적용, 단독 normative authority 아님
- `REFERENCE_ONLY`: 참고/연구 입력, Requirement Authority 절차 없이 denominator 생성 금지

## 7. Product Design Scope 현재 판정
- Final-target tree: `CURRENT_NORMATIVE_PRODUCT_TARGET`
- Master realization tree: `CURRENT_NORMATIVE_DESIGN_REALIZATION`
- DD-001~040: `ADMITTED_DESIGN_OBLIGATIONS / COMPANION_DESIGNED / NON_FINAL`
- Product Design Scope: `DISCOVERY_REOPENED`
- Global Discovery Exhausted: `false`
- Design QA / Baseline Lock: `HOLD`
- Implementation/Test/Independent Assurance: 현재 실제 evidence registry 기준

`COMPLETE_CANDIDATE`, Design Lock, FinalApproval, FinalLock, Production GO, Commercial GO를 현재 상태로 주장하지 않는다.

## 8. 변경 규칙
모든 기능 변경은 `Requirement -> Design -> Contract/Schema -> Operation/API/Event -> Code -> Test -> Evidence -> Status`를 갱신한다. 설계 공백/충돌은 `contracts/design-change-queue.v1.json`으로 추적한다.

## 9. Discovery Saturation
Product Design Scope Complete 재선언은 `DD-040`과 `contracts/design-discovery-saturation.candidate.v1.json`을 따른다. 최소 연속 2개 독립 blind wave에서 신규 P0=0이 필요하며 exact frozen tree SHA와 exact authority population digest가 동일해야 한다. prior DD accepted conclusions가 reviewer input으로 누출되면 해당 wave는 무효다.

## 10. 판정 상한
다음이 실제 evidence로 닫히기 전 Final/Production authority를 금지한다.
- uncontaminated independent discovery saturation
- post-delta exact Requirement Universe / applicability
- granular vertical trace closure
- repository-wide forward/reverse orphan/contradiction
- exact artifact SHA-256 inventory / reconstructability
- Design Lock preflight
- independent CLEAN x2
- 필요한 human design-authority decision

## 11. 현재 최고 표현
`DESIGN_AUTHORITY_RECONCILED_FOR_POST_FINAL_TARGET / PRODUCT_DESIGN_DISCOVERY_REOPENED / DD_001_040_COMPANION_DESIGNED / GRANULAR_TRACE_AND_SATURATION_PENDING / DESIGN_QA_HOLD / NON_FINAL`
