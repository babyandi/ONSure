# ONSure Evidence Graph Storage·Index·Query 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Parents: `30`, `37`, `39`

## 1. 목적
Evidence Graph를 개념 모델로만 두지 않고 수백만~수억 node/edge 규모에서도 Final Reconstruction, Historical Impact, Certificate Verification을 재현 가능하게 수행하도록 저장·인덱스·조회 구조를 정의한다.

## 2. 저장 원칙
- Node/Edge는 immutable append 중심.
- 의미 변경은 기존 record update보다 새 validity/supersession relation을 추가한다.
- Graph Head는 exact node/edge population commitment를 가진다.
- mutable search index는 authority가 아니다. authority는 canonical object + digest + graph head다.
- Query cache 결과를 Final evidence로 직접 사용하지 않는다.

## 3. Logical Storage Layers
### L1 Content-addressed Artifact Store
Raw evidence, receipt bytes, reports, manifests, benchmark outputs.
Key: SHA-256 또는 승인된 digest profile.

### L2 Graph Metadata Store
Node/Edge canonical metadata, tenant, type, digest, creation generation.

### L3 Reverse Index
빠른 impact/reconstruction을 위한 derived index.

### L4 Graph Head / Generation Ledger
Graph generation, parent head, population commitment, canonicalization profile, signer.

### L5 Query/Explanation Cache
비권위 파생 데이터. graph-head mismatch 시 즉시 invalidate.

## 4. Node Partitioning
기본 partition key:
`organization_id / tenant_id / subject_domain / time_bucket_or_generation`.

cross-tenant public authority node는 별도 namespace를 사용한다. Tenant graph와 public authority graph를 물리/논리적으로 구분하고 explicit bridge relation만 허용한다.

## 5. Edge Storage
Edge의 canonical identity 후보:
`hash(relation_type + source_digest + target_digest + rule_id + evidence_digest + generation)`.

같은 semantic relation을 duplicate ingest하면 logical duplicate를 탐지한다. 서로 다른 evidence로 동일 relation을 증명하는 경우 evidence set을 별도 child relation 또는 edge-evidence relation으로 표현해 edge identity를 남용하지 않는다.

## 6. Graph Head
GraphHead 필드:
- graph_id
- tenant_id
- generation
- previous_graph_head_digest nullable
- node_population_root
- edge_population_root
- canonicalization_profile_digest
- created_at
- producer_principal
- signature

대규모 population은 전체 ID 문자열 연결 대신 Merkle tree 또는 deterministic manifest tree를 후보로 사용한다.

## 7. Merkle/Manifest Tree 후보
### Leaf
canonical node/edge digest.

### Internal
sorted child digests commitment.

### Root
population root.

부분 검증자는 특정 node/edge inclusion proof를 통해 전체 graph materialization 없이 GraphHead 포함 여부를 검증할 수 있다.

정확한 tree fanout/hash profile은 Open Decision으로 둔다.

## 8. Reverse Index
필수 index:
- subject_digest → graph nodes
- requirement_digest → claims/evidence
- source/build digest → runs/finals/deployments
- validator_build_digest → evidence/finals/certificates
- oracle_digest → claims/runs
- policy_digest → claims/finals
- dependency_artifact_digest → targets/deployments
- authority/key/principal → approvals/finals/certificates
- certificate_id → final/composition/currentness
- deployment_revision → runtime instances/currentness
- finding fingerprint/class → historical runs

Index entry는 source graph generation을 기록한다.

## 9. Query Types
### Q1 Final Reconstruction
FinalLock/Certificate에서 PRIMARY evidence까지 역추적.

### Q2 Impact
changed node/event에서 affected claims/finals/certificates forward traversal.

### Q3 Explanation
Product result ceiling path/weakest child/conflict path.

### Q4 Audit
principal/authority가 만든 operation/approval/certificate lineage.

### Q5 Qualification
validator/adapter build가 사용된 result population.

### Q6 Tenant Isolation Audit
cross-tenant relation 존재 여부 탐지.

