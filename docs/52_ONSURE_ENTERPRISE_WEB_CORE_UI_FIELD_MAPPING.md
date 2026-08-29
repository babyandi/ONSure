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
- `rawTechnicalMetadata`
- `authority`
- `coreRevision`

## 3. P01 Assurance Workspace mapping

| UI element | Core field | Web may transform | Web MUST NOT do |
|---|---|---|---|
| Project context | ProjectSummary.name/projectId | label/link | infer tenant/project from client input alone |
| Current Assurance | AssuranceSnapshot.canonicalState | localize display label | calculate from evidence/findings |
| Progression | AssuranceSnapshot.progressionStatus | badge/icon | map state to blocked using Web rules |
| Blocking | AssuranceSnapshot.blockingConditions | count/group | create blockers from missing-looking fields |
| Next unresolved requirement | AssuranceSnapshot.unresolvedRequirements | choose deterministic display order supplied by contract; truncate | invent recommendation or priority |
| Important changes | authoritative event/change read model | human formatting | dump raw audit stream as activity |
| NONFINAL context | authoritative overall/product context | presentation | infer finality from current stage |

If the API does not yet provide an authoritative change feed, `Important Changes` remains unavailable rather than using fake sample events in production code.

## 4. P02 Project / Target mapping

| UI element | Core field | Constraints |
|---|---|---|
| Project target count | ProjectSummary.targetCount | exact authoritative count only |
| Assurance distribution | ProjectSummary.assuranceDistribution | render distribution; no average/score |
| Blocking target count | ProjectSummary.blockingTargetCount | no local scan as authority unless contract explicitly defines client aggregation as equivalent read projection |
| Target state | TargetSummary.canonicalAssuranceState | no inheritance |
| Target progression | TargetSummary.progressionStatus | separate column/semantic |
| Evidence count | TargetSummary.evidenceReceiptCount | count is not evidence quality/completeness |
| Freshness | TargetSummary.freshnessState | absent means NOT_AVAILABLE |

## 5. P03 Target Detail mapping

### Header
- Name: TargetSummary.name
- ID: TargetSummary.targetId
- Type: TargetSummary.targetType
- Current Assurance: AssuranceSnapshot.canonicalState
- Progression: AssuranceSnapshot.progressionStatus
- Revision: AssuranceSnapshot.coreRevision

### Why this state?
Render only:
- `satisfiedRequirements[]`
- `unresolvedRequirements[]`
- `blockingConditions[]`

Web may group by status/category. It cannot create a requirement, mark one satisfied, or decide which blocker prevents a stage unless Core provides that relation.

### Evidence panel
Render `supportingEvidence[]` / EvidenceSummary objects. Receipt count does not imply stage completion.

## 6. P04 Evidence Receipt mapping

### Summary section
- Receipt ID ← EvidenceReceipt.evidenceId
- Type ← receiptType
- Subject ← subjectRef
- Result ← boundedResult
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

Empty relationship arrays mean `none linked` only when the Core contract explicitly says the query is complete. Missing/unavailable fields must not be converted to empty arrays by Web adapters.

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
