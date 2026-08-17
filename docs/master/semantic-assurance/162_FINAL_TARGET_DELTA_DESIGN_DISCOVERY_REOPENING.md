# 162 Final-Target Delta Design Discovery Reopening

Status: `DESIGN_AUTHORITY_REVIEW / NORMATIVE_REFINEMENT / NON_FINAL`

## 1. Why Product Design is reopened
`126_FINAL_FRESH_PRODUCT_DESIGN_REVIEW_AND_SCOPE_CLOSURE.md` closed the then-current product-design scope as a candidate after a lifecycle review. That closure predates the separately-authoritative financial final-target tree (`docs/05` + `docs/40~44`) and therefore cannot be inherited unchanged after `160_FINAL_TARGET_PRODUCT_AUTHORITY_RECONCILIATION.md` reopened the Product Design Requirement denominator.

This document does not invalidate proven design or implementation evidence. It reopens **design discovery only for semantic obligations introduced or materially strengthened by FR-FIN-01~22 and the final-target product surfaces**.

`PRODUCT_DESIGN_SCOPE_COMPLETE_CANDIDATE` is therefore superseded for the post-final-target denominator by:

`PRODUCT_DESIGN_DELTA_DISCOVERY_OPEN / NON_FINAL`

## 2. Discovery method
The review uses negative-space discovery rather than document-count or orphan-count reduction. Each candidate is checked across:

1. actor / authority / segregation,
2. normal / alternative / failure / recovery process,
3. lifecycle and state transitions,
4. data lineage / retention / residency,
5. API / event / idempotency / external effects,
6. security / privacy / cryptographic trust,
7. model/provider/tool/plugin uncertainty,
8. evidence / oracle / independent verification,
9. UI disclosure / contestability / accessibility,
10. operation / DR / offboarding / requalification.

A candidate is not closed merely because a nearby document mentions the topic. Closure requires a stable owner, invariant, state/effect semantics, evidence semantics, failure behavior, and a testable oracle.

## 3. Delta candidate inventory

### DD-001 — Black/Gray-box Evidence Sufficiency Floor — P0 / `REFINE_EXISTING`
FR-FIN-05 requires White/Gray/Black-box verification but current design does not define one canonical minimum evidence floor per visibility mode.

Required design:
- `visibility_mode = WHITE | GRAY | BLACK`
- required observation classes per mode
- minimum identity/currentness evidence
- unsupported/unobservable dimensions recorded as `NOT_PROVEN`, never inferred PASS
- confidence/coverage limitations bound to every positive claim
- mode downgrade during a run forces claim requalification

### DD-002 — Oracle Authority, Uncertainty and Dispute — P0 / `REFINE_EXISTING`
Existing verification designs use expected outcomes/oracles, but the final-target product needs explicit authority over who may define or change an oracle.

Required design:
- oracle source, owner, version, digest, validity and applicability
- `KNOWN | PARTIAL | DISPUTED | UNAVAILABLE` oracle state
- validator/model may propose but may not silently self-approve a changed oracle
- disputed oracle => affected result `HOLD/INCONCLUSIVE`
- oracle supersession invalidates dependent verdicts and certificates

### DD-003 — Independent Validator Correlation / Common-mode Independence — P0 / `REFINE_EXISTING`
Different process IDs or model names do not by themselves establish independence.

Required design:
- independence dimensions: organization, codebase, rule source, model/provider family, evidence parser, runtime, operator
- disclosed shared dependencies
- minimum independence profile per Assurance Tier
- shared critical dependency => independence strength downgrade
- no self-validation promotion to independent validation

### DD-004 — Remote Model/Provider Reproducibility and Drift — P0 / `REFINE_EXISTING`
FR-FIN-10/15/18/19 permit remote models/providers, while exact model weights/runtime may be unavailable.

Required design:
- provider/model declared identity + observed identity evidence
- version pinning where supported
- opaque provider change detection using behavior/currentness probes
- fallback-provider use recorded as condition change
- rerun equivalence cannot be asserted across undisclosed provider drift
- provider outage/quota must produce `WAITING/BLOCKED/NOT_RUN`, not a synthetic PASS

### DD-005 — Adapter/Parser Coverage Integrity — P0 / `REFINE_EXISTING`
FR-FIN-01/05/12 depend on complete intake and evidence parsing across heterogeneous inputs.

