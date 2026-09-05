# ONSure Enterprise Web W12 — AutoPilot Live Runtime Binding Contract

- Status: `REBIND_REQUIRED_NONFINAL`
- ONSure issue: #94
- ONSure draft PR: #95
- AutoPilot repository: `babyandi/Autopilot`
- AutoPilot live Runtime authority: `/workspace/Autopilot` Java Runtime + durable PostgreSQL state
- GitHub Actions: NOT USED FOR EXECUTION OR VALIDATION
- Python subprocess orchestrator: NOT AN AUTHORITATIVE EXECUTION PATH
- W12: NOT_RUN until live Runtime ProjectAdmission, Manual Run execution, Java Stage receipts, and durable receipt lineage exist

## 1. Corrected authority boundary

GitHub stores source, Draft PRs, issues, contracts, and evidence references. It is not an execution authority.

The authoritative W12 execution path is the live AutoPilot Java Runtime operating against durable AutoPilot state. ONSure MUST NOT treat a Python subprocess orchestrator, local JSON receipt file, GitHub commit, MissionSpec registration, or Manual Run intent file as execution evidence.

An AutoPilot MissionSpec stored by API is specification/admission material only. It is not equivalent to execution and cannot satisfy W12.

## 2. Live-runtime findings requiring rebinding

The following operational findings were reported from the AutoPilot live environment and are treated as blockers until live Runtime receipts prove otherwise:

- active `/workspace/ONSure` is not the required frozen validation SHA and contains a modified `.gitignore`;
- ONSure PR #95 previously exposed the required source SHA, but active workspace identity did not match it;
- AutoPilot live Runtime does not contain the Draft PR #288 binding assets;
- ProjectAdmission for `ONSURE/ENTERPRISE_WEB_W12` is absent;
- Project Context is revision `0` / empty;
- Mission v2 is not admitted in live Runtime (`MISSION_NOT_FOUND` reported);
- `onsure-w12-*` adapter IDs are not implemented by the Java Stage executor;
- Python orchestration and Java Mission admission are separate execution systems;
- the previous admission helper default port did not match the reported live Java API port `8781`;
- previous G10 `same_sha` semantics were not backed by actual receipt comparison;
- previous G8/G9 runs could share `~/.m2` state;
- previous local JSON receipts were not Java/PostgreSQL durable receipt lineage.

These findings do not establish failure of ONSure Web itself. They establish that the previous W12 binding was not an authoritative AutoPilot execution path.

## 3. Immutable validation subject

The active developer workspace `/workspace/ONSure` MUST NOT be reset or mutated merely to satisfy validation identity.

For each W12 lineage, AutoPilot must create/use an isolated validation worktree checked out at one immutable ONSure commit SHA.

Required subject properties:

- repository: `babyandi/ONSure`
- source branch provenance: `feature/onsure-enterprise-web-springboot`
- exact commit SHA: frozen at admission time
- isolated worktree path: unique to the run/lineage
- clean worktree status: required
- no reuse of active developer-workspace modifications

Any ONSure commit after freeze requires a new source identity and new admission lineage. Results from an older SHA cannot be promoted to the new SHA.

## 4. Required live Runtime sequence

The authoritative order is:

```text
1. ISOLATED_WORKTREE_CREATE_AT_EXACT_SHA
2. PROJECT_CONTEXT_CAS
3. PROJECT_ADMISSION
4. MISSION_SPEC_ADMISSION / SUPPORTED_STAGE_RESOLUTION
5. MANUAL_RUN_BEGIN
6. JAVA_STAGE_EXECUTION
7. DURABLE_STEP_RECEIPT_APPEND
8. DURABLE_MISSION_STATE_READBACK
9. TWO_CLEAN_COMPARISON
10. INTEGRATION_GATE
```

`PROJECT_CONTEXT_CAS → PROJECT_ADMISSION → MANUAL_RUN_BEGIN` is mandatory. Missing ProjectAdmission, empty context, missing mission, unsupported adapter, or stale context revision is HOLD/NOT_RUN, never an invitation to bypass the Runtime with subprocess execution.

