# DD-041 / DD-042 Successor Design Trace Authority

Status: NONFINAL design trace materialization. This document does not grant Human approval, independent qualification, CLEAN, Design Lock, FinalLock, Production GO, or Commercial GO.

## DD-041 — Crypto-erasure completeness and verifiable deletion

DD-041 is the canonical successor obligation adopted from Human-approved finding F-04. It refines the existing tenant/offboarding and retention/deletion authority under FR-FIN-13, FR-FIN-21 and FR-FIN-22.

Machine contract: `contracts/dd-041-042-design-gap-extension.candidate.v1.json`.
Evaluator/runtime implementation: `DesignGapDdSemanticEvaluators` and `DdAssuranceOperationRuntime`.
Qualification authority: current successor frozen bundle and receipt validator; tracked design state cannot imply qualification.
Evidence rule: payload scope, replica/backup/queue/derived copies, key bindings, retention conflicts and verifiable erasure must all be current and authority-bound. Unknown or incomplete scope remains HOLD/BLOCKED.

## DD-042 — Self-referential AI safety claim qualification

DD-042 is the canonical successor obligation adopted from Human-approved finding F-05. It refines assurance/evidence and independent qualification authority under FR-FIN-12 and FR-FIN-18.

Machine contract: `contracts/dd-041-042-design-gap-extension.candidate.v1.json`.
Minimum adversarial authority: `contracts/dd-042-adversarial-minimum-set.v1.json`.
Evaluator/runtime implementation: `DesignGapDdSemanticEvaluators` and `DdAssuranceOperationRuntime`.
Qualification authority: current successor frozen bundle and receipt validator; every mandatory adversarial dimension must be executed and evidenced. Self-authored evidence, evaluator↔oracle circularity, self-promotion, unverified independence provenance, insufficient independent adversarial evidence, or material shared-control loops remain HOLD/INCONCLUSIVE.

## Trace rules

1. DD-041 and DD-042 are first-class canonical DD identities in the successor Requirement Universe; they are not hidden inside the legacy DD-001..040 denominator.
2. Their FR-FIN parent mapping is defined in `contracts/post-final-target-dd-041-042-to-fr-fin-relation.v1.json` and is not double-counted as a second top-level business requirement.
3. Positive semantic evidence is receipt-derived and subject-bound. Static code/materialization never substitutes for independent qualification or target runtime evidence.
4. Design Discovery Wave A/B, DD-040 bounded-rule authority, HDA22, CLEAN A/B, PR review, and main Design Lock remain separate authorities.
5. Any tracked change after a frozen external-assurance subject invalidates subject-bound qualification/Discovery evidence until a new exact-head PREPARE is produced.
