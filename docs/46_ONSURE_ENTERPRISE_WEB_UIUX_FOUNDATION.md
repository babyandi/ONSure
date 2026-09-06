# ONSure Enterprise Web — UI/UX Foundation

- Status: DESIGNED_NONFINAL
- Issue: #94
- Parent: `45_ONSURE_ENTERPRISE_WEB_OBUILDER_BASELINE.md`
- Authority: ONSure Core remains the only authority for policy, assurance state, evidence and approval truth.
- GitHub Actions: not used as validation authority.

## 1. UX mission

ONSure Web is not a BI dashboard and not a second assurance engine. It is a human decision surface over ONSure Core.

Every primary screen MUST answer, in this order:

1. What is the current authoritative state?
2. Why is it in that state?
3. What blocks progression or decision?
4. What evidence supports the state?
5. What unresolved requirement remains?
6. What changed recently, when relevant?

The Web MUST NOT infer a higher assurance state, synthesize a project-wide score, fabricate portfolio counts, or turn UNKNOWN / NOT_RUN / INCONCLUSIVE / HOLD into success-like presentation.

## 2. Interaction contract

The common interaction grammar is:

`Context -> State -> Why -> Blocker -> Evidence -> Next unresolved requirement`

State is an outcome, not a directly editable field. User actions operate on work objects such as Evidence, Finding, Verification and Decision. Core determines any resulting state transition.

Forbidden UI actions include direct `Set EVIDENCED`, `Set PASS`, `Force Verified` or equivalent shortcuts.

## 3. 3-second / 10-second / 30-second comprehension gate

### 3 seconds
The user can identify:
- current scope/context;
- current assurance state or explicit unavailable state;
- whether progression is blocked;
- the single most important unresolved requirement.

### 10 seconds
The user can identify:
- principal blocking reasons;
- supporting evidence count/list without invented aggregation;
- freshness or authority warning when abnormal.

### 30 seconds
The user can navigate to:
- authoritative Evidence Receipt;
- relevant Finding/Verification/Decision;
- change history or audit trace.

A screen that requires the user to decode a large graph, many KPI cards, or a 12-item side menu before reaching these answers fails this gate.

## 4. Primary personas by screen

The information architecture is shared across roles. ONSure does not create separate products for Developer, Reviewer, Auditor and Manager.

- Workspace: reviewer/manager/owner triage.
- Project/Target: product owner/developer/reviewer operational context.
- Evidence: reviewer/auditor technical proof.
- Decisions: reviewer/approver controlled decision.
- Administration: authorized tenant/system administration only.

Role may reorder attention items, but MUST NOT change canonical state, evidence or authorization truth.

## 5. Initial IA

The initial top-level navigation is intentionally small:

1. Workspace
2. Projects
3. Assurance
4. Evidence
5. Decisions
6. Administration

Detailed future domains such as Workbench, Verification, Findings, Delivery and Audit may appear as sub-navigation or later top-level entries after actual volume justifies them.

### Initial structure

```text
Workspace
Projects
  Project Detail
    Targets
      Target Detail
Assurance
  Targets / Verification / Findings (read-first)
Evidence
  Evidence Receipts
  Evidence Detail
Decisions
  Pending / Completed (future write scope)
Administration
```

Targets are not a top-level menu in the first slice. Project -> Target is a navigation relationship, not an assertion that Target identity belongs exclusively to one Project.

## 6. Context model

Do not place Tenant, Project and Environment as three equally prominent global dropdowns.

- Tenant: stable account/context boundary, normally shown in header/account context.
- Project: current workspace context and primary selector.
- Environment: Target/Verification-level context unless Core explicitly defines a global environment scope.

All detail pages preserve context through breadcrumb and canonical IDs.

Evidence and other globally identified objects receive independent URLs even when breadcrumb shows project/target ancestry.

## 7. Assurance presentation

Canonical assurance states remain:

`DECLARED -> DESIGNED -> IMPLEMENTED -> CONNECTED -> TESTED -> EVIDENCED -> INDEPENDENTLY_VERIFIED -> OPERATING_EFFECTIVELY`

Default summary view shows only:
- current state;
- next state, when meaningful;
- progression result;
- blockers/unresolved requirements.

The full 8-state journey is an expandable/detail primitive, not mandatory permanent chrome.

### Distinct concepts

- Assurance State: current proven stage.
- Progression: whether movement toward a higher stage is blocked/available/unknown.
- Verification Result: PASS/FAIL/etc. for an individual run or case.
- Connectivity: Web/Core communication state.

These MUST NOT be merged into one badge.

## 8. Result semantics

Canonical result semantics include:

