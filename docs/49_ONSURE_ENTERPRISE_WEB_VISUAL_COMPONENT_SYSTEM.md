# ONSure Enterprise Web — Visual & Component System

- Status: DESIGNED_NONFINAL
- Issue: #94
- Depends on: `46_ONSURE_ENTERPRISE_WEB_UIUX_FOUNDATION.md`, `47_ONSURE_ENTERPRISE_WEB_CORE_WIREFRAMES.md`
- Scope: visual grammar and reusable domain components; not implementation evidence.

## 1. Visual direction

The product should read as an enterprise assurance workbench, not a marketing SaaS dashboard and not a developer-only console.

Design characteristics:
- compact, calm and evidence-first;
- strong typographic hierarchy;
- restrained border/radius treatment;
- limited semantic color;
- dense tables/lists where operationally useful;
- minimal decorative charts;
- no color-only meaning;
- no excessive pill badges;
- no hero marketing block in authenticated work screens.

Reference character: enterprise control plane + assurance workflow + evidence explorer.

## 2. Layout tokens

Recommended initial physical rules:

```text
Desktop minimum validation: 1366 x 768
Primary design width:       1440 x 900
Large validation:           1920 x 1080
Header:                     ~56 px
Page horizontal padding:    24-32 px
Content max width:          fluid / 1440-aware, not narrow marketing column
Section gap:                20-28 px
Compact control height:     32-36 px
Primary control height:     36-40 px
```

Main operational content MUST begin in the first viewport at 1366x768.

## 3. Typography hierarchy

Use the platform-approved Korean/Latin UI font stack; do not depend on a font file bundled only for mockups.

Suggested hierarchy:
- Page title: 24-28 px / 600-700
- Primary state value: 28-36 px / 650-750
- Section title: 16-20 px / 600-700
- Body: 14-16 px
- Table/meta: 12-14 px
- Canonical IDs: 12-13 px monospace-capable style

Long canonical states may be shown as a secondary technical label below a localized human label.

## 4. Semantic tones

Initial semantic palette is intentionally limited to four families:

- Neutral: ordinary/completed/contextual information.
- Primary: current selection/current assurance emphasis.
- Warning: pending/stale/attention that is not a verified failure.
- Critical: failure, policy denial, severe blocker.

Green MUST NOT be the default color for every completed assurance stage. Completion can use neutral check/icon treatment.

UNKNOWN, NOT_RUN, INCONCLUSIVE, HOLD and NOT_AVAILABLE MUST never use success-like styling.

## 5. State symbols

Visual meaning always combines text + icon + tone.

```text
Completed      ✓  neutral/quiet
Current        ●  primary
Pending        ○  neutral
Blocked        !  warning/critical by reason
Unknown        ?  neutral/muted
Not run        –  muted
Stale          ◷  warning
Unavailable    ×/— neutral warning, not failure unless Core says failure
```

The exact icon library may change; semantics may not.

## 6. Core components

### AppHeader
Contains product identity, stable tenant/account context and user/role context. Project context is page/workspace-driven, not necessarily a global selector.

### PrimaryNavigation
Initial domains only: Workspace, Projects, Assurance, Evidence, Decisions, Administration.

### AssuranceStateDisplay
Shows localized label + canonical state. It does not calculate state.

### ProgressionStatus
Separate from AssuranceStateDisplay. Shows AVAILABLE/BLOCKED/UNKNOWN or contract-defined semantics.

### BlockingReasonList
Renders Core blocker categories and links to supporting objects. It must not invent blocker priority.

### RequirementChecklist
Renders satisfied/unresolved Core requirements for `Why this state?`.

### EvidenceSummaryRow
Human label, scoped result, source/target context, freshness/age, link to receipt.

### EvidenceReceiptIdentity
Copyable evidence ID and full hash access without making raw identifiers dominate the page.

### AuthorityIndicator
Shown prominently when authority is non-live, stale, unavailable or nonfinal. Normal authoritative state remains quiet.

### FreshnessIndicator
Only renders Core-provided semantic freshness; age alone is not converted into freshness.

### ImportantChanges
Curated operational changes, not raw audit stream.

### DecisionReviewPanel
Future component binding the reviewed object/revision, evidence references, policy blockers and explicit decision action.

### PolicyBlockExplanation
Differentiates permission denial, policy precondition failure and SoD conflict.

## 7. Tables

Initial table behavior:
- semantic column headers;
- stable sorting where supported;
- simple filters;
- pagination;
- keyboard focus;
- row opens detail;
- inline actions are visually separate from row navigation.

Deferred:
- arbitrary column resize;
- column chooser;
- saved views;
- user-defined density profiles;
- bulk approval;
- spreadsheet-like grid features.

## 8. Cards

Cards are used for high-level decisions, not every object.

Initial workspace may use up to three summary decision cards:
- Current Assurance;
- Progression;
- Blocking Items.

Operational collections use lists/tables. Nested white cards inside cards are discouraged unless meaningfully separating evidence groups.

## 9. Badges

Badges are reserved for compact semantic metadata such as scoped result, freshness or severity.

Avoid rendering every field as a pill. Canonical IDs, owner names, timestamps and ordinary labels should usually be plain text.

## 10. Error and absence visual patterns

### Authority unavailable
Prominent neutral-warning panel; no stale value is silently promoted to current.

### Stale projection/evidence
Warning icon + reason/source revision when available.

### Permission denied
No protected resource existence disclosure. Explain only the principal/policy-safe reason.

### Empty
Short factual message + implication. No decorative empty illustration is required for operational screens.

### Loading
Skeletons may represent layout only. They MUST NOT contain fake status/result numbers.

## 11. Actions

Primary actions are scarce. A screen has one primary decision/action at most.

Danger/high-impact actions such as FinalLock or Production GO must use a separate review step and must not share ordinary button treatment with low-risk navigation.

No direct assurance-state mutation control is defined.

## 12. Accessibility

Minimum design requirements:
- WCAG-oriented contrast;
- keyboard navigation and visible focus;
- state meaning not dependent on color;
- semantic headings/tables;
- screen-reader-friendly status text;
- interactive targets usable without hover;
- error messages associated with the relevant field/action.

## 13. Localization

Canonical identifiers remain stable in English. Human labels may be localized.

Example:

```text
독립 검증 완료
INDEPENDENTLY_VERIFIED
```

Do not translate canonical state values in transport/API contracts.

## 14. UI-11 / UI-12 status

- UI-11 Visual Design Tokens: DESIGNED_NONFINAL
- UI-12 Component Inventory: DESIGNED_NONFINAL

Visual mockups must follow this system but cannot promote W09 or W12 to final/verified status by appearance alone.