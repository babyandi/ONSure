# ONSure Global Requirement Universe·Denominator 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Parents: `02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md`, `53_END_TO_END_DESIGN_TRACEABILITY_MATRIX.md`, `65_DESIGN_TRACE_REGISTRY_MACHINE_SPEC.md`, `87_DESIGN_LOCK_CHECK_AND_REPOSITORY_ORPHAN_SCAN.md`

## 1. 목적
현재 machine trace는 FR-META-001~060을 중심으로 구성되어 있다. Design Lock 단계에서는 Meta Requirement만이 아니라 ONSure 전체 제품 요구사항이 denominator에 포함되어야 한다. 본 문서는 FR-COM, 프로그램별 기능/수용기준, NFR, 정책·Open Decision에서 파생되는 mandatory requirement까지 하나의 Requirement Universe로 관리하는 기준을 정의한다.

## 2. Requirement Source Class
- EXPLICIT_ID: FR-COM, FR-META, NFR-* 등 명시 ID
- PROGRAM_FUNCTION: OLearning/OPlanning/OReview/OVerification/OImprovement/OMemory/OTraining/OEvidence/OGit/ODelivery/OLicense의 기능 항목
- ACCEPTANCE_CRITERION: 각 Program/Feature 수용기준
- INVARIANT: Architecture/State/Security 문서의 MUST/MUST_NOT 성격 불변식
- POLICY_REQUIRED: Policy Profile이 mandatory로 활성화한 조건
- CONTRACT_REQUIRED: Active Contract의 required invariant
- REGULATORY_REQUIRED: 적용 Industry Profile이 mandatory로 지정한 통제

## 3. Canonical Requirement Identity
명시 ID가 있으면 그대로 사용한다. ID가 없는 항목은 임의 번호를 매번 생성하지 않고 deterministic key를 만든다.

`REQ::<authority_document>::<section_anchor>::<normalized_semantic_key>`

필수 속성:
- requirement_id
- source_class
- authority_document
- source_anchor
- exact_source_digest
- normative_text_digest
- owner_domain
- applicability_rule_ref
- criticality
- status: ACTIVE|SUPERSEDED|RETIRED|OPEN_POLICY

## 4. Universe Generation
1. 권위 문서 exact population lock
2. explicit requirement parser
3. program function/acceptance extraction
4. invariant extraction
5. policy/industry expansion
6. duplicate semantic clustering
7. authority resolution
8. exact requirement population commitment

생성 결과는 `RequirementUniverseSnapshot`으로 봉인한다.

## 5. Denominator 규칙
`total_requirement_count`는 단순 문서 line count가 아니다. exact requirement IDs와 각 digest를 정렬한 manifest digest가 권위다.

필수:
- requirement_ids[]
- requirement_manifest_digest
- authority_document_population_digest
- generation_algorithm_version
- generated_at
- superseded_requirement_ids[]
- unresolved_duplicate_candidates[]

## 6. Duplicate 처리
같은 의미가 여러 문서에 반복될 수 있다. 삭제보다는 관계를 기록한다.
- CANONICAL
- REFINES
- DUPLICATES
- SUPERSEDES
- CONFLICTS_WITH

`DUPLICATES`는 denominator 중복 산입을 방지한다. `REFINES`는 독립 requirement로 유지할 수 있다.

## 7. Applicability
모든 requirement가 모든 제품/Plan/Target에 적용되는 것은 아니다. N/A는 applicability engine이 평가하며 proof 없이 denominator에서 제외할 수 없다.

## 8. Change Impact
권위 문서 변경 시:
- source digest 변경
- affected requirement 재추출
- semantic diff
- added/removed/changed requirement 분류
- Requirement Epoch 증가
- 관련 DesignTrace/Contract/Test/Certificate 재평가

## 9. Orphan 정의
- REQUIREMENT_WITHOUT_DESIGN
- REQUIREMENT_WITHOUT_CONTRACT_WHERE_MACHINE_ENFORCEMENT_REQUIRED
- REQUIREMENT_WITHOUT_TEST
- REQUIREMENT_WITHOUT_EVIDENCE_PATH
- DESIGN_WITHOUT_REQUIREMENT
- CONTRACT_WITHOUT_REQUIREMENT_OR_GOVERNANCE_JUSTIFICATION

## 10. 수용기준
- FR-META 60개 외 전체 제품 Requirement가 동일 Universe에 포함된다.
- ID 없는 기능/수용기준도 denominator에서 사라지지 않는다.
- requirement count보다 exact population digest가 authority다.
- 문서 변경 후 이전 Universe를 CURRENT로 재사용하지 않는다.
- unresolved P0 semantic conflict가 있으면 Design Lock 금지.
