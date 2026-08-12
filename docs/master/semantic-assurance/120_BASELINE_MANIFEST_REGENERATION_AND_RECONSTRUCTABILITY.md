# 120 Baseline Manifest Regeneration 및 Reconstructability

Status: `EXECUTED_INCOMPLETE / HOLD / NON_FINAL`

## 1. 필수 digest set
Design Baseline Manifest는 다음 digest를 결속한다.
- requirement_universe_digest
- applicability_population_digest
- global_trace_digest
- design_artifact_population_digest
- contract_registry_digest
- operation_registry_digest
- event_receipt_registry_digest
- policy_profile_digest
- canonical_status_vocabulary_digest
- authority_mapping_digest

## 2. 현재 상태
- requirement_universe_digest: NOT_PROVEN_GLOBAL
- applicability_population_digest: NOT_PROVEN
- global_trace_digest: PARTIAL_EXPLICIT_73_ONLY
- design_artifact_population_digest: PENDING_CONTENT_SHA256
- contract_registry_digest: CANDIDATE / full canonical population not proven
- operation_registry_digest: candidate v2 exists, full global coverage not proven
- event_receipt_registry_digest: design-defined / machine population incomplete
- policy_profile_digest: design-defined / active bootstrap not authoritative
- status vocabulary digest: candidate exists
- authority mapping digest: design-defined / runtime alignment pending

## 3. Reconstructability rule
Manifest 하나로 exact path/ID population을 재구성할 수 있어야 하며, digest만 있고 denominator row list를 찾을 수 없는 경우 reconstructable=false다.

## 4. 현재 판정
`BASELINE_MANIFEST_REGENERATED_INCOMPLETE`
`RECONSTRUCTABLE=false`

Tasks 23~29는 설계상 digest owner를 지정했지만 global machine populations가 아직 완성되지 않아 positive closure를 발행하지 않는다.
