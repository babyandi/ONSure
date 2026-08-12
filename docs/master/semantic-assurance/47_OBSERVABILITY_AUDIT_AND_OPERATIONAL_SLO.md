# ONSure Observability·Audit·Operational SLO 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
ONSure 자체 운영 이상이 검증결과의 false PASS·stale result·evidence loss로 이어지지 않도록 **관측가능성, 감사, 운영 SLO, degraded mode, incident-to-assurance propagation**을 설계한다.

## 2. Telemetry 축
- Request/Operation Trace
- Validation Run Trace
- Worker/Queue Trace
- Evidence Commit Trace
- Authority/Approval Trace
- Deployment/Currentness Trace
- Certificate/Revocation Trace
- AI Provider/Agent Trace

모든 trace는 tenant/project/target/run/operation correlation을 지원하되 secret/raw source를 포함하지 않는다.

## 3. Core Metrics
### Control Plane
- API availability/error/latency
- authorization deny/error rate
- idempotency conflict
- outbox lag

### Validation
- run success/fail/blocked/not-run distribution
- collector completeness
- evidence commit failure
- oracle timeout
- flaky attempt rate
- critical detector escape

### Assurance
- currentness evaluation lag
- stale/reassessment/invalidated count
- revocation propagation lag
- graph reconstruction failure
- certificate verification uncertainty
- qualification expiry horizon

### Distributed Work
- queue lag
- lease expiry
- duplicate execution
- poison unit count
- partition closure delay
- deterministic aggregation mismatch

## 4. SLO와 Assurance 분리
서비스 SLO가 만족돼도 Assurance PASS가 되는 것은 아니다. 반대로 validation FAIL이 서비스 장애를 뜻하지 않는다.

SLO breach가 Assurance에 영향을 주는 경우만 impact rule로 연결한다. 예:
- evidence store durability breach → evidence-dependent claims reassessment
- collector outage → observation-dependent claim ceiling
- revocation propagation delay → certificate currentness uncertainty

## 5. Degraded Mode
상태 후보:
- NORMAL
- DEGRADED_NONCRITICAL
- DEGRADED_ASSURANCE_LIMITED
- ASSURANCE_ISSUANCE_SUSPENDED
- RECOVERY_QUALIFICATION_REQUIRED

검색 UI 장애 같은 noncritical 문제와 evidence/authority/observer 장애를 구분한다.

## 6. Assurance Issuance Suspension
다음 계열은 강한 issuance를 자동 중단하는 후보다.
- authority/key registry integrity unknown
- evidence commit/graph head integrity unknown
- critical observer outage
- final reconstructor mismatch
- currentness engine materially behind policy SLO
- ONSure release qualification expired/revoked

기존 historical artifact를 삭제하지 않고 신규 Final/Certificate 발급 ceiling을 제한한다.

## 7. Audit Event
필수:
- event_id
- occurred_at/recorded_at
- actor/principal
- authority generation
- operation/subject
- before/after state refs
- policy epoch
- evidence/receipt refs
- correlation/causation
- outcome

Audit log 자체의 chain/head integrity를 관리한다.

## 8. Clock/Time Semantics
- occurred_at와 recorded_at 분리
- clock source/profile 기록
- 큰 clock skew는 freshness/revocation 판단을 HOLD
- distributed worker local time만으로 global order를 확정하지 않음

## 9. Alerting
고위험:
- critical detector escape
- evidence seal corruption
- cross-tenant denial anomaly
- authority/key compromise
- stale/revoked certificate verification spike
- active selector divergence
- ONSure qualification expiry

Alert acknowledged를 결함 해결로 간주하지 않는다.

## 10. Incident → Assurance Impact
Incident 등록 시 affected components/epochs/time window를 기반으로 `ImpactEvaluation`을 생성한다.
결과:
- NO_ASSURANCE_IMPACT_PROVEN
- REASSESSMENT_REQUIRED
- INVALIDATION_REQUIRED
- QUALIFICATION_REQUIRED
- UNKNOWN_HOLD

운영자가 수동으로 '영향 없음'을 선택하는 것만으로 종료하지 않는다.

## 11. Runbook 최소 세트
- Evidence store partial outage
- Ledger/graph inconsistency
- Key compromise
- Revocation propagation failure
- Currentness backlog
- Worker duplicate storm
- Cross-tenant isolation incident
- Hidden corpus leakage
- External AI provider outage/change
- Backup restore

각 Runbook은 containment, evidence preservation, recovery, assurance ceiling, requalification, customer notification을 포함한다.

## 12. Operational SLO 후보
정확한 숫자는 Policy/Open Decision로 관리하되 측정 대상은 고정한다.
- authorization/effect decision latency
- evidence commit durability/latency
- currentness evaluation freshness
- revocation propagation
- certificate verification availability
- graph reconstruction success
- recovery qualification completion

SLO 목표치를 못 정했다고 측정 자체를 생략하지 않는다.

## 13. Negative Test
- collector outage인데 CURRENT 유지
- revocation queue backlog인데 online verifier가 CURRENT 반환
- audit chain gap을 무시
- clock rollback으로 expired approval 재사용
- active selector divergence가 alert만 되고 issuance 지속
- restore 후 qualification 없이 Final 발급
- operational dashboard cache가 stale PASS 표시

## 14. 수용기준
- ONSure 운영 상태가 Assurance 강도에 영향을 줄 때 machine-readable ceiling으로 전파된다.
- audit/event/trace로 Final/Certificate 생성 경위를 재구성할 수 있다.
- degraded mode가 fail-open으로 강한 assurance를 발급하지 않는다.
- recovery 후 qualification 전 strong issuance를 재개하지 않는다.