Required design:
- adapter capability manifest and supported-version range
- parsed / skipped / unsupported / failed object counts
- parse-error denominator binding
- silent drop forbidden
- unsupported format/version => explicit coverage limitation
- adapter upgrade requires regression against preserved fixtures

### DD-006 — Assurance TCB Self-Integrity and Validator Failure Injection — P0 / `REFINE_EXISTING`
ONSure must distinguish a target failure from failure of its own validator, parser, rule engine, evidence store, clock, signer, or policy resolver.

Required design:
- TCB component inventory and version/digest
- health/currentness per critical validator component
- validator error taxonomy separate from target verdict taxonomy
- fail-closed positive claims when critical TCB is unhealthy
- fault-injection fixtures for parser truncation, rule omission, signer failure, clock skew, evidence-store partial write and stale-policy use

### DD-007 — Cryptographic Trust / Time / Key-compromise Requalification — P0 / `REFINE_EXISTING`
Existing crypto/currentness designs need a single product-level requalification rule.

Required design:
- trusted-time source/profile and uncertainty
- signer/key lifecycle, compromise/revocation epoch
- timestamp/anchor verification status
- compromised key invalidates or reclassifies dependent receipts according to event time
- offline grace cannot hide stale revocation state
- re-signing never erases original provenance

### DD-008 — Budget/Quota Exhaustion as Coverage Failure — P0 / `NEW_CROSS_CUTTING_OBLIGATION`
FR-FIN final acceptance requires denominator completeness, while plans/providers/credits impose cost limits.

Required design:
- budget/credit/token/time ceilings bound to ExecutionPlan
- budget exhaustion status distinct from target FAIL
- skipped work due budget remains `NOT_RUN/PARTIAL`
- no automatic scope shrink to preserve a PASS claim
- user-approved scope reduction must regenerate denominator and CoverageReport

### DD-009 — Appeal/Waiver Expiry and Requalification — P0 / `REFINE_EXISTING`
Finding disposition and human review exist, but final-target assurance needs lifecycle semantics for accepted risk/waiver/appeal.

Required design:
- waiver owner, scope, rationale, compensating controls, expiry, max renewals
- waiver may reduce enforcement but may not rewrite historical finding/evidence
- material target/policy/authority change invalidates waiver applicability
- appeal creates a new decision node; original decision remains immutable
- expired waiver => affected claim `REASSESSMENT_REQUIRED`

### DD-010 — Portfolio Common-mode/Systemic Risk Propagation — P0 / `NEW_CROSS_CUTTING_OBLIGATION`
Enterprise Portfolio currently aggregates per-program state, but FR-FIN financial deployment requires common dependency blast-radius reasoning.

Required design:
- dependency groups for shared model/provider/RAG/data/plugin/runtime/vendor
- one critical supplier/control failure can mark dependent programs `AT_RISK/REASSESSMENT_REQUIRED`
- portfolio view must distinguish local finding from common-mode exposure
- systemic invalidation events fan out deterministically with evidence

### DD-011 — Shared Corpus Contamination and Right-to-withdraw — P0 / `REFINE_EXISTING`
Opt-in/opt-out and learning governance exist, but final target needs downstream invalidation when contributed material is later withdrawn, found unlawful, poisoned, or misclassified.

Required design:
- contribution provenance and license/consent basis
- corpus membership epoch
- withdrawal/poisoning event
- dependent pattern/model/rule impact graph
- requalification of outputs trained/derived from invalidated corpus material
- tenant deletion does not silently preserve prohibited derived material

### DD-012 — Human Reviewer Competence / Conflict-of-interest / Rotation — P0 / `REFINE_EXISTING`
Reviewer accuracy is measured, but final financial assurance requires explicit eligibility and conflict controls.

Required design:
- reviewer qualification scope and validity
- conflict-of-interest declaration and disqualification
- separation from target development/vendor incentives
- periodic golden-fixture calibration
- high-risk decision requires currently-qualified reviewer
- qualification expiry invalidates future authority, not historical signed decisions

### DD-013 — Data Rights / Consent / Purpose Provenance — P1 / `REFINE_EXISTING`
Training and evidence data must carry purpose/rights provenance, not only tenant identity.

