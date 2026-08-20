# 163 Final-Target Delta Missing-Design Closure

Status: `NORMATIVE_REFINEMENT / DESIGN_ONLY / NON_FINAL`
Parent: `162_FINAL_TARGET_DELTA_DESIGN_DISCOVERY_REOPENING.md`

## 1. Purpose
This document turns the 24 triaged delta obligations into implementation-grade design obligations. It does not claim implementation, execution, or Design Lock.

Every row is closed at design level only when the following are explicit: owner, canonical object, state/invalidation, authority, effect/API semantics, UI disclosure, evidence, negative/recovery fixture, and independent oracle.

## 2. Canonical companion objects
The following candidate objects are introduced as design-level canonical concepts. Exact JSON schemas remain implementation work unless an existing contract already owns the field.

- `VisibilityEvidenceProfile` — visibility_mode, required_observation_classes, observed_classes, unsupported_classes, mode_digest, limitation_set.
- `OracleAuthorityRecord` — oracle_id, owner, source, version, digest, applicability, state, supersedes, dispute_ref.
- `IndependenceProfile` — validator_id, organization, codebase_lineage, rule_source, model_provider_family, parser_lineage, runtime_domain, operator_domain, shared_dependencies, independence_level.
- `ProviderCurrentnessRecord` — provider/model declared identity, observed probe digest, pinning capability, fallback lineage, drift state.
- `AdapterCoverageReceipt` — adapter/version, input population, parsed/skipped/unsupported/failed counts, version range, coverage digest.
- `TCBHealthReceipt` — component inventory, versions/digests, health/currentness, injected fault, detection result.
- `TrustEpochRecord` — trusted-time profile, signer/key epoch, revocation state, compromise effective time, affected_receipt_scope.
- `CoverageBudgetEnvelope` — budget/credit/token/time ceilings, exhaustion state, excluded_work_set, denominator_change_ref.
- `WaiverDecisionRecord` — finding/control scope, owner, rationale, compensating_control, issued_at, expires_at, renewal_count, invalidation_triggers.
- `SystemicDependencyGroup` — dependency identity/type, dependent programs, criticality, currentness, invalidation fanout digest.
- `CorpusContributionRecord` — origin tenant, consent/license basis, corpus epoch, derived artifacts, withdrawal/poisoning state.
- `ReviewerQualificationRecord` — reviewer, domain scope, qualification evidence, valid_from/to, conflict declarations, calibration history.
- `PurposeRightsRecord` — data/artifact identity, allowed purposes, rights basis, consent/legal basis, retention/residency constraints.
- `EngagementAuthorization` — target owner, target set, allowed test classes, forbidden actions, window, rate ceiling, emergency stop, expiry/revocation.
- `OffboardingPlan/Receipt` — new-effect block, export, token/grant revoke, job/credit settlement, retention/legal hold, deletion, external anchor handling.
- `ClaimLocalizationProfile` — locale mapping, canonical token retention, timestamp/timezone rules, fallback behavior, accessibility critical-message rules.
- `ExternalEffectReceipt` — connector/webhook effect identity, idempotency key, signature, attempt history, replay window, final delivery state.
- `CheckpointEffectLedger` — checkpoint identity, computation replay counter, external-effect dedupe identities, resume provenance.
- `EvidenceHeadReconciliationReceipt` — competing heads, authoritative selection rule, preserved losing head, reconciliation digest.
- `VendorExitPlan` — provider/subprocessor inventory, data/key/artifact export, replacement target, continuity verification, termination evidence.
- `RedactedEvidenceCommitment` — original digest, disclosed fields, redaction map commitment, verifier proof/limitation.
- `BreakGlassReceipt` — principal, reason, bounded authority, TTL, used operations, automatic revoke, retrospective review.
- `PolicyCurrencyRecord` — policy/control source, jurisdiction, effective/superseded dates, currentness state, affected targets.
- `BenchmarkContaminationRecord` — fixture/corpus identity, training/tuning exposure, contamination state, invalidation/replacement lineage.

## 3. State and invalidation rules

### 3.1 Evidence sufficiency
`SUFFICIENT -> DEGRADED -> INSUFFICIENT`; any visibility downgrade, adapter failure, or lost observation class can only keep or lower assurance strength. It cannot raise it.

