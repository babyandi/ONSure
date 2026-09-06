# ONSure Autopilot Parallel Execution Runbook

Status: `EXECUTION_RUNBOOK / NON_FINAL`

## Objective
Run ONSure design closure continuously under babyandi/Autopilot without using raw orphan-count reduction as the completion condition.

## Wave 0 — Freeze snapshot
1. Read current main and Requirement Authority inputs.
2. Produce one denominator snapshot and digest for the wave.
3. Do not let workers independently mutate the global denominator.

## Initial 3-agent schedule
Slot 1: `B_CANDIDATE_TRIAGE` shard 1 (disjoint candidate IDs).
Slot 2: `C_DESIGN_MISSING_CLOSURE` shard 1 (disjoint capability/domain).
Slot 3: alternate `D_EXISTING_SEMANTIC_AUDIT` and `E_FR_FIN_MAPPING` based on READY work.

`A_AUTHORITY_DENOMINATOR` and `I_INTEGRATION` are single-writer coordination lanes and are never executed concurrently with another writer touching their owned files.

## Worker contract
Each write worker gets:
- the exact same wave base SHA;
- a dedicated Git worktree and branch;
- explicit Requirement IDs/capability ownership;
- explicit allowed output paths;
- no permission to edit global denominator/coverage/trace registries.

Each worker returns:
- reviewed IDs;
- design/disposition decisions with source authority;
- changed files;
- local validation result;
- unresolved conflicts/blockers;
- commit SHA.

A worker must fail closed if it encounters an ID/path assigned to another active worker.

## Integration
The integration lane cherry-picks only worker commits that satisfy the worker contract. After every integration wave it reruns authority, denominator, trace, coverage, semantic-narrowing and reverse-orphan scanners. A merge conflict or ownership conflict is not auto-resolved by choosing one worker; the conflicting item is parked and independent work continues.

## Work-conserving behavior
External review, PR merge, authority conflict, or a single blocked Requirement does not stop the mission. Park the dependent item, mark its dependency, and fill the freed worker slot with the next READY shard. Stop the whole mission only when no executable work remains outside true stop conditions.

## Scaling
Start with 3 concurrent write agents because Autopilot has server-local evidence for three isolated parallel agents. Increase concurrency only after two clean integration waves with zero ownership conflicts and acceptable server resource pressure. Prefer 3 stable workers over high concurrency that causes merge/revalidation churn.

## Completion
Do not claim `DESIGN_CLOSED` from document count, orphan reduction, test count, or worker completion. Claim candidate closure only when the active authoritative Requirement Universe reports: candidate triage 100%, missing design 0, partial/semantic narrowing 0, design mapped 100%, semantic coverage 100%, contracted 100%, unreviewed 0, unresolved P0 authority/design conflicts 0, and reproducible CLEAN rerun with the same denominator/coverage digest.

Any authority or denominator change invalidates the previous closure candidate and requires requalification.
