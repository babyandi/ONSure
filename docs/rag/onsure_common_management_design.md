# ONSure Common Management Design

## Purpose

ONSure is the independent verification and learning layer for AI-program
workflows. For ORUDA report generation, ONSure manages shared verification
contracts, repeated-loop harness evidence, cause-aware findings, and RAG
knowledge used to recommend remediation.

The target programs remain in ORUDA:

- ODocument
- OReport
- ODesign
- OUI

ONSure manages what must be checked across those programs and what must be
remembered when a gap is found.

## Ownership

| Area | Owner | Rule |
|---|---|---|
| Program implementation | ORUDA | Runtime code and product behavior live in the target repository. |
| Verification profile | ONSure | Required routes, stage order, output fields, final gates, and evidence binding live in ONSure. |
| RAG knowledge | ONSure | Reusable contracts, RCA patterns, and remediation guidance live in ONSure. |
| Failure memory | ONSure | Stable repeated failures become memory candidates before promotion. |
| Remediation PR | Target repository | ONSure findings create target-program fixes without moving verifier memory into the target. |

## Repository Layout

Recommended ONSure layout:

```text
onsure/
  profiles/
    oruda_report_chain.profile.json
  contracts/
    odocument.contract.md
    oreport.contract.md
    odesign.contract.md
    oui.contract.md
    report_chain.contract.md
  harnesses/
    oruda_report_chain_loop.yaml
  rag/
    source_packs/
      oruda_report_chain.md
    chunks/
      oruda_report_chain.jsonl
    manifests/
      oruda_report_chain.manifest.json
    promotion_queue/
    retired/
  memory/
    failure/
    improvement/
    behavior/
    program/
  remediation_patterns/
    oruda_report_chain.md
```

For PR #5, the initial checked-in pack uses this practical layout:

```text
docs/rag/
  README.md
  onsure_common_management_design.md
  rag_source_pack.md
  rag_ingest_guide.md
rag/
  manifests/oruda_report_chain.manifest.json
  chunks/oruda_report_chain.jsonl
```

## Common Verification Model

ONSure receives a target profile and a run receipt.

The profile defines:

- required program routes
- ordered stages
- required output fields per stage
- required formal procedure steps
- final independent gates

The run receipt proves:

- actual runtime, contract, and test routes
- stage outputs
- body hashes
- parent hashes
- final output binding
- OTester and OAudit status receipts

The common ORUDA report chain is:

```text
ODocument Raw
-> ODocument Claim
-> OReport PageSpec
-> ODesign Formal Procedure
-> OUI Lossless Canonical Scene
-> Canvas Render Binding
-> OTester/OAudit Final Gates
```

## Two-To-Three Loop Harness

ONSure should verify the same target run in repeated loops before promoting a
finding.

Loop 1 detects contract violations.

Loop 2 checks that the same violation produces the same cause code,
remediation target, and memory kind.

Loop 3 checks stability before RAG or memory promotion.

Promotion is allowed only when:

- the decision is stable across loops
- finding codes are stable across loops
- remediation targets are stable across loops
- evidence projection hash is stable across loops
- the target-program fix has a regression test or executable proof

If repeated loops disagree, the target is ONSure itself and the cause code is
`LOOP_RESULT_UNSTABLE`.

## Program Contracts

### ODocument

ODocument must preserve source evidence lineage.

Required checks:

- raw bytes hash exists
- claim contains source hash
- claim is parent-bound to raw
- regenerated claim body is blocked

RAG category:

- `program_contract`
- `lineage_rule`
- `failure_memory_candidate`

### OReport

OReport must transform claims into PageSpec without losing intent or evidence.

Required checks:

- PageSpec contains page intent
- PageSpec contains information priority
- PageSpec contains geometry contract
- PageSpec is parent-bound to claim

RAG category:

- `program_contract`
- `report_quality_rule`
- `lineage_rule`

### ODesign

ODesign must follow the formal design procedure.

Required procedure:

1. page intent
2. information priority
3. concept croquis
4. layout candidate comparison
5. detailed croquis
6. asset selection
7. geometry contract
8. visual quality gate

RAG category:

- `program_contract`
- `formal_procedure`
- `remediation_pattern`

### OUI

OUI must preserve the full canonical scene required for rendering and audit.

Required checks:

- scene objects exist
- field manifest exists
- coordinates, size, font, color, asset, layer, and accessibility metadata are
  preserved when available
- render binding includes canonical run hash and field manifest hash

RAG category:

- `program_contract`
- `lossless_contract`
- `failure_memory_candidate`

## Memory Stores

ONSure should classify findings into four memory stores.

| Store | Use |
|---|---|
| Program Learning | Missing route, missing formal program contract, or target profile gap. |
| Behavior Learning | Parent hash drift, body drift, or unsafe workflow behavior. |
| Failure Memory | Reproducible output omission or final binding gap. |
| Improvement Memory | Final gate policy, loop instability, or verifier improvement rule. |

Memory candidates are never promoted automatically. They remain
`CANDIDATE_NOT_PROMOTED` until evidence review and regression proof succeed.

## RAG Promotion Policy

Do not ingest raw conversation logs directly.

Ingest only stable knowledge:

- common contracts
- verified cause classes
- stable loop findings
- remediation patterns
- final gate rules
- report-quality rules that affect verification

Exclude:

- transient PR status
- temporary branch names
- unverified speculation
- duplicate progress updates
- conversational phrasing that is not a reusable rule