### 3.2 Oracle lifecycle
`PROPOSED -> ACTIVE -> DISPUTED | SUPERSEDED | REVOKED`. A disputed/unavailable oracle makes dependent result `HOLD/INCONCLUSIVE`; replacing the oracle does not rewrite the earlier verdict.

### 3.3 Independence lifecycle
`DECLARED -> ASSESSED -> QUALIFIED -> DEGRADED | EXPIRED | REVOKED`. Shared critical dependencies are explicit, and qualification strength is derived from the weakest mandatory independence dimension for the applicable tier.

### 3.4 Provider/currentness lifecycle
`CURRENT -> DRIFT_SUSPECTED -> REASSESSMENT_REQUIRED -> CURRENT | INVALIDATED`. Fallback-provider execution creates a new condition set and cannot inherit equivalence silently.

### 3.5 Adapter coverage lifecycle
`QUALIFIED -> PARTIAL | UNSUPPORTED | FAILED | STALE`. Any uncounted silent drop is a validator defect, not target PASS.

### 3.6 TCB lifecycle
`HEALTHY -> DEGRADED -> FAILED -> RECOVERED_PENDING_REQUALIFICATION -> HEALTHY`. Critical TCB failure blocks positive claims until requalification evidence exists.

### 3.7 Trust epoch lifecycle
`CURRENT -> REVOKED | COMPROMISED | STALE`. A compromise event computes affected evidence by effect time and signer/key epoch; re-signing creates a new node rather than mutating prior provenance.

### 3.8 Budget lifecycle
`AVAILABLE -> WARNING -> EXHAUSTED -> RESCOPING_REQUIRED | TOPPED_UP`. `EXHAUSTED` never maps to PASS. Scope reduction requires explicit approval and denominator regeneration.

### 3.9 Waiver lifecycle
`PROPOSED -> APPROVED -> ACTIVE -> EXPIRED | INVALIDATED | REVOKED`. Material policy/target/authority change can invalidate an active waiver before expiry.

### 3.10 Systemic dependency lifecycle
`CURRENT -> DEGRADED -> FAILED/REVOKED -> IMPACT_ASSESSMENT -> REQUALIFIED`. Fan-out must be deterministic and evidence-bound.

### 3.11 Corpus lifecycle
`ELIGIBLE -> ACTIVE -> WITHDRAWN | POISONED | RIGHTS_INVALID -> IMPACT_REVIEW -> CLEAN/REQUALIFIED`. Derived artifacts stay traceable through the invalidation graph.

### 3.12 Reviewer lifecycle
`CANDIDATE -> QUALIFIED -> SUSPENDED | EXPIRED | REVOKED`. Conflict-of-interest blocks assignment even when qualification is otherwise valid.

## 4. Authority / SoD rules
- A validator may not self-approve a changed oracle for the same result it is evaluating.
- The same principal may not both grant a high-risk waiver and independently verify that waiver's compensating control.
- Portfolio systemic invalidation cannot be suppressed by an affected product owner alone.
- Shared-corpus withdrawal classification requires Data Governance authority; derived-impact closure requires independent assurance.
- Reviewer qualification owner and reviewer assignment owner must be distinct for regulated profiles.
- Break-glass principal cannot approve its own retrospective assurance.
- Policy currency exceptions cannot make an expired/revoked control pack appear CURRENT.

## 5. API / effect semantics
The existing operation registry remains authoritative. New operations MUST be added before implementation if not already represented. Required operation meanings include:
- inspect/update oracle authority and dispute,
- assess independence profile,
- inspect provider currentness/fallback lineage,
- inspect adapter coverage,
- open/close waiver with expiry,
- compute systemic dependency impact,
- withdraw corpus contribution and run impact analysis,
- qualify/suspend reviewer,
- issue/revoke engagement authorization,
- execute offboarding settlement,
- replay/reconcile external-effect delivery,
- reconcile competing evidence heads,
- execute vendor exit verification,
- execute break-glass retrospective review,
- update policy currency/supersession,
- invalidate contaminated benchmark.

Mutation operations require idempotency key, actor/authority, target population digest, effect-time receipt and explicit retry semantics.

## 6. UI / disclosure rules
No UI may compress these states into a generic PASS/FAIL only.

