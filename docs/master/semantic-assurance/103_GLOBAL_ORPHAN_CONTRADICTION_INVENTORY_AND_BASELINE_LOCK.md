# ONSure Global Orphan·Contradiction·Inventory·Baseline Lock 설계

Status: `DESIGN_ONLY / CANDIDATE / NON_FINAL`
Covers tasks: **5~9**
Parents: `86`, `87`, `90`, `99`, `100`

## 1. Repository-wide orphan scan
Orphan classes:
- REQUIREMENT_ORPHAN
- DESIGN_ORPHAN
- CONTRACT_ORPHAN
- OPERATION_ORPHAN
- API_ORPHAN
- EVENT_ORPHAN
- RECEIPT_ORPHAN
- TEST_ORPHAN
- POLICY_ORPHAN
- UI_CLAIM_ORPHAN
- EVIDENCE_ORPHAN

P0-impact orphan이 하나라도 있으면 Design Lock candidate를 READY로 만들 수 없다.

## 2. Contradiction scan
필수 contradiction axes:
- status vocabulary
- lifecycle state
- assurance strength/tier
- currentness
- authority/SoD
- N/A/applicability
- certificate semantics
- policy default/override
- v1/v2 semantic mapping
- parent/companion precedence

충돌은 좋은 쪽 값을 자동 선택하지 않고 `UNRESOLVED_CONFLICT_HOLD`로 둔다.

## 3. Exact Design Artifact Inventory
Authority population:
- `docs/master/00~08`, `08A`
- `docs/master/semantic-assurance`의 canonical/current design files
- design closure machine candidate files under `contracts/`

각 artifact row:
- path
- git_blob_sha
- content_sha256 (materialization 시 계산)
- authority_class: MASTER|PARENT|COMPANION|MACHINE_CANDIDATE|HISTORICAL
- lifecycle: CURRENT|SUPERSEDED|HISTORICAL
- superseded_by nullable
- included_in_baseline boolean

Git blob SHA와 content SHA-256은 동일한 것으로 취급하지 않는다.

## 4. Baseline population commitment
DesignBaselineManifest는 최소 다음 digest를 결속한다.
- design_artifact_population_digest
- global_requirement_universe_digest
- applicability_population_digest
- global_trace_digest
- contract_registry_digest
- operation_registry_digest
- event_receipt_registry_digest
- policy_profile_digest
- naming_state_vocabulary_digest

어느 하나라도 UNKNOWN이면 baseline lock decision은 HOLD다.

## 5. Lock Check output
`DesignLockCheckReport`:
- source_commit
- inventory_digest
- scan_ruleset_digest
- orphan_counts_by_class
- contradiction_counts_by_class
- unresolved_p0_design_conflicts
- unknown_critical_requirements
- baseline_reconstructable
- decision: READY_FOR_CANDIDATE|HOLD
- reasons[]

## 6. 완료조건
Task 5~9의 설계는 위 검사군과 exact baseline commitment 정의로 닫는다. 실제 repository-wide scanner 실행과 content SHA-256 materialization은 별도 실행 evidence가 필요하다.
