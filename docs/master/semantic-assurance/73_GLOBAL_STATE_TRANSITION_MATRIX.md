# ONSure Global State Transition Matrix

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 원칙
ONSure는 하나의 선형 상태기계로 모든 의미를 표현하지 않는다. 다음 차원을 분리한다.
- Execution Lifecycle
- Verification Decision
- Assurance Strength
- Currentness
- Qualification
- Independence
- Human Acceptance
- Deployment Authorization
- Commercial Authorization

각 전이는 predecessor state, required receipt, required authority, effect-time policy, failure state를 가진다.

## 2. Validation Run
| From | To | 필수 조건 | 실패 |
|---|---|---|---|
| PLANNED | AWAITING_APPROVAL | plan/scope/target locked | HOLD |
| AWAITING_APPROVAL | READY | approval valid at effect time | HOLD |
| READY | RUNNING | tenant/resource authority + sandbox ready | BLOCKED |
| RUNNING | OBSERVED | required collectors complete 또는 explicit partial state | INCONCLUSIVE |
| OBSERVED | DECIDED | oracle/decision contract complete | HOLD |
| DECIDED | EVIDENCE_LOCKED | evidence transaction committed | ABORTED_UNTRUSTED/HOLD |

RUNNING에서 timeout/resource exhaustion/cancel은 PASS 경로로 전환하지 않는다.

## 3. Assurance Publication
SELF_VALIDATION_NONFINAL → INDEPENDENT_OTESTER_PASS → INDEPENDENT_OAUDIT_PASS → HUMAN_ACCEPTANCE_PASS → FINAL_CANDIDATE → FINAL_LOCKED.

각 단계는 이전 단계 receipt digest를 소비한다. 독립 단계는 qualified independent principal/implementation/oracle profile을 요구한다. FinalLock은 FreshnessBarrier 이후만 가능하다.

### Post-Final currentness
FINAL_LOCKED는 historical issuance fact다. 별도 currentness dimension은 CURRENT|STALE|REASSESSMENT_REQUIRED|INVALIDATED|REVOKED|UNKNOWN을 사용한다.

## 4. Deployment Revision
DRAFT → DEPLOYING → OBSERVED → VERIFIED_TO_DEPLOYED → ACTIVE_CURRENT
예외: PARTIAL_DRIFT, FAILED, ROLLED_BACK, REASSESSMENT_REQUIRED, INVALIDATED.

ACTIVE_CURRENT는 production-wide runtime population closure가 있을 때만 허용한다.

## 5. Assurance Certificate
DRAFT → ISSUE_ELIGIBLE → ISSUED
발급 후 current validity는 별도 상태:
ISSUED_CURRENT → STALE / REASSESSMENT_REQUIRED / INVALIDATED / REVOKED / EXPIRED / SUPERSEDED / OFFLINE_STATUS_UNCERTAIN.

REVOKED/INVALIDATED에서 CURRENT로 직접 복귀 금지. 새 evaluation/certificate generation을 생성한다.

## 6. AuthorityGrant
DRAFT → ACTIVE → SUSPENDED|REVOKED|EXPIRED.
SUSPENDED는 별도 승인으로 ACTIVE 복귀 가능하나 original validity/purpose/resource ceiling을 넘지 못한다. REVOKED/EXPIRED는 새 grant 없이 복귀 불가.

## 7. WorkUnit
CREATED → LEASED → RUNNING → RESULT_PREPARED → COMMITTED.
예외: LEASE_EXPIRED, RETRYABLE, FAILED, QUARANTINED, CANCELLED.

stale lease owner의 RESULT_PREPARED는 COMMITTED로 갈 수 없다. takeover는 새 attempt/lease generation을 사용한다.

## 8. Plugin/Adapter
UNREGISTERED → REGISTERED → QUALIFICATION_REQUIRED → QUALIFIED → SUSPENDED|REVOKED|INCOMPATIBLE.
version/artifact/privilege manifest 변경 시 QUALIFIED에서 QUALIFICATION_REQUIRED로 내려간다.

## 9. ONSure Release Qualification
BUILD_CANDIDATE → SELF_VALIDATED_NONFINAL → INDEPENDENT_QUALIFICATION_RUNNING → QUALIFIED_SCOPE_CANDIDATE → QUALIFIED.
예외: PARTIAL, NOT_PROVEN, STALE, REQUALIFICATION_REQUIRED, REVOKED.
Self-validation만으로 QUALIFIED 전이 금지.

## 10. Recovery
FAILURE_DETECTED → SERVICE_RESTORED_NONASSURED → DATA_INTEGRITY_CHECK → LEDGER_EVIDENCE_KEY_RECONCILED → RECOVERY_QUALIFICATION_RUNNING → RECOVERY_QUALIFIED_CURRENT 또는 REASSESSMENT_REQUIRED/UNKNOWN.

서비스 복구와 assurance 복구는 다른 상태다.

## 11. 불법 전이 예
- NOT_RUN → PASS
- SELF_VALIDATION_NONFINAL → FINAL_LOCKED
- REJECTED approval → FINAL_LOCKED
- REVOKED certificate → CURRENT same generation
- expired grant → effect authorized
- unqualified plugin → authoritative final evidence producer
- stale lease → committed authoritative result
- service restored → prior production currentness 자동복원

## 12. 상태 저장 원칙
DB `current_state`는 projection이다. Strong state는 immutable events/receipts/authority/policy/evidence에서 재구성 가능해야 한다. projection과 reconstruction 불일치 시 `STATE_RECONSTRUCTION_HOLD`다.
