# ONSURE Cause-Aware Verification And Memory

## Purpose

ONSURE must not only say that a target AI program failed. It must explain why the gap was missed, which program boundary owns the remediation, and which memory store should learn from the evidence.

## Contract

Every verification run produces:

- `findings`: reproducible failures, omissions, or unsafe completion claims
- `cause`: why the omission could occur
- `remediation`: the program-level change required to prevent recurrence
- `remediation_targets`: affected target programs or boundaries
- `memory_candidates`: Program, Behavior, Failure, or Improvement memory candidates

Memory candidates are never promoted automatically. They remain `CANDIDATE_NOT_PROMOTED` until evidence review and regression proof succeed.

## Cause Classes

| Cause Code | Learns Into | Typical Remediation |
|---|---|---|
| `PROGRAM_ROUTE_MISSING` | Program Learning | Add runtime, contract, and test routes to the profile |
| `STAGE_PARENT_HASH_MISSING` | Behavior Learning | Parent-bind each stage to the previous receipt |
| `STAGE_BODY_DRIFT` | Behavior Learning | Hash the exact canonical body consumed by the next stage |
| `REQUIRED_OUTPUT_FIELD_MISSING` | Failure Memory | Promote missing fields into the program contract |
| `FORMAL_PROCEDURE_MISSING` | Program Learning | Require ordered procedural receipts |
| `RENDER_OR_OUTPUT_BINDING_MISSING` | Failure Memory | Bind final output hash to the full canonical representation |
| `FINAL_GATE_NOT_PASS` | Improvement Memory | Block final completion unless independent gates pass |

## ORUDA Report Chain As A Target Profile

ORUDA can be registered as a target program profile without making ONSURE depend on ORUDA.

The report-generation profile requires:

```text
ODocument Raw
→ ODocument Claim
→ OReport PageSpec
→ ODesign Formal Procedure
→ OUI Lossless Canonical Scene
→ Canvas Render Binding
→ OTester/OAudit Final Gates
```

If OUI drops `field_manifest`, the finding targets OUI and learns into Failure Memory. If ODesign omits one of its formal steps, the finding targets ODesign and learns into Program Learning. If OTester or OAudit is `PENDING`, final completion is blocked and the improvement rule learns into Improvement Memory.

## Acceptance

The first executable contract is `tests/test_cause_aware_verification.py`.

It must prove:

- baseline run is `ALLOW`
- missing route is `BLOCK`
- parent hash drift is `BLOCK`
- body drift is `BLOCK`
- missing formal procedure is `BLOCK`
- missing lossless scene field is `BLOCK`
- final output binding drift is `BLOCK`
- pending final gate is `BLOCK`
