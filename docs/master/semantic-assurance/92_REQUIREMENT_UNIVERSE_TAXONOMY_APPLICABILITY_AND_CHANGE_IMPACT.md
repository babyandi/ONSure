# ONSure Requirement Universe·Taxonomy·Applicability·Change Impact·Global Trace 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Covers: Task 1~5
Parents: 88~91, 53, 65

## 1. Global Requirement Universe materialization
Authoritative requirement population은 다음 source class를 모두 포함한다.
- explicit FR/NFR ID
- Program 기능 bullet
- Program 수용기준
- Architecture invariant
- Security/Privacy obligation
- Operation/Recovery/DR obligation
- Policy/Industry mandatory requirement
- Contractual/Commercial claim ceiling

각 requirement는 `requirement_id`, `source_path`, `source_anchor`, `source_blob_sha`, `normalized_text_digest`, `taxonomy`, `applicability_rule_ref`, `authority_class`, `lifecycle_state`를 가진다.

현재 확인된 FR-COM 13 + FR-META 60은 최소 explicit population일 뿐 global denominator가 아니다.

## 2. Requirement Taxonomy
Canonical top-level:
`FUNCTIONAL|NON_FUNCTIONAL|ASSURANCE|SECURITY|PRIVACY|OPERATIONAL|DEPLOYMENT|AI_SPECIFIC|REGULATORY|COMMERCIAL_CONTRACTUAL`.

보조 dimensions:
- subject: PRODUCT|PROGRAM|TARGET|DEPLOYMENT|RUNTIME|ONSURE_META
- criticality: CRITICAL|HIGH|MEDIUM|LOW
- claim_effect: POSITIVE_CLAIM_GATE|CURRENTNESS_GATE|QUALIFICATION_GATE|INFORMATIONAL
- waivability: NON_WAIVABLE|CONDITIONAL|WAIVABLE

하나의 requirement가 여러 보조 dimension을 가질 수 있지만 top-level taxonomy는 하나만 canonical로 가진다.

## 3. Applicability Model
State:
`APPLICABLE|NOT_APPLICABLE|CONDITIONAL|UNKNOWN`.

N/A에는 반드시:
- rule_id
- subject_digest
- evaluated context digest
- rationale
- evaluator principal/authority
- evidence digest
- challenge status
- evaluated_at

`UNKNOWN`을 N/A로 변환 금지. Industry/Product Plan은 applicability input일 뿐 결과를 직접 덮어쓰지 않는다.

## 4. Requirement Change Impact
Change class:
`EDITORIAL|SEMANTIC_NON_BREAKING|SEMANTIC_BREAKING|SCOPE_EXPANSION|SCOPE_REDUCTION|CRITICALITY_CHANGE|WAIVABILITY_CHANGE|APPLICABILITY_CHANGE`.

Impact traversal target:
Requirement → Design → Contract → Operation/API → Test/Fixture → Evidence → FinalLock → Deployment/Currentness → Composition → Certificate.

Breaking 또는 scope expansion은 기존 positive assurance를 최소 `REASSESSMENT_REQUIRED`로 만든다. scope reduction은 기존 excluded population을 숨길 수 없고 explicit supersession receipt를 요구한다.

## 5. Global Trace Registry v2
Row fields:
- requirement_id
- requirement_digest
- design_refs[]
- contract_refs[]
- operation_refs[]
- api_refs[]
- event_refs[]
- receipt_refs[]
- test_refs[]
- evidence_refs[]
- policy_refs[]
- authority_refs[]
- final_gate_refs[]
- orphan_dimensions[]

P0/claim-gating requirement는 `orphan_dimensions=[]`가 아니면 lock 불가.

## 6. Acceptance
- exact requirement population digest 존재
- duplicate ID 0
- source-less requirement 0
- P0 requirement applicability UNKNOWN 0 또는 explicit HOLD
- change impact가 Final/Certificate까지 graph로 설명 가능
- global trace row count = exact active requirement denominator
