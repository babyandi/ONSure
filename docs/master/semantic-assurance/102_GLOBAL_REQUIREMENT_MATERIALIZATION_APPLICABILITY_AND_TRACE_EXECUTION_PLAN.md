# ONSure Global Requirement Materialization·Applicability·Trace 실행설계

Status: `DESIGN_ONLY / CANDIDATE / NON_FINAL`
Covers tasks: **1~4**
Parents: `88`, `89`, `90`, `91`, `92`

## 1. Global Requirement Universe materialization
Authority sources:
- `docs/master/01~08` 및 `08A`
- 승인된 semantic-assurance companion 중 Requirement를 생성하는 절
- active/candidate Contract에서 외부로 드러나는 normative obligation
- Industry/Policy Profile의 mandatory rule

Requirement class:
`FUNCTIONAL|NFR|ASSURANCE|SECURITY|PRIVACY|OPERATIONAL|DEPLOYMENT|AI|REGULATORY|COMMERCIAL|ARCHITECTURE_INVARIANT|ACCEPTANCE_CRITERION`

현재 확인된 explicit minimum은 `FR-COM-001~013` 13개 + `FR-META-001~060` 60개 = **73개**다. 이는 global total이 아니다.

ID 없는 requirement는 다음 source key로 deterministic candidate ID를 생성한다.
`REQ-{SOURCE_DOC_CODE}-{SECTION_PATH_HASH8}-{ORDINAL}`

ID는 content hash 그 자체가 아니며 문장 편집으로 identity가 무조건 바뀌지 않도록 source lineage와 semantic fingerprint를 함께 가진다.

## 2. Requirement normalization
각 row는 최소 다음 필드를 가진다.
- requirement_id
- source_document/path/section
- source_blob_sha
- requirement_class
- normative_text
- semantic_fingerprint
- parent_requirement_id nullable
- relation: ORIGINAL|REFINES|SUPERSEDES|DUPLICATES|CONFLICTS_WITH
- lifecycle: ACTIVE|DRAFT|SUPERSEDED|RETIRED
- authority_class

Normalization에서 `PASS`, `CURRENT`, `APPROVED`, `ACCEPTED`, `QUALIFIED`를 같은 의미로 병합하지 않는다.

## 3. Applicability population
각 Requirement×Subject Context는:
- APPLICABLE
- NOT_APPLICABLE
- CONDITIONAL
- UNKNOWN

필수 context:
`product_type, target_archetype, industry_profile, service_plan, assurance_tier_requested, environment_class, deployment_mode, ai_present, external_dependency_present`

N/A는 applicability rule id, evaluator, rationale, source facts, evidence digest가 없으면 인정하지 않는다.
UNKNOWN은 denominator에서 제외하지 않는다.

## 4. Global Trace Registry v2
각 active applicable requirement는 최소 다음 downstream relation 중 설계상 필요한 relation을 가진다.
`DESIGN -> CONTRACT -> OPERATION/API -> EVENT/RECEIPT -> TEST -> EVIDENCE -> DECISION/CERTIFICATE`

Requirement가 특정 layer를 필요로 하지 않으면 `NOT_REQUIRED`와 rationale을 명시한다. 빈칸을 N/A로 해석하지 않는다.

## 5. Materialization output candidate
- `GlobalRequirementUniverseSnapshot`
- `RequirementApplicabilitySnapshot`
- `GlobalDesignTraceSnapshot`

각 snapshot은 exact ordered item list 또는 manifest tree와 population digest를 가진다.

## 6. 완료조건
Task 1~4의 설계 완료 조건:
- 모든 authority source class 정의
- ID/normalization/applicability semantics 정의
- exact population digest 규칙 정의
- Global Trace downstream relation 정의

실제 repository extraction은 Claude RU batch가 수행한다. extraction 결과 전에는 global requirement count를 확정하지 않는다.
