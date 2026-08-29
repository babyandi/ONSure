# ONSure Enterprise Web — Remaining Gate Ledger

- Status: NONFINAL
- Scope: PR #95 after UI/UX design, Core read slice, and correction of the AutoPilot live-runtime binding model

| Gate | State | Completion authority |
|---|---|---|
| UI/UX IA/detail | DETAILED_DESIGNED_NONFINAL | design artifacts/readback |
| Visual baseline | CANDIDATE_NONFINAL | independent/human visual review |
| Core Project/Target/Evidence read slice | IMPLEMENTED_NOT_RUN | code + live AutoPilot durable Stage receipts |
| Core eight-stage Assurance projection | NOT_IMPLEMENTED | future authoritative Core provider |
| Previous PR #288 Python binding model | SUPERSEDED_AS_AUTHORITY | ONSure docs/60-61 correction |
| AutoPilot live Project Context | REBIND_REQUIRED | live Project Context CAS + readback |
| `ONSURE/ENTERPRISE_WEB_W12` ProjectAdmission | MISSING_REPORTED | live AutoPilot ProjectAdmission readback |
| Mission v2 live admission | MISSING_REPORTED | live mission readback |
| required `onsure-w12-*` Stage adapters | UNSUPPORTED_REPORTED | live Java executor registry/readback |
| isolated frozen-SHA worktree | NOT_RUN | live AutoPilot G0/source receipt |
| Java/Maven preflight | ENVIRONMENT_PRESENT_REPORTED_NOT_RECEIPTED | live durable Stage receipt |
| build/package | NOT_RUN | live Java Stage receipt |
| unit/MVC/security/Core-read tests | NOT_RUN | live Java Stage receipt |
| static Web-authority checks | NOT_RUN | live Java Stage receipt |
| real Spring runtime | NOT_RUN | live Java Stage receipt |
| health/auth | NOT_RUN | live Java Stage receipt |
| CSP/frame negative checks | NOT_RUN | live Java Stage receipt |
| Core read contract | IMPLEMENTED_NOT_RUN | live Java Stage receipt |
| clean run A | NOT_RUN | isolated worktree + isolated Maven repo + durable receipt |
| clean run B | NOT_RUN | second isolated worktree + second isolated Maven repo + durable receipt |
| G10 actual SHA/policy/lineage comparison | NOT_IMPLEMENTED_IN_LIVE_PATH | durable Runtime integration gate |
| independent validation | NOT_RUN | separate independent process |
| Visual Lock | false | independent/human approval |
| FinalLock | false | independent/human final gate |
| Production GO | false | human operational authority |
| Commercial GO | false | human business authority |

## Corrected execution model

The previous assumption that a Python subprocess orchestrator could be the W12 execution authority is rejected.

Authoritative sequence is now:

```text
isolated frozen-SHA worktree
→ Project Context CAS/readback
→ ProjectAdmission/readback
→ MissionSpec admission/readback
→ supported Java Stage resolution
→ MANUAL_RUN_BEGIN
→ Java Stage execution
→ durable PostgreSQL receipt lineage
→ isolated Clean A
→ isolated Clean B
→ G10 actual comparison
```

GitHub files, local JSON receipts, and Python subprocess success are non-authoritative diagnostics only.

## Reported live-runtime blockers

Operational handoff from the AutoPilot environment reported:

- Java 17 and Maven 3.8.7 available;
- AutoPilot Java liveness `UP`;
- active `/workspace/ONSure` is not the prior frozen SHA and has a `.gitignore` modification;
- PR #95 head matched the then-required source SHA, but active workspace did not;
- PR #288 is not reflected in live Runtime state;
- Project Context revision is `0` / empty;
- `ONSURE/ENTERPRISE_WEB_W12` ProjectAdmission is absent;
- Mission v2 reads as `MISSION_NOT_FOUND`;
- `onsure-w12-*` adapters are not implemented by the Java executor;
- Python orchestration writes local JSON instead of durable AutoPilot receipt lineage;
- previous G10 `same_sha` was hard-coded rather than derived;
- G8/G9 shared Maven local state.

These are execution-binding blockers, not proof that the ONSure Web implementation itself failed.

## Implemented Core read boundary

Web depends on `onsure-core`. Core exposes Project/Target/persisted Evidence facts. Missing configured roots/store are `NOT_AVAILABLE`; read failures are `UNKNOWN`; authoritative empty collections remain distinct.

Until a canonical Core Assurance projection exists:

```text
availability = NOT_AVAILABLE
canonicalState = null
reason = CORE_ASSURANCE_PROJECTION_NOT_IMPLEMENTED
```

No Web rule derives an Assurance state.

## Hard blocks

- No GitHub Actions authority.
- No Python subprocess hidden execution authority.
- No active developer-workspace reset for validation.
- No MissionSpec-file-equals-execution interpretation.
- No unsupported adapter substitution.
- No local-only JSON receipt as W12 evidence.
- No shared Maven repository for two-clean evidence.
- No hard-coded `same_sha` or equivalent success fact.
- No promotion with missing ProjectAdmission, empty/stale Project Context, `MISSION_NOT_FOUND`, unsupported adapter, UNKNOWN, NOT_RUN, INCONCLUSIVE, or HOLD.
- No Web-derived assurance truth.
- No result reuse across different ONSure SHAs.

## Current aggregate

`CORE_READ_IMPLEMENTED_UIUX_REFINED_AUTOPILOT_LIVE_REBIND_REQUIRED_NONFINAL`

This ledger is traceability material, not execution evidence.
