# ONSure Persistence·Consistency·Recovery 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Parents: `39_INVALIDATION_IMPACT_AND_CURRENTNESS_ENGINE.md`, `40_EVIDENCE_GRAPH_STORAGE_INDEX_AND_QUERY.md`

## 1. 목적
ONSure의 Assurance 상태가 DB row, object storage file, cache, queue, index 중 하나에만 존재하는 값 때문에 강해지거나 약해지지 않도록 **권위 저장소·commit 순서·복구·재구성 규칙**을 정의한다.

## 2. 저장 계층별 권위
- Relational DB: mutable lifecycle metadata, tenant/resource ownership, operation intent, idempotency ledger
- Append-only Ledger: authority/effect/state transition의 감사 정본
- Content-addressed Object Store: Evidence/Artifact/Receipt bytes
- Evidence Graph Store: immutable node/edge + signed graph-head generation
- Search/Projection Index: 조회 가속용 비권위 projection
- Cache: 비권위, 언제든 폐기 가능

Projection/Cache/Search 결과를 Final/Currentness/Revocation의 단독 권위로 사용하지 않는다.

## 3. Aggregate Root
별도 transaction boundary를 갖는 최소 Aggregate:
- ValidationRun
- EvidenceCommit
- FinalCandidate/Approval/Lock
- DeploymentRevision
- CurrentnessGeneration
- CompositionSnapshot
- Certificate/Revocation
- AuthorityGrant
- WorkUnit/LogicalEffect

Aggregate 간 cross-boundary 일관성은 outbox/receipt/graph-edge를 사용하고 하나의 거대한 DB transaction으로 위장하지 않는다.

## 4. Evidence Commit Protocol
상태:
`PREPARED → BYTES_DURABLE → METADATA_COMMITTED → GRAPH_COMMITTED → SEALED`
예외:
`ABORTED_UNTRUSTED | ORPHANED_BYTES | RECOVERY_REQUIRED`

규칙:
1. content bytes를 durable store에 기록하고 digest read-back 검증
2. metadata가 exact digest를 참조
3. graph edge가 metadata/evidence digest를 참조
4. ledger append
5. seal receipt 발급

`SEALED` 이전의 Evidence는 Final positive evidence로 소비 금지.

## 5. Transactional Outbox
DB 상태변경과 Event 발행이 모두 필요한 Write는:
- same DB transaction에서 business row + outbox row 기록
- dispatcher가 outbox를 재시도 전송
- consumer는 event_id/idempotency_key로 중복제거

Event broker publish success만으로 business commit을 추정하지 않는다.

## 6. Optimistic Concurrency
각 mutable Aggregate는:
- aggregate_version
- expected_version
- updated_at
- last_event_id
을 가진다.

stale version write는 `VERSION_CONFLICT`로 거부하고 caller가 최신 raw state를 다시 읽어 재평가한다. Final/Approval/Revocation 같은 보안 중요 operation은 blind retry 금지.

## 7. Graph Head Consistency
Evidence Graph generation은:
- generation_id
- previous_head_digest
- node_population_digest
- edge_population_digest
- canonical_order_profile
- created_at
- producer/key/signature
을 가진다.

새 head 생성 중 일부 edge만 commit되면 active head를 변경하지 않는다. active head pointer는 final step의 atomic compare-and-set으로 전환한다.

## 8. Currentness Generation
Currentness는 기존 FinalLock row를 update하지 않고 새 generation을 append한다.
- subject_digest
- source_final_lock_digest
- observation_epoch
- dependency/policy/authority/qualification epochs
- currentness_state
- reason set
- evaluated_at

조회 시 최신 **valid signed generation**을 선택한다. latest timestamp만으로 선택하지 않고 predecessor chain/epoch/signature를 검증한다.

## 9. Deletion과 Tombstone
Evidence retention 종료 시 physical bytes 삭제와 graph/history 의미를 분리한다.
- 삭제 가능 content: cryptographic tombstone으로 대체
- immutable audit/receipt metadata: 법/계약이 허용하는 범위에서 유지
- tombstone에는 original digest, deletion receipt digest, deletion authority, deleted_at 저장

삭제된 Evidence를 재검증할 수 없으면 관련 historical assurance verification capability에 `EVIDENCE_NO_LONGER_REPERFORMABLE` limitation을 표시한다.

## 10. Backup/Restore
복구 단위:
- relational snapshot
- ledger head
- object manifest
- evidence graph head
- key/authority registry generation

Restore 후 반드시 `RecoveryQualification` 수행:
- ledger continuity
- graph head continuity
- object digest sampling/full check 정책
- DB↔object dangling reference
- authority/key currentness
- outbox replay

서비스가 살아났다는 사실만으로 Assurance authority를 복원하지 않는다.

## 11. Split-brain 방지
Multi-node writer 환경에서 FinalLock/Certificate/Revocation/ActiveSelector는 single logical authority generation을 요구한다.
- fencing token 또는 consensus-backed lease
- monotonic authority generation
- stale writer commit 거부

두 region이 서로 다른 Final/Revocation head를 동시에 authoritative로 만들 수 없다.

## 12. Failure Injection
- bytes 저장 후 DB commit 전 crash
- DB commit 후 graph commit 전 crash
- graph commit 후 ledger append 전 crash
- active head CAS 직전/직후 crash
- stale writer가 old fencing token으로 FinalLock 생성
- restore된 DB가 object store보다 과거 generation
- outbox duplicate delivery
- index는 최신인데 authoritative graph는 과거
- cache stale PASS가 revocation 이후 남아 있음

## 13. 수용기준
- partial persistence가 positive assurance로 승격되지 않는다.
- 모든 authoritative state는 재시작 후 canonical raw sources에서 재구성 가능하다.
- cache/index 손상·지연이 Final truth를 바꾸지 않는다.
- restore 후 qualification 전에는 strong assurance issuance를 중단한다.
- stale writer가 Final/Revocation/Selector를 commit하지 못한다.
