# ONSure Enterprise Web W12 — AutoPilot Live Runtime Handoff

Status: `READY_FOR_LIVE_RUNTIME_REBIND_NONFINAL`

This document is the ONSure-side handoff for the next authoritative AutoPilot attempt. It does not modify AutoPilot and does not claim execution.

## Current ONSure subject selection rule

- repository: `babyandi/ONSure`
- PR: #95
- branch: `feature/onsure-enterprise-web-springboot`
- source SHA: resolve the exact current PR #95 HEAD immediately before Project Context CAS / ProjectAdmission and freeze that value for the new lineage

Do not embed a self-referential candidate SHA in this document. If #95 moves after admission, the admitted lineage is stale and a new exact source identity is required. Never reuse a receipt from an older SHA.

## Do not use the active developer workspace as the subject

Reported active `/workspace/ONSure` state differs from the earlier frozen SHA and contains a `.gitignore` modification. Do not reset it.

Create a detached isolated worktree at the exact admitted SHA. The isolated worktree must be clean and its path must become part of the execution/receipt identity.

## Live Runtime prerequisites

Before W12 execution, prove through live Runtime readback:

- AutoPilot Java Runtime liveness is UP;
- Project Context contains the ONSure W12 context at a non-stale revision;
- ProjectAdmission exists for `ONSURE/ENTERPRISE_WEB_W12`;
- MissionSpec exists in live Runtime and reads back with exact mission ID/version/spec digest;
- every required Stage adapter resolves to an implemented executor;
- unsupported adapters are HOLD, not delegated to Python;
- `MANUAL_RUN_BEGIN` occurs only after context and admission are durable.

## Required order

```text
RESOLVE_EXACT_PR95_HEAD
→ ISOLATED_WORKTREE_CREATE
→ SOURCE_IDENTITY_READBACK
→ PROJECT_CONTEXT_CAS
→ PROJECT_CONTEXT_READBACK
→ PROJECT_ADMISSION
→ PROJECT_ADMISSION_READBACK
→ MISSION_SPEC_ADMISSION
→ MISSION_SPEC_READBACK
→ ADAPTER_SUPPORT_READBACK
→ MANUAL_RUN_BEGIN
→ JAVA_STAGE_EXECUTION
→ DURABLE_RECEIPT_READBACK
```

## Logical W12 stages

```text
G0 exact source/worktree identity
G1 onsure-core install + Web package
G2 unit/MVC/security/Core-read tests
G3 static authority contract
G4 real Spring Boot runtime
G5 health/auth
G6 browser security negative checks
G7 Core Project/Target/Evidence read contract
G8 isolated Clean Run A
G9 isolated Clean Run B, same source SHA
G10 durable receipt and reproducibility comparison
```

The logical stages do not authorize a Python executor. A Stage that cannot execute in the admitted durable Runtime is HOLD/NOT_RUN.

## Two-clean requirements

Run A and Run B must have distinct:

- isolated worktree paths;
- Maven local repository paths;
- build output directories/process state;
- runtime process identities;
- receipt identities.

They must share the same admitted:

- ONSure source SHA;
- applicable policy digest;
- applicable execution/configuration identities.

## G10 minimum actual comparisons

G10 must read durable state and compare values. It may not emit pre-filled equality booleans.

Required checks:

- admitted source SHA = Run A source SHA = Run B source SHA;
- policy/config digest equality where required;
- Mission ID/version/spec digest lineage;
- Project Context revision/ProjectAdmission linkage;
- all required Stage receipts exist durably;
- parent receipt chains are complete;
- no unsupported adapter, UNKNOWN, NOT_RUN, INCONCLUSIVE, or HOLD is hidden;
- Run A and B have separate isolation identities.

## Evidence authority

Authoritative:

- live AutoPilot Java Runtime state and durable PostgreSQL receipt lineage.

Non-authoritative diagnostics:

- Python-local JSON receipt;
- stdout success;
- GitHub file presence;
- Draft PR #288 presence;
- MissionSpec JSON without live admission;
- MissionSpec admission without execution;
- historical GitHub Actions output.

## Expected initial outcome

Given the reported live Runtime state, the first correct attempt may legitimately stop at Project Context, ProjectAdmission, Mission admission, or unsupported adapter HOLD. That is preferable to a false W12 pass.

Until the full durable chain exists:

`W12 = NOT_RUN / HOLD_NONFINAL`
