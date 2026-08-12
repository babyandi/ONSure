# ONSure RecoveryQualificationReceipt Contract 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
복구/DR 이후 '서비스가 켜졌다'와 'Assurance 권위가 복구됐다'를 구분하는 machine receipt를 정의한다.

## 2. 필드
- recovery_qualification_id
- incident/recovery_id
- environment/region
- restored_snapshot_ids/digests
- relational_generation
- ledger_head_digest
- object_manifest_digest
- evidence_graph_head_digest
- authority_registry_generation
- active_selector_digest
- policy_profile_digest
- validator_build/qualification digest
- outbox/queue reconciliation digest
- checks[]
- unresolved_gaps[]
- decision: PASS|HOLD|FAIL|INCONCLUSIVE
- issuance_ceiling
- evaluated_by/authority
- evaluated_at
- self_digest/signature

## 3. 필수 Check
- DB integrity
- ledger continuity
- object completeness
- graph continuity
- key/authority currentness
- selector/policy consistency
- validator qualification
- duplicate external effect reconciliation
- certificate/revocation generation freshness

## 4. Decision
PASS는 모든 mandatory check가 PASS이고 unresolved P0 gap이 없을 때만 가능. HOLD/INCONCLUSIVE이면 strong Final/Certificate issuance 금지.

## 5. Partial Recovery
조회/지원 기능은 복구됐지만 authority/evidence가 불완전한 경우 `issuance_ceiling=HISTORICAL_READ_ONLY` 같은 제한 profile을 사용한다.

## 6. Negative Test
- ledger gap인데 PASS
- revocation snapshot old인데 full issuance
- validator qualification expired
- graph head와 DB generation mismatch
- duplicate payment/deployment effect 미reconcile
- unsigned recovery receipt

## 7. 수용기준
모든 DR/restore 후 strong issuance resume은 current RecoveryQualificationReceipt PASS에 결속된다.
