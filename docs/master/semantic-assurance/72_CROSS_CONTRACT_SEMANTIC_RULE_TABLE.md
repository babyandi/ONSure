# ONSure Cross-Contract Semantic Rule Table

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
개별 Schema가 valid여도 계약 조합이 의미적으로 모순되는 경우를 차단한다. 각 Rule은 machine rule id, involved contracts, predicate, failure state, required fixture를 가진다.

## 2. Final / Approval / Lock
| Rule | 조건 | 위반 결과 |
|---|---|---|
| XC-FINAL-001 | FinalLock.approval_decision == APPROVE | BLOCK |
| XC-FINAL-002 | FinalLock.candidate_digest == FinalCandidate.digest | BLOCK |
| XC-FINAL-003 | FinalCandidate.subject/target/scope/requirement/policy epoch == Approval context | BLOCK |
| XC-FINAL-004 | Approval valid_at_effect_time=true, not expired/revoked/consumed | BLOCK |
| XC-FINAL-005 | FreshnessBarrier PASS after latest material event | HOLD/BLOCK |
| XC-FINAL-006 | OTester/OAudit/Human receipts are required assurance class, current and distinct principal where policy requires | HOLD |
| XC-FINAL-007 | unresolved critical blocker/risk ceiling allows requested tier | HOLD |

## 3. Certificate / Currentness
- XC-CERT-001 Certificate.final_lock_digest must resolve to existing FinalLock.
- XC-CERT-002 Certificate.composition_snapshot_digest must be exact product population used for issuance.
- XC-CERT-003 CURRENT certificate requires currentness snapshot CURRENT and verification mode supporting current revocation lookup.
- XC-CERT-004 revoked issuer/verifier key at effect time invalidates issuance authority.
- XC-CERT-005 PDF/HTML claim language cannot exceed machine certificate tier/decision/currentness.
- XC-CERT-006 offline certificate must carry revocation snapshot age/uncertainty ceiling.

## 4. Deployment / Runtime
- XC-DEP-001 Final verified artifact digest == deployment observed artifact digest for production-bound tier.
- XC-DEP-002 active runtime population must reference deployment revision in current scope.
- XC-DEP-003 mixed active revisions in rolling/canary require explicit cohort scope; global CURRENT prohibited until closure.
- XC-DEP-004 rollback target historical FinalLock does not auto-restore CURRENT.
- XC-DEP-005 runtime config/model/prompt/RAG/tool identity drift triggers impact evaluation.

## 5. Composition
- XC-COMP-001 exact subject population digest and edge population digest must match graph snapshot.
- XC-COMP-002 Critical HARD child negative/unresolved state sets parent ceiling.
- XC-COMP-003 N/A child requires applicability proof bound to same subject/requirement epoch.
- XC-COMP-004 conflicting results require supersession proof or CONFLICT_HOLD.
- XC-COMP-005 parent assurance strength/currentness/qualification cannot exceed required child ceiling.
- XC-COMP-006 self-validation child cannot satisfy independent child requirement.

## 6. Authority / Operation
- XC-AUTH-001 operation name/version must be allowed by AuthorityGrant at effect time.
- XC-AUTH-002 resource/tenant/purpose must be subset of grant scope.
- XC-AUTH-003 delegated grant must be subset of parent grant and not outlive it.
- XC-AUTH-004 four-eyes counts unique principal/admin-owner, not key count.
- XC-AUTH-005 break-glass permits bounded operation only and never raises assurance strength.
- XC-AUTH-006 caller supplied `_authorized_*` fields are never authority source.

## 7. Event / Receipt / Evidence
- XC-EVT-001 effect event must have committed receipt or explicit ABORTED/UNKNOWN state.
- XC-EVT-002 Receipt.subject/target/policy/authority epochs must equal operation context.
- XC-EVT-003 event causation/correlation cannot cross tenant except public authority namespace.
- XC-EVT-004 evidence graph node digest must equal canonical serialized authoritative object.
- XC-EVT-005 derived evidence cannot be counted as independent origin from its parent.

## 8. Recovery
- XC-REC-001 restored generation != old generation; historical states remain immutable facts.
- XC-REC-002 recovery qualification must bind restored ledger/evidence/key registry snapshots.
- XC-REC-003 missing authoritative objects force assurance ceiling <= REASSESSMENT_REQUIRED/UNKNOWN.
- XC-REC-004 service health recovery alone cannot restore assurance currentness.

## 9. Distributed Work
- XC-WRK-001 committed WorkUnit result requires active lease or deterministic takeover proof.
- XC-WRK-002 duplicate attempt results cannot duplicate denominator/effect.
- XC-WRK-003 aggregate exact work unit population must close all required partitions.
- XC-WRK-004 aggregate digest is canonical-order independent of completion order.

## 10. AI / Meta-Assurance
- XC-AI-001 BehaviorPopulation target runtime identity must match evaluated current runtime.
- XC-AI-002 sample exclusion must use precommitted rule; post-outcome cherry-pick invalidates population.
- XC-AI-003 majority/cross-model agreement cannot satisfy GT3+ ground truth requirement alone.
- XC-AI-004 ONSureReleaseQualification must include current validator/oracle/adapter generations used by execution.
- XC-AI-005 unsupported archetype cannot inherit another archetype QUALIFIED state.

## 11. Policy / Tier
- XC-POL-001 tenant/product/industry override cannot weaken hard invariant or global safety floor.
- XC-POL-002 policy epoch/digest used by execution must match Final/Certificate policy binding.
- XC-POL-003 product commercial plan cannot imply assurance tier without evidence criteria.

## 12. Fixture 규칙
각 XC rule은 최소 1개 dedicated semantic-invalid fixture를 가진다. 여러 rule을 한 fixture에서 동시에 깨뜨리는 경우에도 각 rule별 single-fault fixture를 별도로 유지한다.
