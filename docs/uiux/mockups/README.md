# ONSure Enterprise Web UI/UX Mockups

Status: DESIGN_MOCKUP_NONFINAL

These assets are static design artifacts only. They are not implementation, runtime, test, assurance, or release evidence.

Authoritative domain truth remains in ONSure Core. Mockups MUST NOT invent assurance state, PASS counts, portfolio scores, blockers, approvals, evidence, or next actions. All displayed sample values are explicitly marked SAMPLE / NONFINAL.

## Included
- P01_ASSURANCE_WORKSPACE.html
- P02_PROJECT_TARGET.html
- P03_TARGET_DETAIL.html
- P04_EVIDENCE_RECEIPT.html
- P05_UI_STATE_GALLERY.html
- onsure-ui-mockup.css
- NEGATIVE_DESIGN_REVIEW.md

## Design grammar
Context → State → Why → Blocker → Evidence → Next unresolved requirement.

## Detailed design contracts
- `docs/51_ONSURE_ENTERPRISE_WEB_UI_STATE_MATRIX.md`
- `docs/52_ONSURE_ENTERPRISE_WEB_CORE_UI_FIELD_MAPPING.md`
- `docs/53_ONSURE_ENTERPRISE_WEB_VISUAL_REFINEMENT_GATE.md`

## Non-goals
- no production JavaScript
- no backend calls
- no write actions
- no FinalLock / Production GO / Commercial GO controls
- no GitHub Actions dependency

## Current visual decision
P01~P05 are review assets only. Visual baseline remains NOT_LOCKED until exceptional-state review, Core field traceability review, 1366×768 render/readback and independent visual review are completed.
