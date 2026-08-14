# ONSure QA Runtime Completion Handoff

Source QA branch: `qa/onsure-design-baseline-lock`
Source QA PR: #47

## Current QA facts to consume
- explicit Product Design Requirement candidate count: 103
  - FR-COM 13
  - FR-META 62
  - FR-FRESH 3
  - FR-LEARN 25
- FR-LEARN applicability: 21 APPLICABLE / 4 CONDITIONAL / 0 UNKNOWN
- FR-LEARN design trace: 25/25 mapped
- total explicit design trace candidate: 98/103; legacy 5 remain untraced
- global exact denominator remains HOLD until non-ID materialization completes

## Execute next
1. Replace all-markdown Requirement generator with Requirement Authority Manifest allowlist/fail-closed input.
2. Remove `longest-text-wins`; canonical text must follow explicit authority/refine/supersession.
3. Regenerate full Product Design RU including the six non-ID classes.
4. Recompute duplicate/semantic-variant disposition after authority filtering.
5. Generate exact Applicability 1:1 with RU and drive Critical UNKNOWN to zero or HOLD.
6. Re-run global forward/reverse trace, orphan and contradiction scans.
7. Materialize 149 learning/validation schema specification into 14 separate JSON Schema contracts and registry entries.
8. Add positive/schema-invalid/semantic-invalid fixtures for all 14 contract families and execute the 12 P0 cross-contract invariants.
9. Populate FR-LEARN-001~025 implementation trace and runtime evidence 25/25.
10. Generate full authoritative content SHA-256 manifest, registry digests, baseline manifest and CLEAN rerun reconstructability evidence.
11. Verify FR-COM-008 actual main branch protection against `contracts/main-branch-protection.v1.json` using an identity with branch-protection read permission.

## Completion gate
Do not claim Design Lock/Final/Production GO unless exact RU digest, applicability digest, trace/orphan/contradiction closure, content SHA-256, registry digests, reconstructability, FR-COM-008 control evidence, and FR-LEARN runtime evidence are all present.
