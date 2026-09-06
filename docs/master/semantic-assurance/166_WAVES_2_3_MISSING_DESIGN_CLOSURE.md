# 166 Waves 2~3 Missing Design Closure

Status: `DESIGN_ONLY / NON_FINAL`
Parent: `165_BLIND_DESIGN_DISCOVERY_WAVES_2_3.md`

## 1. 목적
DD-025~040에 대해 요구사항 문장만 추가하고 끝내지 않고, 구현자가 추가 의미 추정 없이 내려갈 최소 설계요소를 고정한다.

## 2. 공통 closure template
각 DD obligation은 최소 다음을 가진다.
- canonical owner
- canonical object/record
- state/currentness/invalidation semantics
- authority/SoD
- user-visible disclosure/claim ceiling
- evidence/receipt
- negative/recovery fixture
- independent oracle

## 3. 상세 설계

### DD-025 Regulatory Effective-Date & Change Impact
Object: `RegulatoryRequirementVersion`, `RegulatoryChangeImpactSet`.
State: DRAFT→APPROVED→EFFECTIVE→SUPERSEDED→RETIRED, plus FUTURE_EFFECTIVE/HOLD.
Invariant: evaluation uses the version effective for exact jurisdiction/entity/business/effect-time; a newer publication date alone does not retroactively rewrite old evidence.
Evidence: source digest, publisher, effective interval, mapping version, impacted requirement/certificate ids.
Negative fixture: superseded rule used as current; future rule applied early; transition grace ignored.
Oracle: independently reconstruct applicable version from effective-date context.

### DD-026 Applicability Context Change
Object: `ApplicabilityContextSnapshot`, `ApplicabilityDelta`.
Trigger: legal entity/business/data class/materiality/region/product-use changes.
Invariant: high-impact context delta invalidates old applicability closure until recomputed.
UI: explicitly shows `REASSESSMENT_REQUIRED`; no silent continuation of CURRENT.
Fixture: region changes but old privacy controls remain claimed applicable-complete.

### DD-027 Financial Business-Date / Trading-Calendar Integrity
Object: `BusinessCalendarProfile`, `BusinessTimeBinding`.
Fields: timezone, calendar generation, holiday source, cutoff, business_date, wall_clock, monotonic/time authority refs.
Invariant: machine state uses canonical timestamp; financial rule may additionally depend on business-date generation.
Fixture: DST boundary, holiday override, EOD batch crossing midnight.

### DD-028 Black-box Access Constraint Claim Ceiling
Object: `TargetAccessCapabilityManifest` and `UnobservableDimensionSet`.
States: OBSERVABLE / INDIRECTLY_OBSERVABLE / UNOBSERVABLE / ACCESS_DENIED / PROVIDER_LIMITED.
Invariant: required dimension not observable => corresponding claim cannot PASS; ceiling is PARTIAL/NOT_PROVEN/HOLD according to policy floor.
Evidence: access method, rate/token quota, logs/admin/model/data availability, ToS constraint.
Fixture: no model weights/logs yet model-internal robustness declared fully verified.

### DD-029 Third-Party Target Authorization Chain
Object: `TargetTestingAuthorization`.
Fields: owner/delegator, target ids/endpoints, allowed tests/effects, forbidden effects, resource ceiling, validity, revocation, emergency contact, plan digest.
State: DRAFT→APPROVED→ACTIVE→EXPIRED/REVOKED.
Invariant: authorization expiry/revocation blocks new active-effect tests immediately.
Fixture: customer has ONSure license but attempts fuzz/load against unowned SaaS endpoint.

### DD-030 Organization Restructuring & Ownership Transfer
Object: `OrganizationLineageEvent`, `TenantTransferPlan`, `AuthorityRebindingReceipt`.
States: PROPOSED→DUAL_CONTROL_REVIEW→TRANSFER_WINDOW→REBINDING→REQUALIFICATION→COMPLETE/HOLD.
Invariant: evidence history remains immutable; access/approval authority is rebound explicitly, never inferred from tenant rename. Split/merge produces lineage edges, not destructive id rewrite.
SoD: old and new owner authorities cannot be a single unverified principal for material transfer.
Fixture: M&A transfers tenant but former admin token still accesses evidence.

### DD-031 Sovereign Residency Migration & DR Boundary
Object: `ResidencyProfile`, `ResidencyMigrationPlan`, `SovereigntyQualificationReceipt`.
Fields: data classes, allowed regions, key jurisdiction, subprocessors, backup/replica regions, export constraints.
Invariant: DR FULL_SERVICE does not imply sovereignty PASS until residency qualification succeeds.
Fixture: failover to prohibited region; backup encrypted by key outside approved jurisdiction.

### DD-032 Vendor/Model/Service EOL & Exit Requalification
Object: `ExternalDependencyLifecycle`, `ReplacementQualification`.
States: ACTIVE→DEPRECATED→EOL_ANNOUNCED→REPLACEMENT_REQUIRED→REQUALIFYING→REPLACED/UNAVAILABLE.
Invariant: alias/API compatibility does not prove semantic equivalence; material dependency change invalidates affected claims.
Fixture: model alias silently remapped after provider retirement.