### DD-014 — Rules of Engagement / External-effect Authorization — P1 / `REFINE_FR_FRESH_001`
Preserve the FR-FRESH-001 design and bind it to FR-FIN-05/06/07 destructive or externally-visible tests.

### DD-015 — Tenant Offboarding Closure — P1 / `REFINE_FR_FRESH_003`
Preserve FR-FRESH-003 and add final-target vendor/provider tokens, offline bundles, external anchors and shared-corpus derivations to termination settlement.

### DD-016 — Accessibility / Locale / Claim-semantic Integrity — P1 / `REFINE_FR_FRESH_002`
Preserve FR-FRESH-002 and require translated regulatory/assurance claims to retain canonical state/limitation semantics.

### DD-017 — Webhook/Connector External-effect Delivery Semantics — P1 / `REFINE_EXISTING`
Delivery requires idempotency key, retry history, dead-letter state, signature verification and tenant-scoped replay protection.

### DD-018 — Long-running Checkpoint Exactly-once Semantics — P1 / `REFINE_EXISTING`
Pause/resume/retry must distinguish computation replay from externally-visible effect replay; external effects require dedupe/effect receipts.

### DD-019 — DR Split-brain Evidence Ledger Reconciliation — P1 / `REFINE_EXISTING`
Recovery needs an explicit rule for two regions/nodes producing competing evidence heads.

### DD-020 — Vendor/Subprocessor Exit and Continuity — P1 / `REFINE_EXISTING`
Vendor assurance needs replacement/migration, retained evidence access, key/data export and continuity qualification after provider exit.

### DD-021 — Evidence Redaction vs Independent Verifiability — P1 / `REFINE_EXISTING`
Restricted packs may redact sensitive fields, but the verifier must still be able to prove integrity/omission boundaries using commitments or disclosed limitation.

### DD-022 — Break-glass Retrospective Assurance — P1 / `REFINE_EXISTING`
Emergency authority requires TTL, bounded scope, immutable event log, automatic revocation, retrospective independent review and no assurance-tier strengthening.

### DD-023 — Standard/Regulatory Currency and Deprecation — P1 / `REFINE_EXISTING`
Control packs need effective date, supersession, jurisdiction/applicability and stale-policy requalification rules.

### DD-024 — Golden/Benchmark Corpus Anti-contamination — P1 / `REFINE_EXISTING`
Golden fixtures used to qualify validators/reviewers must be separated from training/tuning inputs or contamination must be disclosed and the benchmark invalidated/replaced.

## 4. Triage result
- `VALID_REQUIREMENT / NEW_CROSS_CUTTING_OBLIGATION`: DD-008, DD-010
- `VALID_REQUIREMENT / REFINEMENT`: DD-001~007, DD-009, DD-011~024
- `DUPLICATE`: 0
- `NOT_APPLICABLE`: 0
- `UNKNOWN`: 0

The absence of duplicates here does **not** mean every item is a new subsystem. Most deliberately refine existing owners. The discovery result is that the final-target denominator strengthens cross-cutting obligations enough that the old product-scope closure cannot remain authoritative without these refinements.

## 5. Missing-design closure rules
For every DD item, implementation handoff requires all of the following or an explicit `NOT_APPLICABLE` proof:
- owner component/service,
- canonical data/contract fields,
- state transition and invalidation behavior,
- authority/SoD rule,
- API/event/effect semantics where relevant,
- UI disclosure of HOLD/UNKNOWN/limitation,
- audit/evidence receipt,
- negative/failure/recovery fixture,
- independent-verification oracle.

## 6. Requirement Universe impact
The next Product Design Requirement epoch MUST include the normative statements in this document after Requirement Authority admission. EPOCH 0002 remains historical. The existing EPOCH 0003 candidate MUST be regenerated after this document is admitted; a candidate created before this discovery is stale by definition.

## 7. Design-completeness status
Current status after this wave:

`PRODUCT_DESIGN_DELTA_DISCOVERY_MATERIALIZED / 24_VALID_DELTA_OBLIGATIONS / MISSING_DESIGN_CLOSURE_IN_PROGRESS / NON_FINAL`

Design Lock, FinalApproval, Production GO and Commercial GO remain prohibited.