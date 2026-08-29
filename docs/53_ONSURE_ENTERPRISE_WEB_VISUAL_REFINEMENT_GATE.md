# ONSure Enterprise Web — Visual Refinement Gate

- Status: VISUAL_REFINEMENT_NONFINAL
- Issue: #94
- Scope: P01~P04 static mockups
- Visual baseline lock: NOT_YET
- Implementation authorization: NO
- W12: NOT_RUN

## 1. Goal

This gate decides whether the P01~P04 mockups are coherent enough to become a future visual baseline candidate. It does not prove runtime behavior, Core integration, accessibility compliance or security.

## 2. 3-second / 10-second / 30-second comprehension model

### 3 seconds
The user should identify:
- current context
- current assurance state if authoritative
- whether progression is blocked/held/unknown

Anything else is secondary.

### 10 seconds
The user should understand:
- primary blocking reason or unresolved requirement
- whether evidence exists and whether its authority/freshness is known

### 30 seconds
The user should be able to:
- open the supporting evidence
- understand why the state is held/blocked
- identify the relevant authoritative object/revision

If a screen needs charts, 10 KPI cards or a legend to accomplish the 3-second task, the design is rejected.

## 3. Density limits

### Desktop target
Primary baseline: 1366×768 with graceful use of larger widths.

### Above-the-fold rules
- global header must remain compact
- no marketing hero on authenticated work surfaces
- at most one global NONFINAL banner
- primary decision strip maximum four semantic items
- the first blocking/unresolved information should be visible without scrolling on 768px height when practical

### Card/table balance
- compact summary may use cards/panels
- repeatable domain objects use list/table treatment
- nested card-on-card compositions are discouraged
- avoid pill badges for every value

## 4. Status-color discipline

Default surface is neutral.

Use semantic emphasis only for actual meaning:
- primary/current: restrained primary tone
- warning: HOLD/STALE/INCONCLUSIVE or attention conditions
- critical: authoritative failure or blocking critical condition
- unknown/not-run: neutral/muted, never positive green

Completed earlier assurance stages need not be green. A neutral check/icon is preferred to avoid turning the whole journey into a PASS banner.

## 5. Text hierarchy

Canonical identifiers remain visible but do not dominate human-readable labels.

Example:
- primary: `독립 검증 완료`
- secondary/canonical: `INDEPENDENTLY_VERIFIED`

IDs and hashes should be compact, copyable and visually secondary.

## 6. P01 refinement decision

Retain:
- compact context
- assurance/progression/blocking summary
- first unresolved requirement
- bounded attention list
- important changes only

Reduce/remove:
- decorative hero copy
- portfolio score
- excessive counts
- full 8-stage journey as a permanent dominant element
- raw audit stream

Full assurance path is secondary disclosure, not the home page's permanent visual center.

## 7. P02 refinement decision

Project screen is an inventory/navigation surface first.

Retain:
- project identity
- factual target counts/distribution if authoritative
- target table/list
- explicit state + progression columns

Reject:
- project-wide derived score
- averaged assurance state
- charts that merely restate a small target table
- excessive filters before real scale requires them

## 8. P03 refinement decision

Target Detail is the strongest domain screen and should establish the product DNA.

Visual order:
1. context and target identity
2. current assurance + progression
3. why/blocking/unresolved requirements
4. supporting evidence
5. bounded history/activity

Do not place a full technical receipt dump above blocking reasons.

## 9. P04 refinement decision

Evidence Receipt must feel precise rather than decorative.

Preferred treatment:
- receipt identity header
- bounded result clearly scoped
- source/environment/integrity groups
- relationships
- technical details collapsed or lower in hierarchy

Lineage defaults to bounded trace/list. Full graph is optional advanced exploration and is not part of the initial baseline.

## 10. Negative design checks

Reject a candidate mockup when any of these occur:
1. sample data can plausibly be mistaken for authoritative production data
2. `PASS` appears without a bounded subject
3. state and progression are visually collapsed
4. `UNKNOWN`, `NOT_RUN`, `INCONCLUSIVE`, `HOLD` share success styling
5. project-level state is visually synthesized from target rows
6. Core unavailable state silently displays cached data as current
7. user cannot locate evidence from a decision/state within a short drill-down
8. visual density hides the first blocker below decorative content
9. color is the only status channel
10. unauthorized and policy-blocked are represented identically
11. disabled critical action has no explainable reason path
12. page navigation/context can be lost when drilling into evidence
13. raw hash/ID noise overwhelms human-readable meaning
14. too many global navigation destinations are enabled before their workflows exist
15. Web-generated recommendation is presented as authoritative requirement

## 11. Current assessment

P01~P04 have sufficient structure to continue refinement, but the visual baseline is not locked because:
- real Core data cardinality has not been exercised
- exceptional state variants are not yet rendered as full mockups
- no independent visual/readability review has been executed
- no 1366×768 render/readback evidence has been captured
- W12 remains NOT_RUN

Decision: `CONTINUE_VISUAL_REFINEMENT_NONFINAL`.
