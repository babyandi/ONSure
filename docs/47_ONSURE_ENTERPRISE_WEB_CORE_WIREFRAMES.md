# ONSure Enterprise Web — Core Wireframes P01~P04

- Status: DESIGNED_NONFINAL
- Issue: #94
- Depends on: `46_ONSURE_ENTERPRISE_WEB_UIUX_FOUNDATION.md`
- Scope: read-first core assurance journey only.

## P01 Assurance Workspace

### Primary decision
What requires attention now, and what is the current authoritative assurance context?

### Desktop structure

```text
┌────────────────────────────────────────────────────────────────────┐
│ ONSure   Tenant context             Search(project only)   User    │
├────────────────────────────────────────────────────────────────────┤
│ NONFINAL / authority warning when applicable                      │
├────────────────────────────────────────────────────────────────────┤
│ Assurance Workspace                                                │
│ Project: [current project selector]                                │
├────────────────────┬────────────────────┬──────────────────────────┤
│ Current state      │ Progression        │ Blocking items           │
│ TESTED             │ BLOCKED            │ 3                         │
│ canonical label    │ reason summary     │ drill-down                │
├────────────────────────────────────────────────────────────────────┤
│ Next unresolved requirement                                       │
│ Independent verification evidence is missing                      │
├───────────────────────────────────────┬────────────────────────────┤
│ Needs Attention                       │ Important Changes           │
│ [type] [target] [reason] [age]        │ state/evidence/decision     │
│ ...                                   │ material events only        │
└───────────────────────────────────────┴────────────────────────────┘
```

### Rules
- No hero marketing copy after Core-connected implementation.
- Maximum three top summary blocks in initial slice.
- If no authoritative project is selected, no synthetic state is shown.
- `Blocking items=0` only when Core explicitly returns zero for a known denominator/scope.
- Current state card links to P03 Target Detail or project assurance distribution depending on selected scope.
- Needs Attention is factual, not AI-ranked unless later explicitly defined and labeled advisory.

### Initial empty state

```text
No authoritative assurance data available for this project.
Select a project or verify the Core connection.
```

## P02 Project Detail / Target List

### Primary decision
Which target needs examination, without fabricating a project-wide assurance score?

```text
Breadcrumb: Projects / Project A

Project A
Project ID: PRJ-...
Owner / materiality / last authoritative revision

Targets
┌──────────────────┬──────────────────────┬─────────────┬──────────────┐
│ Target           │ Current Assurance    │ Progression │ Last verified│
├──────────────────┼──────────────────────┼─────────────┼──────────────┤
│ Payment API      │ TESTED               │ BLOCKED     │ 2h ago       │
│ Risk Model       │ EVIDENCED            │ AVAILABLE   │ 1d ago       │
│ RAG Gateway      │ NOT AVAILABLE        │ UNKNOWN     │ —            │
└──────────────────┴──────────────────────┴─────────────┴──────────────┘
```

### Rules
- Do not calculate `Project Assurance = average/minimum/weighted score` unless Core defines such an authoritative object.
- Target rows expose canonical target identity.
- Initial controls: project selector, simple target text filter, state filter, pagination.
- No column customization/saved view/bulk decision in initial slice.
- `NOT AVAILABLE` is not rendered as `0`, blank success, or green.

## P03 Target Detail

### Primary decision
Why is this target in its current state and what prevents progression?

```text
Breadcrumb: Project A / Payment API

Payment API                         TGT-...
Environment: PROD                  Criticality: HIGH

Current Assurance                  Progression
TESTED                             BLOCKED

Why this state?
✓ Required test class A satisfied
✓ Required test class B satisfied
! Independent evidence requirement unresolved

Blocking conditions
- EVIDENCE_MISSING: runtime receipt for required scope
- APPROVAL_REQUIRED: independent reviewer decision

Evidence
[Runtime verification] [scoped result] [age]
[Security verification] [scoped result] [age]
[View all evidence]

Important history
state/evidence/blocker changes only
```

### Rules
- `Why this state?` is rendered from Core-provided satisfied/unresolved conditions, not reconstructed in Web logic.
- The full 8-stage assurance path is collapsed by default and expandable.
- No direct state-change button.
- Evidence summary links to P04.
- If Core revision changes while the page is open, read display may refresh; any future decision surface must bind to a stable snapshot.

## P04 Evidence Receipt Detail

### Primary decision
What exactly does this evidence prove, for which source/scope, and how is it connected to the current assurance claim?

```text
Breadcrumb: Project A / Payment API / Evidence

Runtime Verification                              EV-...
Scoped result: PASSED
Authority: ONSure Core Evidence Ledger

Summary
Target                 Payment API
Verification           VR-...
Source revision/SHA     abc123...
Environment             PROD / runtime identity
Executed at             2026-... KST
Freshness               FRESH / STALE / UNKNOWN

Supports
Assurance requirement   <canonical requirement>
State relationship      supports TESTED

Trace
Verification VR-...
  -> generated Evidence EV-...
  -> linked Finding(s), if any
  -> linked Decision(s), if any

Technical detail [collapsed]
Command / exit / logs / artifacts / raw metadata / hashes
```

### Rules
- Receipt ID is copyable but not visually dominant over human meaning.
- Hash is abbreviated in summary and copyable in full.
- Timestamp always includes timezone; exact time is available even when relative age is shown.
- Stale evidence remains visible but is explicitly non-current for affected claims.
- No `Delete Evidence`; future invalidation/supersession preserves lineage.
- Default lineage is trace/list. Graph is optional advanced view.

## Shared layout constraints

- Desktop-first: validate at 1366x768, 1440x900 and 1920x1080.
- Header target height: ~56 px.
- Context/page-title region must remain compact; no large hero block.
- Main content should begin within the first viewport at 1366x768.
- Left navigation, if used, remains compact and does not exceed the six initial top-level domains.
- Cards are reserved for summary decisions; tables/lists handle operational density.
- Avoid excessive pill badges and rounded-card SaaS styling.

## Shared navigation paths

Initial canonical routes:

```text
/
/projects
/projects/{projectId}
/projects/{projectId}/targets/{targetId}
/evidence/{evidenceId}
/decisions/{decisionId}          # later
/findings/{findingId}            # later/read scope
```

URL hierarchy does not imply domain ownership. Evidence retains a global ID even when breadcrumb displays project/target context.

## Acceptance checks for each wireframe

A wireframe is not considered design-ready unless:
- a 3-second answer exists;
- authoritative vs unavailable state is unambiguous;
- one primary decision is identifiable;
- evidence drill-down is reachable;
- warning usage is restrained;
- no Web-side assurance calculation is required;
- loading/empty/stale/unavailable variants are specified.

P01~P04 remain `DESIGNED_NONFINAL`; visual mockups and implementation are separate gates.