## 10. Query Result Contract
모든 authoritative-adjacent query result는:
- query_id/type
- graph_head_digest
- query_profile_digest
- input identifiers/digests
- result_population_digest
- result_count
- truncated=false required for completeness claim
- generated_at
- producer
을 가진다.

`truncated=true` 결과를 전체 population/absence proof로 사용하지 않는다.

## 11. Pagination
UI pagination과 semantic result population을 구분한다. API가 100건씩 반환해도 `total population`은 Graph query commitment에서 고정한다. Pagination 누락을 denominator 축소로 해석하지 않는다.

## 12. Compaction
장기 운영 시 logical compaction은 가능하나 historical proof를 깨지 않는다.

허용:
- derived index rebuild
- expired cache deletion
- immutable artifact archival tier 이동
- superseded metadata의 read optimization

금지:
- historical node/edge identity 삭제로 Certificate reconstruction 불가
- failure attempt 삭제
- revoked/superseded evidence 삭제로 history 미복원

Retention 정책으로 실제 raw evidence 삭제가 필요한 경우 Deletion Receipt와 graph tombstone/retention disposition을 남긴다.

## 13. Tombstone / Deletion
삭제된 content를 `존재하지 않았음`처럼 만들지 않는다.
Node에:
- content_deleted=true
- deletion_receipt_digest
- deletion_reason/retention policy
- deleted_at
을 파생 validity metadata로 표현한다.

해당 content가 필요한 current certificate는 evidence availability limitation/currentness 영향을 재평가한다.

## 14. Encryption / Tenant Isolation
- tenant-specific encryption key option
- object-level authorization before graph query
- cross-org query default deny
- public verification은 Certificate public profile이 허용한 projection만 조회
- graph query logs는 audit evidence

## 15. Consistency Model
Graph append와 index update가 원자적으로 완전 동기일 필요는 없지만 authority query는 `minimum_graph_head`를 지정할 수 있어야 한다.

Index lag 상태:
- CURRENT_TO_HEAD
- LAGGING
- REBUILDING
- INVALID

LAGGING index로 absence proof 금지. 필요하면 canonical store fallback 또는 HOLD.

## 16. Crash Recovery
- graph append PREPARED/COMMITTED
- GraphHead published only after all committed objects durable
- index update 실패는 graph authority를 무효화하지 않으나 query availability를 제한
- restart 시 orphan PREPARED record quarantine

## 17. Evidence Contradiction Query
CONTRADICTS relation은 query에서 숨기지 않는다. 동일 claim/context에 unresolved contradiction이 있으면 Final Reconstruction/Composition에 conflict signal을 전달한다.

## 18. Performance SLO 후보
실제 수치는 Open Decision이지만 측정항목은 고정한다.
- P95 final reconstruction graph traversal latency
- P95 impact scan latency by affected-node count
- GraphHead publish latency
- index lag time
- inclusion proof generation latency
- query result population size
- cache hit ratio(비권위 metric)

SLO 실패는 Assurance truth를 변경하지 않는다. Timeout 때문에 partial result를 full result로 사용하지 않는다.

## 19. Adversarial Test
- stale index misses affected Certificate
- pagination omits critical node
- graph-head spoof
- Merkle inclusion proof mismatch
- duplicate edge inflation
- cross-tenant bridge injection
- compaction deletes historical failure
- tombstoned evidence treated as present current evidence
- index lag absence proof
- cache from old graph head reused
- relation cycle hidden across partitions

## 20. 수용기준
- Final/Certificate reconstruction은 특정 GraphHead에 대해 deterministic.
- index/cache가 authority가 아님을 코드/계약으로 강제.
- complete query는 truncated=false + exact population digest를 가짐.
- historical Certificate material parents는 retention policy 범위에서 재구성 가능.
- cross-tenant graph leakage 0.
- graph-head mismatch cache 사용 금지.

## 21. 비최종 경계
Storage technology(PostgreSQL/graph DB/object store 등)는 구현 벤치마크 전 확정하지 않는다. 이 문서는 logical architecture와 integrity contract를 정의한다.
