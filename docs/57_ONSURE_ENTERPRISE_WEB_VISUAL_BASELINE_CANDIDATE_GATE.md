# ONSure Enterprise Web — Visual Baseline Candidate Gate

- Status: VISUAL_BASELINE_CANDIDATE_NONFINAL
- Issue: #94
- PR: #95
- Scope: P01~P05 UI/UX design assets
- W12: NOT_RUN
- Visual Lock: false

## 1. Completed gates

The following design gates have now been executed:

1. 1366×768 render/read-back review
2. 3s / 10s / 30s structural-blind self-review
3. P01~P04 Core field traceability audit

## 2. Defects found during the gates

The gates were not rubber stamps. They found and corrected:

- P03 first-view density pushed all Evidence rows below the initial viewport.
- P03 `1 STALE` implied a Web-computed aggregate absent from Core contract.
- P03 unqualified `PASSED` labels could be read too broadly.
- P02 `Independent verified` could be misread as a Project-level assurance judgment.
- P02 `Last verified` lacked an explicit TargetSummary field.
- P01 `Needs Attention` lacked a dedicated authoritative projection contract.
- P04 `Supports TESTED` lacked an explicit Core relationship field.
- P04 `Decision not present` could incorrectly infer authoritative absence from an empty/missing relationship list.

The mockups and Core→UI mapping were refined to address these defects.

## 3. Candidate design grammar

The baseline candidate remains:

`Context → State → Why → Blocker → Evidence → Next unresolved requirement`

Constraints:
- Assurance and Progression remain separate.
- PASS is always bounded to a verification subject/type.
- NONFINAL/UNKNOWN/NOT_RUN/HOLD never acquire success semantics.
- Project pages show distributions/counts, not synthesized assurance scores.
- Web does not calculate freshness, blockers, current state, approval eligibility or readiness.
- detail pages may scroll; primary judgment must not be buried below the first viewport.

## 4. Current screen status

- P01 Assurance Workspace: VISUAL_BASELINE_CANDIDATE
- P02 Project / Target: VISUAL_BASELINE_CANDIDATE
- P03 Target Detail: VISUAL_BASELINE_CANDIDATE_AFTER_REFINEMENT
- P04 Evidence Receipt: VISUAL_BASELINE_CANDIDATE_AFTER_REFINEMENT
- P05 UI State Gallery: DESIGN_REVIEW_BASELINE_CANDIDATE

## 5. Why Visual Lock is still false

The design has passed internal render/read-back and structural self-review, but the blind review was not performed by a genuinely independent fresh reviewer/human process. Locking now would overstate the evidence.

Therefore:

`VISUAL_LOCK = false`

Remaining lock condition:
- approved human design review OR genuinely independent fresh visual review of the candidate mockups with no prior conclusion source.

## 6. Implementation gate

Core-connected implementation may begin only against the documented read-model contract and must preserve unavailable/unknown semantics. However, candidate visual geometry should not be described as locked until the independent/human visual gate above is complete.

Implementation must not copy SAMPLE values or mockup-derived business logic.

## 7. Assurance statement

`P01_P05_DESIGN = VISUAL_BASELINE_CANDIDATE_NONFINAL`

`P01_P04_CORE_FIELD_TRACEABILITY = DESIGN_MAPPED`

`W12 = NOT_RUN`

`FinalLock = false`

`Production GO = false`

`Commercial GO = false`
