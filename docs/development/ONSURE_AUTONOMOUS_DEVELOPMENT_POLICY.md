# ONSure Autonomous Development Policy

Status: `DEVELOPMENT_EXECUTION_POLICY / NON_FINAL`

## Objective
Claude development must continue without stopping for routine implementation choices, reversible remediation, coverage closure, test failures, or an externally blocked dependency that does not block all remaining work. Checkpoints are recorded, not used as approval pauses.

Default rule: **decide and continue** using canonical design/policy authority.

A blocked item is not the same as a blocked program. If one item cannot advance because of an external review, approval, unavailable authority decision, provider wait, or other dependency, park only that item and immediately select the next independent executable work item.

## Decision priority
When multiple compliant options exist, choose in this order:
`Safety > Correctness > Fail-Closed > Reconstructability > Evidence Quality > Backward Compatibility > Simplicity > Performance`.
If still tied, choose the option with smaller blast radius, easier rollback, stronger evidence, and fewer implicit assumptions.

## Autonomous scope
Proceed without asking for approval for:
- implementation design choices already constrained by canonical contracts/policies;
- schema/fixture/test fixes, refactoring, stale registry/doc corrections;
- P1/P2 defect closure;
- Requirement trace/evidence closure and reverse-orphan cleanup;
- negative/adversarial/recovery test additions;
- append-only ledger integrity/hash-chain/tamper-evidence hardening;
- shadow-write, dual-read, reconciliation, migration retry/rollback;
- reversible security remediation required by an existing canonical policy;
- post-remediation revalidation and new evidence epoch creation;
- analysis, mapping, fixtures, tests, migration preparation, and dry-run artifacts for a future authority epoch, provided they are clearly marked candidate/non-active until the authority transition is valid;
- work on independent queue items while another item waits on human review or another external dependency.

## Work-conserving blocked-dependency policy
The execution loop MUST remain work-conserving.

When an item is blocked:
1. record a `BLOCKED_DEPENDENCY` entry with blocker identity, affected scope, evidence, unblock condition, and resume action;
2. freeze only the authority-sensitive transition or destructive/privileged action that requires the blocker to clear;
3. preserve all prior evidence and current working state;
4. identify remaining tasks whose correctness does not depend on the unresolved blocker;
5. continue those tasks immediately in priority order;
6. periodically re-check the blocker at natural checkpoints, but do not busy-wait and do not stop unrelated work;
7. when the blocker clears, resume the parked item from its recorded checkpoint and requalify only the affected denominator/trace/evidence scope.

Examples:
- PR requires independent human review: park merge/cutover only; continue coverage closure, integrity hardening, tests, mappings, dry-runs, unrelated implementation and evidence work.
- Canonical authority conflict: park decisions and artifacts whose semantics depend on the unresolved precedence; continue independent requirements/contracts/tests and other queues.
- Provider/API unavailable: park provider-dependent execution; continue local/offline/testable work.

The loop may terminate for a blocker only when **all remaining executable work is transitively dependent on that blocker** or a true stop condition requires the user to make a decision before any safe progress is possible.

## Reversible operational changes
An operational/configuration change may be executed autonomously only when **all** are true:
1. canonical policy/contract requires the target state;
2. pre-change state is captured as evidence;
3. change is reversible;
4. no data deletion/loss occurs;
5. no privilege/credential scope expansion occurs;
6. it does not create Production/Commercial/Final authority;
7. rollback is defined and recorded.

For FR-COM-008 or equivalent controls, preserve the original FAIL evidence and record:
`FAIL evidence -> remediation receipt -> post-change observation -> re-test -> new PASS/FAIL evidence`.
Historical evidence must never be overwritten.

## Failures are not stop conditions
Do not stop for compile failures, unit/integration failures, fixture/schema failures, migration-test failures, P1/P2 contradictions, trace orphans, coverage gaps, stale docs, reversible configuration mismatches, or one externally blocked queue item. Diagnose/fix/rerun where possible; otherwise park the blocked item, update evidence/registry, and continue independent work.

## True stop conditions
A true stop condition applies to the specific affected work item when at least one is required to continue:
1. irreversible data deletion or unrecoverable change;
2. external purchase/contract/payment with real cost;
3. actual Production GO, Commercial GO, or Final authority approval;
4. two or more canonical authorities conflict with no defined precedence;
5. security posture must be weakened to continue;
6. new privilege/secret/credential scope expansion is required;
7. a new legal/regulatory policy decision is required.

Even when a true stop condition blocks one item, the overall development loop continues on independent executable work unless every remaining item is transitively blocked.

## Continuous execution sequence after Batch 0~9
Continue, without approval pauses, through:
1. Requirement Coverage Closure for remaining orphans;
2. orphan severity recalculation;
3. ledger tamper-evidence/hash-chain hardening;
4. Batch 5 shadow-write/dual-read completion;
5. Batch 8 live shadowing/reconciliation completion;
6. requirement-based Test Coverage Universe completion;
7. reverse-orphan closure;
8. unresolved P1 contradiction closure;
9. CLEAN deterministic reruns;
10. Global Design/Implementation Lock preflight.

If an earlier item is blocked by an external dependency, mark it parked and continue with the next independent item rather than terminating the loop.

Each item must follow:
`Design/Authority -> Contract -> Implementation -> Positive/Negative/Cross-contract Test -> Runtime Evidence -> Trace -> Registry`.

Do not ask “next?” or “apply this?” at checkpoints. Record what was chosen, why, evidence produced, blocker/resume condition where applicable, and rollback path, then continue.

Stop the overall loop only when Global Lock preflight is complete or no safe executable work remains because every remaining item is transitively blocked by one or more true stop conditions.

This policy does not authorize Production GO, Commercial GO, FinalApproval, FinalLock, credential expansion, branch-protection bypass, required-review bypass, or irreversible destructive actions.
