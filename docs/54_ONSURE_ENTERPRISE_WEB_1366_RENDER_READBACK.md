# ONSure Enterprise Web — 1366×768 Render / Read-back Review

- Status: RENDER_REVIEW_COMPLETE_NONFINAL
- Scope: P01~P05 static design mockups
- Viewport: 1366×768
- Method: local headless Chromium render of the repository HTML/CSS design assets; no backend, no Core data, no GitHub Actions
- Authority: design QA only; not implementation/runtime/assurance evidence

## 1. Gate

The first viewport must answer the primary screen question without horizontal scroll or fake data. Detail pages may require vertical scroll, but the first viewport must expose the judgment context and at least the first relevant evidence/trace entry where that is central to the page.

## 2. Results

### P01 Assurance Workspace
- document height at 1366×768: 768px
- horizontal overflow: none
- first viewport: PASS for design-readability
- Current Assurance, Progression and Next unresolved requirement are simultaneously visible.
- Needs Attention and Important Changes are visible without scroll.
- NONFINAL/SAMPLE context is visible but does not dominate the page.

Negative note: the three summary cards can still drift toward KPI-dashboard behavior if more cards are added. Maximum three is retained for this screen.

### P02 Project / Target
- document height: 768px
- horizontal overflow: none
- first viewport: PASS for design-readability
- Target count, blocked-target count, target-level independent-verification distribution and the first target rows are visible together.
- Project-level average/score is absent.

Refinement applied: `Independent verified` was renamed to `Targets independently verified` so the metric cannot be misread as a Project-level assurance judgment.

### P03 Target Detail
Initial render:
- document height: approximately 934px
- first evidence rows were below the first viewport.
- result: FAIL for the target-detail first-view density criterion.

Refinement:
- reduced page/panel vertical spacing only on P03
- removed locally implied `1 STALE` aggregate and changed it to Core-provided target freshness state `STALE`
- bounded results changed to `Runtime: PASSED` and `Security: PASSED`

Re-render:
- document height: approximately 864px
- first evidence row is visible in the 1366×768 first viewport.
- State, Progression, Freshness, Why, Blocking Conditions and first Evidence are visible in one initial decision context.
- result after refinement: PASS_CANDIDATE for visual density; full page still scrolls, intentionally.

### P04 Evidence Receipt
- document height: approximately 940px
- horizontal overflow: none
- first viewport shows Evidence Summary, bounded result, support relation, Target, execution timestamp, source/hash and complete initial Trace.
- lower Technical/Lineage details require scroll.
- result: PASS for detail-page behavior. Forcing the whole receipt into one viewport would reduce readability and is rejected.

Refinement applied:
- result label changed to `Runtime verification: PASSED`
- `Supports TESTED` is explicitly identified as a Core-provided relationship sample
- `Decision not present` now displays relationship-query completeness in the sample, preventing `[]` from being mistaken for authoritative absence.

### P05 UI State Gallery
- document height: approximately 1154px
- designed as a comparison gallery, not a one-decision production screen
- first viewport displays NORMAL_KNOWN, LOADING, EMPTY, UNKNOWN, NOT_RUN, INCONCLUSIVE, HOLD, STALE and FAILED comparisons without success-colored ambiguity.
- vertical scroll is expected to inspect the remaining states.
- result: PASS for design-review purpose; not subject to one-screen production-page density gate.

## 3. Cross-screen findings

PASS:
- no horizontal overflow at 1366px
- navigation remains stable
- state/progression are visually separate
- NONFINAL is present without full-page warning saturation
- neutral states are not rendered as success
- evidence IDs/hashes remain secondary to human-readable meaning

Rejected / controlled:
- adding a fourth P01/P02 metric card
- shrinking typography further merely to eliminate P03/P04 vertical scroll
- converting P04 to a dense single-screen receipt
- using unqualified `PASSED` as a product-level status

## 4. Result

`1366_RENDER_READBACK = PASS_CANDIDATE_NONFINAL`

This is visual QA only. It does not authorize Core-connected implementation and does not change W12 (`NOT_RUN`).