### DD-033 Cryptographic Agility & Historical Verification
Object: `CryptoProfileGeneration`, `HistoricalVerificationAnchor`, `CryptoMigrationReceipt`.
Invariant: active signing/verification uses policy-approved algorithm floor; historical signatures retain fact status with strength/currentness limitation and, where required, renewed anchor before algorithm retirement.
Fixture: SHA/signature algorithm deprecated but certificate still displayed as current strongest tier without limitation.

### DD-034 Regulator/Auditor Selective Disclosure & Chain of Custody
Object: `ExternalEvidencePack`, `DisclosureManifest`, `CustodyTransferReceipt`.
State: PREPARED→REDACTION_VERIFIED→AUTHORIZED→TRANSFERRED→RECEIPT_CONFIRMED→REVOKED/EXPIRED.
Invariant: redaction cannot alter canonical decision/limitation meaning; recipient-purpose binding is explicit.
Fixture: pack redaction removes NOT_RUN limitation while retaining PASS headline.

### DD-035 Privileged Support/Admin Intervention Contamination
Object: `PrivilegedEffectReceipt`.
Fields: actor, reason/ticket, command/change, before/after digest, affected case/evidence/policy/validator ids, approvals, rollback, requalification impact.
Invariant: any privileged mutation to material assurance state invalidates dependent CLEAN/qualification until reconciled.
Fixture: operator fixes DB row manually then old CLEAN evidence remains current.

### DD-036 Collusion / Quorum Common-Ownership Independence
Object: `IndependenceControlGraph`.
Edges: COMMON_EMPLOYER, COMMON_ADMIN, COMMON_MODEL_PIPELINE, SHARED_DRAFT, SHARED_HIDDEN_ACCESS, PRIOR_VERDICT_EXPOSURE.
Invariant: distinct account/key is necessary but not sufficient for independent/four-eyes claim.
Fixture: two reviewers are separate accounts but both read same proposed verdict before review.

### DD-037 Long-Horizon Evidence Readability & Schema Migration
Object: `EvidenceFormatGeneration`, `SchemaMigrationReceipt`, `VerifierCompatibilityManifest`.
Invariant: original bytes/digest remain immutable; migrations are append-only transformations with source→target relation and dual read-back.
Fixture: old evidence retained but parser/runtime needed to interpret it no longer exists.

### DD-038 Financial Transaction / Market-Effect Safe Simulation
Object: `FinancialEffectTestProfile`.
Modes: SIMULATED / SANDBOX / CERTIFIED_TEST_ENV / PRODUCTION_AUTHORIZED.
Invariant: production financial effect requires explicit high-risk authorization, ceiling, abort contact, idempotency/reconciliation and post-effect confirmation; safer mode is default.
Fixture: load/adversarial test accidentally sends real trade/payment.

### DD-039 Monitoring Blind-Spot / Telemetry Loss Claim Ceiling
Object: `ObservationCoverageReport`.
Fields: expected signals, observed signals, gap intervals, sampling rate, collector status, clock quality, truncation/drop count.
Invariant: zero observed failures with insufficient observation cannot prove operating effectiveness.
Fixture: SIEM collector down for half the observation window but system marked effective.

### DD-040 Discovery Exhaustion Protocol
Object: `DiscoveryEpoch`, `DiscoveryLensRun`, `DiscoveryCandidateLedger`, `DiscoverySaturationReceipt`.
Required lens classes: CUSTOMER_LIFECYCLE, FAILURE_RECOVERY, ADVERSARIAL, REGULATORY_STANDARD, FINANCIAL_OPERATION, EXTERNAL_DEPENDENCY, ORGANIZATION_GOVERNANCE, META_ASSURANCE, NEGATIVE_SPACE.
Run independence: at least one blind run must not consume prior accepted candidate conclusions, only frozen baseline scope.
Saturation candidate requires: all mandatory lenses executed, candidate triage 100%, no unresolved P0, and at least 2 consecutive independent discovery waves with zero new P0 and below configured P1 novelty ceiling using unchanged target/authority scope. Any authority/scope/material standard change invalidates saturation.
Anti-cheat: repeated use of the same prompt/checklist/model context is not an independent wave.

## 4. Design completeness for DD-025~040
For these 16 obligations only:
- owner: 16/16
- canonical object: 16/16
- state/invalidation: 16/16
- authority/SoD: 16/16
- disclosure/claim ceiling: 16/16
- evidence: 16/16
- negative/recovery fixture definition: 16/16
- independent oracle: 16/16

This is companion design completeness, not Contract/Implementation/Test execution completeness.

## 5. Remaining after this document
- admit DD-025~040 into Requirement Authority through an allowlisted normative source/explicit authority population change
- regenerate Product Design Requirement Universe
- map DD-001~040 to FR-FIN and granular requirements
- run applicability/global trace/reverse orphan/contradiction/design coverage
- qualify Discovery Saturation Protocol itself

Current: `WAVES_2_3_DELTA_MISSING_DESIGN_ZERO_FOR_16 / GLOBAL_DESIGN_DISCOVERY_STILL_NONFINAL`.