Mandatory disclosures:
- visibility mode + unsupported observation classes,
- oracle state and dispute reason,
- independence profile and shared critical dependencies,
- provider drift/fallback condition change,
- adapter parsed/skipped/unsupported/failed counts,
- TCB degraded/failure banner,
- budget exhaustion and unexecuted scope,
- waiver expiry/compensating controls,
- systemic dependency blast radius,
- corpus withdrawal/derived impact,
- reviewer qualification/conflict state,
- policy stale/superseded state,
- redaction limitation in independently-verifiable evidence packs.

Critical limitations must be visible in the same screen or one interaction away from the positive claim.

## 7. Negative / failure / recovery fixtures
Each DD item receives at least one mandatory fixture:

| ID | Mandatory negative/recovery fixture |
|---|---|
| DD-001 | Black-box run loses one mandatory observation channel mid-run; final positive claim must downgrade. |
| DD-002 | Oracle changes after execution; prior verdict becomes requalification-required, not silently recomputed. |
| DD-003 | Two validators share the same parser/rule source; independence tier must downgrade. |
| DD-004 | Provider silently changes model behavior / fallback is used; equivalence claim blocked. |
| DD-005 | Parser skips unsupported manifest objects; skipped count must surface and coverage cannot be 100%. |
| DD-006 | Inject rule-engine omission / signer failure / partial evidence write; target must not be blamed. |
| DD-007 | Signing key compromise effective between two receipts; affected-set calculation must be deterministic. |
| DD-008 | Credit/token budget expires with 20% cases unrun; decision remains PARTIAL/NOT_RUN. |
| DD-009 | Active waiver expires after a target change; affected approval is invalidated. |
| DD-010 | Shared provider is revoked; all dependent portfolios receive deterministic impact events. |
| DD-011 | Corpus item later marked poisoned; all derived patterns are traced and requalified. |
| DD-012 | Qualified reviewer has declared target-vendor conflict; assignment must be blocked. |
| DD-013 | Data purpose changes from validation to training without basis; training use blocked. |
| DD-014 | Production DAST requested outside authorized time window; execution BLOCKED. |
| DD-015 | Tenant termination with pending webhook/job/credit reservation; all are settled or explicitly held. |
| DD-016 | Locale fallback drops HOLD limitation text; rendering validation must fail. |
| DD-017 | Duplicate webhook retry after timeout; external effect occurs once and attempts remain auditable. |
| DD-018 | Resume after crash repeats computation but not already-committed external effect. |
| DD-019 | Two DR regions create competing evidence heads; both preserved, one authority path selected. |
| DD-020 | Provider exit while evidence retrieval still required; continuity verification must fail if export incomplete. |
| DD-021 | Redacted pack removes a critical field; verifier detects limitation without seeing restricted value. |
| DD-022 | Break-glass operation outlives TTL; subsequent effect is denied and retrospective review flags it. |
| DD-023 | Superseded policy pack used for a new run; result cannot claim current compliance. |
| DD-024 | Golden fixture appears in tuning corpus; qualification benchmark is invalidated/replaced. |

## 8. Independent oracle requirements
- Every fixture must define expected machine state and expected claim-language effect.
- Independent verifier uses preserved input/evidence and must not depend solely on the same model/provider/parser used by the primary validator for P0 items.
- For DD-002, the oracle itself is the object under governance; the independent check verifies authority lineage and dispute semantics rather than merely replaying the same expected answer.
- For DD-006/007/019/024, deterministic artifact/evidence digests are mandatory.

## 9. Completion matrix
All 24 DD rows now have a design owner, candidate canonical object, state/invalidation rule, authority rule, UI disclosure rule, evidence requirement, negative/recovery fixture and independent oracle requirement.

Therefore:

`DELTA_MISSING_DESIGN_COUNT = 0 at companion-design level`

This statement is limited to the 24 discovered delta obligations. It does not prove that discovery is globally exhausted, nor that machine contracts/API registry entries/schemas/tests are implemented.

## 10. Next denominator rule
The Product Design Requirement Universe may only be regenerated after this companion is admitted by Requirement Authority. Any EPOCH 0003 candidate produced before 162/163 is stale and must not be promoted.

Highest claim: `DELTA_DESIGN_MATERIALIZED_NONFINAL`.