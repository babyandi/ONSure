# ONSure Enterprise Web — UI State Matrix

- Status: DETAILED_DESIGNED_NONFINAL
- Issue: #94
- Applies to: P01~P04 mockups and subsequent server-rendered implementation
- Authority: presentation contract only; Core remains authoritative
- W12: NOT_RUN

## 1. Purpose

This matrix prevents the Web surface from collapsing materially different truth states into generic success/error visuals. UI state is a presentation of authoritative or availability facts, not a substitute authority.

## 2. Cross-screen state model

| UI state | Meaning | Required visual treatment | Forbidden treatment | Interaction |
|---|---|---|---|---|
| NORMAL_KNOWN | authoritative data available | neutral/primary presentation with explicit canonical value | inferred aggregate score | normal read actions |
| LOADING | request unresolved | skeleton/placeholder without synthetic data | fake counts, fake PASS, sample value masquerading as result | critical actions unavailable |
| EMPTY | authoritative query succeeded with zero domain objects | concise empty-state explanation | UNKNOWN, NOT_RUN or failure wording | allowed read/navigation only |
| NOT_AVAILABLE | source cannot currently provide value | explicit `NOT_AVAILABLE` or human label | zero, none, PASS | no state-changing action dependent on missing value |
| UNKNOWN | Core reports unknown truth | neutral unknown indicator with `?` icon/text | green/success styling | progression-sensitive actions blocked unless Core explicitly permits |
| NOT_RUN | required execution has not run | muted `NOT_RUN` plus cause/context when available | PASS-like checkmark | no promotion inference |
| INCONCLUSIVE | execution occurred but did not resolve verdict | warning/neutral with explanation | FAIL or PASS coercion | follow Core unresolved requirements |
| HOLD | progression intentionally held | explicit HOLD with blocking reason | generic pending spinner | only allowed corrective/navigation actions |
| STALE | displayed projection/evidence is older than authoritative dependency/revision | warning indicator and source revision context | current/fresh styling | critical decisions blocked unless Core allows stale read |
| CORE_UNAVAILABLE | authoritative Core read failed | prominent service-unavailable banner; previously cached data clearly non-authoritative | silent cache fallback presented as current | retry/read-only diagnostics |
| UNAUTHORIZED | principal lacks resource/action authority | non-disclosing access message | revealing hidden resource existence | no protected action |
| POLICY_BLOCKED | resource visible but action denied by policy/SoD/precondition | explain policy class/reason allowed by Core | generic permission error | corrective path only |
| NONFINAL | independent/final gate not completed | persistent but non-noisy global context marker | production-ready or final-success language | read/allowed workflow only |
| FAILED | authoritative verification/result failure | critical status with failure source | HOLD or UNKNOWN ambiguity | remediation/review paths |
| PASSED_BOUNDED | a specific bounded check passed | label result scope explicitly, e.g. `Runtime verification: PASSED` | standalone global `PASS` | drill-down to evidence |

## 3. Anti-false-pass rules

1. `0` is never a substitute for `NOT_AVAILABLE`, `UNKNOWN`, `NOT_RUN` or authorization failure.
2. A successful HTTP request is not an assurance success state.
3. A technical health status (`UP`) is never displayed as assurance success.
4. Bounded PASS always names its subject and verification type.
5. Project/portfolio screens MUST NOT synthesize an assurance percentage unless such a metric becomes an explicit authoritative Core object in a future normative requirement.
6. Cached or projected data MUST carry authority/revision/freshness context when it can diverge from Core.

## 4. P01 Assurance Workspace states

### NORMAL_KNOWN
Show only the compact decision strip:
- Current Assurance
- Progression
- Blocking count or blocking presence
- First unresolved requirement supplied by Core

### LOADING
Show layout skeletons only. Do not render example states such as TESTED or EVIDENCED.

### CORE_UNAVAILABLE
Replace the decision strip with:
- `Core state unavailable`
- last successful read time if known
- current displayed values suppressed unless explicitly marked cached/non-authoritative

### NONFINAL
One global marker is enough. Do not repeat `NONFINAL` on every card.

## 5. P02 Project / Target states

Project-level screen MUST NOT derive a single assurance state from target rows unless Core has an explicit ProjectAssuranceSnapshot.

Allowed project summaries are factual counts/distributions returned by Core. If the distribution is unavailable, show `NOT_AVAILABLE`, not zeros.

Target rows support canonical states plus progression state. A target with `UNKNOWN` must not inherit a sibling target's known state or project summary.

## 6. P03 Target Detail states

The page must preserve independent axes:
- `canonicalState`
- `progressionStatus`
- `blockingConditions`
- `unresolvedRequirements`
- `evidenceFreshness`

`Why this state?` is rendered only from Core-supplied satisfied/unresolved requirements and blocking conditions. Web code must not reconstruct domain policy.

## 7. P04 Evidence Receipt states

Evidence receipt UI distinguishes:
- receipt existence
- bounded verification result
- freshness
- lineage availability
- independent review/approval presence

A receipt can be valid evidence for a bounded verification while the target remains NONFINAL or BLOCKED. The UI must make this distinction visible.

## 8. Decision safety states for future write surfaces

When write/approval surfaces are introduced:
- `UNAUTHORIZED` and `POLICY_BLOCKED` must remain separate.
- stale/revision-mismatch review MUST require re-read before committing a decision.
- disabled critical actions require an explainable reason from policy/Core, not a Web-invented reason.
- FinalLock, Production GO and Commercial GO are separate decision domains.

## 9. Design gate

This document authorizes no implementation completion claim. The UI State Matrix is a required review input for Visual Baseline Lock and later W12 negative testing.
