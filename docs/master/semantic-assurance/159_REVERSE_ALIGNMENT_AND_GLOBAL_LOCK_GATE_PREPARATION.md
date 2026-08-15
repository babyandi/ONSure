# 159 Reverse Alignment and Global Lock Gate Preparation

Status: `INDEPENDENT_QA_PREPARATION / NON_FINAL`

## Scope
Independent QA track parallel to Claude Batch 5~9 implementation. This document does not assert implementation PASS, Design Lock, Production GO, or Commercial GO.

## 1. Batches 0~4 reverse alignment
Reverse direction must be checked in addition to forward trace:
`Evidence -> Test -> Implementation -> Contract -> Design -> Requirement -> Authority`.
A node without an authoritative parent is a reverse orphan. A test count alone is not coverage evidence.

Current implementation evidence reports BATCH_0~4 as EVIDENCE_READY. Independent requalification remains required after Batch 9 because implementation is still changing.

## 2. FR-LEARN-001~095 consistency
Canonical learning scope is FR-LEARN-001~095. Stale FR-LEARN-25/77 counts are historical snapshots only.
Cross-capability review identified 11 P0 and 4 P1 contradiction classes. PR #48 supplies 11/11 design policy bindings but runtime tests/evidence are not yet executed. Therefore learning design remains HOLD for final lock.

Required invariants include: no learning self-approval; no direct Candidate->ACTIVE/Final; decision-time knowledge epoch binding; tenant-derived scope promotion requires consent/lineage/transfer/approval; deletion propagates through derived lineage; mandatory assurance cannot be skipped for cost; historical decisions are immutable while currentness may become STALE.

## 3. Batch 5 AI/Meta-Assurance gate
Must cover LearningCandidate promotion, Oracle qualification/disagreement, FP/FN feedback, corpus contamination/poisoning/leakage, train-test leakage, validator drift/regression/rollback, derived deletion lineage, tenant scope promotion, stochastic/metamorphic/differential validation, challenge/blind regression, freshness and stop condition.
Strong positive claims are forbidden on unresolved oracle disagreement, stale/unqualified oracle, contaminated benchmark, or self-attested qualification.

## 4. Batch 6 Safety/Appeal gate
Required executable chains:
`Hazard -> escalation -> mitigation -> closure/reopen`
`Appeal -> independent review -> remedy/reversal -> receipt`.
Safety owner and appeal reviewer separation must be provable. Closure without evidence or appeal review by the original decision authority is a negative gate.

## 5. Batch 7 Fresh Review gate
Every refinement must identify canonical parent, affected contracts, migration/compatibility impact, tests and evidence. No stale companion/handoff/QA document may originate new current requirements unless Requirement Authority Manifest marks it eligible.

## 6. Batch 8 Migration/Integration gate
Require old->new mapping, DIRECT/REPERFORMANCE_REQUIRED/UNRECOVERABLE disposition, loss disclosure, retry/idempotency, rollback where meaningful, reconciliation receipts and fail-closed handling of unknown/unmapped values.

## 7. Batch 9 Test Coverage Universe
Coverage denominator is Requirement Universe, not test count. For every applicable requirement track expected test classes: positive, negative, semantic-invalid, cross-contract, adversarial where relevant, recovery where relevant, runtime evidence. N/A and conditional entries require proof/context.

## 8. Known P0 trace gaps
FR-COM-008 and NFR-CONFIG remain explicit test/evidence-path gaps in Batch 0 registry. They must be closed or explicitly external-blocked with authoritative proof before final Design/Implementation Lock.

## 9. PR #48 disposition
PR #48 is design-only refinement. Its 11 P0 bindings are semantically acceptable as precedence/fail-closed policies, but `runtime_tests_run=0` and `evidence_receipts=0`; P1 count=4 remains review pending. It must not be treated as runtime resolution merely by merging.

## 10. Authority/stale-number policy
Current learning identity: FR-LEARN-001~095. Historical 25 and 77 populations remain provenance snapshots. Current Product Design RU epoch is EPOCH::REQUIREMENT::0002 from Batch 0 requalification; exact current count must come from its generation receipt, never hand-maintained arithmetic in a handoff.

## 11. Global Design/Implementation Lock Matrix
Final independent lock rerun after Batch 9 must require all of:
1. Requirement Authority Manifest reviewed population frozen; no authority ambiguity used for a positive claim.
2. Exact active RU population + digest + generation receipt.
3. Applicability 1:1 with RU; Critical UNKNOWN=0; N/A/Conditional proof present.
4. Forward trace closure and reverse orphan scan.
5. Forbidden P0 orphan=0 or authoritative external blocker disposition that forbids final positive claim as appropriate.
6. Unresolved P0 contradiction=0 and unresolved P0 DCQ=0.
7. Contract/schema registry integrity and cross-contract referential integrity.
8. Test Coverage Universe materialized against requirements, not raw test count.
9. Positive + required negative/semantic-invalid/cross-contract/adversarial/recovery evidence complete.
10. Content SHA-256 artifact manifest + registry digest set complete.
11. CLEAN deterministic reconstructability evidence (minimum two independent clean reruns where applicable).
12. Validator/oracle/collector qualification and currentness proven; self-attestation prohibited.
13. Learning corpus contamination/train-test leakage gates PASS.
14. Safety/Appeal executable evidence PASS.
15. Migration/integration reconciliation and loss/unrecoverable disclosure PASS.
16. Historical FinalCandidate/Approval/Lock identities separated from currentness/revocation/deployment state.
17. No UNKNOWN/STALE/PARTIAL/INCONCLUSIVE promoted to positive Final claim.
18. Independent QA rerun after Claude reports Batch 9 complete.

Until all applicable gates are satisfied: `GLOBAL_DESIGN_IMPLEMENTATION_LOCK=HOLD / NON_FINAL`.
