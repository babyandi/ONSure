# ONSure Distributed Currentness & Revocation 설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
Final/Certificate/Qualification이 한 노드에서는 CURRENT인데 다른 노드·region·offline consumer에서는 여전히 오래된 상태로 보이면 assurance는 일관되지 않다. 본 문서는 revocation/currentness를 분산 환경에서 검증 가능한 상태로 정의한다.

## 2. Currentness Model
Historical event와 current validity를 분리한다.
- historical decision: 과거 어떤 시점에 무엇이 발급됐는가
- current disposition: 지금도 사용할 수 있는가

Current disposition:
- CURRENT
- STALE
- REVOKED
- INVALIDATED
- REASSESSMENT_REQUIRED
- STATUS_UNKNOWN

## 3. Revocation Event
필수 필드:
- revocation_id
- subject type/id/digest
- reason
- severity
- triggering evidence
- authority profile
- authority epoch
- issued_at
- effective_at
- propagation scope
- supersedes/replaces
- signature

## 4. Invalidation Graph
변경 source:
- source/artifact
- requirement/scope/denominator
- policy
- oracle/validator
- key/authority
- model/prompt/RAG
- deployment
- critical finding/CVE
- external dependency/service

각 변경이 어떤 receipt/certificate/qualification을 stale 처리하는지 reverse edge를 보존한다.

## 5. Distributed State
각 node/region은:
- current authority epoch
- current revocation watermark
- selector epoch
- last synchronized_at
- pending revocation count
을 가진다.

## 6. Global Currentness
Global CURRENT를 주장하려면 required region/node denominator가 필요하다.

조건:
- required nodes known
- minimum acknowledgement/quorum policy 충족
- stale/unknown required node 0 또는 정책상 명시적 ceiling
- revocation watermark >= required watermark

## 7. Offline Consumer
Offline certificate/receipt consumer는 offline freshness uncertainty를 반드시 노출한다.

필드:
- last_revocation_snapshot_at
- snapshot_epoch
- maximum_offline_age
- current_time_confidence
- offline_disposition

오프라인에서 최신 revocation을 확인하지 못하면 `CURRENT_CONFIRMED`를 주장하지 않는다.

## 8. Revocation Propagation SLA
subject class별 SLA 후보:
- Critical security/authority compromise: shortest SLA
- ordinary stale/config drift: policy SLA

SLA 위반 자체가 assurance Finding이다.

## 9. Recovery
서비스 복구와 assurance 복구를 분리한다. node가 다시 살아났다고 currentness가 자동 회복되지 않는다.

복구 순서:
1. latest selector/authority epoch sync
2. revocation watermark sync
3. stale subject reconciliation
4. local cache purge/rebuild
5. currentness verification receipt

## 10. Backup/Restore
restore된 DB/ledger가 과거 revoked key/certificate/nonce를 부활시키지 않도록 monotonic authority/revocation epoch 외부 기준이 필요하다.

## 11. Queue/Replay
지연된 queue event가 과거 authority를 다시 적용하지 않게 effect-time authority/currentness를 재검증한다.

## 12. Certificate Consumer Contract
Consumer는 최소:
- certificate historical validity
- current revocation disposition
- freshness snapshot age
- assurance level
- limitations
을 확인한다.

단순 `certificate.signatureValid=true`는 사용 가능성을 의미하지 않는다.

## 13. Notification
Revocation/stale propagation은 사용자 surface에도 동일하게 반영한다.
- API
- Web
- VS Code
- report
- webhook/event

surface parity가 깨지면 HOLD 대상이다.

## 14. Negative Fixture
1. region A revoked / B current
2. offline stale snapshot
3. restored DB resurrects revoked cert
4. delayed queue applies old approval
5. stale node returns after partition
6. cache retains revoked authority
7. historical PASS shown as current PASS
8. notification failed but API revoked
9. selector rollback revocation not propagated
10. currentness recovery without reconciliation

## 15. API 후보
- `POST /assurance/revocations`
- `GET /assurance/currentness/{subject}`
- `POST /assurance/currentness/reconcile`
- `GET /assurance/revocation-watermark`
- `POST /assurance/offline-snapshots`

## 16. Claude 개발 경계
구현 순서:
1. RevocationEvent store
2. reverse invalidation graph
3. currentness query
4. region/node watermark
5. offline snapshot
6. restore/replay monotonic checks
7. surface notification parity

## 17. 현재 상태
- 설계: PRESENT
- revocation concepts 일부 기존 계약에 존재
- distributed runtime/currentness reconciliation: NOT_IMPLEMENTED
- execution: NOT_RUN
