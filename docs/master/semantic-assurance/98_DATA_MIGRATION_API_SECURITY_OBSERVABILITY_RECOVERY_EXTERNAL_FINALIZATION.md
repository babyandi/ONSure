# ONSure Data·Migration·API·Security·Observability·Recovery·External Trust 최종 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Covers: Task 31~37

## 1. Persistence/Data Model
데이터 저장을 세 층으로 분리한다.
- Immutable Authority Store: Receipt, Event, Evidence commitment, Approval, Qualification, Revocation
- Mutable Projection Store: current state/dashboard/query projection
- Historical/Graph Store: supersession, invalidation, trace, impact, currentness history

공통 PK는 tenant boundary를 포함하고, cross-tenant FK는 금지한다. Large population은 snapshot/manifest tree로 관리하고 count만 authority로 사용하지 않는다.

## 2. Migration/Cutover
Phase:
`INVENTORY -> SHADOW_READ -> SHADOW_WRITE -> DUAL_WRITE -> DIVERGENCE_ANALYSIS -> READ_CUTOVER -> WRITE_CUTOVER -> V1_RETIRE_CANDIDATE`.

v1/v2 divergence는 `V1_PASS_V2_HOLD` 등 의미별로 분류한다. v2 information gap을 추정값으로 채우지 않는다. rollback은 dual-write epoch와 exact data population을 기준으로 수행한다.

## 3. API Contract
공통 envelope:
- request_id/correlation_id
- operation name/version
- subject/tenant
- idempotency key
- authority context
- input digest

Response는 transport result와 assurance result를 분리한다.
- HTTP success != assurance PASS
- infrastructure timeout != target FAIL
- async job SUCCEEDED != Final PASS

Pagination은 immutable snapshot token을 사용한다. Bulk는 per-item result + aggregate state를 분리한다.

## 4. Security/Privacy Threat-to-Control Trace
Threat categories:
identity spoofing, cross-tenant access, replay, stale authority, malicious verifier/plugin, evidence tamper, rollback, supply-chain substitution, observer deception, data exfiltration.

각 threat는 `threat_id -> control_id -> test_id -> evidence_type -> affected_claim`으로 추적한다.

Privacy:
- purpose limitation
- data minimization
- retention/deletion
- legal hold
- hidden corpus access
- customer source non-disclosure
- certificate disclosure minimization

## 5. Observability/SLO
Observer output은 claim evidence일 수 있으므로 observer 자체 qualification/currentness를 가진다.

상태:
HEALTHY|DEGRADED|BLIND_SPOT|UNAVAILABLE.

Critical observer BLIND_SPOT/UNAVAILABLE이면 해당 absence claim 발급 중단. SLO 위반은 무조건 대상 FAIL이 아니라 ONSure assurance capability degradation으로 분리한다.

## 6. Recovery/DR
Recovery 대상:
DB, evidence store, event/receipt ledger, graph head, key registry cache, policy profile, qualification registry.

복구 후 자동 Current 금지. `RecoveryQualificationReceipt`가 integrity, generation continuity, missing range, replay check, current authority/policy compatibility를 증명해야 한다.

DR target은 별도 deployment environment identity이며 production currentness 자동 상속 금지.

## 7. External Integration Trust
Git/CI/Registry/OLicense/IdP/Payment/LLM Provider/External API 각각에:
- external identity
- endpoint/account/project scope
- request/response digest 또는 provider receipt
- freshness/replay protection
- reconciliation operation
- failure semantics

External success callback을 단독 authority로 사용하지 않는다. Registry tag보다 immutable digest 우선, IdP role보다 ONSure resource ownership 검증 우선.

## 8. Acceptance
- authority record와 projection 분리
- dual-write divergence 숨김 0
- transport/assurance 상태 혼동 0
- threat→test→evidence trace 가능
- observer blind spot의 fail-closed propagation
- recovery 후 requalification 필수
- external callback single-source PASS 금지
