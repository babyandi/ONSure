# ONSure Enterprise Web — 3s / 10s / 30s Structural-Blind UX Review

- Status: STRUCTURAL_BLIND_SELF_REVIEW_NONFINAL
- Scope: P01~P05 rendered mockups
- Important limitation: this review is structurally blind to implementation/backend behavior, but it is NOT an independent external-human or fresh-process blind review. It therefore cannot lock the visual baseline by itself.

## 1. Questions

### 3-second gate
A viewer should identify:
1. what object/context is being viewed,
2. the primary current state/result,
3. whether progression is normal or blocked/nonfinal.

### 10-second gate
A viewer should identify:
1. why the state exists,
2. the main blocker/unresolved item,
3. what evidence/relationship supports the visible statement.

### 30-second gate
A viewer should be able to trace the visible judgment to evidence/context without needing domain-rule inference from the Web UI.

## 2. P01 Assurance Workspace

3s: PASS_CANDIDATE
- `Assurance Workspace`, `TESTED`, `BLOCKED`, `Independent evidence` form a clear first-scan path.
- NONFINAL is visible before the state cards.

10s: PASS_CANDIDATE
- Needs Attention exposes missing independent evidence and stale evidence.
- Important Changes is clearly secondary.

30s: CONDITIONAL
- the page itself does not show the evidence lineage, which is appropriate for an overview.
- each attention item must link to a mapped authoritative object when implemented.
- production use requires Core-provided `AttentionItem[]`; local synthesis is rejected.

## 3. P02 Project / Target

3s: PASS_CANDIDATE
- Project context and target distribution are obvious.
- no Project assurance score is present.

10s: PASS_CANDIDATE after refinement
- `Targets independently verified` now reads as a count of Targets rather than a Project-level judgment.
- the table separates Assurance and Progression.

30s: PASS_CANDIDATE
- per-target evidence count and last-verified time provide navigation cues.
- evidence count must never be interpreted as evidence completeness.

## 4. P03 Target Detail

3s: PASS_CANDIDATE
- object: Payment API
- current state: TESTED
- progression: BLOCKED
- freshness: STALE

10s: PASS_CANDIDATE
- `Why this state?` and `Blocking Conditions` make the reason visible without opening another screen.
- satisfied vs unresolved requirements are clearly separated.

30s: PASS_CANDIDATE after density refinement
- the first Evidence row is visible in the 1366×768 first viewport.
- bounded result labels include the verification scope.
- full evidence list may scroll.

Primary risk retained: `Why this state?` will become false authority if the Web constructs those rows. Only Core-provided requirements/blockers are permitted.

## 5. P04 Evidence Receipt

3s: PASS_CANDIDATE
- object identity and `Runtime verification: PASSED` are clear.
- bounded-result wording prevents a product-level PASS reading.

10s: PASS_CANDIDATE
- subject, supported state, timestamp, source and receipt hash are visible.
- Trace shows how the receipt relates to Project/Target/Verification.

30s: PASS_CANDIDATE
- relationship completeness is explicit before `Decision not present` is interpreted as authoritative absence.
- technical details remain reachable below the fold rather than competing with the summary.

## 6. P05 State Gallery

3s: PASS for review purpose
- the page purpose is explicit: compare exception states and prevent success-like rendering.

10s: PASS for review purpose
- UNKNOWN, NOT_RUN, INCONCLUSIVE, HOLD, STALE and FAILED are visually distinguishable by label and semantics, not color alone.

30s: PASS for review purpose
- rules underneath each state explain prohibited conflations.
- gallery scroll is intentional and is not a production-screen navigation pattern.

## 7. Blind-failure questions

The following questions were checked against the rendered first views:

- Could TESTED be mistaken for Final/Production GO? Controlled by NONFINAL and separate progression semantics.
- Could BLOCKED be mistaken for failed assurance state? Controlled by separate Assurance vs Progression fields.
- Could `PASSED` be mistaken for overall product PASS? Corrected to bounded verification labels.
- Could Project distribution be mistaken for a Project score? Corrected label; no aggregate score.
- Could STALE be derived locally? Mockup refined to display direct target freshness state only.
- Could no approval be inferred from missing data? Contract now requires relationship completeness.

## 8. Result

`3_10_30_STRUCTURAL_BLIND_SELF_REVIEW = PASS_CANDIDATE_NONFINAL`

Not sufficient for `VISUAL_LOCKED`. A fresh independent reviewer or approved human design review remains a separate gate.
