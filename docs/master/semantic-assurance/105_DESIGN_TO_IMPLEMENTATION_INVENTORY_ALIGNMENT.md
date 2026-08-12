# ONSure 설계↔구현 Inventory Alignment

Status: `INVENTORY_COMPARISON_ONLY / NON_FINAL`
Covers tasks: **11~13**
Source basis: PR #44 changed-file inventory on current branch
Important: **코드 의미/품질 리뷰가 아니라 존재·연결성 inventory 비교만 수행한다.**

## 1. 현재 PR에서 확인되는 구현/후보 자산
### Candidate Contract/Registry 계열
현재 PR changed set에 다음 핵심 설계 대상의 machine candidate가 존재한다.
- assurance status/receipt/principal
- denominator/applicability/execution identity
- validator qualification/blind/human reviewer/benchmark/ground truth/hidden corpus
- independent assurance plan/profile/receipt
- semantic gate/final population/final approval/final lock
- target deployment identity/runtime execution receipt/verified-to-deployed
- assurance revocation event
- active selector/selector transition
- requirement universe snapshot
- evidence canonicalization profile
- validator build manifest
- workflow operation registry
- product process lineage

이는 파일 존재를 의미하며 ACTIVE/IMPLEMENTED/VALIDATED를 의미하지 않는다.

### Runtime source 존재
PR changed set에서 확인되는 Candidate Runtime:
- `SemanticAssuranceV2Reconstructor.java`
- `SemanticAssuranceV2WorkflowService.java`
- `SemanticAssuranceV2DispatcherBridge.java`
- `SemanticAssuranceShadowGateComparator.java`
- `TenantRbacService.java` 변경

### Test/validator source 존재
- `validate-semantic-assurance-v2-contracts.py`
- `SemanticAssuranceV2DispatcherBridgeTest.java`
- `SemanticAssuranceV2WorkflowServiceTest.java`
- 기존 v2 fixture set

실제 실행 여부는 별도이며 현재 설계 판단에 PASS로 사용하지 않는다.

## 2. 설계에는 있으나 PR changed set에 독립 machine contract가 아직 보이지 않는 주요 후속 대상
다음은 81~101에서 설계했지만 현재 changed-file inventory 기준 독립 contract/runtime materialization이 아직 보이지 않는 영역이다.
- `AssuranceCurrentnessSnapshot`
- `AssuranceSubject/AssuranceDependencyEdge`
- `CompositionSnapshot/CompositionReceipt`
- `EvidenceGraphSnapshot/GraphHead`
- `AssuranceCertificate/CertificateVerificationReceipt`
- `AuthorityGrant` canonical machine contract
- `DistributedWorkUnit/DistributedAggregationReceipt`
- `PluginManifest/AdapterQualificationRecord` v2 authority contract
- `AIBehaviorPopulation/AI runtime profile` authority contract
- `ONSureReleaseQualification` authority contract
- `AssurancePolicyProfile` authoritative schema/selector
- `RecoveryQualificationReceipt` materialized contract
- Global Requirement Universe exact materialized snapshot beyond candidate plan
- repository-wide Design Lock scanner runtime

상태는 `DESIGNED_NOT_YET_MATERIALIZED`이며 결함 CLOSED를 뜻하지 않는다.

## 3. 구현에 있으나 설계 없는 항목 탐지 규칙
Task 13의 `DESIGN_ORPHAN`은 다음으로 판단한다.
1. PR/branch의 contract/runtime/API/operation/event를 exact inventory한다.
2. canonical DesignTraceRegistry에 reverse lookup한다.
3. requirement/design relation이 없으면 DESIGN_ORPHAN 후보.
4. utility/internal-only라면 `NON_NORMATIVE_INTERNAL` evidence로 제외 가능.

현재 이 full reverse scan은 실행되지 않았으므로 `DESIGN_ORPHAN=0`을 선언하지 않는다.

## 4. 설계에 있으나 구현 없는 항목 분류
- DESIGNED_NOT_MATERIALIZED
- MATERIALIZED_SCHEMA_ONLY
- MATERIALIZED_RUNTIME_CANDIDATE
- TEST_SOURCE_PRESENT_NOT_EXECUTED
- EXECUTED_NONFINAL
- EVIDENCE_BOUND
- QUALIFIED

현재 후속 F~K 설계 다수는 `DESIGNED_NOT_MATERIALIZED`다.

## 5. Alignment Matrix 최소 필드
- design_capability_id
- requirement_ids[]
- expected_contracts[]
- expected_operations[]
- expected_events[]
- expected_runtime_modules[]
- observed_paths[]
- observed_state
- missing_artifacts[]
- extra_artifacts[]
- semantic_review_state: NOT_REVIEWED

## 6. Task 11~13 현재 판정
- Task 11 설계↔실제 Contract/Operation/API 대조: **INVENTORY_LEVEL_DONE / SEMANTIC_REVIEW_DEFERRED**
- Task 12 설계에 있고 구현에 없는 항목 분류: **DONE_AS_DESIGNED_NOT_MATERIALIZED_CATEGORIES**
- Task 13 구현에 있고 설계 없는 항목: **SCAN_RULE_DEFINED / FULL_REVERSE_SCAN_NOT_RUN**

따라서 개발 기준선은 계속 NON_FINAL이다.
