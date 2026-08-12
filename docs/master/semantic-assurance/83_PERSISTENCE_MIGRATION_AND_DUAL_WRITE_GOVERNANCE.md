# Persistence Migration·Dual-write Governance 설계

Status: `DESIGN_ONLY / CANDIDATE / NON_FINAL`

## 1. 목적
v1→v2 계약을 저장소에 적용할 때 split-brain, silent downgrade, stale projection을 방지한다.

## 2. 권위 원칙
- Source of Truth와 Projection을 분리한다.
- v2가 Active되기 전까지 v1은 기존 authority를 유지하되 v2 shadow materialization을 허용한다.
- dual-write는 transition mechanism이며 영구 authority model이 아니다.
- 한 transaction에서 v1 성공/v2 실패 또는 그 반대가 발생하면 `DUAL_WRITE_DIVERGED`를 기록하고 positive assurance를 중지한다.

## 3. Migration 단계
1. schema/table 생성
2. historical backfill candidate
3. reconstruction classification
4. dual-read compare
5. shadow write
6. dual-write with divergence ledger
7. read authority cutover
8. write authority cutover
9. legacy freeze
10. retirement after rollback window

## 4. MigrationRecord
- migration_id
- source_contract_version
- target_contract_version
- source_object_id/digest
- target_object_id/digest nullable
- classification
- migration_state
- divergence_reason nullable
- readback_evidence_ref
- reperformance_ref
- migrated_at

상태:
`DISCOVERED|DIRECT_MAPPED|READBACK_REQUIRED|REPERFORMANCE_REQUIRED|EXTERNAL_AUTHORITY_REQUIRED|MIGRATED_SHADOW|DIVERGED|CUTOVER_ELIGIBLE|UNRECOVERABLE`

## 5. Split-brain 방지
- 같은 logical object의 v1/v2 decision이 다르면 stronger positive를 자동 선택하지 않는다.
- legacy PASS/v2 HOLD는 HOLD.
- v1 state와 v2 projection 둘 다 변경 가능한 구조 금지. 하나만 write-authoritative.
- cache는 migration authority가 아니다.

## 6. Backfill 규칙
Historical object에 존재하지 않았던:
- nonce
- independence
- qualification
- authority effect-time validity
- runtime identity
- deployment identity
를 생성시각 추정으로 채우지 않는다.

## 7. Rollback
Cutover 후 rollback은 단순 feature flag가 아니다.
- rollback target contract digest
- data compatibility proof
- post-cutover object loss 여부
- authority epoch
- rollback receipt
을 요구한다.

## 8. 수용기준
- divergence 0 또는 전건 explicit disposition
- unrecoverable object는 positive v2 claim에 사용 금지
- migration history 삭제 금지
- rollback이 assurance strength를 자동 복원하지 않음
