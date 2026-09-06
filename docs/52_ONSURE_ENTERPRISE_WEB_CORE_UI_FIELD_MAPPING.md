# ONSure Enterprise Web — Core → UI Field Mapping

- Status: DETAILED_DESIGNED_NONFINAL
- Issue: #94
- Scope: P01~P04 read-only vertical slice
- Rule: Web-derived business truth = 0
- W12: NOT_RUN

## 1. Contract principle

The Web surface formats, orders and labels authoritative data. It MUST NOT derive assurance progression, blocking causes, evidence validity, freshness truth, approval authority or project-level assurance by applying its own domain rules.

Display-only transformations are allowed: localization, truncation, relative time formatting, visual grouping and safe sorting of already-authoritative fields.

## 2. Required Core read objects

### ProjectSummary
- `projectId`
- `name`
- `tenantId`
- `criticality` if authoritative
- `targetCount`
- `assuranceDistribution` if authoritative
- `blockingTargetCount` if authoritative
- `lastAuthoritativeChangeAt`
- `coreRevision`

### TargetSummary
- `targetId`
- `projectId`
- `name`
- `targetType`
- `canonicalAssuranceState`
- `progressionStatus`
- `blockingConditionCount`
- `evidenceReceiptCount`
- `freshnessState` if supplied by Core
- `lastVerifiedAt` if supplied by Core
- `coreRevision`

### AssuranceSnapshot
- `targetId`
- `canonicalState`
- `progressionStatus`
- `satisfiedRequirements[]`
- `unresolvedRequirements[]`
- `blockingConditions[]`
- `supportingEvidence[]`
- `lastVerifiedAt`
- `authority`
- `sourceIdentity`
- `coreRevision`

### AttentionItem
Optional P01 read-model item. Required before `Needs Attention` can contain production data.
- `attentionId`
- `category`
- `severity`
- `summary`
- `subjectRef`
- `relatedObjectRefs[]`
- `openedAt` or authoritative age source
- `authority`

The Web MUST NOT synthesize AttentionItem rows by scanning blockers, stale receipts, findings or pending decisions unless Core defines and returns an equivalent projection.

### BlockingCondition
- `conditionId`
- `category`
- `severity`
- `canonicalReasonCode`
- `summary`
- `relatedObjectRefs[]`
- `blocksProgressionTo`
- `authority`

### EvidenceSummary
- `evidenceId`
- `evidenceType`
- `subjectRef`
- `boundedResult`
- `createdAt`
- `freshnessState`
- `sourceIdentity`
- `receiptHash`
- `authority`

### EvidenceReceipt
- `evidenceId`
- `receiptType`
- `subjectRef`
- `verificationRunRef`
- `boundedResult`
- `supportsAssuranceState` if authoritative
- `sourceIdentity`
- `environmentIdentity`
- `executedAt`
- `executorIdentity`
- `receiptHash`
- `parentHash`
- `freshnessState`
- `lineageRefs[]`
- `approvalRefs[]`
- `findingRefs[]`
- `relationshipCompleteness` or equivalent completeness semantics
- `rawTechnicalMetadata`
- `authority`
- `coreRevision`

## 3. P01 Assurance Workspace mapping

| UI element | Core field | Web may transform | Web MUST NOT do |
|---|---|---|---|
| Tenant context | authorized session/resource context + ProjectSummary.tenantId | safe display label | trust caller-supplied tenant identity |
| User role context | authenticated principal/policy context | label | invent effective role |
| Project context | ProjectSummary.name/projectId | label/link | infer project from client input alone |
| Current Assurance | AssuranceSnapshot.canonicalState | localize display label | calculate from evidence/findings |
| Progression | AssuranceSnapshot.progressionStatus | badge/icon | map state to blocked using Web rules |
| Blocking | AssuranceSnapshot.blockingConditions | count/group | create blockers from missing-looking fields |
| Next unresolved requirement | AssuranceSnapshot.unresolvedRequirements | choose deterministic contract order; truncate | invent recommendation or priority |
| Needs Attention | AttentionItem[] | sort only by authoritative order/fields | synthesize attention rows locally |
| Important changes | authoritative event/change read model | human formatting | dump raw audit stream as activity |
| NONFINAL context | authoritative overall/product context | presentation | infer finality from current stage |

If Core does not yet provide `AttentionItem[]` or an authoritative change feed, those sections remain unavailable rather than using derived production values.

