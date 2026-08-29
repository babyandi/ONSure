# ONSure Enterprise Web — Remaining Gate Ledger

- Status: NONFINAL
- Scope: PR #95 after UI/UX design and AutoPilot binding materialization

| Gate | State | Completion authority |
|---|---|---|
| UI/UX IA/detail | DETAILED_DESIGNED_NONFINAL | design artifacts/readback |
| Visual baseline | CANDIDATE_NONFINAL | independent/human visual review |
| AutoPilot binding | MATERIALIZED_NONFINAL | ONSure #95 + AutoPilot #288 contracts |
| exact source SHA freeze | NOT_RUN | AutoPilot G0 receipt |
| Java/Maven preflight | NOT_RUN | AutoPilot receipt |
| build/package | NOT_RUN | AutoPilot G1 receipt |
| unit/MVC/security tests | NOT_RUN | AutoPilot G2 receipt |
| static Web-authority checks | NOT_RUN | AutoPilot G3 receipt |
| real Spring runtime | NOT_RUN | AutoPilot G4 receipt |
| health/auth | NOT_RUN | AutoPilot G5 receipt |
| CSP/frame negative checks | NOT_RUN | AutoPilot G6 receipt |
| Core read contract | NOT_IMPLEMENTED_OR_NOT_RUN | implementation + AutoPilot G7 receipt |
| clean run A | NOT_RUN | AutoPilot G8 receipt |
| same-SHA clean run B | NOT_RUN | AutoPilot G9 receipt |
| receipt readback/integration | NOT_RUN | AutoPilot G10 receipt |
| independent validation | NOT_RUN | separate independent process |
| Visual Lock | false | independent/human approval |
| FinalLock | false | independent/human final gate |
| Production GO | false | human operational authority |
| Commercial GO | false | human business authority |

## Parallelizable remaining work

AutoPilot may run G1/G2/G3 after G0 in parallel and G5/G6/G7 after G4 in parallel. G8 and G9 are intentionally sequential because reproducibility is meaningless if both runs share uncontrolled concurrent state.

## Hard blocks

- No GitHub Actions authority.
- No Web-derived assurance truth.
- No reuse of another SHA's result.
- No promotion when required nodes are UNKNOWN, NOT_RUN, INCONCLUSIVE, HOLD or NOT_IMPLEMENTED.
- No direct main merge from this ledger.

## Current aggregate

`IMPLEMENTATION_PRESENT_DESIGN_REFINED_AUTOPILOT_BOUND_NONFINAL`

This ledger is planning/traceability material, not execution evidence.
