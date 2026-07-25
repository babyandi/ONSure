# ONSure RAG Source Pack: ORUDA Report Chain

## Scope

This source pack prepares reusable RAG material for ONSure. It covers the common
verification and remediation knowledge for ODocument, OReport, ODesign, and OUI
in the ORUDA report-generation chain.

ONSure is the source of truth for verification knowledge. ORUDA is the target
program implementation.

## Architecture Rule

ONSure manages shared verification knowledge, target profiles, cause-aware
findings, repeated-loop harness evidence, and RAG memory. ORUDA manages the
actual implementation of ODocument, OReport, ODesign, and OUI.

This separation prevents the target program from becoming the authority over
its own verification criteria.

## Chain Contract

The expected report-generation chain is:

1. ODocument Raw
2. ODocument Claim
3. OReport PageSpec
4. ODesign Formal Procedure
5. OUI Lossless Canonical Scene
6. Canvas Render Binding
7. OTester/OAudit Final Gates

Every stage after the first must include a parent hash from the previous stage.
Every stage body must be hashed from the exact canonical body consumed by the
next stage. Any middle-output regeneration, fixture replay, or body drift must
block completion.

## ODocument Contract

ODocument owns source evidence conversion into claims.

Required outputs:

- raw bytes hash
- claim body
- claim source hash
- claim body hash
- parent hash from raw to claim

Failure examples:

- claim lacks `source_hash`
- claim changes after hash generation
- claim is not parent-bound to raw

Default remediation:

Promote source hash and claim lineage into the ODocument contract and block
claim creation when the source receipt is missing.

## OReport Contract

OReport owns conversion from claims to PageSpec.

Required PageSpec fields:

- page intent
- information priority
- geometry contract
- parent hash from claim

Quality rule:

OReport must not generate arbitrary report pages without preserving the page
purpose, evidence basis, and layout contract required by downstream ODesign and
OUI.

Default remediation:

Require PageSpec to carry intent, information priority, geometry contract, and
parent lineage. A missing PageSpec contract is an OReport program contract
failure.

## ODesign Contract

ODesign owns the formal design procedure before OUI scene generation.

Required procedure steps:

1. page intent
2. information priority
3. concept croquis
4. layout candidate comparison
5. detailed croquis
6. asset selection
7. geometry contract
8. visual quality gate

Failure examples:

- design skips layout candidate comparison
- design omits detailed croquis
- design output exists but procedure receipt is missing
- design is not parent-bound to PageSpec

Default remediation:

Require ordered formal procedure receipts, parent-bind each procedure result,
and block downstream OUI generation when a required step is missing.

## OUI Contract

OUI owns the lossless canonical scene consumed by the renderer.

Required scene fields:

- objects
- field manifest
- coordinates
- size
- font
- color
- asset reference
- layer order
- accessibility metadata when applicable

Required render binding:

- canonical run hash
- scene hash
- field manifest hash
- render hash

Failure examples:

- scene lacks `field_manifest`
- coordinates or font metadata are dropped
- render hash is not bound to canonical scene
- final output can be produced from a regenerated scene

Default remediation:

Promote the full field manifest and render binding into the OUI contract and
block rendering when canonical scene information is missing.

## Final Gate Rule

Final completion is forbidden unless OTester and OAudit are both `PASS` and
receipt-bound.

If OTester or OAudit is `PENDING`, `NOT_RUN`, missing, or has an invalid receipt
hash, ONSure must block final completion.

## Cause Classes

`PROGRAM_ROUTE_MISSING` means a target program is registered but runtime,
contract, or test route is missing.

`STAGE_PARENT_HASH_MISSING` means a stage can consume fabricated or regenerated
input because parent binding is missing or stale.

`STAGE_BODY_DRIFT` means a stage body changed after hash recording.

`REQUIRED_OUTPUT_FIELD_MISSING` means a required downstream or audit field was
dropped.

`FORMAL_PROCEDURE_MISSING` means output was produced without required design or
program procedure receipts.

`RENDER_OR_OUTPUT_BINDING_MISSING` means final output is not bound to the full
canonical intermediate representation.

`FINAL_GATE_NOT_PASS` means completion was claimed while independent verification
or audit was incomplete.

`LOOP_RESULT_UNSTABLE` means ONSure repeated verification produced inconsistent
decisions or evidence projections.

## Repeated Loop Rule

ONSure should run a target verification two or three times before promoting a
finding into RAG or memory.

Required stability:

- same decision
- same cause code sequence
- same remediation targets
- same memory kinds
- same evidence projection hash

Unstable loop results are ONSure defects, not target-program defects.

## RAG Inclusion Rule

Include:

- stable contracts
- stable failure classes
- stable remediation patterns
- final gate rules
- repeated-loop harness rules
- report quality rules that can be verified

Exclude:

- temporary PR status
- branch names unless needed as evidence
- conversational status updates
- unverified suggestions
- one-off implementation details that are not reusable
