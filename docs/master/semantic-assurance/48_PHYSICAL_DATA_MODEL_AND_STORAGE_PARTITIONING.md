# ONSure Physical Data Model·Storage Partitioning 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Parent: `43_PERSISTENCE_CONSISTENCY_AND_RECOVERY_ARCHITECTURE.md`

## 1. 목적
논리 Entity가 실제 저장구조에서 tenant 누락, unbounded table, weak uniqueness, stale projection 때문에 의미를 잃지 않도록 물리 데이터 모델 원칙을 정의한다.

## 2. 공통 컬럼
모든 tenant-private mutable table은 최소:
- organization_id
- tenant_id
- aggregate_id
- aggregate_version
- created_at/updated_at
- created_by/updated_by
을 가진다.

Global/public authority table은 tenant-private table과 namespace를 분리한다.

## 3. 핵심 테이블군
- validation_run / validation_attempt
- operation_intent / operation_event / idempotency_ledger
- evidence_metadata / evidence_commit / object_manifest
- evidence_graph_node / evidence_graph_edge / evidence_graph_head
- final_candidate / final_approval / final_lock
- deployment_target / deployment_revision / runtime_instance_snapshot
- currentness_generation / invalidation_event / impact_evaluation
- assurance_subject / dependency_edge / composition_snapshot
- assurance_certificate / certificate_revocation
- authority_grant / delegation_edge / break_glass_session
- work_unit / work_attempt / logical_effect_commit
- plugin_manifest / adapter_qualification
- onsure_release_qualification

## 4. Key/Uniqueness
예:
- `(tenant_id, aggregate_id, aggregate_version)` unique
- active FinalLock: `(tenant_id, candidate_digest)` partial unique
- approval nonce single-consume unique
- work logical effect `(tenant_id, logical_effect_id)` unique
- evidence content digest global dedup 가능하더라도 tenant access metadata는 별도
- certificate_id globally unique, private evidence reference는 tenant-scoped

## 5. Partition
대형 append table은 시간+tenant hash partition 후보.
Evidence graph는 subject/product/time bucket 및 reverse index를 사용하되 canonical graph semantics가 partition 경계를 넘어서 보존되어야 한다.

## 6. Referential Integrity
DB FK로 강제할 수 있는 관계는 FK 사용. Object store/ledger/external authority처럼 DB 밖 관계는 digest/reference validation job과 write-time verification을 병행한다.

## 7. Soft Delete 금지 영역
Authority, FinalLock, Revocation, Audit, Evidence graph historical relation은 일반 soft-delete로 숨기지 않는다. supersession/tombstone 상태를 별도 기록한다.

## 8. Projection
Dashboard/Portfolio/Search는 projection table/materialized view를 사용할 수 있다. projection row에는 source_generation/head_digest를 저장해 권위 데이터보다 뒤처졌는지 표시한다.

## 9. Large Population
Requirement/Evidence/Runtime population은 한 JSON blob에 무한정 넣지 않고:
- header snapshot
- item table/chunk manifest
- population digest/Merkle root
로 분리한다.

## 10. Migration
Schema migration은 data semantic migration과 분리한다.
DDL 성공만으로 v2 semantics 완료 주장 금지. Backfill completeness/digest/reconstruction validation receipt를 요구한다.

## 11. Negative Test
- tenant_id 없는 evidence row
- active FinalLock 두 개
- approval nonce 중복 consume
- projection 최신인데 source head stale
- migration 중 일부 child population 누락
- cross-partition graph edge 유실
- global digest dedup 때문에 Tenant A가 Tenant B object 존재를 추론

## 12. 수용기준
- 물리 unique/FK/index가 핵심 semantic invariant를 보조한다.
- projection/cache가 authority가 되지 않는다.
- 대규모 population도 exact membership과 digest를 재구성할 수 있다.
- migration completeness가 증명되기 전 active semantic authority를 전환하지 않는다.
