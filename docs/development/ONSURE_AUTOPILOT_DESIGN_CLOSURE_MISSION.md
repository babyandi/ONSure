# ONSure Autopilot Design Closure Mission

Status: `MISSION_SPEC / NON_FINAL`

## Goal
Close the authoritative ONSure Requirement Universe at the design level without using raw orphan-count reduction as the completion criterion.

Completion is allowed only when the active Requirement denominator is exact and digest-bound and all active requirements satisfy the required design closure state.

## Completion conditions
- authoritative Requirement Universe exact population and digest fixed;
- candidate-extracted rows triaged 100%;
- Missing Design = 0;
- Partial/Semantically Narrowed Design = 0;
- Design Mapped = 100%;
- Semantically Covered = 100%;
- Contracted = 100%;
- unresolved P0 authority conflict = 0;
- unresolved P0 design contradiction = 0;
- reverse orphan = 0 or authoritative disposition;
- clean independent rerun produces the same denominator/coverage result;
- no prior DESIGN_CLOSED claim survives automatically after authority/denominator change.

## Design lifecycle
`DESIGN_DRAFT -> DESIGN_MAPPED -> DESIGN_SEMANTICALLY_COVERED -> DESIGN_CONTRACTED -> DESIGN_TRACE_COMPLETE -> DESIGN_CLOSED`

`DESIGN_CLOSED` requires 100% coverage against the active denominator. Any authority or denominator change causes `STALE_REQUALIFICATION_REQUIRED` until rerun.

## Work queues
1. Authority and denominator requalification.
2. Candidate-extracted requirement triage.
3. Design-missing closure.
4. Existing non-orphan semantic closure audit.
5. FR-FIN-01~22 goal-to-design decomposition and mapping.
6. Cross-capability contradiction and reverse-orphan scan.
7. Independent clean closure rerun.

## Parallel execution policy
Autopilot may execute independent lanes concurrently using isolated Git worktrees. Parallelism is permitted only when write scopes do not overlap or an explicit integration owner exists.

### Lane A — Authority / Denominator
Owns authority manifest, requirement epoch, denominator receipt and candidate disposition registry. No other lane may modify these authoritative files concurrently.

### Lane B — Candidate Triage
Partitions candidate-extracted rows into non-overlapping ID/range shards. Each row must receive one disposition: `VALID_REQUIREMENT`, `DUPLICATE`, `DERIVED`, `NON_REQUIREMENT`, `SUPERSEDED`, `CONFLICT`, or `NEEDS_AUTHORITY_DECISION`. This lane does not alter the active denominator directly; Lane A integrates approved dispositions.

### Lane C — Design-Missing Closure
Partitions valid design-missing requirements by capability/domain. For every requirement, produces design at least through Capability, Component, Operation/API, State/Lifecycle, Data/Schema, Authority/RBAC/SoD, Policy, Failure/Recovery, Evidence and Acceptance/Negative Gate.

### Lane D — Existing Closure Semantic Audit
Rechecks requirements currently reported non-orphan. Detects checker-only false closure, semantic narrowing, caller-supplied representation substituted for live workflow/functionality, stale design, weak evidence and missing runtime connection.

### Lane E — Final Target / FR-FIN Mapping
Maps FR-FIN-01~22 into granular design requirements using `REFINES`, `DECOMPOSES`, `SATISFIES`, `OVERLAPS` and `CONFLICTS`; does not silently delete overlapping granular requirements.

### Lane V — Independent Verifier
Read-only over developer lanes until integration. Validates semantic coverage, duplicate/conflict dispositions, write-scope isolation and completion claims. It must not author the artifacts it approves.

### Integration lane
Only the integration owner may update shared denominator/coverage/lock registries after lane outputs pass review. Merge/cherry-pick conflicts fail closed; no automatic conflict resolution by choosing one side.

## Parallel safety rules
- each write-capable lane gets a dedicated worktree and branch from the same frozen base SHA;
- each assignment declares exclusive path/requirement ownership;
- overlapping ownership is rejected before execution;
- shared registry files are integration-lane-only;
- cross-lane dependencies are represented explicitly; blocked work is parked while independent lanes continue;
- each lane emits commit, changed-file list, requirement IDs, evidence and limitations;
- integration runs global trace/coverage/contradiction tests after every batch of lane commits;
- independent verifier reruns after integration;
- test counts alone are never closure evidence.

## Scheduling recommendation
Start with Lane A plus read-only inventory work in D/E. After the denominator snapshot is frozen, execute B/C/D/E in parallel. Lane C is horizontally sharded by capability/domain; Lane B by candidate ranges. Keep maximum concurrent write agents conservative initially (3) because Autopilot has an evidenced three-agent isolated-worktree parallel gate; increase only after resource/merge-conflict evidence supports it.

## Stop/parking policy
A blocked dependency (review, authority decision, external gate) parks only dependent assignments. The mission stops only when no runnable assignment remains or a true irreversible/security/authority stop condition applies.

## Forbidden shortcuts
- orphan-count reduction as the completion metric;
- file existence as Design coverage;
- checker-only implementation treated as full product-function closure;
- automatically accepting candidate-extracted text as normative requirement;
- longest-text-wins canonicalization;
- parallel agents editing the same normative file without an integration owner;
- stale DESIGN_CLOSED after authority/denominator change.

## Final mission result
The highest automatic result before independent lock qualification is `DESIGN_CLOSURE_CANDIDATE / NON_FINAL`. Production GO, Commercial GO, FinalApproval and FinalLock are outside this mission.