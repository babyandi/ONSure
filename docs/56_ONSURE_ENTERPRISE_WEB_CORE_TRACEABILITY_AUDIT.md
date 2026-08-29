# ONSure Enterprise Web — P01~P04 Core Traceability Audit

- Status: TRACEABILITY_AUDIT_COMPLETE_NONFINAL
- Scope: every visible business field in P01~P04 mockups
- Rule: Web-derived business truth = 0
- W12: NOT_RUN

## 1. Audit method

Each visible business value was checked against `52_ONSURE_ENTERPRISE_WEB_CORE_UI_FIELD_MAPPING.md`.

Classification:
- MAPPED: direct Core/session/policy field exists.
- PRESENTATION_ONLY: safe Web formatting of an already-authoritative value.
- CONTRACT_GAP: visible sample value had no explicit authoritative field and required a contract amendment.
- FORBIDDEN_DERIVATION: value would require Web business-rule computation and must not be implemented.

## 2. P01 Assurance Workspace

MAPPED:
- tenant/project context
- authenticated role context
- Current Assurance
- Progression
- blocking conditions/count
- unresolved requirements
- NONFINAL/finality context

Contract gaps found and closed:
- `Needs Attention` rows were not previously backed by a dedicated read model. Added optional authoritative `AttentionItem[]` contract. Production UI must remain unavailable until Core supplies it.
- `Important Changes` already required an authoritative change/event projection; raw Audit dumping remains prohibited.

Result: TRACEABLE_CONDITIONAL. No production sample data may be substituted for missing Attention/change feeds.

## 3. P02 Project / Target

MAPPED:
- target count
- blocking-target count
- assurance distribution
- target canonical state
- target progression
- evidence receipt count
- target freshness where supplied

Contract gaps found and closed:
- `Last verified` had no field in TargetSummary. Added optional `TargetSummary.lastVerifiedAt`.
- `Independent verified 4` could be misread as Project state. Mapping now explicitly ties it to `assuranceDistribution[INDEPENDENTLY_VERIFIED]` or an equivalent Core-provided Target count; mockup label refined to `Targets independently verified`.

Forbidden derivations confirmed absent:
- Project average assurance
- assurance percentage/score
- local scan used as authoritative blocking-target count

Result: TRACEABLE after contract refinement.

## 4. P03 Target Detail

MAPPED:
- Target identity/type
- Current Assurance
- Progression
- Core revision
- satisfied requirements
- unresolved requirements
- blocking conditions
- supporting evidence
- bounded evidence result
- source identity

Gap / forbidden derivation found and corrected:
- mockup displayed `1 STALE`, which implied a local stale-evidence aggregate not present in the contract. Replaced with direct Core-provided Target freshness state `STALE`.
- evidence results were unqualified `PASSED`; changed to bounded `Runtime: PASSED` / `Security: PASSED` presentation.

Result: TRACEABLE after refinement.

## 5. P04 Evidence Receipt

MAPPED:
- evidence ID/type
- subject
- verification run
- bounded result
- source/environment
- execution time/executor
- receipt and parent hashes
- lineage/findings/approvals
- raw technical metadata
- authority/core revision

Contract gaps found and closed:
- `Supports TESTED` required an explicit relation. Added optional `EvidenceReceipt.supportsAssuranceState`.
- `Decision not present` cannot be inferred from an empty/missing list. Added `relationshipCompleteness` (or equivalent) requirement and refined sample wording.
- command/exit code are permitted only as explicit typed technical metadata, never inferred from logs.

Result: TRACEABLE after contract refinement.

## 6. Global forbidden-field scan

The P01~P04 design contract continues to prohibit unless Core later defines them explicitly:
- assuranceScore
- projectAssuranceAverage
- confidencePercent
- calculatedCurrentState
- calculatedBlocker
- recommendedNextAction
- calculatedFreshness
- approvalEligibility
- finalReady
- productionReady
- commercialReady

No such field is authorized by the current mockups.

## 7. Availability semantics audit

Implementation must preserve:
1. authoritative value,
2. authoritative empty/none,
3. unavailable/unknown/not authorized.

The audit rejects adapters that collapse these into a single `null`, `0`, or empty collection.

## 8. Result

`P01_P04_CORE_FIELD_TRACEABILITY = 100_PERCENT_DESIGN_MAPPED_WITH_CONTRACT_REFINEMENTS`

This means the visible design fields now have a documented authority source or an explicit unavailable behavior. It does NOT mean those Core read models already exist in executable code.
