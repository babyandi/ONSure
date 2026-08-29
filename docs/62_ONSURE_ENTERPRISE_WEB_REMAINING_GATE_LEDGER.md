# ONSure Enterprise Web — Remaining Gate Ledger

- Status: NONFINAL
- Scope: PR #95 after UI/UX design, Core read slice and AutoPilot binding materialization

| Gate | State | Completion authority |
|---|---|---|
| UI/UX IA/detail | DETAILED_DESIGNED_NONFINAL | design artifacts/readback |
| Visual baseline | CANDIDATE_NONFINAL | independent/human visual review |
| Core Project/Target/Evidence read slice | IMPLEMENTED_NOT_RUN | code + AutoPilot G2/G7 receipts |
| Core eight-stage Assurance projection | NOT_IMPLEMENTED | future authoritative Core provider |
| AutoPilot binding | MATERIALIZED_NONFINAL | ONSure #95 + AutoPilot #288 contracts |
| exact source SHA freeze | NOT_RUN | AutoPilot G0 receipt |
| Java/Maven preflight | NOT_RUN | AutoPilot receipt |
| build/package | NOT_RUN | AutoPilot G1 receipt |
| unit/MVC/security/Core-read tests | NOT_RUN | AutoPilot G2 receipt |
| static Web-authority checks | NOT_RUN | AutoPilot G3 receipt |
| real Spring runtime | NOT_RUN | AutoPilot G4 receipt |
| health/auth | NOT_RUN | AutoPilot G5 receipt |
| CSP/frame negative checks | NOT_RUN | AutoPilot G6 receipt |
| Core read contract | IMPLEMENTED_NOT_RUN | AutoPilot G7 receipt |
| clean run A | NOT_RUN | AutoPilot G8 receipt |
| same-SHA clean run B | NOT_RUN | AutoPilot G9 receipt |
| receipt readback/integration | NOT_RUN | AutoPilot G10 receipt |
| independent validation | NOT_RUN | separate independent process |
| Visual Lock | false | independent/human approval |
| FinalLock | false | independent/human final gate |
| Production GO | false | human operational authority |
| Commercial GO | false | human business authority |

## Implemented Core read boundary

Web now depends on the authoritative `onsure-core` module. Core exposes a read-only Enterprise Web facade over `ProductCatalog` and validation-store evidence. Missing Core roots are `NOT_AVAILABLE`; store/read failures are `UNKNOWN`. No Web rule derives assurance state, blockers, approval eligibility or final readiness.

Until a canonical Core Assurance projection exists, `AssuranceSnapshot` explicitly returns availability `NOT_AVAILABLE`, `canonicalState=null`, reason `CORE_ASSURANCE_PROJECTION_NOT_IMPLEMENTED`.

## Parallelizable remaining work

AutoPilot may run G1/G2/G3 after G0 in parallel and G5/G6/G7 after G4 in parallel subject to workspace/process isolation. G8 and G9 remain sequential same-SHA reproducibility gates.

## Hard blocks

- No GitHub Actions authority.
- No Web-derived assurance truth.
- No reuse of another SHA's result.
- No promotion when required nodes are UNKNOWN, NOT_RUN, INCONCLUSIVE or HOLD.
- Missing eight-stage Core projection remains NOT_AVAILABLE, not a guessed stage.
- No direct main merge from this ledger.

## Current aggregate

`CORE_READ_IMPLEMENTED_DESIGN_REFINED_AUTOPILOT_BOUND_NONFINAL`

This ledger is planning/traceability material, not execution evidence.
