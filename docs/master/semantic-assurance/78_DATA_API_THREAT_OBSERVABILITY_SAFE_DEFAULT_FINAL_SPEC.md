# ONSure Data·API·Threat·Observability·Safe Default Final Specification

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. Physical Data Model 원칙
- immutable authority/evidence tables와 mutable projection/cache를 분리한다.
- 모든 tenant-owned table은 organization_id/tenant_id를 key 또는 partition boundary에 포함한다.
- authoritative object는 immutable id + canonical digest + generation을 가진다.
- projection의 `current_state`는 authority가 아니며 reconstruction 가능해야 한다.

### 핵심 저장군
- target_manifest / scope_epoch / requirement_epoch
- execution_run / attempt / runtime_receipt
- evidence_object / evidence_edge / graph_head
- finding / decision / accepted_risk
- authority_grant / approval / replay_ledger
- qualification / independent_receipt
- final_candidate / final_lock
- deployment_revision / runtime_instance / currentness_snapshot
- composition_snapshot
- certificate / revocation
- work_unit / aggregation_receipt
- plugin/adapter qualification
- onsure_release_qualification

## 2. Key/Constraint
- tenant + object_id uniqueness
- digest field fixed-length SHA-256 profile
- approval nonce/idempotency key single-consume uniqueness
- active final lock per candidate generation uniqueness
- active authority grant state uniqueness where policy requires
- work unit logical effect uniqueness
- graph generation/head monotonicity

## 3. Partitioning
대형 event/evidence/work-unit는 tenant + time/generation partition 후보. partition pruning이 denominator를 변경하지 않도록 exact population manifest를 authority로 사용한다.

## 4. API 공통 Envelope
Request:
- request_id/correlation_id
- tenant context
- operation name/version
- resource identity
- purpose
- idempotency key (write/effect)
- expected version/generation

Response:
- result status
- authoritative object/receipt ref
- error code/category
- retryable
- current version/generation
- evidence/trace refs

## 5. Error Taxonomy
### Transport
AUTHENTICATION_FAILED, RATE_LIMITED, SERVICE_UNAVAILABLE, TIMEOUT.

### Authorization
TENANT_SCOPE_VIOLATION, RESOURCE_SCOPE_VIOLATION, PURPOSE_NOT_ALLOWED, AUTHORITY_EXPIRED, SOD_NOT_SATISFIED.

### Concurrency/Idempotency
VERSION_CONFLICT, IDEMPOTENCY_CONTEXT_CONFLICT, LEASE_EXPIRED, DUPLICATE_EFFECT.

### Assurance
EVIDENCE_INCOMPLETE, TARGET_IDENTITY_MISMATCH, SCOPE_EPOCH_MISMATCH, QUALIFICATION_NOT_CURRENT, CURRENTNESS_UNKNOWN, FINAL_FRESHNESS_FAILED, INDEPENDENCE_NOT_PROVEN, COMPOSITION_CONFLICT, CERTIFICATE_REVOKED.

## 6. Pagination/Bulk
Pagination token은 snapshot/population digest에 결속한다. 페이지 사이 population drift가 발생하면 동일 결과집합처럼 합치지 않는다. Bulk operation은 item별 result/receipt를 보존하고 일부 성공을 전체 성공으로 표현하지 않는다.

## 7. Async Job
202 Accepted는 business/assurance PASS가 아니다. job_id, submitted operation context digest를 반환하고 terminal result는 별도 receipt를 가진다.

## 8. Threat Model
### Attacker classes
- malicious tenant/user
- compromised admin/insider
- compromised validator/plugin/adapter
- compromised CI/provider/key
- malicious target attempting validation evasion
- accidental stale cache/projection/operator error

### Core abuse cases
- cross-tenant object ID swap
- authority replay/TOCTOU
- evidence substitution/replay/tamper
- scope/denominator shrink
- cross-run result mixing
- retry history deletion
- false independence via same principal/implementation
- stale/revoked certificate reuse
- mutable artifact/model alias substitution
- plugin privilege escalation
- hidden benchmark leakage
- recovery rollback of ledger/key state

## 9. Trust Boundaries
Client/API boundary, Core/Sandbox, Core/External Provider, Validator/Target, Evidence Store/Projection DB, Key Registry/Signing Service, Independent Verifier/Core, Offline/Online boundary를 명시한다. 경계 통과 데이터는 identity/provenance/purpose를 보존한다.

## 10. Observability
### Required dimensions
- operation/actor/principal/tenant/resource/purpose
- request/effect timestamps
- authority/policy epochs
- decision/currentness/qualification
- receipt/evidence refs
- error/latency/retry/attempt
- collector health/completeness

### Assurance-critical SLO
- evidence commit integrity
- graph head update reliability
- authority/revocation lookup availability
- currentness observer freshness
- certificate verification service availability
- queue/work-unit closure

SLO 실패는 단순 운영 대시보드 경고가 아니라 affected Assurance issuance suspension/currentness downgrade에 연결될 수 있다.

## 11. Degraded Mode
Observer/revocation/key registry/evidence store 등 assurance-critical dependency가 degraded면:
- read-only historical view는 가능할 수 있음
- new FinalLock/Certificate issuance는 policy에 따라 SUSPENDED
- currentness는 UNKNOWN/STALE ceiling
- effect operation은 required authority/evidence가 없으면 BLOCKED

## 12. Safe Default Matrix
| 조건 | 기본 상태 |
|---|---|
| field missing but required for positive claim | HOLD/INVALID |
| unknown target feature | NOT_PROVEN/PARTIAL |
| collector incomplete | INCONCLUSIVE/HOLD |
| authority unverifiable | BLOCKED |
| policy conflict | HOLD |
| currentness observer stale | STALE/REASSESSMENT_REQUIRED |
| revocation lookup unavailable | UNKNOWN/HOLD per profile |
| duplicate/conflicting evidence | CONFLICT_HOLD |
| resource budget exhausted | RESOURCE_LIMIT/BLOCKED |
| unsupported schema/version | INCOMPATIBLE/HOLD |
| recovery integrity incomplete | REASSESSMENT_REQUIRED/UNKNOWN |

## 13. Privacy/Data Governance
- data class: PUBLIC|INTERNAL|CONFIDENTIAL|RESTRICTED|SECRET
- purpose binding과 retention policy
- hidden corpus/ground truth 접근 별도 권한
- AI provider 전송 allowlist/data reuse prohibition
- legal hold와 freshness 분리
- evidence/export에는 redaction profile 및 disclosure manifest

## 14. Security Acceptance
- client supplied authority/context secret fields 불신
- object-level tenant authorization on every resource operation
- sourceRoot/deploymentRoot server-side resolution
- sandbox egress deny/default + privilege manifest
- signing keys/roots rotation/revocation
- immutable audit for high-risk authority/policy/certificate operations

## 15. 완료조건
Data/API/Threat/Observability 설계가 구현에 내려갈 때 모든 P0 effect/claim path는 authoritative storage, API semantic, threat countermeasure, telemetry, safe default를 함께 가져야 한다.