- PASS
- FAIL
- BLOCKED
- HOLD
- NOT_RUN
- INCONCLUSIVE
- NOT_APPLICABLE
- UNKNOWN where defined

PASS is always qualified by scope, for example `Runtime verification: PASSED`; a floating global `PASS` badge is prohibited unless Core defines and returns that exact authoritative object result.

`UP` from `/healthz` is service health only and MUST NOT be presented as assurance success.

## 9. Authority and provenance

Normal state display is assumed authoritative only when it originates from the Core read contract and its revision/provenance is available.

When authoritative data is unavailable:

```text
State unavailable
Authoritative Core state could not be read.
No cached or estimated state is shown as current truth.
```

Projection/cached data, if later used, MUST expose source revision and projected/synchronized time. Stale authoritative-read projections may be displayed only with an explicit stale warning.

## 10. Warning discipline

Warnings are scarce semantic resources.

- Normal states use restrained neutral/primary treatment.
- NONFINAL appears once at page/workspace level unless object-specific repetition is necessary.
- Blocked reason is classified, not merely painted red.
- Connectivity warnings appear only when degraded/unavailable.
- Completed assurance stages do not all become green PASS-like blocks.

Primary blocker classes:
- EVIDENCE_MISSING
- VERIFICATION_FAILED
- APPROVAL_REQUIRED
- POLICY_DENIED
- AUTHORITY_UNAVAILABLE
- STALE_EVIDENCE
- SOD_CONFLICT
- UNKNOWN / NOT_RUN / INCONCLUSIVE conditions where applicable

## 11. Evidence UX

Evidence is available from every assurance decision context, but technical IDs and hashes are progressively disclosed.

Summary form:
- human label/type;
- scoped result;
- target/source;
- age/freshness when meaningful.

Detail form:
- receipt ID;
- source revision/SHA;
- environment;
- execution identity;
- timestamp/timezone;
- parent lineage/hash;
- linked verification/finding/approval;
- raw technical detail.

Evidence lineage defaults to a trace/list, not a dense global graph. Graph view is optional advanced visualization.

## 12. History and audit separation

`Recent/Important Changes` is a curated operational view.

`Audit Trail` is complete evidence-grade history.

The Workspace MUST NOT dump every audit event into an activity feed. Important Changes initially include:
- assurance state change;
- blocking finding opened/resolved;
- evidence invalidated/staled;
- decision completed;
- authoritative read loss/recovery where material.

## 13. Decision UX rules

Decision screens use a stable snapshot/revision. If Core revision changes during review, the decision is blocked and review must restart or explicitly rebase according to policy.

Permission denial and policy blocking are different UX states:

- permission denied: the principal lacks authority;
- policy blocked: the principal may have authority but preconditions are unsatisfied.

SoD conflicts explain the rule without leaking cross-tenant resource existence.

High-risk decisions such as FinalLock, Production GO and Commercial GO remain separate decision classes and MUST NOT be collapsed into ordinary approval buttons.

## 14. Empty/error/loading states

Designed states are mandatory:
- loading: neutral skeleton, never fake status values;
- empty: factual absence plus concise implication;
- unavailable: authoritative source not readable;
- stale: source revision/freshness mismatch;
- unauthorized/not available: no cross-tenant existence leak;
- nonfinal: independent/final acceptance not completed;
- partial: fields absent are explicitly marked, not silently defaulted to zero.

`0` MUST NOT substitute for `unknown`, `not available`, or `not run`.

## 15. Scope control

Initial UX explicitly excludes:
- portfolio assurance score/percentage;
- global confidence score;
- bulk independent approval;
- direct state mutation;
- global lineage graph as primary navigation;
- complex saved views;
- configurable data-grid framework;
- global multi-object search;
- decorative charts without a concrete decision purpose.

## 16. UI artifact status

- UI-01 UX Principles: DESIGNED_NONFINAL
- UI-02 Information Architecture: DESIGNED_NONFINAL
- UI-03 Screen Inventory: DESIGNED_NONFINAL
- UI-04 User/Role Matrix: DESIGNED_NONFINAL
- UI-05 Navigation Model: DESIGNED_NONFINAL
- UI-06 State Presentation Rules: DESIGNED_NONFINAL
- UI-07 Evidence Presentation Rules: DESIGNED_NONFINAL
- UI-08 Error/Empty/Stale States: DESIGNED_NONFINAL
- UI-09 Core Read Model dependency: CONTRACT_REQUIRED, see `48_ONSURE_ENTERPRISE_WEB_READ_MODEL_CONTRACT.md`

This document is design authority for Web presentation only. It is not W12 runtime evidence.