## 4. P02 Project / Target mapping

| UI element | Core field | Constraints |
|---|---|---|
| Project target count | ProjectSummary.targetCount | exact authoritative count only |
| Assurance distribution | ProjectSummary.assuranceDistribution | render distribution; no average/score |
| Targets independently verified | assuranceDistribution[INDEPENDENTLY_VERIFIED] or explicit authoritative count | label must refer to targets, never imply Project-level state |
| Blocking target count | ProjectSummary.blockingTargetCount | no local scan as authority unless contract explicitly defines equivalent projection |
| Target state | TargetSummary.canonicalAssuranceState | no inheritance |
| Target progression | TargetSummary.progressionStatus | separate column/semantic |
| Evidence count | TargetSummary.evidenceReceiptCount | count is not evidence quality/completeness |
| Freshness | TargetSummary.freshnessState | absent means NOT_AVAILABLE |
| Last verified | TargetSummary.lastVerifiedAt | relative time is display-only; absent means NOT_AVAILABLE |

## 5. P03 Target Detail mapping

### Header
- Name: TargetSummary.name
- ID: TargetSummary.targetId
- Type: TargetSummary.targetType
- Current Assurance: AssuranceSnapshot.canonicalState
- Progression: AssuranceSnapshot.progressionStatus
- Target freshness: TargetSummary.freshnessState when authoritative
- Revision: AssuranceSnapshot.coreRevision

The Web MUST NOT display a locally calculated count such as `1 STALE` unless Core explicitly supplies that aggregate. A target-level freshness enum may be displayed directly.

### Why this state?
Render only:
- `satisfiedRequirements[]`
- `unresolvedRequirements[]`
- `blockingConditions[]`

Web may group by status/category. It cannot create a requirement, mark one satisfied, or decide which blocker prevents a stage unless Core provides that relation.

### Evidence panel
Render `supportingEvidence[]` / EvidenceSummary objects. Receipt count does not imply stage completion. Bounded result labels must retain their test/verification scope, e.g. `Runtime: PASSED`, not an unqualified global `PASS`.

## 6. P04 Evidence Receipt mapping

### Summary section
- Receipt ID ← EvidenceReceipt.evidenceId
- Type ← receiptType
- Subject ← subjectRef
- Result ← boundedResult
- Supports ← supportsAssuranceState only when explicitly authoritative
- Executed ← executedAt
- Executor ← executorIdentity
- Authority ← authority

### Source section
- Source ← sourceIdentity
- Environment ← environmentIdentity
- Core revision ← coreRevision

### Integrity section
- Receipt hash ← receiptHash
- Parent hash ← parentHash

### Relationships
- Findings ← findingRefs[]
- Approvals ← approvalRefs[]
- Lineage trace ← lineageRefs[]

`Decision not present` or `none linked` may be shown only when `relationshipCompleteness` (or equivalent contract semantics) confirms that the relationship query is complete. Missing/unavailable fields must not be converted to empty arrays by Web adapters.

`Command`, `exitCode` and similar technical values may be rendered from explicitly typed/specified entries in `rawTechnicalMetadata`; the UI must not infer them from log text.

## 7. Allowed Web-only fields

These are presentation metadata and may be generated by Web:
- localized display label
- CSS semantic class derived directly from canonical enum mapping
- shortened hash display while retaining full value for copy
- relative-time string alongside absolute timestamp
- breadcrumb presentation generated from already-authorized resource context
- pagination/view controls

They MUST NOT become persisted business authority.

## 8. Forbidden Web-derived fields

The following are forbidden unless later introduced as explicit Core fields:
- `assuranceScore`
- `projectAssuranceAverage`
- `confidencePercent`
- `calculatedCurrentState`
- `calculatedBlocker`
- `recommendedNextAction`
- `calculatedFreshness`
- `approvalEligibility`
- `finalReady`
- `productionReady`
- `commercialReady`

## 9. Availability semantics

Adapters must preserve three distinct states:
1. value present
2. authoritative empty/none
3. value unavailable/unknown/not authorized

A DTO design that collapses all three into `null`, `0` or `[]` is rejected.

## 10. Traceability gate

Before P01~P04 implementation can be treated as Core-connected, each visible business field must be traceable to one entry in this mapping or a documented successor contract. Any unmapped business value is presumed Web-invented until proven otherwise.
