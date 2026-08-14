# 141 Requirement Universe Authority Decision

Status: DESIGN_AUTHORITY_DECISION / DCQ-0002_RESOLVED_FOR_DESIGN

DCQ-0002는 하나의 enum에 대한 네 버전 충돌이 아니라 두 종류의 Requirement Universe가 같은 이름을 사용한 충돌로 판정한다.

## A. Product Design Requirement Universe
Authority: 88 + 92.
Purpose: ONSure 자체 제품 설계의 QA denominator.

Canonical fields:
- source_class from 88: EXPLICIT_ID, PROGRAM_FUNCTION, ACCEPTANCE_CRITERION, INVARIANT, POLICY_REQUIRED, CONTRACT_REQUIRED, REGULATORY_REQUIRED
- taxonomy from 92: FUNCTIONAL, NON_FUNCTIONAL, ASSURANCE, SECURITY, PRIVACY, OPERATIONAL, DEPLOYMENT, AI_SPECIFIC, REGULATORY, COMMERCIAL_CONTRACTUAL

두 필드는 서로 다른 축이며 하나의 enum으로 합치지 않는다.
Canonical names: ProductDesignRequirementUniverse, ProductDesignRequirementUniverseSnapshot.

## B. Target Assurance Requirement Universe
Authority: 11 Bundle D.
Purpose: ONSure가 검증하는 고객/대상 Program의 requirement denominator.
Canonical names: TargetAssuranceRequirementUniverse, TargetAssuranceRequirementUniverseSnapshot.

11의 BUSINESS, CONTRACT, POLICY, CODE, CONFIG, ARCHITECTURE, DATA, API, SECURITY, PRIVACY, OPERATIONS, RIGHTS, FAILURE_RECOVERY, RUNTIME_BEHAVIOR, EXTERNAL_STANDARD는 target source vocabulary다.

기존 contracts/requirement-universe-snapshot.candidate.v2.schema.json은 target_id와 target discovery 상태를 요구하므로 Target Assurance 계열의 기존 candidate로 분류한다. Product Design Requirement Universe 계약으로 사용하지 않는다.

다음 target 계약에서는 target-assurance-requirement-universe-snapshot 같은 구분된 이름을 사용하고, 기존 candidate enum에서 11 vocabulary로의 mapping/migration을 명시한다. 기존 candidate는 migration 전까지 non-authoritative candidate다.

## Development consequence
Batch 0에서 88 source_class와 92 taxonomy를 두 필드로 구현한 선택은 Product Design Requirement Universe에 대해 올바르다. 11 vocabulary로 바꾸지 않는다.

Batch 1의 TargetManifest/RequirementUniverse 계열은 Target Assurance Requirement Universe라는 별도 identity로 진행한다. Product Design schema 이름을 재사용하지 않는다. 새 다섯 번째 vocabulary를 만들지 않는다.

DCQ-0002는 design authority 수준에서 RESOLVED_WITH_AUTHORITY로 disposition한다. 단, Product Design exact population, applicability, trace/orphan, artifact SHA-256, registry digest, reconstructable baseline은 별도 Design QA gate로 남는다.
