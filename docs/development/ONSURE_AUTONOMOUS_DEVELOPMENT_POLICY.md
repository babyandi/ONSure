# ONSure Autonomous Development Policy

Status: `DEVELOPMENT_EXECUTION_POLICY / NON_FINAL`

## Objective
Claude development must continue without stopping for routine implementation choices, reversible remediation, coverage closure, or test failures. Checkpoints are recorded, not used as approval pauses.

Default rule: **decide and continue** using canonical design/policy authority. Ask the user only for a true stop condition defined below.

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
- post-remediation revalidation and new evidence epoch creation.

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
Do not stop for compile failures, unit/integration failures, fixture/schema failures, migration-test failures, P1/P2 contradictions, trace orphans, coverage gaps, stale docs, or reversible configuration mismatches. Diagnose, fix, rerun, update evidence/registry, continue.

## True stop conditions
Ask the user only when at least one is required to continue:
1. irreversible data deletion or unrecoverable change;
2. external purchase/contract/payment with real cost;
3. actual Production GO, Commercial GO, or Final authority approval;
4. two or more canonical authorities conflict with no defined precedence;
5. security posture must be weakened to continue;
6. new privilege/secret/credential scope expansion is required;
7. a new legal/regulatory policy decision is required.

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

Each item must follow:
`Design/Authority -> Contract -> Implementation -> Positive/Negative/Cross-contract Test -> Runtime Evidence -> Trace -> Registry`.

Do not ask “next?” or “apply this?” at checkpoints. Record what was chosen, why, evidence produced, and rollback path, then continue. Stop only at a true stop condition or after Global Lock preflight is complete.

This policy does not authorize Production GO, Commercial GO, FinalApproval, FinalLock, credential expansion, or irreversible destructive actions.
