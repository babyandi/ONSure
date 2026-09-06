# ONSure Enterprise Web W12 — AutoPilot Live Runtime Execution Runbook

- Status: `LIVE_RUNTIME_REBIND_REQUIRED_NONFINAL`
- Issue: #94
- ONSure Draft PR: #95
- GitHub Actions: NOT USED
- Python subprocess orchestrator: NOT AUTHORITATIVE
- Authoritative execution: AutoPilot live Java Runtime + durable PostgreSQL lineage

## Purpose

Validate one exact ONSure Web source SHA through the live AutoPilot Runtime without mutating the active developer workspace and without allowing a sidecar Python process to become a hidden execution authority.

## Preconditions

The following must be true before `MANUAL_RUN_BEGIN`:

1. AutoPilot Java liveness is `UP` on the actual live Runtime endpoint.
2. Java 17 and Maven are available to the admitted executor.
3. An isolated ONSure worktree exists at the exact validation SHA.
4. The isolated worktree is clean.
5. Active `/workspace/ONSure` is not reset or reused as the validation subject.
6. Project Context CAS has installed the intended context and returned a non-stale revision.
7. `ONSURE/ENTERPRISE_WEB_W12` ProjectAdmission exists in the live Runtime.
8. MissionSpec is admitted and reads back from the live Runtime.
9. Every required Stage `adapter_id` resolves to an implemented live executor.
10. Unsupported adapters remain HOLD and are not substituted by Python subprocesses.

Reported live API port is `8781`; clients must use discovered/runtime-configured endpoint data rather than a hard-coded `8080` default.

## Authoritative sequence

```text
A. Resolve exact current PR #95 HEAD
B. Create isolated validation worktree at that exact SHA
C. Verify HEAD and clean status
D. PROJECT_CONTEXT_CAS
E. Read back context revision/content
F. PROJECT_ADMISSION
G. Read back ProjectAdmission
H. MISSION_SPEC_ADMISSION
I. Read back exact mission ID/version/spec digest
J. Resolve every Stage adapter against live executor registry
K. MANUAL_RUN_BEGIN
L. Java Stage execution and durable receipt persistence
M. Durable mission/receipt readback
N. Clean Run A
O. Clean Run B with independent worktree/Maven repo
P. G10 actual lineage comparison
```

If any step D through K is missing, W12 remains `NOT_RUN` or HOLD. Do not fall back to `subprocess.run()` as an alternate execution path.

## Isolated source subject

Example shape, not a literal mandatory path:

```bash
git -C /workspace/ONSure worktree add --detach \
  /workspace/validation/onsure-web-w12/<RUN_ID>/source \
  <EXACT_SHA>
```

The validator then proves:

```bash
git -C <ISOLATED_WORKTREE> rev-parse HEAD
git -C <ISOLATED_WORKTREE> status --porcelain
```

Expected:

- HEAD exactly equals admitted source SHA;
- status output is empty.

The active developer workspace may be on another SHA and may contain local modifications. That state must not contaminate or be destroyed by W12.

## Project Context / admission order

Required contract:

```text
PROJECT_CONTEXT_CAS(expected_revision=N)
  → PROJECT_CONTEXT_READBACK(revision=N+1)
  → PROJECT_ADMISSION(project_id=ONSURE/ENTERPRISE_WEB_W12)
  → PROJECT_ADMISSION_READBACK
  → MANUAL_RUN_BEGIN
```

CAS conflict, revision `0` with empty context when a populated context is required, missing ProjectAdmission, or stale admission => HOLD.

## Mission and adapter rules

`MISSION_NOT_FOUND` means the Mission is not admitted. A Mission JSON present in GitHub does not satisfy this condition.

Before execution, enumerate all required Stage adapters. For each:

```text
SUPPORTED_BY_LIVE_JAVA_EXECUTOR → eligible to execute
UNSUPPORTED / UNKNOWN           → HOLD_UNSUPPORTED_ADAPTER
```

No “adapter registered on paper” behavior is acceptable.

## W12 Stage semantics

Logical stages remain:

- G0 exact SHA / isolated worktree identity
- G1 Core install + Web package
- G2 unit/MVC/security/Core-read tests
- G3 static authority checks
- G4 actual Spring Boot runtime
- G5 health/authentication
- G6 CSP/frame/security negative checks
- G7 Core Project/Target/Evidence read contract and explicit Assurance availability
- G8 Clean Run A
- G9 Clean Run B on same SHA
- G10 durable readback/integration comparison

The logical graph is not itself an executor. Java Runtime Stage execution and durable receipts are required.

## Clean Run A / B isolation

Run A and Run B must not share build state that could create false reproducibility.

At minimum use distinct:

```text
worktree A != worktree B
Maven repo A != Maven repo B
target/build output A != target/build output B
runtime process A != runtime process B
receipt lineage A != receipt lineage B
```

For Maven, use a per-run repository such as:

```bash
mvn -Dmaven.repo.local=<RUN_SPECIFIC_M2> ...
```

Both runs must nevertheless use the same admitted source SHA and equivalent declared policy/config identities.

## G10 comparison

G10 computes, it does not assert.

Required comparison inputs include:

```text
runA.source_sha == runB.source_sha == admitted_source_sha
runA.policy_digest == runB.policy_digest
required receipt parent chains complete
required stage denominator complete
no unsupported adapter
no UNKNOWN / NOT_RUN / INCONCLUSIVE hidden
receipts durable and readable from AutoPilot state
```

A constant `same_sha: true` field has zero evidence value.

## Receipt authority

Accepted:

- Java Stage executor receipts persisted/read back through AutoPilot durable Runtime state.

Not accepted as W12 authority:

- Python-local receipt JSON;
- stdout-only success;
- MissionSpec file in Git;
- MissionSpec registration with no execution;
- manual shell success detached from AutoPilot receipt lineage;
- historical GitHub Actions output.

## Result interpretation

Before durable live Runtime execution:

`W12 = NOT_RUN`

After complete same-SHA two-clean live execution, maximum self-validation state:

`W12_SELF_VALIDATION_CLEAN_NONFINAL`

Independent verification, FinalLock, Production GO, and Commercial GO remain separate gates.