## 5. Adapter policy

A GoalNode may execute only when its `adapter_id` is supported by the live Java Stage executor or by another explicitly admitted durable executor whose receipts enter the same AutoPilot durable lineage.

Unsupported `onsure-w12-*` adapters MUST resolve to `HOLD_UNSUPPORTED_ADAPTER` or equivalent explicit non-success state.

It is forbidden to register fictional adapter IDs merely to make a MissionSpec structurally complete.

## 6. Corrected Goal DAG semantics

Logical W12 dependency graph:

```text
G0 SOURCE_IDENTITY / ISOLATED WORKTREE
  ↓
  ├─ G1 CORE_INSTALL_AND_WEB_PACKAGE
  │      ↓
  │   G2 UNIT_MVC_SECURITY_CORE_READ_TESTS
  └─ G3 STATIC_AUTHORITY_CONTRACT
          ↓ join G2 + G3
      G4 RUNTIME_BOOT
          ↓
      ├─ G5 HEALTH_AND_AUTH
      ├─ G6 BROWSER_SECURITY_NEGATIVE
      └─ G7 CORE_READ_CONTRACT
          ↓ join ALL
      G8 CLEAN_RUN_A
          ↓
      G9 CLEAN_RUN_B_SAME_SHA
          ↓
      G10 DURABLE_RECEIPT_INTEGRATION_GATE
```

This DAG is a semantic requirement. It does not authorize Python subprocess execution. AutoPilot live Runtime decides execution according to admitted/supported Stage adapters and durable state transitions.

## 7. Two-clean isolation

G8 and G9 must use:

- the same immutable ONSure SHA;
- separately created isolated worktrees or independently reset immutable worktrees;
- separate Maven local repositories (`-Dmaven.repo.local=<unique-run-path>` or equivalent);
- separately created build outputs;
- separately allocated runtime process/port state;
- equivalent declared policy digest and execution profile.

A shared `~/.m2`, shared target directory, reused running process, or reused prior receipt cannot establish two-clean reproducibility.

## 8. Durable receipt requirements

Only receipts produced/accepted by the authoritative AutoPilot Runtime and persisted in durable AutoPilot lineage may satisfy W12.

Each required Stage receipt must support readback of at least:

- mission ID/version/spec digest;
- project context revision and ProjectAdmission identity;
- exact ONSure SHA;
- isolated worktree identity;
- Stage/adapter identity;
- parent receipt digests;
- policy/config digests relevant to the Stage;
- execution/result state;
- bounded evidence references;
- timestamps and attempt identity.

A JSON file written only by a Python runner is debugging material, not authoritative evidence.

## 9. G10 actual comparison

G10 MUST derive its result from durable readback. It must not contain a literal or pre-filled `same_sha: true`.

G10 must compare at minimum:

- Run A exact source SHA vs Run B exact source SHA;
- Run A policy/config digest vs Run B policy/config digest;
- required parent receipt lineage;
- Stage denominator/completeness;
- unsupported/skipped/unknown states;
- required receipt existence in durable store.

Any mismatch or missing lineage => HOLD.

## 10. Promotion rules

Before live Runtime durable receipts:

`W12 = NOT_RUN`

Even after two technically clean runs, the maximum self-validation result is `W12_SELF_VALIDATION_CLEAN_NONFINAL` until independent validation is satisfied.

Forbidden from this binding alone:

- canonical Assurance state promotion
- EVIDENCED
- INDEPENDENTLY_VERIFIED
- FinalLock
- Production GO
- Commercial GO

## 11. Superseded design

The previous design that treated `scripts/run_autopilot_onsure_web_w12_orchestrator_v2.py` or another Python subprocess runner as the effective execution authority is superseded and must not be used as W12 evidence.

AutoPilot Draft PR #288 may remain historical/design material, but ONSure does not depend on that Draft PR being merged to claim live-runtime execution. The live Runtime itself must contain/admit the required project context, ProjectAdmission, supported adapters, mission, execution states, and durable receipt lineage.
